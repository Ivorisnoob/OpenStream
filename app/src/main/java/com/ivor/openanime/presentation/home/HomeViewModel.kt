package com.ivor.openanime.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.data.remote.model.AnimeDto
import com.ivor.openanime.domain.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                coroutineScope {
                    val trendingDeferred = async { repository.getTrendingAnime() }
                    val topRatedDeferred = async { repository.getTopRatedAnime() }
                    val popularDeferred = async { repository.getPopularAnime(page = 1) }
                    val airingTodayDeferred = async { repository.getAiringTodayAnime() }

                    val trending = trendingDeferred.await().getOrElse { emptyList() }
                    val topRated = topRatedDeferred.await().getOrElse { emptyList() }
                    val popular = popularDeferred.await().getOrElse { emptyList() }
                    val airingToday = airingTodayDeferred.await().getOrElse { emptyList() }

                    if (trending.isEmpty() && topRated.isEmpty() && popular.isEmpty() && airingToday.isEmpty()) {
                        _uiState.value = HomeUiState.Error("Failed to load data")
                    } else {
                        _uiState.value = HomeUiState.Success(
                            trending = trending,
                            topRated = topRated,
                            popular = popular,
                            airingToday = airingToday
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val trending: List<AnimeDto>,
        val topRated: List<AnimeDto>,
        val popular: List<AnimeDto>,
        val airingToday: List<AnimeDto>
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
