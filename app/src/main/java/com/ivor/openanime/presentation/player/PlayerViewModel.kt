package com.ivor.openanime.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.data.remote.SubtitleApi
import com.ivor.openanime.data.remote.TmdbApi
import com.ivor.openanime.data.remote.model.AnimeDetailsDto
import com.ivor.openanime.data.remote.model.EpisodeDto
import com.ivor.openanime.data.remote.model.SubtitleDto
import com.ivor.openanime.data.remote.model.toAnimeDto
import com.ivor.openanime.domain.repository.AnimeRepository
import com.ivor.openanime.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val subtitleApi: SubtitleApi,
    private val repository: AnimeRepository,
    private val downloadRepository: DownloadRepository,
    val dataSourceFactory: androidx.media3.datasource.cache.CacheDataSource.Factory
) : ViewModel() {

    private val _nextEpisodes = MutableStateFlow<List<EpisodeDto>>(emptyList())
    val nextEpisodes = _nextEpisodes.asStateFlow()

    private val _isLoadingEpisodes = MutableStateFlow(false)
    val isLoadingEpisodes = _isLoadingEpisodes.asStateFlow()

    private val _remoteSubtitles = MutableStateFlow<List<SubtitleDto>>(emptyList())
    val remoteSubtitles = _remoteSubtitles.asStateFlow()

    // NEW state for details
    private val _mediaDetails = MutableStateFlow<AnimeDetailsDto?>(null)
    val mediaDetails = _mediaDetails.asStateFlow()

    private val _currentEpisode = MutableStateFlow<EpisodeDto?>(null)
    val currentEpisode = _currentEpisode.asStateFlow()

    suspend fun getPlaybackUri(downloadId: String): String? {
        return downloadRepository.getPlaybackUri(downloadId)
    }

    fun downloadVideo(url: String, title: String, fileName: String, mediaType: String, tmdbId: Int, season: Int, episode: Int) {
        viewModelScope.launch {
            val details = _mediaDetails.value
            if (details != null) {
                downloadRepository.downloadVideo(
                    url = url,
                    title = title,
                    fileName = fileName,
                    posterPath = details.posterPath,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode
                )
            }
        }
    }

    fun loadSeasonDetails(mediaType: String, tmdbId: Int, seasonNumber: Int, currentEpisodeNumber: Int) {
        viewModelScope.launch {
            // Fetch Media Details (Show or Movie)
            launch {
                try {
                    val result = if (mediaType == "movie") {
                        repository.getMovieDetails(tmdbId)
                    } else {
                        repository.getAnimeDetails(tmdbId)
                    }
                    result.onSuccess { details ->
                        _mediaDetails.value = details
                        repository.addToWatchHistory(details.toAnimeDto(mediaType))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fetch Season Episodes (for TV)
            if (mediaType != "movie") {
                _isLoadingEpisodes.value = true
                try {
                    // Fetch full season details
                    val seasonDetails = tmdbApi.getSeasonDetails(tmdbId, seasonNumber)

                    // Set current episode details
                    val currentEp = seasonDetails.episodes.find { it.episodeNumber == currentEpisodeNumber }
                    if (currentEp != null) {
                        _currentEpisode.value = currentEp
                    }

                    // Filter for next episodes
                    _nextEpisodes.value = seasonDetails.episodes.filter { it.episodeNumber > currentEpisodeNumber }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isLoadingEpisodes.value = false
                }
            } else {
                _nextEpisodes.value = emptyList()
                _currentEpisode.value = null
            }

            // Fetch subtitles
            try {
                val jsonElement = if (mediaType == "tv") {
                    subtitleApi.searchSubtitles(tmdbId, seasonNumber, currentEpisodeNumber)
                } else {
                    subtitleApi.searchSubtitles(tmdbId)
                }
                val json = Json { ignoreUnknownKeys = true }
                
                val subs = when (jsonElement) {
                    is JsonArray -> {
                        jsonElement.map { json.decodeFromJsonElement<SubtitleDto>(it) }
                    }
                    is JsonObject -> {
                        jsonElement.values.map { json.decodeFromJsonElement<SubtitleDto>(it) }
                    }
                    else -> emptyList()
                }
                
                _remoteSubtitles.value = subs
                android.util.Log.i("PlayerViewModel", "Fetched ${subs.size} subtitles for ID $tmdbId")
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Failed to fetch subtitles: ${e.message}", e)
            }
        }
    }
}
