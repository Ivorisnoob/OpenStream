package com.ivor.openstream.presentation.widget

import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repaints the Continue Watching widget as progress changes, while the app process is alive.
 *
 * Every mutation already flows through `WatchProgressRepository` (the player recording progress,
 * Home removing a title, Details marking episodes watched, a profile switch), so subscribing to it
 * covers the lot without the widget being wired into any of them. `continueWatching` follows the
 * active profile, so a switch repaints the widget too.
 *
 * A periodic job would be worse here: the process is already awake during the only window where
 * progress changes, and a WorkManager periodic request is inexact and Doze-deferred. The widget's
 * own `updatePeriodMillis` repairs anything missed while the process was dead.
 */
@Singleton
class ContinueWatchingWidgetRefresher @Inject constructor(
    private val watchProgressRepository: WatchProgressRepository,
    private val widget: ContinueWatchingWidget
) {

    /**
     * [scope] must outlive any Activity, so callers pass the application scope. Nothing renders
     * unless a widget is actually placed.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            watchProgressRepository.continueWatching(limit = 1)
                // Rounding to whole percent keeps a 10s progress save from repainting 270 times
                // an hour for changes too small to see.
                .map { row -> row.firstOrNull()?.renderKey() }
                .distinctUntilChanged()
                .collect { widget.render() }
        }
    }

    private fun WatchProgress.renderKey() =
        listOf(tmdbId, mediaType, season, episode, title, episodeTitle, stillPath, (fraction * 100).toInt())
            .joinToString("|")
}