package com.ivor.openstream.data.downloads

import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.AutoDeleteWatched
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Deletes finished downloads of episodes and movies the active profile has watched to the end,
 * once [AutoDeleteWatched] says it is time: half an hour after (so the credits can still play from
 * the file), a day after, or a week after. Runs when progress or the setting changes, and at
 * every app start, which is when the day and week delays come due.
 *
 * Smart Downloads cleans up its own episodes either way; this covers everything else too.
 */
@Singleton
class WatchedDownloadCleaner @Inject constructor(
    private val appSettingsStore: AppSettingsStore,
    private val watchProgressRepository: WatchProgressRepository,
    private val downloadRepository: DownloadRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var started = false

    /** Starts watching; call once from [com.ivor.openstream.OpenStreamApp.onCreate]. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Only what decides a run: positions are saved every few seconds while playing.
            val finished = watchProgressRepository.allProgress()
                .map { rows -> rows.filter { it.completed }.map { Triple(it.tmdbId, it.season, it.episode) }.toSet() }
                .distinctUntilChanged()
            val setting = appSettingsStore.settings.map { it.autoDeleteWatched }.distinctUntilChanged()
            combine(setting, finished) { _, _ -> Unit }
                .debounce(3.seconds)
                .collect { clean() }
        }
    }

    private suspend fun clean() {
        if (!mutex.tryLock()) return
        try {
            val delay = appSettingsStore.current.autoDeleteWatched.delayMs
            if (delay < 0) return
            val now = System.currentTimeMillis()
            val finishedAt = watchProgressRepository.allProgress().first()
                .filter { it.completed }
                // A movie has one entry whatever its season and episode say.
                .associate { progress ->
                    val key = if (progress.isMovie) "movie:${progress.tmdbId}" else "tv:${progress.tmdbId}:${progress.season}:${progress.episode}"
                    key to progress.updatedAt
                }
            val downloads = downloadRepository.getAllDownloads().first()
                .filter { it.status == DownloadStatus.COMPLETED }
            for (download in downloads) {
                val key = if (download.mediaType == "movie") {
                    "movie:${download.tmdbId}"
                } else {
                    "tv:${download.tmdbId}:${download.season}:${download.episode}"
                }
                val watchedAt = finishedAt[key] ?: continue
                if (now - watchedAt < delay) continue
                // One bad row never stops the rest.
                runCatching { downloadRepository.removeDownload(download.downloadId) }
            }
        } finally {
            mutex.unlock()
        }
    }
}
