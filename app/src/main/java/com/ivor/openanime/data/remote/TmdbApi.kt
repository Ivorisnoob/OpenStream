package com.ivor.openanime.data.remote

import com.ivor.openanime.data.remote.model.AnimeDetailsDto
import com.ivor.openanime.data.remote.model.AnimeDto
import com.ivor.openanime.data.remote.model.SeasonDetailsDto
import com.ivor.openanime.data.remote.model.TmdbResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("discover/tv")
    suspend fun getPopularAnime(
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("with_genres") genres: String = "16", // Animation
        @Query("with_original_language") language: String = "ja", // Anime usually
        @Query("with_keywords") keywords: String = "210024|287501" // Optionally specify anime-specific keywords
    ): TmdbResponse<AnimeDto>

    @GET("trending/tv/{time_window}")
    suspend fun getTrendingAnime(
        @Path("time_window") timeWindow: String = "day",
        @Query("page") page: Int = 1
    ): TmdbResponse<AnimeDto>

    @GET("tv/top_rated")
    suspend fun getTopRatedAnime(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbResponse<AnimeDto>

    @GET("tv/airing_today")
    suspend fun getAiringTodayAnime(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US",
        @Query("timezone") timezone: String = "America/New_York"
    ): TmdbResponse<AnimeDto>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<AnimeDto>

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<AnimeDto>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<AnimeDto>

    @GET("tv/{id}")
    suspend fun getAnimeDetails(
        @Path("id") id: Int
    ): AnimeDetailsDto

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int
    ): AnimeDetailsDto

    @GET("tv/{id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @Path("id") id: Int,
        @Path("season_number") seasonNumber: Int
    ): SeasonDetailsDto
}
