package com.ivor.openstream.data.streaming.anime

import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.domain.model.MediaIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** One AniList episode: what anime streaming sites index by. */
data class AnimeEpisodeRef(
    val anilistId: Int,
    val malId: Int?,
    /** Episode number within the AniList entry (1 for movies). */
    val episode: Int,
    /** Search titles, best first (romaji, English). */
    val titles: List<String>
)

/**
 * Maps a TMDB title/season/episode to the AniList entry and episode anime sites use.
 *
 * TMDB keeps a show's seasons under one id while AniList gives every season its own entry, so:
 * ani.zip (keyless) finds the franchise's first entry from the TMDB id, AniList's GraphQL API
 * (keyless) walks its sequels, and each candidate's ani.zip episode list is checked for the TVDB
 * season/episode, then for the TMDB air date. Titles ani.zip doesn't know (non-anime) map to null.
 *
 * Relies on undocumented behaviour of api.ani.zip (`themoviedb_id` lookups return the first
 * season only) and AniList's public rate limit (~30-90 requests a minute).
 */
@Singleton
class AnimeEpisodeMapper @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient,
    private val tmdbApi: TmdbApi,
    private val json: Json
) {
    /**
     * One lock per episode: the anime providers resolving it together share one lookup, while a
     * lookup still stuck on the episode the user just left doesn't hold up the next one.
     */
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val refs = ConcurrentHashMap<String, Optional>()
    private val entries = ConcurrentHashMap<Int, Entry>()
    private val roots = ConcurrentHashMap<String, Optional>()

    suspend fun map(identity: MediaIdentity): AnimeEpisodeRef? = withContext(Dispatchers.IO) {
        refs[identity.cacheKey]?.let { return@withContext it.ref }
        locks.getOrPut(identity.cacheKey) { Mutex() }.withLock {
            refs[identity.cacheKey]?.let { return@withLock it.ref }
            val ref = runCatching { lookup(identity) }.getOrElse { error ->
                // Network trouble (or cancellation) is not an answer: don't cache it.
                if (error is IOException || error is CancellationException) throw error else null
            }
            refs[identity.cacheKey] = Optional(ref)
            ref
        }
    }

    private suspend fun lookup(identity: MediaIdentity): AnimeEpisodeRef? {
        val isMovie = identity.tmdbType == "movie"
        val rootId = rootAnilistId(identity.tmdbId, isMovie) ?: return null
        val root = entry(rootId) ?: return null
        if (isMovie) return root.ref(1)

        // Walk the sequel chain lazily; most requests are for the first entry.
        val chain = mutableListOf(root)
        var cursor = root
        while (chain.size < MAX_CHAIN) {
            val next = cursor.sequelId?.let { entry(it) } ?: break
            if (chain.any { it.anilistId == next.anilistId }) break
            chain += next
            cursor = next
        }

        chain.forEach { entry ->
            entry.episodes.firstOrNull { it.season == identity.season && it.number == identity.episode }
                ?.let { return entry.ref(it.anilistEpisode) }
        }

        val airDate = runCatching {
            tmdbApi.getSeasonDetails(identity.tmdbId, identity.season)
                .episodes.firstOrNull { it.episodeNumber == identity.episode }?.airDate
        }.getOrNull()?.takeIf { it.length >= 10 }?.take(10)
        if (airDate != null) {
            chain.forEach { entry ->
                entry.episodes.firstOrNull { it.airDate == airDate }
                    ?.let { return entry.ref(it.anilistEpisode) }
            }
        }

        // Single-entry shows TMDB numbers the same way AniList does.
        if (identity.season == 1 && (root.episodeCount == null || identity.episode <= root.episodeCount)) {
            return root.ref(identity.episode)
        }
        return null
    }

    private fun rootAnilistId(tmdbId: Int, isMovie: Boolean): Int? {
        val key = "${if (isMovie) "movie" else "tv"}:$tmdbId"
        roots[key]?.let { return it.ref?.anilistId }
        val body = get("$ANIZIP/mappings?themoviedb_id=$tmdbId") ?: run {
            roots[key] = Optional(null)
            return null
        }
        val mappings = json.parseToJsonElement(body).jsonObject["mappings"]?.jsonObject
        val type = mappings?.get("type")?.jsonPrimitive?.contentOrNull
        // TMDB movie and TV ids share numbers; the entry's type says which one ani.zip matched.
        val id = mappings?.get("anilist_id")?.jsonPrimitive?.intOrNull
            ?.takeIf { (type == "MOVIE") == isMovie }
        roots[key] = Optional(id?.let { AnimeEpisodeRef(it, null, 1, emptyList()) })
        return id
    }

    private fun entry(anilistId: Int): Entry? {
        entries[anilistId]?.let { return it }
        val media = anilistMedia(anilistId) ?: return null
        val episodes = get("$ANIZIP/mappings?anilist_id=$anilistId")
            ?.let { parseEpisodes(it) }
            .orEmpty()
        val titles = media["title"]?.jsonObject
        val entry = Entry(
            anilistId = anilistId,
            malId = media["idMal"]?.jsonPrimitive?.intOrNull,
            titles = listOfNotNull(
                titles?.get("romaji")?.jsonPrimitive?.contentOrNull,
                titles?.get("english")?.jsonPrimitive?.contentOrNull
            ).distinct(),
            episodeCount = media["episodes"]?.jsonPrimitive?.intOrNull,
            sequelId = media["relations"]?.jsonObject?.get("edges")?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { edge ->
                    val node = edge["node"]?.jsonObject
                    edge["relationType"]?.jsonPrimitive?.contentOrNull == "SEQUEL" &&
                        node?.get("type")?.jsonPrimitive?.contentOrNull == "ANIME" &&
                        node["format"]?.jsonPrimitive?.contentOrNull in SERIES_FORMATS
                }
                ?.get("node")?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull,
            episodes = episodes
        )
        entries[anilistId] = entry
        return entry
    }

    private fun anilistMedia(id: Int): JsonObject? {
        val payload = buildJsonObject {
            put("query", MEDIA_QUERY)
            putJsonObject("variables") { put("id", id) }
        }.toString()
        val request = Request.Builder()
            .url(ANILIST)
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_USER_AGENT)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("AniList returned HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        return json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("Media") as? JsonObject
    }

    /** ani.zip `episodes`: numeric keys are AniList episodes; `S1`-style keys are specials. */
    private fun parseEpisodes(body: String): List<EpisodeMapping> {
        val episodes = json.parseToJsonElement(body).jsonObject["episodes"] as? JsonObject ?: return emptyList()
        return episodes.mapNotNull { (key, value) ->
            val number = key.toIntOrNull() ?: return@mapNotNull null
            val episode = value as? JsonObject ?: return@mapNotNull null
            EpisodeMapping(
                anilistEpisode = number,
                season = episode["seasonNumber"]?.jsonPrimitive?.intOrNull,
                number = episode["episodeNumber"]?.jsonPrimitive?.intOrNull,
                airDate = (episode["airDate"] ?: episode["airdate"])?.jsonPrimitive?.contentOrNull?.take(10)
            )
        }
    }

    private fun get(url: String): String? =
        client.newCall(
            Request.Builder().url(url).header("User-Agent", BROWSER_USER_AGENT).build()
        ).execute().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                response.code == 404 -> null
                else -> throw IOException("${response.request.url.host} returned HTTP ${response.code}")
            }
        }

    private data class Optional(val ref: AnimeEpisodeRef?)

    private data class EpisodeMapping(
        val anilistEpisode: Int,
        val season: Int?,
        val number: Int?,
        val airDate: String?
    )

    private data class Entry(
        val anilistId: Int,
        val malId: Int?,
        val titles: List<String>,
        val episodeCount: Int?,
        val sequelId: Int?,
        val episodes: List<EpisodeMapping>
    ) {
        fun ref(episode: Int) = AnimeEpisodeRef(anilistId, malId, episode, titles)
    }

    private companion object {
        const val ANIZIP = "https://api.ani.zip"
        const val ANILIST = "https://graphql.anilist.co"
        const val MAX_CHAIN = 12
        val SERIES_FORMATS = setOf("TV", "TV_SHORT", "ONA")
        const val MEDIA_QUERY =
            "query(\$id:Int){Media(id:\$id,type:ANIME){id idMal episodes format " +
                "title{romaji english} relations{edges{relationType node{id type format}}}}}"
    }
}
