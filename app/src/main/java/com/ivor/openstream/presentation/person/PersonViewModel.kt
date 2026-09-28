package com.ivor.openstream.presentation.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.PersonDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PersonUiState {
    data object Loading : PersonUiState
    data class Success(val person: PersonDto, val credits: List<AnimeDto>) : PersonUiState
    data class Error(val message: String) : PersonUiState
}

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val tmdbApi: TmdbApi,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val personId: Int = checkNotNull(savedStateHandle["personId"])

    private val _uiState = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PersonUiState.Loading
            _uiState.value = runCatching { tmdbApi.getPerson(personId) }
                .fold(
                    onSuccess = { person -> PersonUiState.Success(person, person.filmography()) },
                    onFailure = { PersonUiState.Error(it.message ?: "Couldn't load this person") }
                )
        }
    }
}

/**
 * Everything they acted in or worked on, once per title, most popular first. Talk and news shows
 * are dropped: guest spots there crowd out the actual filmography.
 */
private fun PersonDto.filmography(): List<AnimeDto> {
    val credits = combinedCredits ?: return emptyList()
    return (credits.cast + credits.crew)
        .filter { it.posterPath != null && it.genreIds.orEmpty().none { genre -> genre in TALK_AND_NEWS } }
        .distinctBy { "${it.mediaType}:${it.id}" }
        .sortedByDescending { it.popularity ?: 0.0 }
}

private val TALK_AND_NEWS = setOf(10763, 10767)
