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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
            // Only what a run decides on: the player saves positions every few seconds, and a run
            // per save would ask TMDB about every show while something is streaming.
            val watched = watchProgressRepository.allProgress()
                .map { rows -> rows.map { listOf(it.tmdbId, it.season, it.episode, it.completed) }.toSet() }
                .distinctUntilChanged()
            val smartSettings = appSettingsStore.settings
                .map { it.smartDownloads to it.smartKeepAhead }
                .distinctUntilChanged()
            combine(smartSettings, watched) { _, _ -> Unit }
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
        val now = System.currentTimeMillis()
        val completed = all.filter { it.completed }
        val completedKeys = completed
            .map { progressKey(it.mediaType, it.tmdbId, it.season, it.episode) }
            .toSet()
        // Watched a moment ago: the player may still be showing its credits from this very file.
        val justCompletedKeys = completed
            .filter { now - it.updatedAt < DELETE_GRACE_MS }
            .map { progressKey(it.mediaType, it.tmdbId, it.season, it.episode) }
            .toSet()

        // Deletion runs even on metered networks; only new enqueues are gated below.
        runCatching {
            val smartRows = downloadRepository.getAllDownloads().first().filter { it.isSmart }
            for (row in smartRows) {
                try {
                    val key = progressKey(row.mediaType, row.tmdbId, row.season, row.episode)
                    if (key in completedKeys && key !in justCompletedKeys) {
                        downloadRepository.removeDownload(row.downloadId)
                    }
                } catch (_: Exception) {
                    // One bad row never blocks the rest.
                }
            }
        }

        // Nobody asked for these episodes right now, so they wait for Wi-Fi whatever the setting
        // for downloads the user starts by hand.
        val metered = runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true
        }.getOrDefault(true)
        if (metered) return

        // Shows being watched: anything played lately, most recent first. A finished episode
        // counts, since the next one is exactly what should be waiting.
        val series = all.filter { !it.isMovie }
            .groupBy { it.tmdbId }
            .mapValues { (_, rows) -> rows.maxBy { it.updatedAt } }
            .filterValues { now - it.updatedAt < ACTIVE_WINDOW_MS }
            .entries
            .sortedByDescending { it.value.updatedAt }
            .take(MAX_SHOWS_PER_RUN)

        for ((tmdbId, latest) in series) {
            try {
                reconcileShow(tmdbId, latest, completedKeys, keepAhead)
            } catch (_: Exception) {
                // One bad show never blocks the rest.
            }
        }
    }

    private suspend fun reconcileShow(
        tmdbId: Int,
        current: WatchProgress,
        completedKeys: Set<String>,
        keepAhead: Int
    ) {
        // Cached answers are fine: they are a few hours old at most, and the episode notifier
        // refreshes them when new episodes air.
        val details = animeRepository.getMediaDetails(tmdbId, "tv").getOrNull() ?: return
        val (curS, curE) = current.season to current.episode
        // An episode started (or queued as up next) is kept too; a finished one is not.
        val fromEpisode = if (current.completed) curE + 1 else curE

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
            val seasonNumber = season
            val episodes = animeRepository.getSeasonDetails(tmdbId, season).getOrNull()?.episodes.orEmpty()
                .filter { isReleased(it.airDate) }
                .filter { if (seasonNumber == curS) it.episodeNumber >= fromEpisode else true }
                // Already seen: a rewatch further back must not bring these down again.
                .filter { progressKey("tv", tmdbId, seasonNumber, it.episodeNumber) !in completedKeys }
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
        const val MAX_SHOWS_PER_RUN = 5
        /** A show counts as being watched for this long after it was last played. */
        const val ACTIVE_WINDOW_MS = 21L * 24 * 60 * 60 * 1000
        /** How long a just-watched smart episode is kept before it is deleted. */
        const val DELETE_GRACE_MS = 30L * 60 * 1000
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
