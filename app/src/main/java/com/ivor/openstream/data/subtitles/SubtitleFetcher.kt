package com.ivor.openstream.data.subtitles

import com.ivor.openstream.data.repository.OpenSubtitlesRepository
import com.ivor.openstream.data.repository.SubSourceRepository
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Downloads a subtitle file as text, whatever it came wrapped in: OpenSubtitles' gzip files,
 * SubSource's zip archives (behind a download token), or plain SRT/VTT/ASS from a stream's own
 * host. Shared by the player and the cast proxy.
 */
@Singleton
class SubtitleFetcher @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient
) {
    /** The subtitle's text. [headers] are the stream's (Referer...), sent only to the stream's hosts. */
    suspend fun fetchText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val bytes = when {
            SubSourceRepository.isSubSourceUrl(url) -> download(SubSourceRepository.resolveDownloadUrl(client, url), emptyMap())
            url.toHttpUrlOrNull()?.host?.endsWith("opensubtitles.org") == true ->
                // OpenSubtitles only asks for a User-Agent; the stream's Referer would be wrong there.
                download(url, mapOf("User-Agent" to OpenSubtitlesRepository.USER_AGENT))
            else -> download(url, mapOf("User-Agent" to BROWSER_USER_AGENT) + headers)
        }
        val text = decode(unwrap(bytes))
        if (text.isBlank()) throw IOException("Subtitle file is empty")
        text
    }

    private fun download(url: String, headers: Map<String, String>): ByteArray {
        val request = Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Subtitle server returned HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Empty subtitle response")
        }
    }

    /** Detects zip and gzip by their magic bytes rather than trusting URLs or headers. */
    private fun unwrap(bytes: ByteArray): ByteArray = when {
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte() ->
            largestSubtitleInZip(bytes)
        bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte() ->
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        else -> bytes
    }

    /** The biggest subtitle file in an archive (the full track rather than a forced-parts one). */
    private fun largestSubtitleInZip(bytes: ByteArray): ByteArray {
        var best: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.lowercase()
                if (!entry.isDirectory && SUBTITLE_EXTENSIONS.any { name.endsWith(it) }) {
                    val content = zip.readBytes()
                    if (content.size > (best?.size ?: -1)) best = content
                }
            }
        }
        return best ?: throw IOException("No subtitle file in the archive")
    }

    /** UTF-8 when the file is valid UTF-8, otherwise Windows-1252 (common for older SRTs). */
    private fun decode(bytes: ByteArray): String {
        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            strict.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
        return text.removePrefix("﻿")
    }

    private companion object {
        val SUBTITLE_EXTENSIONS = listOf(".srt", ".vtt", ".ass", ".ssa")
    }
}
