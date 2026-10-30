package com.hfspaces.explorer.system

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.hfspaces.explorer.HfSpacesApplication
import com.hfspaces.explorer.R
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.MediaRepository
import com.hfspaces.explorer.data.repository.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps launcher shortcuts pointing at the Spaces the user runs most recently and favourites. */
class ShortcutsUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val favorites: FavoritesRepository,
    private val tasks: TaskManager
) {
    fun start() {
        scope.launch {
            combine(
                tasks.tasks.map { list -> list.map { it.space }.distinctBy { it.id }.take(MAX) },
                favorites.observe()
            ) { recent, favs -> pickShortcutSpaces(recent, favs, maxShortcuts()) }
                .distinctUntilChanged()
                .collect { spaces -> runCatching { publish(spaces) } }
        }
    }

    private fun maxShortcuts() = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceIn(1, MAX)

    private fun publish(spaces: List<HfSpace>) {
        val shortcuts = spaces.mapIndexed { rank, space ->
            ShortcutInfoCompat.Builder(context, "space:${space.id}")
                .setShortLabel("${space.displayEmoji} ${space.displayTitle}".take(25))
                .setLongLabel("Run ${space.displayTitle}".take(45))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_run))
                .setIntent(DeepLinks.runnerIntent(context, space.id))
                .setRank(rank)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    companion object {
        private const val MAX = 4
    }
}

/** Recently run Spaces first, then favourites, without duplicates. */
fun pickShortcutSpaces(recent: List<HfSpace>, favorites: List<HfSpace>, max: Int): List<HfSpace> =
    (recent + favorites).filter { it.isRunnable || it.sdk == null }.distinctBy { it.id }.take(max)

/** Home-screen widget showing the newest saved output; tap opens the gallery. */
class LatestOutputWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as HfSpacesApplication
        val pending = goAsync()
        app.container.applicationScope.launch {
            try {
                app.container.widgetUpdater.update()
            } finally {
                pending.finish()
            }
        }
    }
}

class WidgetUpdater(private val context: Context, private val media: MediaRepository) {
    suspend fun update() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, LatestOutputWidget::class.java))
        if (ids.isEmpty()) return
        val latest = media.latest()
        val views = withContext(Dispatchers.IO) {
            RemoteViews(context.packageName, R.layout.widget_latest_output).apply {
                val bitmap = latest?.takeIf { it.type == GeneratedMediaType.IMAGE }?.localPath
                    ?.let { RunNotifications.loadThumbnail(it, maxSide = 400) }
                if (bitmap != null) {
                    setImageViewBitmap(R.id.widget_image, bitmap)
                    setViewVisibility(R.id.widget_image, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widget_image, View.GONE)
                }
                if (latest != null) {
                    setTextViewText(R.id.widget_title, latest.textContent?.take(120)?.takeIf { bitmap == null } ?: latest.title)
                    setTextViewText(R.id.widget_subtitle, "${latest.spaceEmoji ?: "🤗"} ${latest.spaceTitle}")
                } else {
                    setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                    setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_empty_subtitle))
                }
                setOnClickPendingIntent(
                    R.id.widget_root,
                    android.app.PendingIntent.getActivity(
                        context,
                        0,
                        DeepLinks.routeIntent(context, "media"),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
        manager.updateAppWidget(ids, views)
    }
}
