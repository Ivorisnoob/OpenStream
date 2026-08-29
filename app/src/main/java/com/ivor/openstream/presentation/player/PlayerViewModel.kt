package com.ivor.openstream.presentation.player

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.Cache
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.remote.SubtitleApi
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.data.remote.model.toAnimeDto
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.repository.StreamingRepository
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

private const val KEY_CAPTION_STYLE = "caption_style"
private const val MAX_AUTOMATIC_FAILOVERS = 3
private const val STREAM_REFRESH_AGE_MS = 6 * 60 * 60 * 1_000L

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
    private val subtitleApi: SubtitleApi,
    private val animeRepository: AnimeRepository,
    private val streamingRepository: StreamingRepository,
    private val downloadRepository: DownloadRepository,
    private val sharedPreferences: SharedPreferences,
    private val json: Json,
    val downloadCache: Cache
) : ViewModel() {
    private val _captionSettings = MutableStateFlow(loadCaptionSettings())
    val captionSettings: StateFlow<CaptionStyleSettings> = _captionSettings.asStateFlow()

    private val _nextEpisodes = MutableStateFlow<List<EpisodeDto>>(emptyList())
    val nextEpisodes = _nextEpisodes.asStateFlow()

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

    private val _mediaType = MutableStateFlow("tv")
    private var currentIdentity: MediaIdentity? = null
    private var loadJob: Job? = null
    private var resolutionJob: Job? = null
    private val failedServerIds = linkedSetOf<String>()
    private var automaticFailovers = 0

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

    fun downloadVideo(
        server: VideoServer,
        title: String,
        fileName: String,
        mediaType: String,
        tmdbId: Int,
        season: Int,
        episode: Int
    ) {
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
            runCatching {
                downloadRepository.downloadVideo(
                    server = currentServer,
                    title = title,
                    fileName = fileName,
                    posterPath = details.posterPath,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode
                )
            }.onFailure {
                _playerEvents.tryEmit(it.message ?: "Download could not be started.")
            }
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
        _mediaDetails.value = null
        _currentEpisode.value = null
        _remoteSubtitles.value = emptyList()
        _nextEpisodes.value = emptyList()
        _serversState.value = ServersState.Idle
        _activeServer.value = null
        failedServerIds.clear()
        automaticFailovers = 0

        loadJob = viewModelScope.launch {
            launch {
                val detailsResult = if (mediaType == "movie") {
                    animeRepository.getMovieDetails(tmdbId)
                } else {
                    animeRepository.getAnimeDetails(tmdbId)
                }
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
                        _currentEpisode.value = seasonDetails.episodes
                            .find { it.episodeNumber == currentEpisodeNumber }
                        _nextEpisodes.value = seasonDetails.episodes
                            .filter { it.episodeNumber > currentEpisodeNumber }
                    }
                _isLoadingEpisodes.value = false
            }

            launch {
                runCatching {
                    if (mediaType == "tv") {
                        subtitleApi.searchSubtitles(tmdbId, seasonNumber, currentEpisodeNumber)
                    } else {
                        subtitleApi.searchSubtitles(tmdbId)
                    }
                }.onSuccess { jsonElement ->
                    val subtitles = when (jsonElement) {
                        is JsonArray -> jsonElement.map { json.decodeFromJsonElement<SubtitleDto>(it) }
                        is JsonObject -> jsonElement.values.map { json.decodeFromJsonElement<SubtitleDto>(it) }
                        else -> emptyList()
                    }
                    _remoteSubtitles.value = subtitles
                }
            }
        }
    }

    fun selectServer(serverId: String) {
        val server = availableServers().firstOrNull { it.id == serverId } ?: return
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
        val next = availableServers().firstOrNull { it.id !in failedServerIds }
        if (next != null && automaticFailovers < MAX_AUTOMATIC_FAILOVERS) {
            automaticFailovers++
            _activeServer.value = next
            setActiveId(next.id)
            _playerEvents.tryEmit("${failed.name} stopped responding. Switched to ${next.name}.")
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
        _activeServer.value = null
        startResolution(identity)
    }

    private fun startResolution(identity: MediaIdentity) {
        resolutionJob?.cancel()
        resolutionJob = viewModelScope.launch {
            streamingRepository.resolveServers(identity).collect { progress ->
                val healthyServers = progress.servers.filterNot { it.id in failedServerIds }
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
