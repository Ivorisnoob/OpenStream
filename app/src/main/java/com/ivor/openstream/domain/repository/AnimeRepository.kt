package com.ivor.openstream.domain.repository

import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.SeasonDetailsDto
import com.ivor.openstream.domain.model.AnimeCatalog
import com.ivor.openstream.domain.model.BrowseGenre

interface AnimeRepository {
    suspend fun getPopularAnime(page: Int): Result<List<AnimeDto>>
    suspend fun getTrendingAnime(timeWindow: String = "day", page: Int = 1): Result<List<AnimeDto>>
    suspend fun getTopRatedAnime(page: Int = 1): Result<List<AnimeDto>>
    suspend fun getAiringTodayAnime(page: Int = 1): Result<List<AnimeDto>>

    /** One of Home's curated, anime-only lists. */
    /**
     * One of Home's curated lists. Lists are cached for a few hours so Home opens instantly and
     * stays stable; [forceRefresh] fetches a fresh copy (pull to refresh).
     */
    suspend fun getCatalog(catalog: AnimeCatalog, forceRefresh: Boolean = false): Result<List<AnimeDto>>

    /** Popular movies and series in a genre, interleaved by popularity. */
    suspend fun discoverByGenre(genre: BrowseGenre, page: Int): Result<List<AnimeDto>>

    suspend fun searchAnime(
        query: String,
        page: Int,
        mediaType: String = "all",
        sortBy: String = "popularity.desc"
    ): Result<List<AnimeDto>>
    suspend fun getAnimeDetails(id: Int): Result<AnimeDetailsDto>
    suspend fun getMovieDetails(id: Int): Result<AnimeDetailsDto>
    suspend fun getMediaDetails(id: Int, mediaType: String): Result<AnimeDetailsDto>
    suspend fun getSeasonDetails(animeId: Int, seasonNumber: Int): Result<SeasonDetailsDto>
    
    // Watch History
    suspend fun addToWatchHistory(anime: AnimeDto)
    suspend fun getWatchHistory(): List<AnimeDto>
    suspend fun clearWatchHistory()
}
