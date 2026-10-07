package com.ivor.openstream.data.downloads

import android.content.Context
import android.net.ConnectivityManager
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.domain.model.DownloadTarget
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the next few unwatched episodes of in-progress shows offline and deletes smart rows once
 * their episode is watched. Rows the user downloaded by hand (`isSmart == false`) are never
 * touched. Every show and every row is guarded, so one failure never aborts a run.
 */
@Singleton
class SmartDownloads @Inject constructor(
    private val appSettingsStore: AppSettingsStore,
    private val watchProgressRepository: WatchProgressRepository,
    private val downloadRepository: DownloadRepository,
    private val animeRepository: AnimeRepository,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var started = false

    /** Starts the debounced observer; call once from [com.ivor.openstream.OpenStreamApp.onCreate]. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(appSettingsStore.settings, watchProgressRepository.allProgress()) { _, _ -> Unit }
                .debounce(2.seconds)
                .collect { reconcile() }
        }
    }

    /** Fire-and-forget trigger, e.g. right after the toggle is switched on. */
    fun reconcileNow() {
        scope.launch { reconcile() }
    }

    /** Single-flight: a run already in progress wins, a concurrent caller drops out. */
    suspend fun reconcile() {
        if (!mutex.tryLock()) return
        try {
            withContext(Dispatchers.IO) { reconcileLocked() }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun reconcileLocked() {
        val settings = appSettingsStore.current
        if (!settings.smartDownloads) return
        val keepAhead = settings.smartKeepAhead

        val all = watchProgressRepository.allProgress().first()
        val completedKeys = all
            .filter { it.completed }
            .map { progressKey(it.mediaType, it.tmdbId, it.season, it.episode) }
            .toSet()

        // Deletion runs even on metered networks; only new enqueues are gated below.
        runCatching {
            val smartRows = downloadRepository.getAllDownloads().first().filter { it.isSmart }
            for (row in smartRows) {
                try {
                    if (progressKey(row.mediaType, row.tmdbId, row.season, row.episode) in completedKeys) {
                        downloadRepository.removeDownload(row.downloadId)
                    }
                } catch (_: Exception) {
                    // One bad row never blocks the rest.
                }
            }
        }

        val metered = runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true
        }.getOrDefault(false)
        if (settings.wifiOnlyDownloads && metered) return

        val series = all.filter { !it.isMovie }
            .groupBy { it.tmdbId }
            .filterValues { rows -> rows.any { !it.completed } }

        for ((tmdbId, rows) in series.entries.take(MAX_SHOWS_PER_RUN)) {
            try {
                reconcileShow(tmdbId, rows, keepAhead)
            } catch (_: Exception) {
                // One bad show never blocks the rest.
            }
        }
    }

    private suspend fun reconcileShow(tmdbId: Int, rows: List<WatchProgress>, keepAhead: Int) {
        val details = animeRepository.getMediaDetails(tmdbId, "tv").getOrNull() ?: return
        val current = rows.maxByOrNull { it.updatedAt } ?: return
        val (curS, curE) = current.season to current.episode

        val existing = downloadRepository.getDownloadsForTitle(tmdbId, "tv").first()
            // Completed, active, failed (retry is manual) and user-paused rows all block
            // a smart re-enqueue: re-queueing a paused row would wipe its progress.
            .filter {
                it.status == DownloadStatus.COMPLETED ||
                    DownloadStatus.isActive(it.status) ||
                    it.status == DownloadStatus.FAILED ||
                    it.status == DownloadStatus.PAUSED
            }
            .map { it.season to it.episode }
            .toSet()

        val seasonNumbers = details.seasons.orEmpty()
            .filter { it.seasonNumber > 0 }
            .map { it.seasonNumber }
            .sorted()

        val targets = mutableListOf<Pair<Int, EpisodeDto>>()
        var season = curS
        while (targets.size < keepAhead) {
            val episodes = animeRepository.getSeasonDetails(tmdbId, season).getOrNull()?.episodes.orEmpty()
                .filter { isReleased(it.airDate) }
                .filter { if (season == curS) it.episodeNumber > curE else true }
                .sortedBy { it.episodeNumber }
            for (episode in episodes) {
                if (targets.size >= keepAhead) break
                targets += season to episode
            }
            if (targets.size >= keepAhead) break
            season = seasonNumbers.firstOrNull { it > season } ?: break
        }

        for ((seasonNumber, episode) in targets) {
            try {
                if (seasonNumber to episode.episodeNumber in existing) continue
                if (context.filesDir.usableSpace < MIN_FREE_BYTES) continue
                downloadRepository.enqueue(
                    listOf(
                        DownloadTarget(
                            tmdbId = tmdbId,
                            mediaType = "tv",
                            season = seasonNumber,
                            episode = episode.episodeNumber,
                            showTitle = details.name,
                            episodeTitle = episode.name,
                            posterPath = details.posterPath,
                            stillPath = episode.stillPath ?: details.backdropPath,
                            year = details.date.take(4).toIntOrNull(),
                            isSmart = true
                        )
                    )
                )
            } catch (_: Exception) {
                // One bad episode never blocks the rest.
            }
        }
    }

    private companion object {
        const val MAX_SHOWS_PER_RUN = 25
        const val MIN_FREE_BYTES = 500L * 1024L * 1024L

        fun progressKey(mediaType: String, tmdbId: Int, season: Int, episode: Int) =
            "$mediaType:$tmdbId:$season:$episode"

        fun isReleased(airDate: String?): Boolean {
            val date = airDate?.takeIf { it.isNotBlank() } ?: return true
            return runCatching { !LocalDate.parse(date.take(10)).isAfter(LocalDate.now()) }
                .getOrDefault(true)
        }
    }
}
