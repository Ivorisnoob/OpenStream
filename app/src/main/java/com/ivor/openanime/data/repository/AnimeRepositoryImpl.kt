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
