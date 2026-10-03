package com.ivor.openstream.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ivor.openstream.MainActivity
import com.ivor.openstream.R
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.WatchProgressRepository
import com.ivor.openstream.presentation.components.badge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws the Continue Watching widget.
 *
 * Reading and drawing both happen in the app's process; only inflating and compositing happen in
 * the launcher's, which is why the layout uses plain colour resources instead of theme attributes.
 */
@Singleton
class ContinueWatchingWidget @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchProgressRepository: WatchProgressRepository,
    private val artwork: ContinueWatchingArtwork
) {

    /** Repaints every placed instance. Safe to call when none are placed. */
    suspend fun render() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ContinueWatchingProvider::class.java))
        if (ids.isEmpty()) return
        // One read for all instances, not one per instance.
        val item = watchProgressRepository.continueWatching(limit = 1).first().firstOrNull()
        manager.updateAppWidget(ids, viewsFor(item))
    }

    private suspend fun viewsFor(item: WatchProgress?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_continue_watching)
        if (item == null) {
            views.setViewVisibility(R.id.widget_continue_filled, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_continue_empty, android.view.View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_continue_root, openHome())
            return views
        }

        views.setViewVisibility(R.id.widget_continue_filled, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.widget_continue_empty, android.view.View.GONE)
        views.setTextViewText(R.id.widget_continue_badge, item.badge())
        views.setTextViewText(R.id.widget_continue_title, item.title)
        views.setTextViewText(
            R.id.widget_continue_subtitle,
            item.episodeTitle?.takeIf { !item.isMovie } ?: item.mediaType.replaceFirstChar { it.uppercase() }
        )

        // A queued "up next" episode has no position yet, so its fraction is 0; showing an empty
        // bar there reads as "broken" rather than "not started".
        val started = item.fraction > 0f
        views.setViewVisibility(R.id.widget_continue_progress, if (started) android.view.View.VISIBLE else android.view.View.GONE)
        if (started) {
            // RemoteViews.setProgressBar only takes progress, max and indeterminate here; the bar
            // takes its colour from the theme's progressBarStyle, so there is no tint to set.
            views.setProgressBar(R.id.widget_continue_progress, (item.fraction * 100).toInt(), 100, false)
        }

        // Every field above comes from Room, so the card is complete offline; only art needs the
        // network. On failure the placeholder colour behind the ImageView is what shows.
        artwork.load(item)?.let { views.setImageViewBitmap(R.id.widget_continue_art, it) }
        views.setContentDescription(R.id.widget_continue_root, item.title)

        views.setOnClickPendingIntent(R.id.widget_continue_root, openDetails(item))
        return views
    }

    /**
     * The tap reuses the TMDB deep link the app already handles, so the widget needs no new
     * navigation: `MainActivity` is singleTask with an `onNewIntent`, its VIEW filter covers
     * themoviedb.org movie and TV paths, and `DeepLinks` turns that into a Details route.
     */
    private fun openDetails(item: WatchProgress): PendingIntent {
        val type = if (item.isMovie) "movie" else "tv"
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(android.net.Uri.parse("https://www.themoviedb.org/$type/${item.tmdbId}"))
        return PendingIntent.getActivity(context, item.tmdbId, intent, immutableFlags())
    }

    private fun openHome(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        return PendingIntent.getActivity(context, 0, intent, immutableFlags())
    }

    private fun immutableFlags() =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}