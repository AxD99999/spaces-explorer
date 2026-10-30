package com.hfspaces.explorer.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.hfspaces.explorer.HfSpacesApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the process in the foreground while Spaces are running, so Android doesn't kill long
 * generations when the user leaves the app. Stops itself once nothing is running.
 */
class RunService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updater: Job? = null

    private val container get() = (application as HfSpacesApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tasks = container.taskManager
        if (intent?.action == ACTION_STOP_ALL) tasks.cancelAll()

        val active = tasks.tasks.value.filter { it.isActive }
        // Must call startForeground promptly after startForegroundService, even if we stop right away.
        ServiceCompat.startForeground(
            this,
            RunNotifications.ONGOING_ID,
            container.notifications.ongoing(active),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        if (updater?.isActive != true) {
            updater = scope.launch {
                // Poll rather than collect: streaming runs update many times a second.
                while (isActive) {
                    val running = tasks.tasks.value.filter { it.isActive }
                    if (running.isEmpty()) {
                        ServiceCompat.stopForeground(this@RunService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                    if (container.notifications.canPost()) {
                        @Suppress("MissingPermission")
                        NotificationManagerCompat.from(this@RunService)
                            .notify(RunNotifications.ONGOING_ID, container.notifications.ongoing(running))
                    }
                    delay(UPDATE_INTERVAL_MS)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_ALL = "com.hfspaces.explorer.STOP_ALL"
        private const val UPDATE_INTERVAL_MS = 1_000L

        fun startIntent(context: Context) = Intent(context, RunService::class.java)

        fun stopAllIntent(context: Context) = Intent(context, RunService::class.java).setAction(ACTION_STOP_ALL)
    }
}
