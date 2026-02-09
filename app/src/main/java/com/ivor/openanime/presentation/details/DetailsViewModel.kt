package com.ivor.openanime.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.data.remote.model.AnimeDetailsDto
import com.ivor.openanime.data.remote.model.SeasonDetailsDto
import com.ivor.openanime.domain.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repository: AnimeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val animeId: Int = checkNotNull(savedStateHandle["animeId"])
    
    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            repository.getAnimeDetails(animeId)
                .onSuccess { details ->
                    _uiState.value = DetailsUiState.Success(details)
                    // Load the first season by default - usually season 1 or the first in list
                    // Filter out season 0 (Specials) if desired, but for now just take first.
                    val defaultSeason = details.seasons.find { it.seasonNumber == 1 } ?: details.seasons.firstOrNull()
                    defaultSeason?.let { season ->
                        loadSeason(season.seasonNumber)
                    }
                }
                .onFailure { exception ->
                    _uiState.value = DetailsUiState.Error(exception.message ?: "Unknown error")
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
