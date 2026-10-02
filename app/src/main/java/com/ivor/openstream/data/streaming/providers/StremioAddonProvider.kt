package com.ivor.openstream.data.streaming.providers

import android.util.Log
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.StreamSubtitle
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * A Stremio add-on, spoken over its public HTTP protocol
 * (https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md):
 * `{base}/manifest.json` says which content types and id schemes it serves, and
 * `{base}/stream/{movie|series}/{id}.json` lists streams, where `id` is an IMDb id
 * (`tt0133093`, or `tt0903747:1:1` for an episode) or, when the add-on accepts them, `tmdb:` ids.
 *
 * Only streams with a direct `url` can play here. Torrent (`infoHash`), YouTube and external-link
 * streams are skipped: there is no torrent engine or debrid account behind them. Configured add-ons
 * keep their settings in the base URL, so they work as pasted.
 */
class StremioAddonProvider(
    override val id: String,
    override val displayName: String,
    override val priority: Int,
    override val isFallback: Boolean,
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json
) : StreamProvider {
    override val isEnabled: Boolean = true

    /** Read once per app run; add-ons rarely change what they serve. */
    @Volatile
    private var addonInfo: AddonInfo? = null

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> = withContext(Dispatchers.IO) {
        try {
            val info = addonInfo ?: loadAddonInfo().also { addonInfo = it }
            val type = if (identity.tmdbType == "movie") "movie" else "series"
            if (info.types.isNotEmpty() && type !in info.types) return@withContext Result.success(emptyList())

            // The add-on's preferred id scheme first; the next only when the first finds nothing.
            for (videoId in candidateIds(identity, info)) {
                val servers = fetchStreams(type, videoId)
                if (servers.isNotEmpty()) return@withContext Result.success(servers)
            }
            Result.success(emptyList())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "$displayName: ${error.message}")
            Result.failure(error)
        }
    }

    private fun candidateIds(identity: MediaIdentity, info: AddonInfo): List<String> {
        val suffix = if (identity.tmdbType == "movie") "" else ":${identity.season}:${identity.episode}"
        val prefixes = info.idPrefixes
        val imdb = identity.imdbId?.takeIf { it.startsWith("tt") }
        val takesImdb = prefixes == null || prefixes.any { it.startsWith("tt") || it == "imdb" }
        val takesTmdb = prefixes != null && prefixes.any { it.startsWith("tmdb") }
        return buildList {
            if (imdb != null && takesImdb) add(imdb + suffix)
            if (takesTmdb) add("tmdb:${identity.tmdbId}$suffix")
        }
    }

    private fun fetchStreams(type: String, videoId: String): List<VideoServer> {
        val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("stream")
            ?.addPathSegment(type)
            ?.addPathSegment("$videoId.json")
            ?.build()
            ?: throw IOException("Bad add-on address")
        val body = get(url.toString()) ?: return emptyList()
        val streams = (json.parseToJsonElement(body) as? JsonObject)?.get("streams") as? JsonArray ?: return emptyList()

        val seenIds = HashSet<String>()
        return streams.mapNotNull { element ->
            val stream = element as? JsonObject ?: return@mapNotNull null
            toServer(stream)?.let { server ->
                // Two streams with the same label still need different ids.
                var serverId = server.id
                var suffix = 2
                while (!seenIds.add(serverId)) serverId = "${server.id}-${suffix++}"
                server.copy(id = serverId)
            }
        }.take(MAX_STREAMS)
    }

    private fun toServer(stream: JsonObject): VideoServer? {
        val url = stream.text("url")?.trim()
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return null

        val hints = stream["behaviorHints"] as? JsonObject
        val filename = hints?.text("filename")
        val nameLines = stream.text("name").nonBlankLines()
        val detailLines = (stream.text("title") ?: stream.text("description")).nonBlankLines()
        val label = (nameLines.take(2) + detailLines.take(1))
            .joinToString(" · ")
            .ifBlank { displayName }
            .take(MAX_LABEL_LENGTH)

        val requestHeaders = ((hints?.get("proxyHeaders") as? JsonObject)?.get("request") as? JsonObject)
            ?.mapNotNull { (name, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { name to it } }
            ?.toMap()
            .orEmpty()

        val subtitles = (stream["subtitles"] as? JsonArray).orEmpty().mapNotNull { element ->
            val subtitle = element as? JsonObject ?: return@mapNotNull null
            val subtitleUrl = subtitle.text("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val rawLanguage = subtitle.text("lang")
            val language = SavedSubtitleRepository.normalizeLanguage(rawLanguage)
            StreamSubtitle(
                url = subtitleUrl,
                label = language?.let { SavedSubtitleRepository.languageName(it) } ?: rawLanguage ?: "Subtitles",
                language = language
            )
        }

        val searchable = listOfNotNull(stream.text("name"), stream.text("title"), stream.text("description"), filename)
            .joinToString(" ")
        return VideoServer(
            id = "$id-" + Integer.toHexString((label + filename.orEmpty()).hashCode()),
            providerId = id,
            providerName = displayName,
            name = label,
            url = url,
            quality = qualityOf(searchable),
            headers = mapOf("User-Agent" to BROWSER_USER_AGENT) + requestHeaders,
            subtitles = subtitles
        )
    }

    /**
     * An explicit resolution ("1080p") wins over words like "4K" that add-ons also use in their
     * own names ("4KHDHub 1080p" is a 1080p stream).
     */
    private fun qualityOf(text: String): StreamQuality {
        val height = Regex("(?i)(?:^|\\D)(2160|1440|1080|720|480|360)p").find(text)?.groupValues?.get(1)
        return when {
            height != null -> StreamQuality.parse("${height}p")
            Regex("(?i)\\b(4k|uhd)\\b").containsMatchIn(text) -> StreamQuality.Q2160
            else -> StreamQuality.UNKNOWN
        }
    }

    private fun loadAddonInfo(): AddonInfo {
        val body = runCatching { get("${baseUrl.trimEnd('/')}/manifest.json") }.getOrNull()
            ?: return AddonInfo(types = emptySet(), idPrefixes = null)
        val manifest = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return AddonInfo(types = emptySet(), idPrefixes = null)
        // A "stream" resource given as an object may narrow types and id prefixes for itself.
        val streamResource = (manifest["resources"] as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .firstOrNull { it.text("name") == "stream" }
        val types = (streamResource?.get("types") ?: manifest["types"]).strings()
        val prefixes = (streamResource?.get("idPrefixes") ?: manifest["idPrefixes"])
            ?.let { it.strings() }
            ?.takeIf { it.isNotEmpty() }
        return AddonInfo(types = types.toSet(), idPrefixes = prefixes)
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                // Many add-ons answer 404 for titles they don't have.
                response.code == 404 -> null
                else -> throw IOException("$displayName returned HTTP ${response.code}")
            }
        }
    }

    private data class AddonInfo(
        val types: Set<String>,
        /** Null when the add-on doesn't say: then IMDb ids are assumed, as in Stremio itself. */
        val idPrefixes: List<String>?
    )

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.strings(): List<String> =
        (this as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.isNotEmpty() }

    private fun String?.nonBlankLines(): List<String> =
        this?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private companion object {
        const val TAG = "StremioAddon"
        const val MAX_STREAMS = 30
        const val MAX_LABEL_LENGTH = 90
    }
}
