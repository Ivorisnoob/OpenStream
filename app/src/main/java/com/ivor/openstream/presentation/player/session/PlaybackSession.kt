package com.ivor.openstream.presentation.player.session

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.cast.CastMediaItemConverter
import com.ivor.openstream.data.cast.CastMediaProxy
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.data.streaming.DownloadRequestHeaderStore
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.ImagePrefixStrippingDataSource
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.WatchProgressRepository
import com.ivor.openstream.presentation.player.NextEpisodeTarget
import com.ivor.openstream.presentation.player.components.SUBTITLES_OFF
import com.ivor.openstream.presentation.player.components.sameLanguage
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
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
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
    val server: VideoServer?,
    /** Where playback of this item starts when it is (re)loaded on a new player, such as a TV. */
    val startPositionMs: Long = 0L
) {
    val isMovie: Boolean get() = mediaType == "movie"

    /** Identifies the title and episode, whichever stream or player is showing it. */
    val key: String get() = "$mediaType:$tmdbId:$season:$episode"

    val subtitle: String
        get() = if (isMovie) "" else "S$season · E$episode" + (episodeTitle?.let { " · $it" } ?: "")

    fun matches(mediaType: String, tmdbId: Int, season: Int, episode: Int): Boolean =
        this.mediaType == mediaType && this.tmdbId == tmdbId && this.season == season && this.episode == episode
}

/** What the Cast button and the casting screen show. */
data class CastStatus(
    /** Google Play services Cast works on this device. */
    val supported: Boolean = false,
    /** A receiver was seen on the network. */
    val devicesAvailable: Boolean = false,
    val connecting: Boolean = false,
    /** Name of the receiver while a session is up; null when not casting. */
    val deviceName: String? = null
) {
    val isCasting: Boolean get() = deviceName != null
}

/** Something that went wrong while casting. [fromStream] errors mean the receiver couldn't play it. */
data class CastError(val message: String, val fromStream: Boolean)

/** A subtitle the TV can show; [id] matches the [SubtitleDto] it came from. */
data class CastSubtitleOption(val id: String, val label: String, val language: String?)

data class CastSubtitles(val options: List<CastSubtitleOption> = emptyList(), val activeId: String? = null)

/** A pending stop: after a set time, or when the current episode or movie ends. */
sealed interface SleepTimer {
    data class After(val minutes: Int, val endsAtMs: Long) : SleepTimer
    data object EndOfEpisode : SleepTimer
}

/**
 * One player for the whole app. The player screen attaches to it; leaving that screen keeps
 * playback going in the mini player instead of tearing the stream down. Watch progress is saved
 * here, so it keeps being recorded whichever surface is showing the video.
 *
 * Casting: while a Cast session is up, [castPlayer] plays instead of the local [player]. Connecting
 * moves the current item to the TV at the phone's position; disconnecting brings it back to the
 * phone, paused where the TV was. Media reaches the receiver through [CastMediaProxy].
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: Cache,
    private val watchProgressRepository: WatchProgressRepository,
    private val castProxy: CastMediaProxy,
    private val downloadHeaders: DownloadRequestHeaderStore
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

    private var localPlayerCreated = false

    init {
        // Saves progress every few seconds, and remembers where the TV is (its state is gone once
        // the session ends).
        scope.launch {
            while (true) {
                delay(PROGRESS_TICK_MS)
                val remote = castPlayer?.takeIf { castConnected }
                if (remote != null) {
                    if (remote.currentMediaItem?.mediaId == castRequestedKey) {
                        lastCastPositionMs = remote.currentPosition
                        remote.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { lastCastDurationMs = it }
                    }
                    if (remote.isPlaying) recordProgress(force = false)
                } else if (localPlayerCreated && player.isPlaying) {
                    recordProgress(force = false)
                }
            }
        }
    }

    /** Created on first use, on the main thread, by whichever surface shows video first. */
    val player: ExoPlayer by lazy { buildPlayer().also { localPlayerCreated = true } }

    // region Cast state

    private var castContext: CastContext? = null
    private var castInitAttempted = false

    /** Plays on the TV while [CastStatus.isCasting]; null when Cast isn't available. */
    var castPlayer: CastPlayer? = null
        private set

    private var castConnected = false
    private val _castStatus = MutableStateFlow(CastStatus())
    val castStatus: StateFlow<CastStatus> = _castStatus.asStateFlow()

    private val _castError = MutableStateFlow<CastError?>(null)
    val castError: StateFlow<CastError?> = _castError.asStateFlow()

    private val _castLoading = MutableStateFlow(false)
    val castLoading: StateFlow<Boolean> = _castLoading.asStateFlow()

    private val _castSubtitles = MutableStateFlow(CastSubtitles())
    val castSubtitles: StateFlow<CastSubtitles> = _castSubtitles.asStateFlow()

    /** What was last sent to the receiver (NowPlaying.key and source URL). */
    private var castRequestedKey: String? = null
    private var castRequestedUri: String? = null
    private var castLoadJob: Job? = null
    private var castTextStyleApplied = false
    /** Subtitle id -> receiver track id for what the receiver has loaded. */
    private var castLoadedTracks: Map<String, Long> = emptyMap()
    private var subtitleCandidates: List<SubtitleDto> = emptyList()
    private var preferredSubtitleLanguage: String? = null
    /** Last position/duration read from the receiver; its state is gone once the session ends. */
    private var lastCastPositionMs = 0L
    private var lastCastDurationMs = 0L

    /** Whichever player is showing the current item: the TV while casting, else the phone. */
    val activePlayer: Player
        get() = castPlayer?.takeIf { castConnected } ?: player

    /** The playing position, read from the TV while casting. */
    fun currentPositionMs(): Long = when {
        castConnected -> castPlayer?.currentPosition ?: lastCastPositionMs
        localPlayerCreated -> player.currentPosition
        else -> 0L
    }

    /** Route selector for the Cast device picker; null when Cast isn't available. */
    val castRouteSelector: MediaRouteSelector?
        get() = runCatching { castContext?.mergedSelector }.getOrNull()

    // endregion

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
        if (castConnected) syncCast()
    }

    fun togglePlayback() {
        val active = activePlayer
        if (active.isPlaying || (castConnected && active.playWhenReady)) active.pause() else active.play()
    }

    /** Arms, replaces or (with null) cancels the sleep timer. */
    fun setSleepTimer(timer: SleepTimer?) {
        sleepJob?.cancel()
        sleepJob = null
        _sleepTimer.value = timer
        if (timer is SleepTimer.After) {
            sleepJob = scope.launch {
                delay((timer.endsAtMs - System.currentTimeMillis()).coerceAtLeast(0L))
                activePlayer.pause()
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

    /** Ends the session: saves where the user stopped and unloads the stream (on the TV too). */
    fun stop() {
        setSleepTimer(null)
        recordProgress(force = true)
        if (castConnected) {
            castLoadJob?.cancel()
            castPlayer?.stop()
            castPlayer?.clearMediaItems()
            castRequestedKey = null
            castRequestedUri = null
            castLoadedTracks = emptyMap()
            _castSubtitles.value = CastSubtitles()
            _castLoading.value = false
            _castError.value = null
        }
        if (localPlayerCreated) {
            player.stop()
            player.clearMediaItems()
        }
        _nowPlaying.value = null
        requestHeaders = emptyMap()
    }

    // region Casting

    /**
     * Connects to the Cast framework. Call once on the main thread (from the activity); does nothing
     * without Google Play services.
     */
    fun initCast() {
        if (castInitAttempted) return
        castInitAttempted = true
        val cast = runCatching { CastContext.getSharedInstance(context) }
            .onFailure { Log.w(TAG, "Cast unavailable: ${it.message}") }
            .getOrNull() ?: return
        castContext = cast
        val remote = CastPlayer(cast, CastMediaItemConverter())
        remote.addListener(castListener)
        remote.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() = onCastConnected()
            override fun onCastSessionUnavailable() = onCastDisconnected()
        })
        castPlayer = remote
        cast.addCastStateListener { refreshCastStatus() }
        refreshCastStatus()
        // A session that survived an app restart is already up.
        if (remote.isCastSessionAvailable) onCastConnected()
    }

    /** Disconnects from the TV; playback carries on (paused) on the phone. */
    fun stopCasting() {
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
    }

    /** Hardware volume keys while casting change the TV's volume. Returns true when handled. */
    fun adjustCastVolume(up: Boolean): Boolean {
        if (!castConnected) return false
        val session = currentCastSession() ?: return false
        return runCatching {
            session.volume = (session.volume + if (up) VOLUME_STEP else -VOLUME_STEP).coerceIn(0.0, 1.0)
        }.isSuccess
    }

    /**
     * Subtitles the screen found for the current item, and the language the user last chose
     * ([SUBTITLES_OFF] for off). The TV gets the preferred language on (re)load.
     */
    fun setSubtitleCandidates(candidates: List<SubtitleDto>, preferredLanguage: String?) {
        val changed = candidates != subtitleCandidates || preferredLanguage != preferredSubtitleLanguage
        subtitleCandidates = candidates
        preferredSubtitleLanguage = preferredLanguage
        if (!changed) return
        updateCastSubtitleOptions()
        if (!castConnected || castRequestedKey == null) return
        // Subtitles in the user's language showed up after the TV started: reload once to add them.
        val wanted = preferredLanguage?.takeUnless { it == SUBTITLES_OFF } ?: return
        if (_castSubtitles.value.activeId != null) return
        val match = candidates.firstOrNull { sameLanguage(it.language, wanted) } ?: return
        if (match.id !in castLoadedTracks) reloadOnCast(forcedSubtitleId = match.id)
    }

    /** Shows [id] on the TV (null turns subtitles off). */
    fun selectCastSubtitle(id: String?) {
        val client = currentCastSession()?.remoteMediaClient ?: return
        if (id == null) {
            client.setActiveMediaTracks(LongArray(0))
            _castSubtitles.value = _castSubtitles.value.copy(activeId = null)
            return
        }
        val trackId = castLoadedTracks[id]
        if (trackId != null) {
            client.setActiveMediaTracks(longArrayOf(trackId))
            _castSubtitles.value = _castSubtitles.value.copy(activeId = id)
        } else {
            // Receivers can't add tracks to loaded media: reload in place with this one on.
            reloadOnCast(forcedSubtitleId = id)
        }
    }

    /** Tries the current stream on the TV again, from where it was. */
    fun retryCast() = reloadOnCast(forcedSubtitleId = _castSubtitles.value.activeId)

    private fun currentCastSession(): CastSession? =
        runCatching { castContext?.sessionManager?.currentCastSession }.getOrNull()

    private fun refreshCastStatus() {
        val cast = castContext ?: return
        val state = runCatching { cast.castState }.getOrDefault(CastState.NO_DEVICES_AVAILABLE)
        _castStatus.value = CastStatus(
            supported = true,
            devicesAvailable = state != CastState.NO_DEVICES_AVAILABLE,
            connecting = state == CastState.CONNECTING,
            deviceName = if (castConnected) {
                currentCastSession()?.castDevice?.friendlyName ?: "TV"
            } else {
                null
            }
        )
    }

    private fun onCastConnected() {
        castConnected = true
        castTextStyleApplied = false
        _castError.value = null
        refreshCastStatus()
        val item = _nowPlaying.value
        if (item == null) {
            adoptRemoteItem()
            return
        }
        val position = if (localPlayerCreated) player.currentPosition else item.startPositionMs
        if (localPlayerCreated) {
            player.pause()
            // Keep the item (to resume locally later) but stop buffering it.
            player.stop()
        }
        val remote = castPlayer
        if (remote?.currentMediaItem?.mediaId == item.key) {
            // Rejoined a session already playing this: keep going.
            castRequestedKey = item.key
            castRequestedUri = item.mediaUri
            return
        }
        loadOnCast(item, position, forcedSubtitleId = null)
    }

    private fun onCastDisconnected() {
        recordProgressAt(lastCastPositionMs, lastCastDurationMs)
        castConnected = false
        castLoadJob?.cancel()
        castRequestedKey = null
        castRequestedUri = null
        castLoadedTracks = emptyMap()
        _castSubtitles.value = CastSubtitles(activeId = null)
        _castLoading.value = false
        _castError.value = null
        refreshCastStatus()
        _isPlaying.value = false
        updateCastSubtitleOptions()

        // Carry on on the phone from where the TV was, paused.
        val item = _nowPlaying.value ?: return
        val uri = Uri.parse(item.mediaUri)
        val local = player
        if (local.currentMediaItem?.localConfiguration?.uri != uri) {
            setRequestHeaders(headersFor(item))
            local.setMediaItem(MediaItem.fromUri(uri), lastCastPositionMs)
        } else {
            local.seekTo(lastCastPositionMs)
        }
        local.playWhenReady = false
        local.prepare()
    }

    /** The screen changed episode or source while casting: send the new one to the TV. */
    private fun syncCast() {
        val item = _nowPlaying.value ?: return
        if (item.key == castRequestedKey && item.mediaUri == castRequestedUri) return
        val start = if (item.key == castRequestedKey) currentPositionMs() else item.startPositionMs
        loadOnCast(item, start, forcedSubtitleId = null)
    }

    private fun reloadOnCast(forcedSubtitleId: String?) {
        val item = _nowPlaying.value ?: return
        if (!castConnected) return
        loadOnCast(item, currentPositionMs(), forcedSubtitleId)
    }

    private fun loadOnCast(item: NowPlaying, startPositionMs: Long, forcedSubtitleId: String?) {
        val remote = castPlayer ?: return
        castRequestedKey = item.key
        castRequestedUri = item.mediaUri
        castTextStyleApplied = false
        _castError.value = null
        _castLoading.value = true
        val headers = headersFor(item)
        val subtitles = pickCastSubtitles(forcedSubtitleId)
        val receiver = currentCastSession()?.castDevice?.inetAddress
        castLoadJob?.cancel()
        castLoadJob = scope.launch {
            val built = withContext(Dispatchers.IO) {
                runCatching { buildCastItem(item, headers, subtitles.first, subtitles.second, receiver) }
            }
            if (!castConnected || castRequestedKey != item.key) return@launch
            built.onSuccess { (mediaItem, tracks) ->
                castLoadedTracks = tracks
                _castSubtitles.value = _castSubtitles.value.copy(activeId = subtitles.second?.takeIf { it in tracks })
                updateCastSubtitleOptions()
                remote.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
                remote.playWhenReady = true
                remote.prepare()
            }.onFailure { error ->
                Log.w(TAG, "Could not prepare the cast stream", error)
                _castLoading.value = false
                _castError.value = CastError(
                    message = error.message ?: "Couldn't send this to the TV.",
                    fromStream = false
                )
            }
        }
    }

    /** Subtitles to send (the chosen one first) and the id to start with, if any. */
    private fun pickCastSubtitles(forcedId: String?): Pair<List<SubtitleDto>, String?> {
        val preferred = preferredSubtitleLanguage
        val activeId = forcedId ?: preferred
            ?.takeUnless { it == SUBTITLES_OFF }
            ?.let { language -> subtitleCandidates.firstOrNull { sameLanguage(it.language, language) }?.id }
        val active = subtitleCandidates.firstOrNull { it.id == activeId }
        val ordered = (listOfNotNull(active) + subtitleCandidates.filter { it.id != activeId })
            .distinctBy { it.url }
            .take(MAX_CAST_SUBTITLES)
        return ordered to active?.id
    }

    private fun updateCastSubtitleOptions() {
        val options = subtitleCandidates.distinctBy { it.url }.map { subtitle ->
            CastSubtitleOption(
                id = subtitle.id,
                label = subtitle.display ?: subtitle.language?.uppercase() ?: "Subtitles",
                language = subtitle.language
            )
        }
        _castSubtitles.value = _castSubtitles.value.copy(options = options)
    }

    /** Blocking: probes the stream and builds proxied URLs. Runs on the IO dispatcher. */
    private fun buildCastItem(
        item: NowPlaying,
        headers: Map<String, String>,
        subtitles: List<SubtitleDto>,
        activeSubtitleId: String?,
        receiver: InetAddress?
    ): Pair<MediaItem, Map<String, Long>> {
        val url = castProxy.mediaUrl(item.mediaUri, headers, receiver)
            ?: throw IOException("Connect this phone to the same Wi-Fi network as the TV.")
        val probe = castProxy.probe(item.mediaUri, headers)
        val tracks = linkedMapOf<String, Long>()
        val configurations = subtitles.mapNotNull { subtitle ->
            val subtitleUrl = castProxy.subtitleUrl(subtitle.url, headers, receiver) ?: return@mapNotNull null
            tracks[subtitle.id] = tracks.size + 1L
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                .setMimeType("text/vtt")
                .setId(subtitle.id)
                .setLabel(subtitle.display ?: subtitle.language?.uppercase() ?: "Subtitles")
                .setLanguage(subtitle.language)
                .setSelectionFlags(if (subtitle.id == activeSubtitleId) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val extras = Bundle().apply {
            putBoolean(CastMediaItemConverter.EXTRA_IS_EPISODE, !item.isMovie)
            probe.hlsVideoSegmentFormat?.let { putString(CastMediaItemConverter.EXTRA_HLS_VIDEO_FORMAT, it) }
            putString(EXTRA_MEDIA_TYPE, item.mediaType)
            putInt(EXTRA_TMDB_ID, item.tmdbId)
            putInt(EXTRA_SEASON, item.season)
            putInt(EXTRA_EPISODE, item.episode)
            putString(EXTRA_SOURCE_URI, item.mediaUri)
            item.downloadId?.let { putString(EXTRA_DOWNLOAD_ID, it) }
            putString(EXTRA_TITLE, item.title)
            item.episodeTitle?.let { putString(EXTRA_EPISODE_TITLE, it) }
            item.posterPath?.let { putString(EXTRA_POSTER, it) }
            item.backdropPath?.let { putString(EXTRA_BACKDROP, it) }
            item.stillPath?.let { putString(EXTRA_STILL, it) }
        }
        val artwork = (item.backdropPath ?: item.stillPath ?: item.posterPath)
            ?.let { Uri.parse("https://image.tmdb.org/t/p/w1280$it") }
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setSubtitle(item.subtitle.takeIf { it.isNotEmpty() })
            .setAlbumTitle(item.title.takeUnless { item.isMovie })
            .setArtworkUri(artwork)
            .setExtras(extras)
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(item.key)
            .setUri(url)
            .setMimeType(probe.mimeType)
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(configurations)
            .build()
        return mediaItem to tracks
    }

    /** After an app restart the TV may still be playing: rebuild what's playing from its extras. */
    private fun adoptRemoteItem() {
        val remote = castPlayer ?: return
        val mediaItem = remote.currentMediaItem ?: return
        val extras = mediaItem.mediaMetadata.extras ?: return
        val source = extras.getString(EXTRA_SOURCE_URI) ?: return
        val item = NowPlaying(
            mediaType = extras.getString(EXTRA_MEDIA_TYPE) ?: return,
            tmdbId = extras.getInt(EXTRA_TMDB_ID),
            season = extras.getInt(EXTRA_SEASON, 1),
            episode = extras.getInt(EXTRA_EPISODE, 1),
            downloadId = extras.getString(EXTRA_DOWNLOAD_ID),
            mediaUri = source,
            title = extras.getString(EXTRA_TITLE) ?: mediaItem.mediaMetadata.title?.toString().orEmpty(),
            episodeTitle = extras.getString(EXTRA_EPISODE_TITLE),
            posterPath = extras.getString(EXTRA_POSTER),
            backdropPath = extras.getString(EXTRA_BACKDROP),
            stillPath = extras.getString(EXTRA_STILL),
            next = null,
            server = null
        )
        castRequestedKey = item.key
        castRequestedUri = item.mediaUri
        _nowPlaying.value = item
        // The receiver keeps fetching through the proxy; bring it back up on its saved port.
        scope.launch(Dispatchers.IO) { castProxy.start() }
    }

    private fun headersFor(item: NowPlaying): Map<String, String> =
        item.server?.headers?.takeIf { it.isNotEmpty() }
            ?: if (item.downloadId != null) downloadHeaders.headersFor(Uri.parse(item.mediaUri)) else emptyMap()

    private val castListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!castConnected) return
            _isPlaying.value = isPlaying
            if (!isPlaying) recordProgress(force = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!castConnected) return
            when (playbackState) {
                Player.STATE_READY -> {
                    _castLoading.value = false
                    _castError.value = null
                    if (!castTextStyleApplied) {
                        castTextStyleApplied = true
                        runCatching {
                            currentCastSession()?.remoteMediaClient
                                ?.setTextTrackStyle(TextTrackStyle.fromSystemSettings(context))
                        }
                    }
                }
                Player.STATE_ENDED -> {
                    _castLoading.value = false
                    recordProgress(force = true, ended = true)
                    if (_sleepTimer.value == SleepTimer.EndOfEpisode) {
                        endedBySleepTimer = true
                        setSleepTimer(null)
                    }
                }
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!castConnected) return
            Log.w(TAG, "Receiver error: ${error.errorCodeName} ${error.message}")
            _castLoading.value = false
            _castError.value = CastError(
                message = "The TV couldn't play this source.",
                fromStream = true
            )
        }
    }

    // endregion

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
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(ImagePrefixStrippingDataSource.Factory(dataSource))
            )
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                playWhenReady = true
                // Debug builds log load errors, format switches and dropped frames under "EventLogger".
                if (BuildConfig.DEBUG) addAnalyticsListener(EventLogger())
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (castConnected) return
                        _isPlaying.value = isPlaying
                        if (!isPlaying) recordProgress(force = true)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (castConnected) return
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
            }
    }

    private fun recordProgress(force: Boolean, ended: Boolean = false) {
        val item = _nowPlaying.value ?: return
        val active: Player = if (castConnected) {
            val remote = castPlayer ?: return
            // The screen may already describe the next item while the TV still plays the last one.
            if (remote.currentMediaItem?.mediaId != item.key) return
            remote
        } else {
            if (!localPlayerCreated) return
            val playingUri = player.currentMediaItem?.localConfiguration?.uri ?: return
            // The screen may already describe the next item while the player still plays the last one.
            if (playingUri != Uri.parse(item.mediaUri)) return
            player
        }
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val position = if (ended) duration else active.currentPosition.coerceAtLeast(0L)
        saveProgress(item, position, duration, force)
    }

    /** Saves the TV's last known position when the session ends (its player is empty by then). */
    private fun recordProgressAt(position: Long, duration: Long) {
        val item = _nowPlaying.value ?: return
        if (castRequestedKey != item.key || duration <= 0L) return
        saveProgress(item, position, duration, force = true)
    }

    private fun saveProgress(item: NowPlaying, position: Long, duration: Long, force: Boolean) {
        if (position < WatchProgress.MIN_SAVED_POSITION_MS) return

        val key = item.key
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
        const val TAG = "PlaybackSession"
        const val PROGRESS_TICK_MS = 1_000L
        const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
        const val VOLUME_STEP = 0.05
        /** Receivers load every track with the media; keep the list short. */
        const val MAX_CAST_SUBTITLES = 12

        const val EXTRA_MEDIA_TYPE = "openstream.media_type"
        const val EXTRA_TMDB_ID = "openstream.tmdb_id"
        const val EXTRA_SEASON = "openstream.season"
        const val EXTRA_EPISODE = "openstream.episode"
        const val EXTRA_SOURCE_URI = "openstream.source_uri"
        const val EXTRA_DOWNLOAD_ID = "openstream.download_id"
        const val EXTRA_TITLE = "openstream.title"
        const val EXTRA_EPISODE_TITLE = "openstream.episode_title"
        const val EXTRA_POSTER = "openstream.poster"
        const val EXTRA_BACKDROP = "openstream.backdrop"
        const val EXTRA_STILL = "openstream.still"
    }
}
