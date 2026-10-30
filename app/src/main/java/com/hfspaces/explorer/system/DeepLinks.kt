package com.hfspaces.explorer.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.hfspaces.explorer.MainActivity
import com.hfspaces.explorer.data.gradio.PickedFile
import com.hfspaces.explorer.data.remote.SpaceIds
import com.hfspaces.explorer.data.repository.UploadStaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where an outside entry point (notification, shortcut, widget, share) wants to go. */
sealed interface DeepLink {
    data class Runner(val spaceId: String, val fromTaskId: String? = null) : DeepLink
    data class Screen(val route: String) : DeepLink
    data object ShareTarget : DeepLink
}

/** Content shared into the app, waiting for the user to pick a Space. */
data class SharedContent(val files: List<PickedFile>, val text: String?) {
    val isEmpty: Boolean get() = files.isEmpty() && text.isNullOrBlank()
}

/** Turns incoming intents into navigation requests and holds shared content. */
class DeepLinks(private val scope: CoroutineScope, private val staging: UploadStaging) {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    private val _shared = MutableStateFlow<SharedContent?>(null)
    val shared: StateFlow<SharedContent?> = _shared.asStateFlow()

    private val _sharing = MutableStateFlow(false)

    /** True while shared files are being copied into app storage. */
    val sharing: StateFlow<Boolean> = _sharing.asStateFlow()

    fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> receiveShare(intent)
            else -> {
                val spaceId = intent.getStringExtra(EXTRA_SPACE_ID)
                val route = intent.getStringExtra(EXTRA_ROUTE)
                when {
                    spaceId != null && SpaceIds.isValidSpaceId(spaceId) ->
                        _pending.value = DeepLink.Runner(spaceId, intent.getStringExtra(EXTRA_TASK_ID))
                    route != null && route in ALLOWED_ROUTES -> _pending.value = DeepLink.Screen(route)
                }
            }
        }
    }

    fun consume(link: DeepLink) {
        _pending.compareAndSet(link, null)
    }

    /** Hands shared content to the runner of the chosen Space (once). */
    fun takeShared(): SharedContent? = _shared.value.also { _shared.value = null }

    fun clearShared() {
        _shared.value = null
    }

    private fun receiveShare(intent: Intent) {
        val uris = sharedUris(intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        if (uris.isEmpty() && text == null) return
        _pending.value = DeepLink.ShareTarget
        _sharing.value = true
        scope.launch {
            try {
                val files = uris.take(MAX_SHARED_FILES).mapNotNull { uri -> runCatching { staging.stage(uri) }.getOrNull() }
                _shared.value = SharedContent(files, text)
            } finally {
                _sharing.value = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        )
        Intent.ACTION_SEND_MULTIPLE ->
            (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
        else -> emptyList()
    }

    companion object {
        const val EXTRA_SPACE_ID = "com.hfspaces.explorer.SPACE_ID"
        const val EXTRA_TASK_ID = "com.hfspaces.explorer.TASK_ID"
        const val EXTRA_ROUTE = "com.hfspaces.explorer.ROUTE"
        private const val MAX_SHARED_FILES = 10

        /** Screens outside callers may open; anything else is ignored. */
        val ALLOWED_ROUTES = setOf("media", "tasks", "favourites", "my_spaces", "compare")

        fun runnerIntent(context: Context, spaceId: String, taskId: String? = null): Intent =
            Intent(context, MainActivity::class.java)
                .setAction("com.hfspaces.explorer.OPEN_RUNNER.$spaceId")
                .putExtra(EXTRA_SPACE_ID, spaceId)
                .apply { taskId?.let { putExtra(EXTRA_TASK_ID, it) } }
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun routeIntent(context: Context, route: String): Intent =
            Intent(context, MainActivity::class.java)
                .setAction("com.hfspaces.explorer.OPEN.$route")
                .putExtra(EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
