package com.ivor.openanime.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.data.remote.model.AnimeDetailsDto
import com.ivor.openanime.data.local.entity.WatchLaterEntity
import com.ivor.openanime.data.remote.model.SeasonDetailsDto
import com.ivor.openanime.data.remote.model.toAnimeDto
import com.ivor.openanime.domain.repository.AnimeRepository
import com.ivor.openanime.domain.repository.WatchLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val downloadRepository: com.ivor.openanime.domain.repository.DownloadRepository,
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

    fun downloadVideo(
        url: String,
        title: String,
        posterPath: String?,
        mediaType: String,
        tmdbId: Int,
        season: Int,
        episode: Int
    ) {
        viewModelScope.launch {
            try {
                // Ensure a safe filename
                var safeTitle = title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                if (safeTitle.length > 50) safeTitle = safeTitle.take(50)
                
                val fileName = "${safeTitle}_${tmdbId}_S${season}E${episode}.mp4"
                
                downloadRepository.downloadVideo(
                    url = url,
                    title = title,
                    fileName = fileName,
                    posterPath = posterPath,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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