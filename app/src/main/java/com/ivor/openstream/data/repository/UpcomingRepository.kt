package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.model.AiringEpisodeDto
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.WatchLaterRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class UpcomingKind { SERIES, ANIME, MOVIE }

/** One dated release: an episode of a followed show, or a saved film. */
data class UpcomingItem(
    val tmdbId: Int,
    val mediaType: String,
    val kind: UpcomingKind,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val stillPath: String?,
    val season: Int?,
    val episode: Int?,
    val episodeName: String?,
    val airDate: LocalDate
) {
    val key: String get() = "$mediaType:$tmdbId:$season:$episode"
}

data class UpcomingSchedule(
    val items: List<UpcomingItem>,
    /** Titles the schedule was built from. */
    val followedCount: Int,
    /** Titles TMDB could not answer for, so the schedule may be missing something. */
    val failedCount: Int
)

/**
 * What is coming from the titles the user follows: saved titles plus shows with watch progress, as
 * for the new-episode notification. TMDB dates are days with no air time, so the schedule stops at
 * the day. Everything goes through the shared TMDB cache, so opening the screen is cheap.
 */
@Singleton
class UpcomingRepository @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val watchProgressRepository: WatchProgressRepository
) {
    private data class Followed(val id: Int, val mediaType: String)

    suspend fun load(today: LocalDate = LocalDate.now(), fresh: Boolean = false): UpcomingSchedule = coroutineScope {
        val saved = watchLaterRepository.getWatchLaterList().first()
        val progress = watchProgressRepository.allProgress().first()
        val watched = progress.filter { it.completed }.mapTo(HashSet()) { Triple(it.tmdbId, it.season, it.episode) }
        val followed = (
            saved.map { Followed(it.id, if (it.mediaType == "movie") "movie" else "tv") } +
                progress.filter { !it.isMovie && !it.isUpNext }.map { Followed(it.tmdbId, "tv") }
            ).distinct().take(MAX_TITLES)

        val permits = Semaphore(MAX_CONCURRENT)
        val results = followed.map { title ->
            async {
                permits.withPermit {
                    try {
                        if (title.mediaType == "movie") {
                            movieItems(title.id, today, fresh)
                        } else {
                            showItems(title.id, today, fresh, watched)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll()

        UpcomingSchedule(
            items = results.filterNotNull().flatten().sortedWith(compareBy({ it.airDate }, { it.title }, { it.episode })),
            followedCount = followed.size,
            failedCount = results.count { it == null }
        )
    }

    private suspend fun movieItems(id: Int, today: LocalDate, fresh: Boolean): List<UpcomingItem> {
        val details = animeRepository.getMediaDetails(id, "movie", fresh).getOrThrow()
        val date = parseDate(details.date) ?: return emptyList()
        if (date.isBefore(today.minusDays(PAST_DAYS)) || date.isAfter(today.plusDays(MOVIE_FUTURE_DAYS))) return emptyList()
        return listOf(
            UpcomingItem(
                tmdbId = id,
                mediaType = "movie",
                kind = UpcomingKind.MOVIE,
                title = details.name,
                posterPath = details.posterPath,
                backdropPath = details.backdropPath,
                stillPath = null,
                season = null,
                episode = null,
                episodeName = null,
                airDate = date
            )
        )
    }

    private suspend fun showItems(
        id: Int,
        today: LocalDate,
        fresh: Boolean,
        watched: Set<Triple<Int, Int, Int>>
    ): List<UpcomingItem> {
        val details = animeRepository.getMediaDetails(id, "tv", fresh).getOrThrow()
        val first = today.minusDays(PAST_DAYS)
        val last = today.plusDays(SHOW_FUTURE_DAYS)
        val known = listOfNotNull(details.nextEpisodeToAir, details.lastEpisodeToAir)
            .filter { parseDate(it.airDate)?.let { date -> date in first..last } == true }
        if (known.isEmpty()) return emptyList()

        // The season lists add stills and whatever comes after the next episode. Without them, the
        // two episodes the title itself names still make a usable schedule.
        val listed = known.map { it.seasonNumber }.distinct().flatMap { season ->
            animeRepository.getSeasonDetails(id, season, fresh).getOrNull()?.episodes.orEmpty()
        }
        val anime = details.genres.orEmpty().any { it.id == 16 } && details.originalLanguage.equals("ja", ignoreCase = true)
        fun item(season: Int, episode: Int, name: String?, airDate: String?, stillPath: String?): UpcomingItem? {
            val date = parseDate(airDate)?.takeIf { it in first..last } ?: return null
            if (Triple(id, season, episode) in watched) return null
            return UpcomingItem(
                tmdbId = id,
                mediaType = "tv",
                kind = if (anime) UpcomingKind.ANIME else UpcomingKind.SERIES,
                title = details.name,
                posterPath = details.posterPath,
                backdropPath = details.backdropPath,
                stillPath = stillPath,
                season = season,
                episode = episode,
                episodeName = name?.takeIf { it.isNotBlank() },
                airDate = date
            )
        }
        val fromSeasons = listed.mapNotNull { item(it.seasonNumber, it.episodeNumber, it.name, it.airDate, it.stillPath) }
        val listedKeys = listed.mapTo(HashSet()) { it.seasonNumber to it.episodeNumber }
        val fromTitle = known
            .filter { (it.seasonNumber to it.episodeNumber) !in listedKeys }
            .mapNotNull { airing: AiringEpisodeDto ->
                item(airing.seasonNumber, airing.episodeNumber, airing.name, airing.airDate, null)
            }
        return fromSeasons + fromTitle
    }

    private fun parseDate(value: String?): LocalDate? =
        value?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    private companion object {
        const val MAX_TITLES = 60
        const val MAX_CONCURRENT = 6
        const val PAST_DAYS = 7L
        const val SHOW_FUTURE_DAYS = 60L
        const val MOVIE_FUTURE_DAYS = 365L
    }
}
