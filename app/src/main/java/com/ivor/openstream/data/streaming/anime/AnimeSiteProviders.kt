package com.ivor.openstream.data.streaming.anime

import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.data.streaming.hosters.HosterExtractors
import com.ivor.openstream.domain.model.HLS_MIME_TYPE
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** A catalog entry for an anime site: [baseUrl] is the site's origin, so a domain move is a data change. */
data class AnimeSiteSpec(
    val id: String,
    val name: String,
    val baseUrl: String,
    val priority: Int
)

/**
 * Shared shape of the anime site providers: map the TMDB title to an AniList episode first (none
 * for non-anime, which then resolves to an empty list), then ask the site for sub and dub.
 */
abstract class AnimeSiteProvider(
    protected val spec: AnimeSiteSpec,
    private val mapper: AnimeEpisodeMapper
) : StreamProvider {
    override val id: String = "anime-${spec.id}"
    override val displayName: String = spec.name
    override val priority: Int = spec.priority
    override val isEnabled: Boolean = true

    protected val base: String = spec.baseUrl.trimEnd('/')

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> = runCatching {
        val ref = mapper.map(identity) ?: return@runCatching emptyList()
        withContext(Dispatchers.IO) { streams(ref) }
    }

    protected abstract suspend fun streams(ref: AnimeEpisodeRef): List<VideoServer>
}

internal fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

internal fun decodeHtml(value: String): String = value
    .replace("&#039;", "'")
    .replace("&quot;", "\"")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")

internal fun dataAttributes(tag: String): Map<String, String> =
    Regex("""data-([\w-]+)="([^"]*)"""").findAll(tag).associate { it.groupValues[1] to it.groupValues[2] }

/**
 * Anikoto (anikototv.to): search the site, confirm the show by the MAL id each episode carries,
 * then resolve the episode's sub and dub servers. Every server is a megaplay embed.
 */
class AnikotoProvider(
    spec: AnimeSiteSpec,
    mapper: AnimeEpisodeMapper,
    private val client: OkHttpClient,
    private val json: Json,
    private val megaplay: MegaplayExtractor,
    private val hosters: HosterExtractors
) : AnimeSiteProvider(spec, mapper) {
    private val showPages = ConcurrentHashMap<Int, String>()
    private val episodeLists = ConcurrentHashMap<String, List<Map<String, String>>>()

    private val ajax get() = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to "$base/")

    override suspend fun streams(ref: AnimeEpisodeRef): List<VideoServer> {
        val page = showPage(ref) ?: return emptyList()
        val episode = episodes(page).firstOrNull { it["num"]?.toDoubleOrNull() == ref.episode.toDouble() }
            ?: return emptyList()
        val ids = episode["ids"] ?: return emptyList()
        val html = ajaxResult(client.fetch("$base/ajax/server/list?servers=$ids", ajax))

        val servers = listOf(StreamAudio.SUB to "sub", StreamAudio.DUB to "dub").flatMap { (audio, type) ->
            val block = Regex("""data-type="$type">(.*?)</ul>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1).orEmpty()
            Regex("""data-link-id="([^"]+)"[^>]*>([^<]+)<""").findAll(block)
                .map { Triple(audio, it.groupValues[1], it.groupValues[2].trim()) }
                .toList()
        }
        return coroutineScope {
            servers.map { (audio, linkId, label) ->
                async {
                    runCatching {
                        val result = json.parseToJsonElement(
                            client.fetch("$base/ajax/server?get=$linkId", ajax)
                        ).jsonObject["result"]?.jsonObject
                        val url = result?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                        when {
                            "megaplay" in url -> megaplay.extract(url, "$base/", id, spec.name, label, audio)
                            // Common embed hosts (Filemoon, StreamWish, Voe...) have native extractors.
                            hosters.supports(url) -> hosters.extract(url, "$base/").map { stream ->
                                VideoServer(
                                    id = "$id-${stream.url.hashCode()}",
                                    providerId = id,
                                    providerName = spec.name,
                                    name = "$label · ${stream.host} · ${audio.label}",
                                    url = stream.url,
                                    quality = stream.quality,
                                    audio = audio,
                                    headers = stream.headers,
                                    audioLanguage = if (audio == StreamAudio.DUB) "English" else "Japanese",
                                    mimeType = if (stream.isHls) HLS_MIME_TYPE else null
                                )
                            }
                            else -> emptyList()
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
    }

    /** The show's watch page URL, matched by MAL id (or the first result when AniList has none). */
    private fun showPage(ref: AnimeEpisodeRef): String? {
        showPages[ref.anilistId]?.let { return it }
        for (title in ref.titles) {
            val html = client.fetch("$base/filter?keyword=${title.urlEncoded()}")
            val results = html.split("<div class=\"item ").drop(1).mapNotNull { item ->
                Regex("""<a class="name d-title" href="([^"]+)"""").find(item)?.groupValues?.get(1)
            }
            for (href in results.take(5)) {
                val first = episodes(href).firstOrNull() ?: continue
                if (ref.malId == null || first["mal"] == ref.malId.toString()) {
                    showPages[ref.anilistId] = href
                    return href
                }
            }
        }
        return null
    }

    private fun episodes(href: String): List<Map<String, String>> {
        episodeLists[href]?.let { return it }
        val url = if (href.startsWith("http")) href else "$base/${href.trimStart('/')}"
        val page = client.fetch(url)
        val showId = Regex("""id="watch-main"[^>]*?data-id="(\d+)"""").find(page)?.groupValues?.get(1)
            ?: return emptyList()
        val html = ajaxResult(client.fetch("$base/ajax/episode/list/$showId", ajax))
        return Regex("""<a [^>]*data-num="[^>]*>""").findAll(html)
            .map { dataAttributes(it.value) }
            .toList()
            .also { episodeLists[href] = it }
    }

    private fun ajaxResult(body: String): String =
        json.parseToJsonElement(body).jsonObject["result"]?.jsonPrimitive?.contentOrNull.orEmpty()
}

/**
 * Re:Anime (reanime.to): its own servers ship encrypted payloads, but megaplay serves the same
 * episodes by AniList id, which is what the site's players use too.
 */
class ReAnimeProvider(
    spec: AnimeSiteSpec,
    mapper: AnimeEpisodeMapper,
    private val megaplay: MegaplayExtractor
) : AnimeSiteProvider(spec, mapper) {
    override suspend fun streams(ref: AnimeEpisodeRef): List<VideoServer> = coroutineScope {
        listOf(StreamAudio.SUB to "sub", StreamAudio.DUB to "dub").flatMap { (audio, type) ->
            listOf("HD-1" to "tcdn", "HD-2" to "bcdn").map { (label, server) ->
                async {
                    runCatching {
                        megaplay.extract(
                            embed = "$MEGAPLAY/stream/ani/${ref.anilistId}/${ref.episode}/$type?s=$server",
                            referer = "$base/",
                            providerId = id,
                            providerName = spec.name,
                            label = label,
                            audio = audio
                        )
                    }.getOrDefault(emptyList())
                }
            }
        }.awaitAll().flatten()
    }

    private companion object {
        const val MEGAPLAY = "https://megaplay.buzz"
    }
}

/**
 * animepahe: sits behind a Cloudflare JavaScript challenge, cleared once in a hidden WebView
 * ([CloudflareClearance]); its kwik player pages hide the HLS URL in a p.a.c.k.e.r script.
 * Challenges that need the user (Turnstile) make this source fail; the others still play.
 */
class AnimePaheProvider(
    spec: AnimeSiteSpec,
    mapper: AnimeEpisodeMapper,
    private val client: OkHttpClient,
    private val json: Json,
    private val clearance: CloudflareClearance
) : AnimeSiteProvider(spec, mapper) {
    private val sessions = ConcurrentHashMap<Int, String>()

    override suspend fun streams(ref: AnimeEpisodeRef): List<VideoServer> {
        val session = animeSession(ref) ?: return emptyList()
        val episodeSession = episodeSession(session, ref.episode) ?: return emptyList()
        val play = clearance.fetch("$base/play/$session/$episodeSession", referer = "$base/")
        val buttons = Regex("""<button[^>]*data-src="[^"]*"[^>]*>""").findAll(play)
            .map { dataAttributes(it.value) }
            .filter { !it["src"].isNullOrBlank() }
            .sortedByDescending { it["resolution"]?.toIntOrNull() ?: 0 }
            .toList()
        return coroutineScope {
            buttons.map { button ->
                async {
                    runCatching {
                        val kwik = button.getValue("src")
                        val kwikUrl = kwik.toHttpUrl()
                        val html = client.fetch(kwik, mapOf("Referer" to "$base/"))
                        val m3u8 = Regex("""https?://[^'"\\\s]+\.m3u8[^'"\\\s]*""")
                            .find(PackerUnpacker.unpack(html))?.value
                            ?: return@runCatching null
                        val audio = if (button["audio"] == "eng") StreamAudio.DUB else StreamAudio.SUB
                        val resolution = button["resolution"].orEmpty()
                        VideoServer(
                            id = "$id-${m3u8.hashCode()}",
                            providerId = id,
                            providerName = spec.name,
                            name = "${button["fansub"]?.let(::decodeHtml) ?: "Kwik"} · ${audio.label}",
                            url = m3u8,
                            quality = StreamQuality.parse("${resolution}p"),
                            audio = audio,
                            headers = mapOf(
                                "Referer" to "${kwikUrl.scheme}://${kwikUrl.host}/",
                                "User-Agent" to clearance.userAgent()
                            ),
                            audioLanguage = if (audio == StreamAudio.DUB) "English" else "Japanese"
                        )
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun api(query: String) =
        json.parseToJsonElement(clearance.fetch("$base/api?$query")).jsonObject

    private suspend fun animeSession(ref: AnimeEpisodeRef): String? {
        sessions[ref.anilistId]?.let { return it }
        val links = Regex(
            "anilist\\.co/anime/${ref.anilistId}\\b|myanimelist\\.net/anime/${ref.malId ?: "none"}\\b"
        )
        for (title in ref.titles) {
            val results = api("m=search&q=${title.urlEncoded()}")["data"]
                ?.let { it as? JsonArray }
                .orEmpty()
                .mapNotNull { it.jsonObject["session"]?.jsonPrimitive?.contentOrNull }
            for (session in results.take(3)) {
                if (links.containsMatchIn(clearance.fetch("$base/anime/$session"))) {
                    sessions[ref.anilistId] = session
                    return session
                }
            }
        }
        return null
    }

    /**
     * animepahe keeps counting across seasons (a second season may start at 13), so episodes are
     * renumbered from the first one listed; only the page holding [episode] is fetched.
     */
    private suspend fun episodeSession(session: String, episode: Int): String? {
        val first = api("m=release&id=$session&sort=episode_asc&page=1")
        val firstData = first["data"] as? JsonArray ?: return null
        val firstNumber = firstData.firstOrNull()?.jsonObject?.get("episode")?.jsonPrimitive?.contentOrNull
            ?.toDoubleOrNull()?.toInt() ?: return null
        val offset = (firstNumber - 1).coerceAtLeast(0)
        val perPage = first["per_page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 }
            ?: firstData.size.coerceAtLeast(1)
        val target = episode + offset
        val page = (target - firstNumber) / perPage + 1
        val data = if (page <= 1) {
            firstData
        } else {
            api("m=release&id=$session&sort=episode_asc&page=$page")["data"]
                as? JsonArray ?: return null
        }
        return data.map { it.jsonObject }
            .firstOrNull { it["episode"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() == target.toDouble() }
            ?.get("session")?.jsonPrimitive?.contentOrNull
    }
}
