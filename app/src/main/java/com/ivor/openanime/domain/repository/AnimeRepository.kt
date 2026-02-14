package com.ivor.openanime.domain.repository

import com.ivor.openanime.data.remote.model.AnimeDetailsDto
import com.ivor.openanime.data.remote.model.AnimeDto
import com.ivor.openanime.data.remote.model.SeasonDetailsDto

interface AnimeRepository {
    suspend fun getPopularAnime(page: Int): Result<List<AnimeDto>>
    suspend fun getTrendingAnime(timeWindow: String = "day", page: Int = 1): Result<List<AnimeDto>>
    suspend fun getTopRatedAnime(page: Int = 1): Result<List<AnimeDto>>
    suspend fun getAiringTodayAnime(page: Int = 1): Result<List<AnimeDto>>

    suspend fun searchAnime(query: String, page: Int, filter: String = "all"): Result<List<AnimeDto>>
    suspend fun getAnimeDetails(id: Int): Result<AnimeDetailsDto>
    suspend fun getMovieDetails(id: Int): Result<AnimeDetailsDto>
    suspend fun getMediaDetails(id: Int, mediaType: String): Result<AnimeDetailsDto>
    suspend fun getSeasonDetails(animeId: Int, seasonNumber: Int): Result<SeasonDetailsDto>
    
    // Watch History
    suspend fun addToWatchHistory(anime: AnimeDto)
    suspend fun getWatchHistory(): List<AnimeDto>
    suspend fun clearWatchHistory()
}
