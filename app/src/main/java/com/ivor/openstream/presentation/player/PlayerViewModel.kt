package com.ivor.openstream.presentation.player

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.domain.repository.SubtitleRepository
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.data.remote.model.toAnimeDto
import com.ivor.openstream.domain.model.DownloadTarget
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.repository.StreamingRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import com.ivor.openstream.domain.repository.WatchLaterRepository
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import androidx.media3.exoplayer.ExoPlayer
import com.ivor.openstream.presentation.player.session.NowPlaying
import com.ivor.openstream.presentation.player.session.PlaybackSession
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.repository.SkipSegment
import com.ivor.openstream.data.repository.SkipTimesRepository
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.presentation.player.session.SleepTimer
import com.ivor.openstream.presentation.player.session.CastError
import com.ivor.openstream.presentation.player.session.CastStatus
import com.ivor.openstream.presentation.player.session.CastSubtitleOption
import com.ivor.openstream.presentation.player.session.CastSubtitles
import com.ivor.openstream.presentation.player.components.SUBTITLES_OFF
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaRouteSelector
import javax.inject.Inject

private const val KEY_CAPTION_STYLE = "caption_style"
private const val KEY_PREFERRED_AUDIO = "preferred_audio_language"
private const val KEY_PREFERRED_SUBTITLE = "preferred_subtitle_language"
private const val MAX_AUTOMATIC_FAILOVERS = 3
private const val STREAM_REFRESH_AGE_MS = 6 * 60 * 60 * 1_000L

/** The episode playback continues with, possibly the first episode of the next season. */
data class NextEpisodeTarget(
    val season: Int,
    val episode: Int,
    val title: String?,
    val stillPath: String?
)

sealed interface ServersState {
    data object Idle : ServersState

    data class Resolving(
        val servers: List<VideoServer>,
        val activeId: String?,
        val completedProviders: Int,
        val totalProviders: Int,
        val failedProviders: List<String>
    ) : ServersState

    data class Ready(
        val servers: List<VideoServer>,
        val activeId: String?,
        val failedProviders: List<String>
    ) : ServersState

    data class Empty(val failedProviders: List<String>) : ServersState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val subtitleRepository: SubtitleRepository,
    private val animeRepository: AnimeRepository,
    private val streamingRepository: StreamingRepository,
    private val downloadRepository: DownloadRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val sharedPreferences: SharedPreferences,
    private val json: Json,
    private val playbackSession: PlaybackSession,
    appSettingsStore: AppSettingsStore,
    private val skipTimesRepository: SkipTimesRepository
) : ViewModel() {
    /** Intro/recap/credits times for the current anime episode; empty when unknown. */
    private val _skipSegments = MutableStateFlow<List<SkipSegment>>(emptyList())
    val skipSegments: StateFlow<List<SkipSegment>> = _skipSegments.asStateFlow()

    /** Seek step, default speed and auto-play from Settings. */
    val appSettings: StateFlow<AppSettings> = appSettingsStore.settings

    /** The app-wide player; the screen attaches to it rather than owning one. */
    val player: ExoPlayer get() = playbackSession.player

    fun applyRequestHeaders(headers: Map<String, String>) = playbackSession.setRequestHeaders(headers)

    val sleepTimer: StateFlow<SleepTimer?> = playbackSession.sleepTimer

    fun setSleepTimer(timer: SleepTimer?) = playbackSession.setSleepTimer(timer)

    fun consumeEndedBySleepTimer(): Boolean = playbackSession.consumeEndedBySleepTimer()

    val castStatus: StateFlow<CastStatus> = playbackSession.castStatus
    val castError: StateFlow<CastError?> = playbackSession.castError
    val castLoading: StateFlow<Boolean> = playbackSession.castLoading
    val castSubtitles: StateFlow<CastSubtitles> = playbackSession.castSubtitles

    /** Plays on the TV while casting. */
    val castPlayer: Player? get() = playbackSession.castPlayer

    val castRouteSelector: MediaRouteSelector? get() = playbackSession.castRouteSelector

    fun stopCasting() = playbackSession.stopCasting()

    fun retryCast() = playbackSession.retryCast()

    /** Keeps the TV's subtitle list in step with what this screen found. */
    fun setCastSubtitleCandidates(subtitles: List<SubtitleDto>, preferredLanguage: String?) =
        playbackSession.setSubtitleCandidates(subtitles, preferredLanguage)

    /** Subtitle picked on the casting screen: shown on the TV and remembered like in the player. */
    fun selectCastSubtitle(option: CastSubtitleOption?) {
        playbackSession.selectCastSubtitle(option?.id)
        when {
            option == null -> setPreferredSubtitleLanguage(SUBTITLES_OFF)
            option.language != null -> setPreferredSubtitleLanguage(option.language)
        }
    }

    private val _mediaUri = MutableStateFlow<Pair<String, String?>?>(null)

    /** The screen reports what it handed to the player (stream URL, plus the download id offline). */
    fun onMediaLoaded(mediaUri: String, downloadId: String?) {
        _mediaUri.value = mediaUri to downloadId
    }

    /** A stream still playing from the mini player that this screen should continue, not reload. */
    private var adoptedServer: VideoServer? = null


    private val _captionSettings = MutableStateFlow(loadCaptionSettings())
    val captionSettings: StateFlow<CaptionStyleSettings> = _captionSettings.asStateFlow()

    /** Audio language the user last picked (for example `en` for dubs); applied to new streams. */
    private val _preferredAudioLanguage = MutableStateFlow(sharedPreferences.getString(KEY_PREFERRED_AUDIO, null))
    val preferredAudioLanguage: StateFlow<String?> = _preferredAudioLanguage.asStateFlow()

    fun setPreferredAudioLanguage(language: String?) {
        _preferredAudioLanguage.value = language
        sharedPreferences.edit().putString(KEY_PREFERRED_AUDIO, language).apply()
    }

    /**
     * Subtitle language the user last picked, [SUBTITLES_OFF] if they turned subtitles off, or
     * null if they never chose; applied to every new stream.
     */
    private val _preferredSubtitleLanguage = MutableStateFlow(sharedPreferences.getString(KEY_PREFERRED_SUBTITLE, null))
    val preferredSubtitleLanguage: StateFlow<String?> = _preferredSubtitleLanguage.asStateFlow()

    fun setPreferredSubtitleLanguage(language: String) {
        _preferredSubtitleLanguage.value = language
        sharedPreferences.edit().putString(KEY_PREFERRED_SUBTITLE, language).apply()
    }

    private val _nextEpisodes = MutableStateFlow<List<EpisodeDto>>(emptyList())
    val nextEpisodes = _nextEpisodes.asStateFlow()

    /** Every episode of the season being watched, for the episode list under the player. */
    private val _seasonEpisodes = MutableStateFlow<List<EpisodeDto>>(emptyList())
    val seasonEpisodes: StateFlow<List<EpisodeDto>> = _seasonEpisodes.asStateFlow()

    /** (mediaType, tmdbId) of the title on screen; drives the per-title flows below. */
    private val _title = MutableStateFlow<Pair<String, Int>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val episodeProgress: StateFlow<Map<Pair<Int, Int>, WatchProgress>> = _title
        .flatMapLatest { title ->
            title?.let { (type, id) ->
                watchProgressRepository.progressForTitle(type, id)
                    .map { rows -> rows.associateBy { it.season to it.episode } }
            } ?: flowOf(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val isSaved: StateFlow<Boolean> = _title
        .flatMapLatest { title -> title?.let { watchLaterRepository.isWatchLater(it.second) } ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleSaved() {
        val details = _mediaDetails.value ?: return
        viewModelScope.launch {
            if (isSaved.value) {
                watchLaterRepository.removeFromWatchLaterById(details.id)
            } else {
                watchLaterRepository.addToWatchLater(
                    WatchLaterEntity(
                        id = details.id,
                        title = details.name,
                        posterPath = details.posterPath,
                        mediaType = _mediaType.value,
                        voteAverage = details.voteAverage
                    )
                )
            }
        }
    }

    private val _isLoadingEpisodes = MutableStateFlow(false)
    val isLoadingEpisodes = _isLoadingEpisodes.asStateFlow()

    private val _remoteSubtitles = MutableStateFlow<List<SubtitleDto>>(emptyList())
    val remoteSubtitles = _remoteSubtitles.asStateFlow()

    private val _mediaDetails = MutableStateFlow<AnimeDetailsDto?>(null)
    val mediaDetails = _mediaDetails.asStateFlow()

    private val _currentEpisode = MutableStateFlow<EpisodeDto?>(null)
    val currentEpisode = _currentEpisode.asStateFlow()

    private val _serversState = MutableStateFlow<ServersState>(ServersState.Idle)
    val serversState: StateFlow<ServersState> = _serversState.asStateFlow()

    private val _activeServer = MutableStateFlow<VideoServer?>(null)
    val activeServer: StateFlow<VideoServer?> = _activeServer.asStateFlow()

    private val _playerEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val playerEvents = _playerEvents.asSharedFlow()

    /** Null until the saved position is known, so playback never starts at 0 by mistake. */
    private val _startPositionMs = MutableStateFlow<Long?>(null)
    val startPositionMs: StateFlow<Long?> = _startPositionMs.asStateFlow()

    private val _nextEpisode = MutableStateFlow<NextEpisodeTarget?>(null)
    val nextEpisode: StateFlow<NextEpisodeTarget?> = _nextEpisode.asStateFlow()

    private val _mediaType = MutableStateFlow("tv")
    private var currentSeason = 1
    private var currentEpisodeNumber = 1
    private var currentIdentity: MediaIdentity? = null
    private var loadJob: Job? = null
    private var resolutionJob: Job? = null
    private val failedServerIds = linkedSetOf<String>()
    private var automaticFailovers = 0
    private var backupSourcesSearched = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentDownload: StateFlow<DownloadEntity?> = combine(
        _mediaDetails,
        _currentEpisode,
        _mediaType
    ) { details, episode, type ->
        Triple(details, episode, type)
    }.flatMapLatest { (details, episode, type) ->
        if (details == null || (type == "tv" && episode == null)) {
            flowOf(null)
        } else {
            downloadRepository.getDownloadByContent(
                tmdbId = details.id,
                season = if (type == "movie") 1 else episode?.seasonNumber ?: 1,
                episode = if (type == "movie") 1 else episode?.episodeNumber ?: 1,
                mediaType = type
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun loadCaptionSettings(): CaptionStyleSettings = try {
        sharedPreferences.getString(KEY_CAPTION_STYLE, null)
            ?.let { json.decodeFromString<CaptionStyleSettings>(it) }
            ?: CaptionStyleSettings()
    } catch (_: Exception) {
        CaptionStyleSettings()
    }

    fun updateCaptionSettings(settings: CaptionStyleSettings) {
        _captionSettings.value = settings
        sharedPreferences.edit()
            .putString(KEY_CAPTION_STYLE, json.encodeToString(settings))
            .apply()
    }

    suspend fun getPlaybackUri(downloadId: String): String? =
        downloadRepository.getPlaybackUri(downloadId)

    fun removeDownload(downloadId: String) {
        viewModelScope.launch { downloadRepository.removeDownload(downloadId) }
    }

    fun downloadVideo(server: VideoServer) {
        viewModelScope.launch {
            val details = _mediaDetails.value ?: return@launch
            val currentServer = if (System.currentTimeMillis() - server.resolvedAt > STREAM_REFRESH_AGE_MS) {
                streamingRepository.refreshServer(server).getOrElse {
                    _playerEvents.tryEmit("That server expired. Choose another source.")
                    return@launch
                }
            } else {
                server
            }
            val episode = _currentEpisode.value
            val isMovie = _mediaType.value == "movie"
            runCatching {
                downloadRepository.download(
                    server = currentServer,
                    target = DownloadTarget(
                        tmdbId = details.id,
                        mediaType = _mediaType.value,
                        season = currentSeason,
                        episode = currentEpisodeNumber,
                        showTitle = details.name,
                        episodeTitle = episode?.name.takeUnless { isMovie },
                        posterPath = details.posterPath,
                        stillPath = episode?.stillPath ?: details.backdropPath,
                        year = details.date.take(4).toIntOrNull()
                    )
                )
            }.onSuccess {
                _playerEvents.tryEmit("Downloading. Find it in Downloads.")
            }.onFailure {
                _playerEvents.tryEmit(it.message ?: "Download could not be started.")
            }
        }
    }

    // Declared after every state flow it reads, so they are initialised when this runs.
    init {
        viewModelScope.launch {
            combine(_mediaDetails, _currentEpisode, _nextEpisode, _activeServer, _mediaUri) { details, episode, next, server, media ->
                if (details == null || media == null) return@combine null
                NowPlaying(
                    mediaType = _mediaType.value,
                    tmdbId = details.id,
                    season = currentSeason,
                    episode = currentEpisodeNumber,
                    downloadId = media.second,
                    mediaUri = media.first,
                    title = details.name,
                    episodeTitle = episode?.name.takeUnless { _mediaType.value == "movie" },
                    posterPath = details.posterPath,
                    backdropPath = details.backdropPath,
                    stillPath = episode?.stillPath,
                    next = next,
                    server = server,
                    startPositionMs = _startPositionMs.value ?: 0L
                )
            }.collect { nowPlaying -> nowPlaying?.let(playbackSession::update) }
        }
    }

    fun loadSeasonDetails(
        mediaType: String,
        tmdbId: Int,
        seasonNumber: Int,
        currentEpisodeNumber: Int,
        resolveStreams: Boolean = true
    ) {
        loadJob?.cancel()
        resolutionJob?.cancel()
        _mediaType.value = mediaType
        currentSeason = seasonNumber
        this.currentEpisodeNumber = currentEpisodeNumber
        _mediaUri.value = null
        _startPositionMs.value = null
        _nextEpisode.value = null
        _mediaDetails.value = null
        _currentEpisode.value = null
        _remoteSubtitles.value = emptyList()
        _skipSegments.value = emptyList()
        _nextEpisodes.value = emptyList()
        _seasonEpisodes.value = emptyList()
        _title.value = mediaType to tmdbId
        _serversState.value = ServersState.Idle
        _activeServer.value = null
        failedServerIds.clear()
        automaticFailovers = 0
        backupSourcesSearched = false
        switchedFrom = null

        val continuing = playbackSession.nowPlaying.value
            ?.takeIf { it.matches(mediaType, tmdbId, seasonNumber, currentEpisodeNumber) }
        adoptedServer = continuing?.server?.takeIf { continuing.downloadId == null }
        adoptedServer?.let { _activeServer.value = it }

        loadJob = viewModelScope.launch {
            launch {
                _startPositionMs.value = if (continuing != null) {
                    playbackSession.currentPositionMs()
                } else {
                    watchProgressRepository.get(mediaType, tmdbId, seasonNumber, currentEpisodeNumber)
                        ?.resumePositionMs ?: 0L
                }
            }

            launch {
                // The richer lookup brings recommendations for the "More like this" row.
                val detailsResult = animeRepository.getMediaDetails(tmdbId, mediaType)
                detailsResult.onSuccess { details ->
                    _mediaDetails.value = details
                    animeRepository.addToWatchHistory(details.toAnimeDto(mediaType))
                    val identity = MediaIdentity(
                        tmdbId = tmdbId,
                        tmdbType = mediaType,
                        title = details.name,
                        season = seasonNumber,
                        episode = currentEpisodeNumber,
                        year = details.date.take(4).toIntOrNull()
                    )
                    currentIdentity = identity
                    if (resolveStreams) startResolution(identity)
                    if (details.isAnime()) {
                        launch { loadSkipSegments(details, mediaType, seasonNumber, currentEpisodeNumber) }
                    }
                }.onFailure {
                    if (resolveStreams) _serversState.value = ServersState.Empty(emptyList())
                }
            }

            launch {
                if (mediaType == "movie") {
                    _nextEpisodes.value = emptyList()
                    _currentEpisode.value = null
                    return@launch
                }
                _isLoadingEpisodes.value = true
                runCatching { tmdbApi.getSeasonDetails(tmdbId, seasonNumber) }
                    .onSuccess { seasonDetails ->
                        _seasonEpisodes.value = seasonDetails.episodes
                        _currentEpisode.value = seasonDetails.episodes
                            .find { it.episodeNumber == currentEpisodeNumber }
                        _nextEpisodes.value = seasonDetails.episodes
                            .filter { it.episodeNumber > currentEpisodeNumber }
                        _nextEpisode.value = findNextEpisode(
                            tmdbId = tmdbId,
                            seasonNumber = seasonNumber,
                            remaining = _nextEpisodes.value
                        )
                    }
                _isLoadingEpisodes.value = false
            }

            launch {
                _remoteSubtitles.value = subtitleRepository.search(
                    MediaIdentity(
                        tmdbId = tmdbId,
                        tmdbType = mediaType,
                        title = "",
                        season = seasonNumber,
                        episode = currentEpisodeNumber
                    )
                )
            }
        }
    }

    private suspend fun loadSkipSegments(details: AnimeDetailsDto, mediaType: String, season: Int, episode: Int) {
        val isMovie = mediaType == "movie"
        // Later seasons are separate AniSkip/MAL entries, told apart by the year they started.
        val seasonYear = if (isMovie || season <= 1) {
            null
        } else {
            details.seasons?.firstOrNull { it.seasonNumber == season }?.airDate?.take(4)?.toIntOrNull()
        }
        val identity = MediaIdentity(
            tmdbId = details.id,
            tmdbType = mediaType,
            title = details.name,
            season = season,
            episode = episode
        )
        _skipSegments.value = skipTimesRepository.segmentsFor(identity, seasonYear)
    }

    private suspend fun findNextEpisode(
        tmdbId: Int,
        seasonNumber: Int,
        remaining: List<EpisodeDto>
    ): NextEpisodeTarget? {
        remaining.firstOrNull { isReleased(it.airDate) }?.let { episode ->
            return NextEpisodeTarget(seasonNumber, episode.episodeNumber, episode.name, episode.stillPath)
        }
        // The season is over (or the rest is unreleased): continue with the next real season.
        val seasons = _mediaDetails.value?.seasons
            ?: animeRepository.getAnimeDetails(tmdbId).getOrNull()?.seasons
            ?: return null
        val nextSeason = seasons
            .filter { it.seasonNumber > seasonNumber && it.episodeCount > 0 }
            .minByOrNull { it.seasonNumber }
            ?: return null
        if (!isReleased(nextSeason.airDate)) return null
        return NextEpisodeTarget(nextSeason.seasonNumber, 1, null, null)
    }

    private fun isReleased(airDate: String?): Boolean {
        if (airDate.isNullOrBlank()) return true
        return runCatching { !LocalDate.parse(airDate).isAfter(LocalDate.now()) }.getOrDefault(true)
    }

    /** The stream the user switched away from; restored if their pick refuses to play. */
    private var switchedFrom: VideoServer? = null

    fun selectServer(serverId: String) {
        val server = availableServers().firstOrNull { it.id == serverId } ?: return
        switchedFrom = _activeServer.value?.takeIf { it.id != serverId }
        failedServerIds.remove(serverId)
        automaticFailovers = 0
        _activeServer.value = server
        setActiveId(serverId)
        currentIdentity?.let { streamingRepository.rememberServer(it, server) }
    }

    fun onPlaybackReady() {
        val identity = currentIdentity ?: return
        val server = _activeServer.value ?: return
        streamingRepository.rememberServer(identity, server)
    }

    fun onPlaybackError() {
        val failed = _activeServer.value ?: return
        failedServerIds += failed.id
        // A source the user picked by hand (often a dub) failed: go back to what was playing rather
        // than jumping to an arbitrary server. Some dub hosts lock links to Vidking's own servers.
        val previous = switchedFrom?.takeIf { it.id !in failedServerIds }
        switchedFrom = null
        if (previous != null) {
            _activeServer.value = previous
            setActiveId(previous.id)
            currentIdentity?.let { streamingRepository.rememberServer(it, previous) }
            val label = failed.audioLanguage?.let { "${failed.name} ($it)" } ?: failed.name
            _playerEvents.tryEmit("$label won't play on this device. Back to ${previous.name}.")
            return
        }
        val next = availableServers().firstOrNull { it.id !in failedServerIds }
        if (next != null && automaticFailovers < MAX_AUTOMATIC_FAILOVERS) {
            automaticFailovers++
            _activeServer.value = next
            setActiveId(next.id)
            _playerEvents.tryEmit("${failed.name} stopped responding. Switched to ${next.name}.")
        } else if (!backupSourcesSearched && currentIdentity != null) {
            // Every direct link failed to play: widen the search to the backup sources once.
            backupSourcesSearched = true
            automaticFailovers = 0
            _activeServer.value = null
            _playerEvents.tryEmit("${failed.name} stopped responding. Searching backup sources…")
            startResolution(currentIdentity!!, includeFallbacks = true)
        } else {
            _activeServer.value = null
            setActiveId(null)
            _playerEvents.tryEmit("No more healthy servers. Choose a source or retry.")
        }
    }

    fun retryResolution() {
        val identity = currentIdentity ?: return
        failedServerIds.clear()
        automaticFailovers = 0
        backupSourcesSearched = true
        _activeServer.value = null
        startResolution(identity, includeFallbacks = true)
    }

    private fun startResolution(identity: MediaIdentity, includeFallbacks: Boolean = false) {
        resolutionJob?.cancel()
        resolutionJob = viewModelScope.launch {
            streamingRepository.resolveServers(identity, includeFallbacks).collect { progress ->
                // Keep the stream the mini player is already playing at the top of the list.
                val candidates = adoptedServer?.let { adopted ->
                    listOf(adopted) + progress.servers.filterNot { it.id == adopted.id }
                } ?: progress.servers
                val healthyServers = candidates.filterNot { it.id in failedServerIds }
                val active = _activeServer.value?.takeIf { current ->
                    healthyServers.any { it.id == current.id }
                } ?: healthyServers.firstOrNull()
                _activeServer.value = active

                _serversState.value = when {
                    progress.isComplete && healthyServers.isEmpty() ->
                        ServersState.Empty(progress.failedProviders)
                    progress.isComplete ->
                        ServersState.Ready(healthyServers, active?.id, progress.failedProviders)
                    else ->
                        ServersState.Resolving(
                            servers = healthyServers,
                            activeId = active?.id,
                            completedProviders = progress.completedProviders,
                            totalProviders = progress.totalProviders,
                            failedProviders = progress.failedProviders
                        )
                }
            }
        }
    }

    private fun availableServers(): List<VideoServer> = when (val state = _serversState.value) {
        is ServersState.Resolving -> state.servers
        is ServersState.Ready -> state.servers
        else -> emptyList()
    }

    private fun setActiveId(activeId: String?) {
        _serversState.value = when (val state = _serversState.value) {
            is ServersState.Resolving -> state.copy(activeId = activeId)
            is ServersState.Ready -> state.copy(activeId = activeId)
            else -> state
        }
    }
}

/** Japanese animation: the titles AniSkip can have times for. */
private fun AnimeDetailsDto.isAnime(): Boolean =
    genres.orEmpty().any { it.id == 16 } && originalLanguage.equals("ja", ignoreCase = true)
