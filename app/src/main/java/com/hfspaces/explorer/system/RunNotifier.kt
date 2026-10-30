package com.hfspaces.explorer.system

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.data.repository.MediaRepository
import com.hfspaces.explorer.data.repository.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Tracks whether any activity is started, i.e. whether the user can see the app. */
class AppVisibility : Application.ActivityLifecycleCallbacks {
    private var started = 0
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    override fun onActivityStarted(activity: Activity) {
        started++
        _visible.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        _visible.value = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * Connects runs to the system: starts [RunService] while anything runs and posts a notification
 * for runs that finish while the app is in the background (in the app, a snackbar does that).
 */
class RunNotifier(
    private val context: Context,
    private val scope: CoroutineScope,
    private val tasks: TaskManager,
    private val media: MediaRepository,
    private val notifications: RunNotifications,
    private val visibility: AppVisibility,
    private val notifyOnFinish: () -> Boolean
) {
    fun start() {
        notifications.createChannels()
        scope.launch {
            tasks.tasks
                .map { list -> list.any { it.isActive } }
                .distinctUntilChanged()
                .collect { anyActive ->
                    if (anyActive) {
                        // Allowed: runs are started from the visible app. Ignore if the OS refuses anyway.
                        runCatching { ContextCompat.startForegroundService(context, RunService.startIntent(context)) }
                    }
                }
        }
        scope.launch {
            tasks.finished.collect { task ->
                if (task.status != TaskStatus.CANCELLED && !visibility.visible.value && notifyOnFinish()) {
                    notifications.finished(task, media.firstImagePath(task.savedMediaIds))
                }
            }
        }
    }
}
