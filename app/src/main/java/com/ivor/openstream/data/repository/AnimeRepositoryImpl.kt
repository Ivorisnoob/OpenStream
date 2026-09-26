package com.ivor.openstream.data.repository

import android.content.SharedPreferences
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.SeasonDetailsDto
import com.ivor.openstream.domain.repository.AnimeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ivor.openstream.domain.model.AnimeCatalog
import com.ivor.openstream.domain.model.BrowseGenre
import kotlinx.coroutines.awaitAll
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

    override suspend fun getCatalog(catalog: AnimeCatalog, forceRefresh: Boolean): Result<List<AnimeDto>> {
        val cached = catalogCache[catalog] ?: readCachedCatalog(catalog)?.also { catalogCache[catalog] = it }
        val isFresh = cached != null && System.currentTimeMillis() - cached.savedAt < CATALOG_TTL_MS
        if (!forceRefresh && isFresh) return Result.success(cached!!.items)
        return fetchCatalog(catalog)
            .onSuccess { items ->
                val entry = CachedCatalog(System.currentTimeMillis(), items)
                catalogCache[catalog] = entry
                sharedPreferences.edit().putString(catalogKey(catalog), json.encodeToString(entry)).apply()
            }
            // Offline or rate limited: an older list beats an empty Home.
            .recoverCatching { error -> cached?.items ?: throw error }
    }

    private fun readCachedCatalog(catalog: AnimeCatalog): CachedCatalog? = runCatching {
        sharedPreferences.getString(catalogKey(catalog), null)?.let { json.decodeFromString<CachedCatalog>(it) }
    }.getOrNull()

    private fun catalogKey(catalog: AnimeCatalog) = "catalog_cache_${catalog.name}"

    private suspend fun fetchCatalog(catalog: AnimeCatalog): Result<List<AnimeDto>> = runCatching {
        val anime = mapOf(
            "with_genres" to "$ANIMATION_GENRE",
            "with_original_language" to "ja",
            "include_adult" to "false"
        )
        val results = when (catalog) {
            AnimeCatalog.TRENDING -> api.getTrendingAll("week").results
                // The mixed feed also lists people.
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            AnimeCatalog.NEW_EPISODES -> api.getOnTheAir().results
            AnimeCatalog.POPULAR_MOVIES -> api.discoverMovieWith(
                mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "300", "include_adult" to "false")
            ).results.map { it.copy(mediaType = "movie") }
            AnimeCatalog.POPULAR_SERIES -> api.discoverTvWith(
                mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "200", "include_adult" to "false")
            ).results
            AnimeCatalog.TOP_RATED_MOVIES -> api.discoverMovieWith(
                mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "3000", "include_adult" to "false")
            ).results.map { it.copy(mediaType = "movie") }
            AnimeCatalog.TRENDING_ANIME -> coroutineScope {
                // TMDB's trending feed cannot be filtered server-side, so read a few pages and keep anime.
                (1..3).map { page -> async { api.getTrendingAnime("week", page).results } }
                    .awaitAll()
                    .flatten()
                    .filter { it.originalLanguage == "ja" && it.genreIds.orEmpty().contains(ANIMATION_GENRE) }
            }.ifEmpty { api.discoverTvWith(anime + ("sort_by" to "popularity.desc")).results }
            AnimeCatalog.ANIME_MOVIES -> api.discoverMovieWith(
                anime + mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "100")
            ).results.map { it.copy(mediaType = "movie") }
        }
        results.filter { it.posterPath != null }.distinctBy { "${it.mediaType}:${it.id}" }
    }

    override suspend fun discoverByGenre(genre: BrowseGenre, page: Int): Result<List<AnimeDto>> = runCatching {
        coroutineScope {
            val common = mapOf("sort_by" to "popularity.desc", "include_adult" to "false", "page" to "$page") +
                if (genre.isAnime) mapOf("with_original_language" to "ja") else emptyMap()
            val movies = genre.movieGenreId?.let { id ->
                async {
                    api.discoverMovieWith(common + mapOf("with_genres" to "$id", "vote_count.gte" to "50"))
                        .results.map { it.copy(mediaType = "movie") }
                }
            }
            val series = genre.tvGenreId?.let { id ->
                async {
                    api.discoverTvWith(common + mapOf("with_genres" to "$id", "vote_count.gte" to "30"))
                        .results.map { it.copy(mediaType = "tv") }
                }
            }
            (movies?.await().orEmpty() + series?.await().orEmpty())
                .filter { it.posterPath != null }
                .sortedByDescending { it.popularity ?: 0.0 }
        }
    }

    override suspend fun searchAnime(
        query: String,
        page: Int,
        mediaType: String,
        sortBy: String
    ): Result<List<AnimeDto>> = runCatching {
        require(query.isNotBlank()) { "Search query cannot be blank" }

        val (tvShows, movies) = coroutineScope {
            when (mediaType) {
                "tv" -> api.searchTv(query.trim(), page).results to emptyList()
                "movie" -> emptyList<AnimeDto>() to api.searchMovie(query.trim(), page).results
                else -> {
                    val tvRequest = async { api.searchTv(query.trim(), page).results }
                    val movieRequest = async { api.searchMovie(query.trim(), page).results }
                    tvRequest.await() to movieRequest.await()
                }
            }
        }

        AnimeSearchResults.prepare(
            tvShows = tvShows,
            movies = movies,
            sortBy = sortBy,
            query = query
        )
    }

    override suspend fun getAnimeDetails(id: Int): Result<AnimeDetailsDto> = runCatching {
        api.getAnimeDetails(id = id)
    }

    override suspend fun getMovieDetails(id: Int): Result<AnimeDetailsDto> = runCatching {
        api.getMovieDetails(id = id)
    }

    override suspend fun getMediaDetails(id: Int, mediaType: String): Result<AnimeDetailsDto> = runCatching {
        // Everything the title page shows, in one request.
        if (mediaType == "movie") {
            api.getMovieDetails(id = id, appendToResponse = "videos,credits,recommendations")
        } else {
            api.getAnimeDetails(id = id, appendToResponse = "videos,aggregate_credits,recommendations")
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

    private val catalogCache = java.util.concurrent.ConcurrentHashMap<AnimeCatalog, CachedCatalog>()

    @kotlinx.serialization.Serializable
    private data class CachedCatalog(val savedAt: Long, val items: List<AnimeDto>)

    private companion object {
        const val ANIMATION_GENRE = 16
        const val CATALOG_TTL_MS = 3 * 60 * 60 * 1000L
    }
}
