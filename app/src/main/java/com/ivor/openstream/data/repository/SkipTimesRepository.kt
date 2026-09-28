package com.ivor.openstream.data.repository

import android.util.Log
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.anime.AnimeEpisodeMapper
import com.ivor.openstream.domain.model.MediaIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class SkipType(val label: String) {
    INTRO("Skip intro"),
    RECAP("Skip recap"),
    CREDITS("Skip credits")
}

/**
 * A stretch of an episode the player offers to jump past. [episodeLengthMs] is the length of the
 * file the submitter timed (0 when unknown); cuts of different lengths have different times.
 */
data class SkipSegment(val type: SkipType, val startMs: Long, val endMs: Long, val episodeLengthMs: Long = 0L)

/**
 * The segments that fit a video [durationMs] long: per type, the submission timed on the file
 * closest in length. Before the duration is known the first submission of each type is used.
 */
fun List<SkipSegment>.forDuration(durationMs: Long): List<SkipSegment> =
    groupBy { it.type }.values.mapNotNull { sameType ->
        if (durationMs <= 0L) sameType.firstOrNull()
        else sameType.minByOrNull { if (it.episodeLengthMs > 0) kotlin.math.abs(it.episodeLengthMs - durationMs) else Long.MAX_VALUE / 2 }
    }.filter { durationMs <= 0L || it.startMs < durationMs }.sortedBy { it.startMs }

/**
 * Intro, recap and credits times for anime from AniSkip, a community database keyed by MyAnimeList
 * id and the episode number within that MAL entry.
 *
 * TMDB doesn't carry MAL ids, and TMDB seasons often don't line up with MAL entries (split cours,
 * arc-based seasons, absolute numbering). So the lookup goes through [AnimeEpisodeMapper] first,
 * the same TMDB -> AniList mapping the anime sources use (ani.zip + AniList), which also gives the
 * episode number inside the entry. Only titles it can't map fall back to AniList's title search,
 * matched on format and the year the season started airing.
 *
 * All of these are keyless public APIs whose behaviour isn't formally documented or guaranteed:
 * api.ani.zip, AniList GraphQL (graphql.anilist.co, about 90 requests a minute) and AniSkip v2
 * (api.aniskip.com). Any failure just means no skip button.
 */
@Singleton
class SkipTimesRepository @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient,
    private val json: Json,
    private val episodeMapper: AnimeEpisodeMapper
) {
    private val malIds = ConcurrentHashMap<String, Int>()

    /**
     * @param identity the TMDB title, season and episode being played.
     * @param seasonYear the year the TMDB season started airing, for the title-search fallback.
     */
    suspend fun segmentsFor(identity: MediaIdentity, seasonYear: Int?): List<SkipSegment> =
        withContext(Dispatchers.IO) {
            val isMovie = identity.tmdbType == "movie"
            val mapped = runCatching { episodeMapper.map(identity) }
                .onFailure { Log.w(TAG, "Episode mapping failed for ${identity.title}: ${it.message}") }
                .getOrNull()
            val fromMapping = mapped?.malId?.let { malId ->
                runCatching { skipTimes(malId, if (isMovie) 1 else mapped.episode) }
                    .onFailure { Log.w(TAG, "AniSkip failed for MAL $malId: ${it.message}") }
                    .getOrNull()
            }
            if (!fromMapping.isNullOrEmpty()) return@withContext fromMapping
            // Mapped to a MAL entry that simply has no times: a title search would only guess worse.
            if (mapped?.malId != null && fromMapping != null) return@withContext emptyList()

            runCatching {
                val malId = malIdFor(identity.title, isMovie, seasonYear) ?: return@runCatching emptyList()
                skipTimes(malId, if (isMovie) 1 else identity.episode)
            }.onFailure { Log.w(TAG, "No skip times for ${identity.title}: ${it.message}") }
                .getOrDefault(emptyList())
        }

    private fun malIdFor(title: String, isMovie: Boolean, seasonYear: Int?): Int? {
        val key = "$title|$isMovie|$seasonYear"
        malIds[key]?.let { return it }

        val body = buildJsonObject {
            put("query", ANILIST_QUERY)
            putJsonObject("variables") { put("search", title) }
        }.toString()
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_USER_AGENT)
            .build()
        val text = client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        val media = json.decodeFromString(AniListResponse.serializer(), text).data?.page?.media.orEmpty()
            .filter { it.idMal != null }

        // Full series first: ONA shorts and specials often rank above the show itself
        // ("Sousou no Frieren: ●● no Mahou" comes before "Sousou no Frieren").
        val formatGroups = if (isMovie) listOf(setOf("MOVIE")) else listOf(setOf("TV"), setOf("TV_SHORT", "ONA"))
        val candidates = formatGroups.asSequence()
            .map { formats -> media.filter { it.format in formats } }
            .firstOrNull { group -> group.isNotEmpty() && (seasonYear == null || group.any { it.startDate?.year == seasonYear }) }
            ?: formatGroups.asSequence().map { formats -> media.filter { it.format in formats } }.firstOrNull { it.isNotEmpty() }
            ?: return null
        // Later seasons are separate AniList entries; the one that started the year the TMDB season did wins.
        val chosen = seasonYear?.let { year -> candidates.firstOrNull { it.startDate?.year == year } }
            ?: candidates.firstOrNull()
            ?: return null
        return chosen.idMal?.also { malIds[key] = it }
    }

    private fun skipTimes(malId: Int, episode: Int): List<SkipSegment> {
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
            "?types=op&types=ed&types=recap&types=mixed-op&types=mixed-ed&episodeLength=0"
        val request = Request.Builder().url(url).header("User-Agent", BROWSER_USER_AGENT).build()
        val text = client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                // AniSkip answers 404 when an episode has no submissions.
                response.code == 404 -> return emptyList()
                else -> throw java.io.IOException("AniSkip returned HTTP ${response.code}")
            }
        } ?: return emptyList()
        val response = json.decodeFromString(AniSkipResponse.serializer(), text)
        if (!response.found) return emptyList()
        return response.results.mapNotNull { result ->
            val type = when (result.skipType) {
                "op", "mixed-op" -> SkipType.INTRO
                "recap" -> SkipType.RECAP
                "ed", "mixed-ed" -> SkipType.CREDITS
                else -> return@mapNotNull null
            }
            val interval = result.interval ?: return@mapNotNull null
            SkipSegment(
                type = type,
                startMs = (interval.startTime * 1000).toLong(),
                endMs = (interval.endTime * 1000).toLong(),
                episodeLengthMs = (result.episodeLength * 1000).toLong()
            ).takeIf { it.endMs > it.startMs }
        }.sortedBy { it.startMs }
    }

    private companion object {
        const val TAG = "SkipTimes"
        const val ANILIST_QUERY =
            "query (\$search: String) { Page(perPage: 10) { media(search: \$search, type: ANIME) " +
                "{ idMal format startDate { year } } } }"
    }
}

@Serializable
private data class AniListResponse(val data: AniListData? = null)

@Serializable
private data class AniListData(@kotlinx.serialization.SerialName("Page") val page: AniListPage? = null)

@Serializable
private data class AniListPage(val media: List<AniListMedia> = emptyList())

@Serializable
private data class AniListMedia(val idMal: Int? = null, val format: String? = null, val startDate: AniListDate? = null)

@Serializable
private data class AniListDate(val year: Int? = null)

@Serializable
private data class AniSkipResponse(val found: Boolean = false, val results: List<AniSkipResult> = emptyList())

@Serializable
private data class AniSkipResult(
    val skipType: String? = null,
    val interval: AniSkipInterval? = null,
    val episodeLength: Double = 0.0
)

@Serializable
private data class AniSkipInterval(val startTime: Double = 0.0, val endTime: Double = 0.0)
