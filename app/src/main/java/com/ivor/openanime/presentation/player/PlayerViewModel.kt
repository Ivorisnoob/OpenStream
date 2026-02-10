package com.ivor.openanime.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.data.remote.TmdbApi
import com.ivor.openanime.data.remote.model.EpisodeDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val tmdbApi: TmdbApi
) : ViewModel() {

    private val _nextEpisodes = MutableStateFlow<List<EpisodeDto>>(emptyList())
    val nextEpisodes = _nextEpisodes.asStateFlow()

    private val _isLoadingEpisodes = MutableStateFlow(false)
    val isLoadingEpisodes = _isLoadingEpisodes.asStateFlow()

    fun loadSeasonDetails(tmdbId: Int, seasonNumber: Int, currentEpisodeNumber: Int) {
        viewModelScope.launch {
            _isLoadingEpisodes.value = true
            try {
                // Fetch full season details
                val seasonDetails = tmdbApi.getSeasonDetails(tmdbId, seasonNumber)
                
                // Filter for episodes after the current one
                // We keep upcoming episodes.
                _nextEpisodes.value = seasonDetails.episodes.filter { it.episodeNumber > currentEpisodeNumber }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingEpisodes.value = false
            }
        }
    }
}
