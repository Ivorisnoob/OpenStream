package com.ivor.openstream.presentation.player.session

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.WatchProgressRepository
import com.ivor.openstream.presentation.player.NextEpisodeTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** What is loaded in the shared player, with everything needed to reopen it and save progress. */
data class NowPlaying(
    val mediaType: String,
    val tmdbId: Int,
    val season: Int,
    val episode: Int,
    val downloadId: String?,
    val mediaUri: String,
    val title: String,
    val episodeTitle: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val stillPath: String?,
    val next: NextEpisodeTarget?,
    val server: VideoServer?
) {
    val isMovie: Boolean get() = mediaType == "movie"

    val subtitle: String
        get() = if (isMovie) "" else "S$season · E$episode" + (episodeTitle?.let { " · $it" } ?: "")

    fun matches(mediaType: String, tmdbId: Int, season: Int, episode: Int): Boolean =
        this.mediaType == mediaType && this.tmdbId == tmdbId && this.season == season && this.episode == episode
}

/** A pending stop: after a set time, or when the current episode or movie ends. */
sealed interface SleepTimer {
    data class After(val minutes: Int, val endsAtMs: Long) : SleepTimer
    data object EndOfEpisode : SleepTimer
}

/**
 * One player for the whole app. The player screen attaches to it; leaving that screen keeps
 * playback going in the mini player instead of tearing the stream down. Watch progress is saved
 * here, so it keeps being recorded whichever surface is showing the video.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: Cache,
    private val watchProgressRepository: WatchProgressRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Headers the current stream needs (Referer, Origin...). Applied to every request it makes. */
    @Volatile
    private var requestHeaders: Map<String, String> = emptyMap()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimer?>(null)
    val sleepTimer: StateFlow<SleepTimer?> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null
    private var endedBySleepTimer = false

    private var lastSavedPositionMs = -1L
    private var completionRecordedFor: String? = null

    /** Created on first use, on the main thread, by whichever surface shows video first. */
    val player: ExoPlayer by lazy { buildPlayer() }

    fun setRequestHeaders(headers: Map<String, String>) {
        requestHeaders = headers
    }

    /** Called by the player screen whenever the playing item or its metadata changes. */
    fun update(nowPlaying: NowPlaying) {
        val previous = _nowPlaying.value
        if (previous == null || !previous.matches(nowPlaying.mediaType, nowPlaying.tmdbId, nowPlaying.season, nowPlaying.episode)) {
            lastSavedPositionMs = -1L
            completionRecordedFor = null
            endedBySleepTimer = false
        }
        _nowPlaying.value = nowPlaying
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
    }

    /** Arms, replaces or (with null) cancels the sleep timer. */
    fun setSleepTimer(timer: SleepTimer?) {
        sleepJob?.cancel()
        sleepJob = null
        _sleepTimer.value = timer
        if (timer is SleepTimer.After) {
            sleepJob = scope.launch {
                delay((timer.endsAtMs - System.currentTimeMillis()).coerceAtLeast(0L))
                player.pause()
                _sleepTimer.value = null
            }
        }
    }

    /**
     * True once after playback ended because of the end-of-episode timer, so the screen skips
     * auto-playing the next episode.
     */
    fun consumeEndedBySleepTimer(): Boolean {
        val ended = endedBySleepTimer
        endedBySleepTimer = false
        return ended
    }

    /** Ends the session: saves where the user stopped and unloads the stream. */
    fun stop() {
        setSleepTimer(null)
        recordProgress(force = true)
        player.stop()
        player.clearMediaItems()
        _nowPlaying.value = null
        requestHeaders = emptyMap()
    }

    private fun buildPlayer(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val upstream = ResolvingDataSource.Factory(DefaultDataSource.Factory(context, http)) { spec ->
            spec.withAdditionalHeaders(requestHeaders)
        }
        // Downloads live in this cache, so offline copies play without a connection.
        val dataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                playWhenReady = true
                // Debug builds log load errors, format switches and dropped frames under "EventLogger".
                if (BuildConfig.DEBUG) addAnalyticsListener(EventLogger())
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (!isPlaying) recordProgress(force = true)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            recordProgress(force = true, ended = true)
                            // Registered before the screen's listener, so this is set by the time it asks.
                            if (_sleepTimer.value == SleepTimer.EndOfEpisode) {
                                endedBySleepTimer = true
                                setSleepTimer(null)
                            }
                        }
                    }
                })
                scope.launch {
                    while (true) {
                        delay(PROGRESS_TICK_MS)
                        if (isPlaying) recordProgress(force = false)
                    }
                }
            }
    }

    private fun recordProgress(force: Boolean, ended: Boolean = false) {
        val item = _nowPlaying.value ?: return
        val playingUri = player.currentMediaItem?.localConfiguration?.uri ?: return
        // The screen may already describe the next item while the player still plays the last one.
        if (playingUri != Uri.parse(item.mediaUri)) return

        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val position = if (ended) duration else player.currentPosition.coerceAtLeast(0L)
        if (position < WatchProgress.MIN_SAVED_POSITION_MS) return

        val key = "${item.mediaType}:${item.tmdbId}:${item.season}:${item.episode}"
        val completed = position.toFloat() / duration >= WatchProgress.COMPLETION_FRACTION
        if (completed && completionRecordedFor == key) return
        if (!force && !completed && abs(position - lastSavedPositionMs) < PROGRESS_SAVE_INTERVAL_MS) return

        lastSavedPositionMs = position
        watchProgressRepository.record(
            WatchProgress(
                tmdbId = item.tmdbId,
                mediaType = item.mediaType,
                season = item.season,
                episode = item.episode,
                title = item.title,
                episodeTitle = item.episodeTitle,
                posterPath = item.posterPath,
                backdropPath = item.backdropPath,
                stillPath = item.stillPath,
                positionMs = position,
                durationMs = duration,
                completed = completed
            )
        )
        if (completed) {
            completionRecordedFor = key
            queueNext(item)
        }
    }

    /** Surfaces the following episode in Continue Watching once this one is finished. */
    private fun queueNext(item: NowPlaying) {
        val next = item.next ?: return
        scope.launch {
            val existing = watchProgressRepository.get(item.mediaType, item.tmdbId, next.season, next.episode)
            if (existing?.completed == true) return@launch
            val queuedAt = System.currentTimeMillis() + 1
            watchProgressRepository.record(
                existing?.copy(updatedAt = queuedAt) ?: WatchProgress(
                    tmdbId = item.tmdbId,
                    mediaType = item.mediaType,
                    season = next.season,
                    episode = next.episode,
                    title = item.title,
                    episodeTitle = next.title,
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    stillPath = next.stillPath,
                    updatedAt = queuedAt
                )
            )
        }
    }

    private companion object {
        const val PROGRESS_TICK_MS = 1_000L
        const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
    }
}
