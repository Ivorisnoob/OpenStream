package com.ivor.openstream.data.streaming.hosters

import android.util.Base64
import android.util.Log
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.anime.PackerUnpacker
import com.ivor.openstream.domain.model.StreamQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** A direct stream pulled out of a hoster's embed page. */
data class HosterStream(
    val url: String,
    /** Headers the stream's CDN wants (usually the hoster as Referer). */
    val headers: Map<String, String>,
    val isHls: Boolean,
    val quality: StreamQuality,
    /** Short hoster name for server labels, e.g. "Filemoon". */
    val host: String
)

/**
 * Turns common embed hosts into direct streams, so their embeds play without the hidden WebView:
 * Filemoon, StreamWish/VidHide (same player family), Voe, Mp4Upload, Vidmoly and ok.ru.
 *
 * Written from how each page delivers its player config (p.a.c.k.e.r scripts, inline player
 * setup, redirects, JSON options). All of it is undocumented and changes without notice; an
 * extractor that stops matching just returns nothing and the caller falls back.
 */
@Singleton
class HosterExtractors @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient,
    private val json: Json
) {
    private enum class Host(val label: String, val domains: List<String>) {
        FILEMOON("Filemoon", listOf("filemoon", "moonplayer", "kerapoxy", "bf0skv", "fmoonembed", "moflix", "cinegrab")),
        STREAMWISH(
            "StreamWish",
            listOf("streamwish", "wishembed", "swdyu", "wishfast", "sfastwish", "strwish", "playerwish", "hlswish", "awish", "dwish", "embedwish", "mwish", "flaswish", "obeywish", "cdnwish", "asnwish", "jodwish", "streamhg", "dhcplay", "hglink")
        ),
        VIDHIDE("VidHide", listOf("vidhide", "filelions", "alions", "dlions", "mlions", "vidhidepro", "vidhidevip", "vidhidefast", "ryderjet", "smoothpre", "movearnpre", "dinisglows", "mivalyo", "dintezuvio")),
        VOE("Voe", listOf("voe.sx", "voe-", "voeun", "voeunblock", "tubelessceliolymph", "maxfinishseveral", "crystaltreatmenteast", "graceaddresscommunity", "christopheruntilpoint", "launchreliantcleaverriver", "jennifercertaindevelopment")),
        MP4UPLOAD("Mp4Upload", listOf("mp4upload")),
        VIDMOLY("Vidmoly", listOf("vidmoly")),
        OKRU("ok.ru", listOf("ok.ru", "odnoklassniki"));

        fun matches(host: String): Boolean = domains.any { host.contains(it) }
    }

    /** True when [url] is on a host this class can extract. */
    fun supports(url: String): Boolean = hostOf(url) != null

    /** Direct streams from a hoster embed; empty when the page didn't match (or isn't a hoster). */
    suspend fun extract(url: String, referer: String? = null): List<HosterStream> = withContext(Dispatchers.IO) {
        val host = hostOf(url) ?: return@withContext emptyList()
        runCatching {
            when (host) {
                Host.FILEMOON -> filemoon(url, referer)
                Host.STREAMWISH, Host.VIDHIDE -> packedPlayer(url, referer, host)
                Host.VOE -> voe(url, referer)
                Host.MP4UPLOAD -> mp4upload(url, referer)
                Host.VIDMOLY -> vidmoly(url, referer)
                Host.OKRU -> okru(url)
            }
        }.onFailure { Log.w(TAG, "${host.label} extraction failed: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private fun hostOf(url: String): Host? {
        val host = url.toHttpUrlOrNull()?.host?.lowercase(Locale.ROOT) ?: return null
        return Host.entries.firstOrNull { it.matches(host) }
    }

    // region Hosts

    /** Filemoon: the page wraps the player in an iframe; the player's packed script holds `file:`. */
    private fun filemoon(url: String, referer: String?): List<HosterStream> {
        var pageUrl = url
        var html = fetch(url, referer)
        Regex("""<iframe[^>]+src="(https?://[^"]+)"""").find(html)?.groupValues?.get(1)?.let { inner ->
            html = fetch(inner, url)
            pageUrl = inner
        }
        val script = PackerUnpacker.unpack(packedScript(html) ?: html)
        val file = Regex("""file\s*:\s*"([^"]+)"""").find(script)?.groupValues?.get(1) ?: return emptyList()
        return listOf(stream(absolute(file, pageUrl), pageUrl, Host.FILEMOON))
    }

    /**
     * StreamWish and VidHide share a player: a packed jwplayer setup whose `links` object lists
     * `hls4`/`hls2`/`hls3` URLs (hls4 and relative ones are served by the page's own host).
     */
    private fun packedPlayer(url: String, referer: String?, host: Host): List<HosterStream> {
        val embed = url.replace("/f/", "/e/").replace("/d/", "/e/")
        val html = fetch(embed, referer)
        val script = PackerUnpacker.unpack(packedScript(html) ?: html)
        val links = Regex(""""(hls\d)"\s*:\s*"([^"]+)"""").findAll(script)
            .associate { it.groupValues[1] to it.groupValues[2] }
        val file = listOf("hls4", "hls2", "hls3").firstNotNullOfOrNull { links[it] }
            ?: Regex("""file\s*:\s*"([^"]+\.m3u8[^"]*)"""").find(script)?.groupValues?.get(1)
            ?: return emptyList()
        return listOf(stream(absolute(file, embed), embed, host))
    }

    /**
     * Voe: mirror domains redirect first. Older pages carry `'hls': '<url or base64>'`; newer ones
     * hide a JSON config in `<script type="application/json">` behind rot13, junk markers, base64,
     * a character shift and a reversal.
     */
    private fun voe(url: String, referer: String?): List<HosterStream> {
        var pageUrl = url
        var html = fetch(url, referer)
        Regex("""window\.location\.href\s*=\s*'(https?://[^']+)'""").find(html)?.groupValues?.get(1)?.let { next ->
            html = fetch(next, url)
            pageUrl = next
        }
        val direct = Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)?.let { value ->
            if (value.startsWith("http")) value else runCatching { String(Base64.decode(value, Base64.DEFAULT)) }.getOrNull()
        }
        val source = direct?.takeIf { it.startsWith("http") }
            ?: Regex("""<script type="application/json">\s*\["?(.*?)"?]\s*</script>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1)?.let(::voeDecode)
            ?: return emptyList()
        return listOf(stream(source, pageUrl, Host.VOE))
    }

    private fun voeDecode(encoded: String): String? = runCatching {
        val rot13 = encoded.map { char ->
            when (char) {
                in 'a'..'z' -> 'a' + (char - 'a' + 13) % 26
                in 'A'..'Z' -> 'A' + (char - 'A' + 13) % 26
                else -> char
            }
        }.joinToString("")
        val cleaned = VOE_JUNK.fold(rot13) { text, junk -> text.replace(junk, "") }
        val step1 = String(Base64.decode(cleaned, Base64.DEFAULT))
        val shifted = step1.map { (it.code - 3).toChar() }.joinToString("").reversed()
        val config = json.parseToJsonElement(String(Base64.decode(shifted, Base64.DEFAULT))).jsonObject
        (config["source"] ?: config["direct_access_url"])?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Mp4Upload: `player.src({ type: "video/mp4", src: "..." })`, sometimes packed. */
    private fun mp4upload(url: String, referer: String?): List<HosterStream> {
        val id = Regex("""(?:embed-)?([a-z0-9]{12})""").findAll(url.substringAfterLast('/')).lastOrNull()?.groupValues?.get(1)
        val embed = if (id != null) "https://www.mp4upload.com/embed-$id.html" else url
        val html = fetch(embed, referer ?: "https://www.mp4upload.com/")
        val script = PackerUnpacker.unpack(packedScript(html) ?: html)
        val src = Regex("""src\s*:\s*"(https?://[^"]+\.mp4[^"]*)"""").find(script)?.groupValues?.get(1)
            ?: return emptyList()
        return listOf(stream(src, "https://www.mp4upload.com/", Host.MP4UPLOAD))
    }

    /** Vidmoly: a jwplayer `sources: [{file:"...m3u8"}]` right in the page. */
    private fun vidmoly(url: String, referer: String?): List<HosterStream> {
        val embed = url.replace("/w/", "/embed-").let { if ("/embed-" in it && !it.endsWith(".html")) "$it.html" else it }
        val html = fetch(embed, referer ?: "https://vidmoly.to/")
        val script = PackerUnpacker.unpack(packedScript(html) ?: html)
        val file = Regex("""file\s*:\s*"([^"]+\.m3u8[^"]*)"""").find(script)?.groupValues?.get(1) ?: return emptyList()
        return listOf(stream(file, embed, Host.VIDMOLY))
    }

    /** ok.ru: the embed's `data-options` JSON has metadata with an HLS manifest and MP4 renditions. */
    private fun okru(url: String): List<HosterStream> {
        val id = Regex("""/video(?:embed)?/(\d+)""").find(url)?.groupValues?.get(1) ?: return emptyList()
        val html = fetch("https://ok.ru/videoembed/$id", "https://ok.ru/")
        val options = Regex("""data-options="([^"]+)"""").find(html)?.groupValues?.get(1)
            ?.let { json.parseToJsonElement(unescapeHtml(it)).jsonObject } ?: return emptyList()
        val metadata: JsonObject = when (val raw = options["flashvars"]?.jsonObject?.get("metadata")) {
            is JsonObject -> raw
            is JsonPrimitive -> raw.contentOrNull?.let { json.parseToJsonElement(it).jsonObject }
            else -> null
        } ?: return emptyList()
        val headers = mapOf("Referer" to "https://ok.ru/", "User-Agent" to BROWSER_USER_AGENT)
        metadata["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }?.let { hls ->
            return listOf(HosterStream(hls, headers, isHls = true, quality = StreamQuality.parse("1080p"), host = Host.OKRU.label))
        }
        val best = (metadata["videos"] as? JsonArray).orEmpty()
            .mapNotNull { element -> (element as? JsonObject)?.let { it["name"].text() to it["url"].text() } }
            .filter { (_, videoUrl) -> videoUrl?.startsWith("http") == true }
            .maxByOrNull { (name, _) -> OKRU_QUALITIES.indexOf(name) }
            ?: return emptyList()
        return listOf(
            HosterStream(best.second!!, headers, isHls = false, quality = StreamQuality.parse(okruQuality(best.first)), host = Host.OKRU.label)
        )
    }

    // endregion

    private fun stream(url: String, pageUrl: String, host: Host): HosterStream {
        val origin = pageUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
        return HosterStream(
            url = url,
            headers = buildMap {
                put("Referer", "${origin ?: pageUrl}/")
                origin?.let { put("Origin", it) }
                put("User-Agent", BROWSER_USER_AGENT)
            },
            isHls = ".m3u8" in url || "/hls" in url,
            quality = StreamQuality.parse(Regex("""(\d{3,4})p""").find(url)?.value ?: "720p"),
            host = host.label
        )
    }

    private fun fetch(url: String, referer: String?): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .apply { referer?.let { header("Referer", it) } }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("${response.request.url.host} returned HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    private fun packedScript(html: String): String? =
        Regex("""eval\(function\(p,a,c,k,e,[dr]\).*?\.split\('\|'\)[^<]*""", RegexOption.DOT_MATCHES_ALL).find(html)?.value

    private fun absolute(file: String, pageUrl: String): String =
        if (file.startsWith("http")) file else pageUrl.toHttpUrlOrNull()?.resolve(file)?.toString() ?: file

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun unescapeHtml(value: String) = value
        .replace("&quot;", "\"").replace("&#34;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun okruQuality(name: String?) = when (name) {
        "full" -> "1080p"
        "hd" -> "720p"
        "sd" -> "480p"
        "low" -> "360p"
        else -> "240p"
    }

    private companion object {
        const val TAG = "HosterExtractors"
        val OKRU_QUALITIES = listOf("mobile", "lowest", "low", "sd", "hd", "full")
        val VOE_JUNK = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
    }
}
