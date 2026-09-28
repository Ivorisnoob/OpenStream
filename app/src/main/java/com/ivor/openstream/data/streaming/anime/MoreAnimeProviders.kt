package com.ivor.openstream.data.streaming.anime

import com.ivor.openstream.domain.model.HLS_MIME_TYPE
import com.ivor.openstream.domain.model.SkipSegment
import com.ivor.openstream.domain.model.SkipType
import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.StreamSubtitle
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * FourAnimo (4animo.xyz): its player host (`endpoint`, cdn.4animo.xyz) embeds by AniList id and
 * episode, for sub and dub. The embed page carries a one-time `getSources` token; the JSON it
 * returns has the HLS playlist, caption tracks and intro/outro times.
 *
 * Undocumented behaviour relied on: tokens are bound to the client's IP (a phone keeps one; a
 * connection from another IP gets "invalid token"), and playlists/segments want the player host
 * as Referer. Streams are always HLS behind `/p?t=` URLs, so the MIME type is set explicitly.
 */
class FourAnimoProvider(
    spec: AnimeSiteSpec,
    mapper: AnimeEpisodeMapper,
    private val client: OkHttpClient,
    private val json: Json
) : AnimeSiteProvider(spec, mapper) {

    override suspend fun streams(ref: AnimeEpisodeRef): List<VideoServer> = coroutineScope {
        listOf(StreamAudio.SUB to "sub", StreamAudio.DUB to "dub").map { (audio, type) ->
            async { runCatching { stream(ref, audio, type) }.getOrNull() }
        }.awaitAll().filterNotNull()
    }

    private fun stream(ref: AnimeEpisodeRef, audio: StreamAudio, type: String): VideoServer? {
        val embed = "$base/embed/ani/${ref.anilistId}/${ref.episode}/$type?k=1"
        val page = client.fetch(embed, mapOf("Referer" to SITE))
        val sourcesPath = Regex("""sourcesUrl\s*=\s*'([^']+)'""").find(page)?.groupValues?.get(1) ?: return null
        val data = json.parseToJsonElement(client.fetch(absolute(sourcesPath), mapOf("Referer" to embed))).jsonObject
        val file = (data["sources"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("file")
            ?.jsonPrimitive?.contentOrNull ?: return null
        val headers = mapOf("Referer" to "$base/", "Origin" to base)
        val url = absolute(file)
        val playlist = runCatching { client.fetch(url, headers) }.getOrNull() ?: return null
        if (!playlist.trimStart().startsWith("#EXTM3U")) return null

        val subtitles = (data["tracks"] as? JsonArray).orEmpty().mapNotNull { element ->
            val track = element as? JsonObject ?: return@mapNotNull null
            if (track["kind"]?.jsonPrimitive?.contentOrNull !in setOf(null, "captions", "subtitles")) return@mapNotNull null
            val label = track["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            StreamSubtitle(
                url = absolute(track["file"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null),
                label = label,
                language = languageCode(label),
                headers = headers
            )
        }
        val server = data["server"]?.jsonPrimitive?.contentOrNull ?: "HD-1"
        return VideoServer(
            id = "$id-$type-${ref.anilistId}-${ref.episode}",
            providerId = id,
            providerName = spec.name,
            name = "$server · ${audio.label}",
            url = url,
            quality = bestPlaylistQuality(playlist),
            audio = audio,
            headers = headers,
            subtitles = subtitles,
            audioLanguage = if (audio == StreamAudio.DUB) "English" else "Japanese",
            mimeType = HLS_MIME_TYPE,
            skipSegments = listOfNotNull(
                segment(data["intro"], SkipType.INTRO),
                segment(data["outro"], SkipType.CREDITS)
            )
        )
    }

    private fun segment(value: Any?, type: SkipType): SkipSegment? {
        val range = value as? JsonObject ?: return null
        val start = range["start"]?.jsonPrimitive?.doubleOrNull ?: return null
        val end = range["end"]?.jsonPrimitive?.doubleOrNull ?: return null
        return SkipSegment(type, (start * 1000).toLong(), (end * 1000).toLong()).takeIf { it.endMs > it.startMs }
    }

    private fun absolute(path: String) = if (path.startsWith("http")) path else "$base/${path.trimStart('/')}"

    private companion object {
        const val SITE = "https://4animo.xyz/"
    }
}

/**
 * AnimeGG (animegg.org): sub and dub as progressive MP4s. Search by title, confirm the series page,
 * then the episode page (`/<slug>-episode-N`) embeds one player per version whose page lists a
 * `/play/<id>/video.mp4` link per quality; those redirect to the vidcache CDN.
 *
 * Undocumented behaviour relied on: the play links and CDN want Referer and Origin set to the
 * site plus desktop Chrome client hints, and the redirect target is on a non-standard port.
 */
class AnimeGGProvider(
    spec: AnimeSiteSpec,
    mapper: AnimeEpisodeMapper,
    private val client: OkHttpClient
) : AnimeSiteProvider(spec, mapper) {
    private val slugs = ConcurrentHashMap<Int, String>()

    private val playHeaders: Map<String, String>
        get() = mapOf(
            "Referer" to "$base/",
            "Origin" to base,
            "sec-ch-ua" to "\"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\", \"Not-A.Brand\";v=\"99\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\""
        )

    override suspend fun streams(ref: AnimeEpisodeRef): List<VideoServer> {
        val slug = seriesSlug(ref) ?: return emptyList()
        val episodeUrl = "$base/$slug-episode-${ref.episode}"
        val page = client.fetch(episodeUrl, mapOf("Referer" to "$base/series/$slug"))
        // Each tab: data-id (the embed) and data-version (subbed, dubbed, raw).
        val tabs = Regex("""<a [^>]*data-id='(\d+)'[^>]*data-version="(\w+)"""").findAll(page)
            .map { it.groupValues[1] to it.groupValues[2] }
            .distinct()
            .toList()
        return coroutineScope {
            tabs.map { (embedId, version) ->
                async { runCatching { embedStreams(embedId, version, episodeUrl) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten()
        }
    }

    private fun embedStreams(embedId: String, version: String, episodeUrl: String): List<VideoServer> {
        val audio = when (version.lowercase(Locale.ROOT)) {
            "dubbed" -> StreamAudio.DUB
            "subbed" -> StreamAudio.SUB
            else -> return emptyList() // Raw: no subtitles at all.
        }
        val embed = "$base/embed/$embedId"
        val html = client.fetch(embed, mapOf("Referer" to episodeUrl))
        val sources = Regex("""file:\s*"(/play/[^"]+)",\s*label:\s*"(\d+)p"""").findAll(html)
            .map { it.groupValues[1] to it.groupValues[2].toInt() }
            .sortedByDescending { it.second }
            .toList()
        return sources.map { (path, height) ->
            VideoServer(
                id = "$id-$embedId-$height",
                providerId = id,
                providerName = spec.name,
                name = "Animegg ${height}p · ${audio.label}",
                url = "$base$path",
                quality = StreamQuality.parse("${height}p"),
                audio = audio,
                headers = playHeaders + ("Referer" to embed),
                audioLanguage = if (audio == StreamAudio.DUB) "English" else "Japanese",
                mimeType = "video/mp4"
            )
        }
    }

    /** The series slug, taken from search results and matched against the AniList titles. */
    private fun seriesSlug(ref: AnimeEpisodeRef): String? {
        slugs[ref.anilistId]?.let { return it }
        for (title in ref.titles) {
            val html = client.fetch("$base/search/?q=${title.urlEncoded()}", mapOf("Referer" to "$base/"))
            val found = Regex("""href="/series/([a-z0-9-]+)"""").findAll(html).map { it.groupValues[1] }.distinct().toList()
            val wanted = slugify(title)
            val slug = found.firstOrNull { it == wanted } ?: found.firstOrNull() ?: continue
            slugs[ref.anilistId] = slug
            return slug
        }
        return null
    }

    private fun slugify(title: String) = title.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}

/** ISO 639-1 code for an English language name such as "Portuguese (Brazil)"; null when unknown. */
internal fun languageCode(label: String): String? {
    val name = label.substringBefore('(').trim().lowercase(Locale.ROOT)
    return Locale.getAvailableLocales()
        .firstOrNull { it.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT) == name }
        ?.language
}
