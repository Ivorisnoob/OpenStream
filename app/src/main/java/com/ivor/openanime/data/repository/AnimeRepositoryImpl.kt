package com.ivor.openanime.data.repository

import android.content.SharedPreferences
import com.ivor.openanime.data.remote.TmdbApi
import com.ivor.openanime.data.remote.model.AnimeDetailsDto
import com.ivor.openanime.data.remote.model.AnimeDto
import com.ivor.openanime.data.remote.model.SeasonDetailsDto
import com.ivor.openanime.domain.repository.AnimeRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class AnimeRepositoryImpl @Inject constructor(
    private val api: TmdbApi,
    private val sharedPreferences: SharedPreferences,
    private val json: Json
) : AnimeRepository {

    private val HISTORY_KEY = "watch_history_list"

    override suspend fun getPopularAnime(page: Int): Result<List<AnimeDto>> = runCatching {
        api.getPopularAnime(page = page).results
    }

    override suspend fun getTrendingAnime(timeWindow: String, page: Int): Result<List<AnimeDto>> = runCatching {
        api.getTrendingAnime(timeWindow, page).results
    }

    override suspend fun getTopRatedAnime(page: Int): Result<List<AnimeDto>> = runCatching {
        api.getTopRatedAnime(page).results
    }

    override suspend fun getAiringTodayAnime(page: Int): Result<List<AnimeDto>> = runCatching {
        api.getAiringTodayAnime(page).results
    }

    override suspend fun searchAnime(query: String, page: Int, filter: String): Result<List<AnimeDto>> = runCatching {
        when (filter) {
            "movie" -> api.searchMovie(query, page).results.map { it.copy(mediaType = "movie") }
            "tv" -> api.searchTv(query, page).results.map { it.copy(mediaType = "tv") }
            else -> api.searchMulti(query, page).results
                .filter { it.mediaType == "tv" || it.mediaType == "movie" }
        }
    }

    override suspend fun discoverWithFilters(query: String, page: Int, mediaType: String, sortBy: String): Result<List<AnimeDto>> = runCatching {
        var keywordIds: String? = null
        if (query.isNotBlank()) {
            val keywordResponse = api.searchKeyword(query, 1)
            if (keywordResponse.results.isNotEmpty()) {
                // Use up to top 3 matching keywords for discovery
                keywordIds = keywordResponse.results.take(3).joinToString("|") { it.id.toString() }
            }
        }

        // If a query was entered but no keywords were found, fallback to standard search
        if (query.isNotBlank() && keywordIds == null) {
            return searchAnime(query, page, mediaType)
        }

        val movies = mutableListOf<AnimeDto>()
        val tvShows = mutableListOf<AnimeDto>()

        if (mediaType == "movie" || mediaType == "all") {
            val movieRes = api.discoverMovie(page = page, sortBy = sortBy, withKeywords = keywordIds)
            movies.addAll(movieRes.results.map { it.copy(mediaType = "movie") })
        }

        if (mediaType == "tv" || mediaType == "all") {
            val tvRes = api.discoverTv(page = page, sortBy = sortBy, withKeywords = keywordIds)
            tvShows.addAll(tvRes.results.map { it.copy(mediaType = "tv") })
        }

        val combined = (movies + tvShows)

        // Local sorting logic if "all" is selected since they are combined
        when (sortBy) {
            "popularity.desc" -> combined.sortedByDescending { it.popularity ?: 0.0 }
            "popularity.asc" -> combined.sortedBy { it.popularity ?: 0.0 }
            "vote_average.desc" -> combined.sortedByDescending { it.voteAverage ?: 0.0 }
            "vote_average.asc" -> combined.sortedBy { it.voteAverage ?: 0.0 }
            "first_air_date.desc", "primary_release_date.desc" -> combined.sortedByDescending { it.releaseDate ?: it.firstAirDate ?: "" }
            "first_air_date.asc", "primary_release_date.asc" -> combined.sortedBy { it.releaseDate ?: it.firstAirDate ?: "" }
            else -> combined
        }
    }

    override suspend fun getAnimeDetails(id: Int): Result<AnimeDetailsDto> = runCatching {
        api.getAnimeDetails(id = id)
    }

    override suspend fun getMovieDetails(id: Int): Result<AnimeDetailsDto> = runCatching {
        api.getMovieDetails(id = id)
    }

    override suspend fun getMediaDetails(id: Int, mediaType: String): Result<AnimeDetailsDto> = runCatching {
        if (mediaType == "movie") {
            api.getMovieDetails(id = id)
        } else {
            api.getAnimeDetails(id = id)
        }
    }

    override suspend fun getSeasonDetails(animeId: Int, seasonNumber: Int): Result<SeasonDetailsDto> = runCatching {
        api.getSeasonDetails(id = animeId, seasonNumber = seasonNumber)
    }

    override suspend fun addToWatchHistory(anime: AnimeDto) {
        val history = getWatchHistory().toMutableList()
        history.removeIf { it.id == anime.id }
        history.add(0, anime)
        if (history.size > 50) history.removeAt(history.lastIndex)
        
        sharedPreferences.edit().putString(HISTORY_KEY, json.encodeToString(history)).apply()
    }

    override suspend fun getWatchHistory(): List<AnimeDto> {
        val jsonString = sharedPreferences.getString(HISTORY_KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun clearWatchHistory() {
        sharedPreferences.edit().remove(HISTORY_KEY).apply()
    }
}
