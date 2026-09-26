package com.ivor.openstream.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.remote.model.SeasonDetailsDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.data.remote.model.toAnimeDto
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.domain.model.DownloadTarget
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.repository.WatchLaterRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val downloadRepository: DownloadRepository,
    private val watchProgressRepository: WatchProgressRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val animeId: Int = checkNotNull(savedStateHandle["animeId"])
    private val mediaType: String = checkNotNull(savedStateHandle["mediaType"])
    
    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()


    val isWatchLater: StateFlow<Boolean> = watchLaterRepository.isWatchLater(animeId)
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** Every episode the user has touched, newest first. */
    private val titleProgress = watchProgressRepository.progressForTitle(mediaType, animeId)

    val episodeProgress: StateFlow<Map<Pair<Int, Int>, WatchProgress>> = titleProgress
        .map { rows -> rows.associateBy { it.season to it.episode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** What the primary action continues with, or null to start from the beginning. */
    val resumeTarget: StateFlow<WatchProgress?> = titleProgress
        .map { rows -> rows.firstOrNull()?.takeUnless { it.completed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
                    // Open on the season the user was last watching, otherwise season 1.
                    details.seasons?.let { seasons ->
                        val lastWatchedSeason = titleProgress.first().firstOrNull()?.season
                        val defaultSeason = seasons.find { it.seasonNumber == lastWatchedSeason }
                            ?: seasons.find { it.seasonNumber == 1 }
                            ?: seasons.firstOrNull()
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

    /** Marks an episode (or the movie) finished without playing it, e.g. watched elsewhere. */
    fun setWatched(episode: EpisodeDto, watched: Boolean) {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        viewModelScope.launch {
            if (!watched) {
                watchProgressRepository.clearEpisode(mediaType, animeId, episode.seasonNumber, episode.episodeNumber)
                return@launch
            }
            val durationMs = (episode.runtime ?: details.typicalRuntime ?: DEFAULT_RUNTIME_MIN) * 60_000L
            watchProgressRepository.record(
                WatchProgress(
                    tmdbId = animeId,
                    mediaType = mediaType,
                    season = episode.seasonNumber,
                    episode = episode.episodeNumber,
                    title = details.name,
                    episodeTitle = episode.name.takeIf { mediaType != "movie" },
                    posterPath = details.posterPath,
                    backdropPath = details.backdropPath,
                    stillPath = episode.stillPath,
                    positionMs = durationMs,
                    durationMs = durationMs,
                    completed = true
                )
            )
        }
    }

    /** Download state per episode of this title, keyed by (season, episode). */
    val episodeDownloads: StateFlow<Map<Pair<Int, Int>, DownloadEntity>> =
        downloadRepository.getDownloadsForTitle(animeId, mediaType)
            .map { rows -> rows.associateBy { it.season to it.episode } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Hands episodes to the app-wide download queue; it keeps going after this screen closes. */
    fun downloadEpisodes(episodes: List<EpisodeDto>) {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        downloadRepository.enqueue(
            episodes.map { episode ->
                DownloadTarget(
                    tmdbId = animeId,
                    mediaType = mediaType,
                    season = episode.seasonNumber,
                    episode = episode.episodeNumber,
                    showTitle = details.name,
                    episodeTitle = episode.name.takeIf { mediaType != "movie" },
                    posterPath = details.posterPath,
                    stillPath = episode.stillPath ?: details.backdropPath,
                    year = details.date.take(4).toIntOrNull()
                )
            }
        )
    }
}

private const val DEFAULT_RUNTIME_MIN = 24

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(
        val details: AnimeDetailsDto,
        val selectedSeasonDetails: SeasonDetailsDto? = null,
        val isLoadingEpisodes: Boolean = false
    ) : DetailsUiState
    data class Error(val message: String) : DetailsUiState
}
