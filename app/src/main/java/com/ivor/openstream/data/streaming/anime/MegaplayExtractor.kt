package com.ivor.openstream.data.streaming.anime

import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.StreamSubtitle
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** GET returning the body, or throwing on anything but 200. */
internal fun OkHttpClient.fetch(url: String, headers: Map<String, String> = emptyMap()): String =
    newCall(
        Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
    ).execute().use { response ->
        if (response.code != 200) throw IOException("${response.request.url.host} returned HTTP ${response.code}")
        response.body?.string().orEmpty()
    }

/** Height of the best rendition a master playlist lists, as a quality. */
internal fun bestPlaylistQuality(playlist: String): StreamQuality =
    Regex("""RESOLUTION=\d+x(\d+)""").findAll(playlist)
        .mapNotNull { it.groupValues[1].toIntOrNull() }
        .maxOrNull()
        ?.let { StreamQuality.parse("${it}p") }
        ?: StreamQuality.UNKNOWN

/**
 * Resolves megaplay.buzz embeds (used by Anikoto and Re:Anime) to their HLS stream.
 *
 * Undocumented third-party behaviour this relies on: the embed page carries `data-id`,
 * `/stream/getSourcesNew` answers `{enc, tracks, intro, outro}`, and `enc` is base64url
 * AES-256-CBC JSON whose key and IV come from megaplay's player script (`trustAesKey`/`trustAesIv`,
 * the key zero-padded to 32 bytes). The CDN answers 403 without megaplay's Referer. The `tcdn`
 * server hides MPEG-TS segments behind a PNG header; the player strips it
 * (`ImagePrefixStrippingDataSource`).
 */
@Singleton
class MegaplayExtractor @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient,
    private val json: Json
) {
    fun extract(
        embed: String,
        referer: String,
        providerId: String,
        providerName: String,
        label: String,
        audio: StreamAudio
    ): List<VideoServer> {
        val page = client.fetch(embed, mapOf("Referer" to referer))
        val id = Regex("""data-id="(\d+)"""").find(page)?.groupValues?.get(1) ?: return emptyList()
        val embedUrl = embed.toHttpUrl()
        val origin = "${embedUrl.scheme}://${embedUrl.host}"
        val server = embedUrl.queryParameter("s")
        val response = json.parseToJsonElement(
            client.fetch(
                "$origin/stream/getSourcesNew?id=$id${server?.let { "&s=$it" }.orEmpty()}",
                mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to embed)
            )
        ).jsonObject
        val enc = response["enc"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val file = (json.parseToJsonElement(decrypt(enc)) as? JsonObject)
            ?.get("file")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("http") }
            ?: return emptyList()

        val headers = mapOf("Referer" to "$origin/", "User-Agent" to BROWSER_USER_AGENT)
        // Some of megaplay's CDN hosts refuse anything but its own player, and which host a
        // server gets rotates; drop streams that don't load instead of offering dead ones.
        val playlist = runCatching { client.fetch(file, headers) }.getOrNull() ?: return emptyList()

        val subtitles = response["tracks"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it["kind"]?.jsonPrimitive?.contentOrNull == "captions" }
            .mapNotNull { track ->
                val url = track["file"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }
                    ?: return@mapNotNull null
                val name = track["label"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                StreamSubtitle(url = url, label = name, language = name, headers = headers)
            }

        return listOf(
            VideoServer(
                id = "$providerId-${file.hashCode()}",
                providerId = providerId,
                providerName = providerName,
                name = "$label · ${audio.label}",
                url = file,
                quality = bestPlaylistQuality(playlist),
                audio = audio,
                headers = headers,
                subtitles = subtitles,
                audioLanguage = if (audio == StreamAudio.DUB) "English" else "Japanese"
            )
        )
    }

    companion object {
        private const val KEY = "i?LMTAx0Q6,:}50U"
        private const val IV = "W0;27ToaUpl_P%'c"

        /** Decrypts getSourcesNew's `enc` field. */
        fun decrypt(enc: String): String {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(KEY.toByteArray().copyOf(32), "AES"),
                IvParameterSpec(IV.toByteArray().copyOf(16))
            )
            val bytes = Base64.getUrlDecoder()
                .decode(enc.trim().replace('+', '-').replace('/', '_').trimEnd('='))
            return String(cipher.doFinal(bytes), Charsets.UTF_8)
        }
    }
}
