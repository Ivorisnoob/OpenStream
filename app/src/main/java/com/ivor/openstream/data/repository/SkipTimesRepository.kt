package com.ivor.openstream.data.repository

import android.util.Log
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

/** A stretch of an episode the player offers to jump past. */
data class SkipSegment(val type: SkipType, val startMs: Long, val endMs: Long)

/**
 * Intro, recap and credits times for anime from AniSkip, a community database keyed by MyAnimeList
 * id. TMDB doesn't carry MAL ids, so the id comes from AniList's public search, matched on title and,
 * for later seasons, the year the season started airing.
 *
 * Both are keyless public APIs whose behaviour isn't formally documented or guaranteed: AniList
 * GraphQL (graphql.anilist.co, rate limited to about 90 requests a minute) and AniSkip v2
 * (api.aniskip.com). Any failure just means no skip button.
 */
@Singleton
class SkipTimesRepository @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient,
    private val json: Json
) {
    private val malIds = ConcurrentHashMap<String, Int>()

    suspend fun segmentsFor(title: String, isMovie: Boolean, seasonYear: Int?, episode: Int): List<SkipSegment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val malId = malIdFor(title, isMovie, seasonYear) ?: return@runCatching emptyList()
                skipTimes(malId, if (isMovie) 1 else episode)
            }.onFailure { Log.w(TAG, "No skip times for $title: ${it.message}") }
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
            .build()
        val text = client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        val media = json.decodeFromString(AniListResponse.serializer(), text).data?.page?.media.orEmpty()
            .filter { it.idMal != null }

        val formats = if (isMovie) setOf("MOVIE") else setOf("TV", "TV_SHORT", "ONA")
        val candidates = media.filter { it.format in formats }.ifEmpty { media }
        // Later seasons are separate AniList entries; the one that started the year the TMDB season did wins.
        val chosen = seasonYear?.let { year -> candidates.firstOrNull { it.startDate?.year == year } }
            ?: candidates.firstOrNull()
            ?: return null
        return chosen.idMal?.also { malIds[key] = it }
    }

    private fun skipTimes(malId: Int, episode: Int): List<SkipSegment> {
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
            "?types[]=op&types[]=ed&types[]=recap&types[]=mixed-op&types[]=mixed-ed&episodeLength=0"
        val text = client.newCall(Request.Builder().url(url).build()).execute()
            .use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
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
            SkipSegment(type, (interval.startTime * 1000).toLong(), (interval.endTime * 1000).toLong())
                .takeIf { it.endMs > it.startMs }
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
private data class AniSkipResult(val skipType: String? = null, val interval: AniSkipInterval? = null)

@Serializable
private data class AniSkipInterval(val startTime: Double = 0.0, val endTime: Double = 0.0)
