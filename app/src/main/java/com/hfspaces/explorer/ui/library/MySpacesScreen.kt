package com.hfspaces.explorer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.HfSpaceRuntime
import com.hfspaces.explorer.data.model.SpaceStage
import com.hfspaces.explorer.data.remote.HfHubClient
import com.hfspaces.explorer.data.remote.LogKind
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.ErrorState
import com.hfspaces.explorer.ui.common.FullScreenBox
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.common.SectionCard
import com.hfspaces.explorer.ui.common.SpaceCard
import com.hfspaces.explorer.ui.common.StagePill
import com.hfspaces.explorer.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class MySpacesState(
    val loading: Boolean = true,
    val needsToken: Boolean = false,
    val account: String? = null,
    val spaces: List<HfSpace> = emptyList(),
    val error: String? = null
)

/** Spaces owned by the token's account and its organisations. */
class MySpacesViewModel(private val hub: HfHubClient, private val apiKeys: ApiKeysRepository) : ViewModel() {
    private val _state = MutableStateFlow(MySpacesState())
    val state: StateFlow<MySpacesState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val token = defaultToken(apiKeys)
            if (token == null) {
                _state.value = MySpacesState(loading = false, needsToken = true)
                return@launch
            }
            try {
                val me = hub.whoAmI(token) ?: throw IllegalStateException("Hugging Face rejected your default token")
                val owners = listOfNotNull(me.name) + me.orgs.mapNotNull { it.name }
                val limit = Semaphore(4)
                val listed = owners.map { owner -> async { runCatching { hub.spacesByAuthor(owner, token) }.getOrDefault(emptyList()) } }
                    .awaitAll().flatten().distinctBy { it.id }
                // The listing has no runtime; fetch it so stages are accurate.
                val withRuntime = listed.map { space ->
                    async { limit.withPermit { space.copy(runtime = runCatching { hub.fetchRuntime(space.id, token) }.getOrNull() ?: space.runtime) } }
                }.awaitAll()
                _state.value = MySpacesState(loading = false, account = me.fullname ?: me.name, spaces = withRuntime.sortedBy { it.id.lowercase() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }
}

private suspend fun defaultToken(apiKeys: ApiKeysRepository): String? =
    apiKeys.observe().first().let { keys -> (keys.firstOrNull { it.isDefault } ?: keys.firstOrNull())?.token?.takeIf { it.isNotBlank() } }

@Composable
fun MySpacesScreen(
    viewModel: MySpacesViewModel,
    onBack: () -> Unit,
    onManage: (String) -> Unit,
    onRun: (String) -> Unit,
    onManageKeys: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Spaces")
                        state.account?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.spaces.isNotEmpty(), onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.needsToken -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MessageState(
                        icon = Icons.Outlined.Key,
                        title = "Add a token first",
                        message = "My Spaces lists the Spaces your default Hugging Face token can manage. Restarting or changing hardware needs a token with write access.",
                        actionLabel = "Add a token",
                        onAction = onManageKeys
                    )
                }
                state.loading && state.spaces.isEmpty() -> FullScreenBox { CircularProgressIndicator() }
                state.error != null && state.spaces.isEmpty() -> FullScreenBox { ErrorState(state.error!!, onRetry = viewModel::refresh) }
                state.spaces.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MessageState(icon = Icons.Outlined.Dns, title = "No Spaces yet", message = "Spaces you or your organisations own will show up here.")
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.spaces, key = { it.id }) { space ->
                        SpaceCard(
                            space = space,
                            isFavorite = false,
                            onOpen = { onManage(space.id) },
                            onRun = { onRun(space.id) },
                            onToggleFavorite = { onManage(space.id) },
                            showFavorite = false
                        )
                    }
                }
            }
        }
    }
}

/** Hardware tiers that can be requested. Paid tiers bill the owner. */
val HARDWARE_FLAVORS = listOf(
    "cpu-basic", "cpu-upgrade", "zero-a10g", "t4-small", "t4-medium", "l4x1", "l4x4",
    "a10g-small", "a10g-large", "a10g-largex2", "l40sx1", "a100-large"
)

data class ManageState(
    val runtime: HfSpaceRuntime? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val busy: String? = null,
    val logKind: LogKind = LogKind.RUN,
    val logs: List<String> = emptyList(),
    val logError: String? = null
)

class ManageSpaceViewModel(
    savedState: SavedStateHandle,
    private val hub: HfHubClient,
    private val apiKeys: ApiKeysRepository
) : ViewModel() {
    val spaceId: String = checkNotNull(savedState.get<String>("spaceId"))
    private val _state = MutableStateFlow(ManageState())
    val state: StateFlow<ManageState> = _state.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var logJob: Job? = null

    init {
        refresh()
        streamLogs(LogKind.RUN)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val runtime = hub.fetchRuntime(spaceId, defaultToken(apiKeys))
                _state.update { it.copy(runtime = runtime, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun restart(factory: Boolean) = action(if (factory) "Rebuilding" else "Restarting") { token -> hub.restartSpace(spaceId, token, factory) }
    fun pause() = action("Pausing") { token -> hub.pauseSpace(spaceId, token) }
    fun setHardware(flavor: String) = action("Requesting $flavor") { token -> hub.requestHardware(spaceId, token, flavor) }

    fun streamLogs(kind: LogKind) {
        logJob?.cancel()
        _state.update { it.copy(logKind = kind, logs = emptyList(), logError = null) }
        logJob = viewModelScope.launch {
            val token = defaultToken(apiKeys) ?: run {
                _state.update { it.copy(logError = "Logs need a token with access to this Space") }
                return@launch
            }
            try {
                hub.streamLogs(spaceId, token, kind) { line ->
                    _state.update { it.copy(logs = (it.logs + line).takeLast(MAX_LOG_LINES)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(logError = e.userMessage()) }
            }
        }
    }

    private fun action(label: String, block: suspend (String) -> Unit) {
        viewModelScope.launch {
            val token = defaultToken(apiKeys) ?: run {
                messages.trySend("Add a token with write access first")
                return@launch
            }
            _state.update { it.copy(busy = label) }
            try {
                block(token)
                messages.trySend("$label… the Space will update shortly")
                delay(1500)
                refresh()
                streamLogs(_state.value.logKind)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.trySend(e.userMessage())
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    private companion object {
        const val MAX_LOG_LINES = 500
    }
}

private enum class PendingAction { RESTART, REBUILD, PAUSE }

@Composable
fun ManageSpaceScreen(viewModel: ManageSpaceViewModel, onBack: () -> Unit, onRun: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    var pending by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    var hardwareChoice by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    val logListState = rememberLazyListState()
    LaunchedEffect(state.logs.size) { if (state.logs.isNotEmpty()) logListState.scrollToItem(state.logs.size - 1) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(viewModel.spaceId.substringAfter('/')) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh status") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Status") {
                val runtime = state.runtime
                when {
                    state.loading && runtime == null -> CircularProgressIndicator()
                    runtime == null -> Text(state.error ?: "Unknown", color = MaterialTheme.colorScheme.error)
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StagePill(SpaceStage.from(runtime.stage))
                            Spacer(Modifier.width(8.dp))
                            Text(runtime.hardware?.current ?: runtime.hardware?.requested ?: "no hardware", style = MaterialTheme.typography.bodyMedium)
                        }
                        runtime.errorMessage?.let { Text(it.take(300), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
                state.busy?.let { Text("$it…", style = MaterialTheme.typography.labelMedium) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pending = PendingAction.RESTART }, enabled = state.busy == null) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Restart")
                    }
                    OutlinedButton(onClick = { pending = PendingAction.REBUILD }, enabled = state.busy == null) { Text("Factory rebuild") }
                    OutlinedButton(onClick = { pending = PendingAction.PAUSE }, enabled = state.busy == null) {
                        Icon(Icons.Outlined.Pause, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pause")
                    }
                    TextButton(onClick = { onRun(viewModel.spaceId) }) { Text("Run") }
                }
            }
            SectionCard("Hardware") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HARDWARE_FLAVORS.forEach { flavor ->
                        FilterChip(
                            selected = state.runtime?.hardware?.requested == flavor,
                            onClick = { if (state.runtime?.hardware?.requested != flavor) hardwareChoice = flavor },
                            enabled = state.busy == null,
                            label = { Text(flavor) }
                        )
                    }
                }
            }
            TabRow(selectedTabIndex = state.logKind.ordinal) {
                LogKind.entries.forEach { kind ->
                    Tab(selected = state.logKind == kind, onClick = { viewModel.streamLogs(kind) }, text = { Text(if (kind == LogKind.BUILD) "Build logs" else "Run logs") })
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            ) {
                when {
                    state.logError != null -> Text(state.logError!!, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                    state.logs.isEmpty() -> Text("Waiting for log output…", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    else -> LazyColumn(state = logListState, contentPadding = PaddingValues(12.dp)) {
                        items(state.logs) { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
            }
        }
    }

    pending?.let { action ->
        val (title, body) = when (action) {
            PendingAction.RESTART -> "Restart this Space?" to "Running sessions are interrupted while it restarts."
            PendingAction.REBUILD -> "Factory rebuild?" to "Rebuilds the image from scratch, clearing caches. This can take several minutes."
            PendingAction.PAUSE -> "Pause this Space?" to "It stops running (and billing) until you restart it."
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        PendingAction.RESTART -> viewModel.restart(factory = false)
                        PendingAction.REBUILD -> viewModel.restart(factory = true)
                        PendingAction.PAUSE -> viewModel.pause()
                    }
                    pending = null
                }) { Text("Confirm", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
    hardwareChoice?.let { flavor ->
        AlertDialog(
            onDismissRequest = { hardwareChoice = null },
            title = { Text("Switch to $flavor?") },
            text = {
                Text(
                    if (flavor == "cpu-basic" || flavor == "zero-a10g") "The Space restarts on the new hardware."
                    else "Paid hardware is billed to the Space owner's account by the hour until you switch back. The Space restarts on the new hardware."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setHardware(flavor)
                    hardwareChoice = null
                }) { Text("Switch", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { hardwareChoice = null }) { Text("Cancel") } }
        )
    }
}
