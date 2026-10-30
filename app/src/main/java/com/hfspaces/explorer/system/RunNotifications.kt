package com.hfspaces.explorer.system

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hfspaces.explorer.R
import com.hfspaces.explorer.data.model.SpaceTask
import com.hfspaces.explorer.data.model.TaskStatus
import kotlin.math.roundToInt

/** Builds and posts the app's notifications. */
class RunNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_ACTIVE, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Runs in progress")
                    .setDescription("Shown while Spaces are running in the background")
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_RESULTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Finished runs")
                    .setDescription("Tells you when a run finishes or fails while you're away")
                    .build()
            )
        )
    }

    /** The ongoing notification that keeps the foreground service alive. */
    fun ongoing(active: List<SpaceTask>, now: Long = System.currentTimeMillis()): Notification {
        val first = active.firstOrNull()
        val title = when {
            active.size > 1 -> "${active.size} runs in progress"
            first != null -> "Running ${first.space.displayTitle}"
            else -> "Finishing up…"
        }
        val text = first?.let { describe(it, now) } ?: ""
        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_stat_space)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                activity(
                    if (active.size == 1 && first != null) DeepLinks.runnerIntent(context, first.space.id)
                    else DeepLinks.routeIntent(context, "tasks"),
                    REQUEST_OPEN
                )
            )
            .addAction(0, if (active.size > 1) "Stop all" else "Stop", service(RunService.stopAllIntent(context), REQUEST_STOP))
        if (active.size > 1) {
            builder.setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    active.take(5).forEach { style.addLine("${it.space.displayTitle}: ${describe(it, now)}") }
                }
            )
        }
        val fraction = first?.progressFraction
        when {
            active.size == 1 && fraction != null -> builder.setProgress(100, (fraction * 100).roundToInt(), false)
            active.isNotEmpty() -> builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    /** Posts a "done" or "failed" notification for a run the user isn't looking at. */
    fun finished(task: SpaceTask, thumbnailPath: String?) {
        if (!canPost()) return
        val ok = task.status == TaskStatus.COMPLETED
        val title = if (ok) "${task.space.displayTitle} finished" else "${task.space.displayTitle} failed"
        val text = if (ok) {
            val saved = task.savedMediaIds.size
            "Done in %.0fs".format(task.elapsedSeconds()) + if (saved > 0) " · $saved saved" else ""
        } else {
            task.error ?: "The run didn't complete"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_stat_space)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(activity(DeepLinks.runnerIntent(context, task.space.id), task.id.hashCode()))
        thumbnailPath?.let(::loadThumbnail)?.let { bitmap ->
            builder.setLargeIcon(bitmap)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap).bigLargeIcon(null as Bitmap?))
        }
        if (!ok) builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        @Suppress("MissingPermission") // checked in canPost()
        manager.notify(task.id.hashCode(), builder.build())
    }

    fun canPost(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun describe(task: SpaceTask, now: Long): String {
        val elapsed = "%.0fs".format(task.elapsedSeconds(now))
        val eta = task.etaSeconds?.takeIf { it > 1 }?.let { " · ~${it.roundToInt()}s left" } ?: ""
        return "${task.progressMessage ?: "Starting…"} · $elapsed$eta"
    }

    private fun activity(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun service(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val CHANNEL_ACTIVE = "runs_active"
        const val CHANNEL_RESULTS = "runs_results"
        const val ONGOING_ID = 1001
        private const val REQUEST_OPEN = 1
        private const val REQUEST_STOP = 2

        /** Decodes a downsampled bitmap (about [maxSide] px) for notifications and widgets. */
        fun loadThumbnail(path: String, maxSide: Int = 512): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
