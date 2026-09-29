package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a kids profile may see: US certification G/PG for movies, TV-Y/TV-Y7/TV-G/TV-PG for shows.
 *
 * Discover requests carry TMDB's `certification_country`/`certification.lte` filters. Lists TMDB
 * can't filter (trending, airing, search) and shows are checked title by title against their US
 * rating (`release_dates` / `content_ratings`); a title without a US rating is left out.
 */
@Singleton
class KidsContentFilter @Inject constructor(
    private val api: TmdbApi,
    private val profiles: ProfileRepository
) {
    private val verdicts = ConcurrentHashMap<String, Boolean>()
    private val permits = Semaphore(6)

    val isActive: Boolean get() = profiles.isKidsActive

    /** Extra query parameters for `discover/movie` while a kids profile is active. */
    fun movieDiscoverParams(): Map<String, String> =
        if (isActive) mapOf("certification_country" to "US", "certification.lte" to "PG") else emptyMap()

    /** Extra query parameters for `discover/tv` while a kids profile is active. */
    fun tvDiscoverParams(): Map<String, String> =
        if (isActive) mapOf("certification_country" to "US", "certification.lte" to "TV-PG") else emptyMap()

    /** The titles a kids profile may see, in order; everything when no kids profile is active. */
    suspend fun filter(items: List<AnimeDto>, moviesPreFiltered: Boolean = false): List<AnimeDto> {
        if (!isActive || items.isEmpty()) return items
        return coroutineScope {
            items.map { item ->
                async {
                    val allowed = when {
                        item.adult == true -> false
                        item.isMovie && moviesPreFiltered -> true
                        else -> permits.withPermit { isAllowed(item) }
                    }
                    item.takeIf { allowed }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun isAllowed(item: AnimeDto): Boolean {
        val key = "${if (item.isMovie) "movie" else "tv"}:${item.id}"
        verdicts[key]?.let { return it }
        val verdict = runCatching {
            if (item.isMovie) {
                api.getMovieReleaseDates(item.id).results
                    .firstOrNull { it.country == "US" }
                    ?.releaseDates?.map { it.certification.trim() }?.filter { it.isNotEmpty() }
                    ?.let { certifications -> certifications.isNotEmpty() && certifications.all { it in MOVIE_RATINGS } }
                    ?: false
            } else {
                api.getTvContentRatings(item.id).results
                    .firstOrNull { it.country == "US" }
                    ?.rating?.trim()
                    ?.let { it in TV_RATINGS }
                    ?: false
            }
        }.getOrNull() ?: return false // Network trouble: hide it now, ask again next time.
        verdicts[key] = verdict
        return verdict
    }

    private companion object {
        val MOVIE_RATINGS = setOf("G", "PG")
        val TV_RATINGS = setOf("TV-Y", "TV-Y7", "TV-G", "TV-PG")
    }
}
