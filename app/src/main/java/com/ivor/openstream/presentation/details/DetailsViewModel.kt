package com.ivor.openstream.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.remote.model.SeasonDetailsDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.data.remote.model.toAnimeDto
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.repository.StreamingRepository
import com.ivor.openstream.domain.repository.WatchLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val downloadRepository: DownloadRepository,
    private val streamingRepository: StreamingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val animeId: Int = checkNotNull(savedStateHandle["animeId"])
    private val mediaType: String = checkNotNull(savedStateHandle["mediaType"])
    
    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _downloadQueueState = MutableStateFlow(DownloadQueueState())
    val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()
    private var downloadJob: Job? = null

    val isWatchLater: StateFlow<Boolean> = watchLaterRepository.isWatchLater(animeId)
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            repository.getMediaDetails(animeId, mediaType)
                .onSuccess { details ->
                    _uiState.value = DetailsUiState.Success(details)
                    // Add to watch history
                    viewModelScope.launch {
                        repository.addToWatchHistory(details.toAnimeDto(mediaType))
                    }
                    // Load the first season by default
                    details.seasons?.let { seasons ->
                        val defaultSeason = seasons.find { it.seasonNumber == 1 } ?: seasons.firstOrNull()
                        defaultSeason?.let { season ->
                            loadSeason(season.seasonNumber)
                        }
                    }
                }
                .onFailure { exception ->
                    _uiState.value = DetailsUiState.Error(exception.message ?: "Unknown error")
                }
        }
    }

    fun toggleWatchLater() {
        val currentState = _uiState.value
        if (currentState is DetailsUiState.Success) {
            viewModelScope.launch {
                val details = currentState.details
                val item = WatchLaterEntity(
                    id = details.id,
                    title = details.name,
                    posterPath = details.posterPath,
                    mediaType = mediaType,
                    voteAverage = details.voteAverage
                )
                if (isWatchLater.value) {
                    watchLaterRepository.removeFromWatchLaterById(details.id)
                } else {
                    watchLaterRepository.addToWatchLater(item)
                }
            }
        }
    }

    fun loadSeason(seasonNumber: Int) {
        val currentState = _uiState.value
        if (currentState is DetailsUiState.Success) {
            viewModelScope.launch {
                _uiState.value = currentState.copy(isLoadingEpisodes = true)
                repository.getSeasonDetails(animeId, seasonNumber)
                    .onSuccess { seasonDetails ->
                        (_uiState.value as? DetailsUiState.Success)?.let { successState ->
                            _uiState.value = successState.copy(
                                selectedSeasonDetails = seasonDetails,
                                isLoadingEpisodes = false
                            )
                        }
                    }
                    .onFailure {
                        (_uiState.value as? DetailsUiState.Success)?.let { successState ->
                            _uiState.value = successState.copy(isLoadingEpisodes = false)
                        }
                    }
            }
        }
    }

    fun downloadEpisodes(episodes: List<EpisodeDto>) {
        if (episodes.isEmpty() || downloadJob?.isActive == true) return
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        downloadJob = viewModelScope.launch {
            var failed = 0
            _downloadQueueState.value = DownloadQueueState(
                isRunning = true,
                remaining = episodes.size,
                total = episodes.size
            )
            episodes.forEachIndexed { index, episode ->
                val identity = MediaIdentity(
                    tmdbId = animeId,
                    tmdbType = mediaType,
                    title = details.name,
                    season = episode.seasonNumber,
                    episode = episode.episodeNumber,
                    year = details.date.take(4).toIntOrNull()
                )
                val server = withTimeoutOrNull(20_000) {
                    streamingRepository.resolveServers(identity)
                        .first { progress -> progress.servers.any { it.isDownloadable } }
                        .servers
                        .firstOrNull { it.isDownloadable }
                }
                if (server == null) {
                    failed++
                } else {
                    val safeTitle = episode.name
                        .replace(Regex("[^a-zA-Z0-9.-]"), "_")
                        .take(50)
                    runCatching {
                        downloadRepository.downloadVideo(
                            server = server,
                            title = episode.name,
                            fileName = "${safeTitle}_${animeId}_S${episode.seasonNumber}E${episode.episodeNumber}.mp4",
                            posterPath = episode.stillPath ?: details.posterPath,
                            mediaType = mediaType,
                            tmdbId = animeId,
                            season = episode.seasonNumber,
                            episode = episode.episodeNumber
                        )
                    }.onFailure { failed++ }
                }
                _downloadQueueState.value = _downloadQueueState.value.copy(
                    remaining = episodes.size - index - 1,
                    failed = failed
                )
            }
            _downloadQueueState.value = _downloadQueueState.value.copy(isRunning = false)
        }
    }

    fun cancelDownloads() {
        downloadJob?.cancel()
        _downloadQueueState.value = DownloadQueueState()
    }

data class DownloadQueueState(
    val isRunning: Boolean = false,
    val remaining: Int = 0,
    val total: Int = 0,
    val failed: Int = 0
)

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(
        val details: AnimeDetailsDto,
        val selectedSeasonDetails: SeasonDetailsDto? = null,
        val isLoadingEpisodes: Boolean = false
    ) : DetailsUiState
    data class Error(val message: String) : DetailsUiState
}
}
