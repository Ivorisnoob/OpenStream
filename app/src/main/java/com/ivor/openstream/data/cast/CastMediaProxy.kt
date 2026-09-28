package com.ivor.openstream.data.cast

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import com.ivor.openstream.data.repository.OpenSubtitlesRepository
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.ImagePrefixStrippingDataSource
import com.ivor.openstream.data.subtitles.isSubtitleAd
import com.ivor.openstream.data.subtitles.parseSubtitles
import com.ivor.openstream.data.subtitles.toWebVtt
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A small HTTP server on the phone that a Cast receiver streams through.
 *
 * A Chromecast fetches media itself, so it can't send the Referer/Origin headers most sources need,
 * it needs CORS headers most stream CDNs don't send, it can't read the fake PNG prefix some anime
 * CDNs put before TS segments, and it only shows WebVTT captions. So while casting, every URL handed
 * to the receiver points here: requests are replayed upstream with the stream's headers (through the
 * download cache, so downloaded episodes cast too), HLS playlists are rewritten so their segments,
 * keys and renditions come back through the proxy, and subtitles are converted to WebVTT.
 *
 * URLs are stateless (`/<token>/m/<base64 of url + headers>/<name>`) so they keep working across a
 * restart of the proxy; the random token keeps the server from being an open relay on the LAN. The
 * phone has to stay on the same network as the receiver while casting.
 */
@OptIn(UnstableApi::class)
@Singleton
class CastMediaProxy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: Cache,
    private val preferences: SharedPreferences
) {
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "CastProxy").apply { isDaemon = true }
    }

    @Volatile
    private var server: ServerSocket? = null

    private val token: String by lazy {
        preferences.getString(KEY_TOKEN, null) ?: ByteArray(12)
            .also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
            .also { preferences.edit().putString(KEY_TOKEN, it).apply() }
    }

    private val dataSourceFactory: DataSource.Factory by lazy {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val cached = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, http))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ImagePrefixStrippingDataSource.Factory(cached)
    }

    private val subtitleCache: MutableMap<String, ByteArray> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 8
        }
    )

    /** Starts the server if needed; returns its port, or null when no socket could be opened. */
    @Synchronized
    fun start(): Int? {
        server?.takeUnless { it.isClosed }?.let { return it.localPort }
        val socket = runCatching { bind(preferences.getInt(KEY_PORT, DEFAULT_PORT)) }
            .recoverCatching { bind(0) }
            .onFailure { Log.e(TAG, "Could not open the cast proxy", it) }
            .getOrNull() ?: return null
        preferences.edit().putInt(KEY_PORT, socket.localPort).apply()
        server = socket
        executor.execute { acceptLoop(socket) }
        Log.i(TAG, "Cast proxy listening on ${socket.localPort}")
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        server?.let { runCatching { it.close() } }
        server = null
        subtitleCache.clear()
    }

    /**
     * The proxy URL a receiver at [receiver] should use for [target]. Null when the phone has no
     * address on a local network or the server can't start.
     */
    fun mediaUrl(target: String, headers: Map<String, String>, receiver: InetAddress?): String? {
        val base = baseUrl(receiver) ?: return null
        return "$base/m/${encode(target, headers)}/${fileName(target, "media")}"
    }

    /** Like [mediaUrl] for a subtitle file, served as WebVTT whatever its original format. */
    fun subtitleUrl(target: String, headers: Map<String, String>, receiver: InetAddress?): String? {
        val base = baseUrl(receiver) ?: return null
        return "$base/s/${encode(target, headers)}/subtitles.vtt"
    }

    /** What a receiver needs to be told up front about a stream: its type and HLS segment format. */
    data class StreamProbe(val mimeType: String, val hlsVideoSegmentFormat: String?)

    /**
     * Looks at the start of [target] (and, for an HLS master playlist, its first variant) to tell
     * HLS from progressive files and TS from fMP4 segments. Blocking; call off the main thread.
     */
    fun probe(target: String, headers: Map<String, String>): StreamProbe {
        val path = Uri.parse(target).path.orEmpty().lowercase()
        if (path.endsWith(".mpd")) return StreamProbe("application/dash+xml", null)
        val playlist = runCatching { readPlaylist(target, headers) }.getOrNull()
            ?: return StreamProbe(if (path.endsWith(".m3u8")) HLS_MIME else guessContentType(target).takeUnless { it == "video/mp2t" } ?: "video/mp4", null)
        val (text, base) = playlist
        val mediaPlaylist = if (text.contains("#EXT-X-STREAM-INF")) {
            val variant = text.lineSequence().map { it.trim() }
                .dropWhile { !it.startsWith("#EXT-X-STREAM-INF") }
                .drop(1)
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            variant?.let { resolve(base, it) }?.let { runCatching { readPlaylist(it, headers) }.getOrNull()?.first }
        } else {
            text
        }
        val format = when {
            mediaPlaylist == null -> null
            mediaPlaylist.contains("#EXT-X-MAP") -> HLS_FMP4
            else -> HLS_TS
        }
        return StreamProbe(HLS_MIME, format)
    }

    /** The playlist text and its address after redirects, or null when [target] isn't a playlist. */
    private fun readPlaylist(target: String, headers: Map<String, String>): Pair<String, String>? {
        val source = dataSourceFactory.createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(Uri.parse(target)).setHttpRequestHeaders(headers).build())
            val head = readUpTo(source, SNIFF_BYTES)
            if (!head.isPlaylist()) return null
            val body = head + readRest(source, MAX_PLAYLIST_BYTES)
            return body.toString(Charsets.UTF_8) to (source.uri?.toString() ?: target)
        } finally {
            runCatching { source.close() }
        }
    }

    private fun baseUrl(receiver: InetAddress?): String? {
        val port = start() ?: return null
        val host = lanAddressFor(receiver) ?: return null
        return "http://$host:$port/$token"
    }

    private fun bind(port: Int): ServerSocket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(port))
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (error: IOException) {
                Log.w(TAG, "Accept failed", error)
                continue
            }
            executor.execute { handle(client) }
        }
    }

    // region HTTP

    private fun handle(socket: Socket) {
        socket.use {
            runCatching {
                socket.soTimeout = 30_000
                val input = BufferedInputStream(socket.getInputStream())
                val output = socket.getOutputStream().buffered(64 * 1024)
                val requestLine = readLine(input) ?: return
                val requestHeaders = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) requestHeaders[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                if (method == "OPTIONS") {
                    writeHead(output, 204, "No Content", emptyMap(), 0L)
                    output.flush()
                    return
                }
                if (method != "GET" && method != "HEAD") {
                    writeError(output, 405, "Method Not Allowed")
                    return
                }
                val segments = parts[1].substringBefore('?').split('/').filter { it.isNotEmpty() }
                if (segments.size < 3 || segments[0] != token) {
                    writeError(output, 403, "Forbidden")
                    return
                }
                val decoded = decode(segments[2]) ?: run {
                    writeError(output, 400, "Bad Request")
                    return
                }
                val host = requestHeaders["host"] ?: return
                when (segments[1]) {
                    "m" -> serveMedia(decoded.first, decoded.second, requestHeaders["range"], method == "HEAD", host, output)
                    "s" -> serveSubtitle(decoded.first, decoded.second, method == "HEAD", output)
                    else -> writeError(output, 404, "Not Found")
                }
                output.flush()
            }.onFailure { error ->
                // Receivers drop connections all the time (seeks, quality switches); that is not news.
                if (error !is SocketException) Log.w(TAG, "Request failed: ${error.message}")
            }
        }
    }

    private fun serveMedia(
        target: String,
        headers: Map<String, String>,
        rangeHeader: String?,
        headOnly: Boolean,
        host: String,
        output: OutputStream
    ) {
        val range = parseRange(rangeHeader)
        val start = range?.first ?: 0L
        val length = range?.second?.let { end -> end - start + 1 } ?: C.LENGTH_UNSET.toLong()
        val source = dataSourceFactory.createDataSource()
        try {
            val spec = DataSpec.Builder()
                .setUri(Uri.parse(target))
                .setHttpRequestHeaders(headers)
                .setPosition(start)
                .setLength(length)
                .build()
            val opened = try {
                source.open(spec)
            } catch (error: HttpDataSource.InvalidResponseCodeException) {
                writeError(output, error.responseCode, error.responseMessage ?: "Upstream error")
                return
            } catch (_: FileNotFoundException) {
                writeError(output, 404, "Not Found")
                return
            } catch (error: IOException) {
                Log.w(TAG, "Upstream failed for ${Uri.parse(target).host}: ${error.message}")
                writeError(output, 502, "Bad Gateway")
                return
            }

            val upstreamHeaders = source.responseHeaders
            val head = if (start == 0L) readUpTo(source, SNIFF_BYTES) else ByteArray(0)
            if (head.isPlaylist()) {
                val body = head + readRest(source, MAX_PLAYLIST_BYTES)
                val base = source.uri?.toString() ?: target
                val rewritten = rewritePlaylist(body.toString(Charsets.UTF_8), base, headers, host)
                    .toByteArray(Charsets.UTF_8)
                writeHead(
                    output, 200, "OK",
                    mapOf("Content-Type" to "application/vnd.apple.mpegurl", "Cache-Control" to "no-cache"),
                    rewritten.size.toLong()
                )
                if (!headOnly) output.write(rewritten)
                return
            }

            val contentType = upstreamHeaders.first("Content-Type")
                ?.takeUnless { it.startsWith("image/", ignoreCase = true) || it.startsWith("text/plain", ignoreCase = true) }
                ?: guessContentType(target)
            val responseHeaders = mutableMapOf("Content-Type" to contentType, "Accept-Ranges" to "bytes")
            if (range != null) {
                val total = totalLength(upstreamHeaders, target, start, opened, range.second)
                val end = when {
                    opened != C.LENGTH_UNSET.toLong() -> start + opened - 1
                    range.second != null -> range.second!!
                    total != null -> total - 1
                    else -> null
                }
                responseHeaders["Content-Range"] = "bytes $start-${end ?: ""}/${total ?: "*"}"
                writeHead(output, 206, "Partial Content", responseHeaders, opened.takeIf { it != C.LENGTH_UNSET.toLong() })
            } else {
                writeHead(output, 200, "OK", responseHeaders, opened.takeIf { it != C.LENGTH_UNSET.toLong() })
            }
            if (headOnly) return
            output.write(head)
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, read)
            }
        } finally {
            runCatching { source.close() }
        }
    }

    private fun serveSubtitle(target: String, headers: Map<String, String>, headOnly: Boolean, output: OutputStream) {
        val body = subtitleCache[target] ?: runCatching { fetchSubtitleAsVtt(target, headers) }
            .onFailure { Log.w(TAG, "Subtitle conversion failed: ${it.message}") }
            .getOrNull()
            ?.also { subtitleCache[target] = it }
        if (body == null) {
            writeError(output, 502, "Bad Gateway")
            return
        }
        writeHead(output, 200, "OK", mapOf("Content-Type" to "text/vtt; charset=utf-8"), body.size.toLong())
        if (!headOnly) output.write(body)
    }

    private fun fetchSubtitleAsVtt(target: String, headers: Map<String, String>): ByteArray {
        val url = java.net.URL(target)
        val connection = url.openConnection() as java.net.HttpURLConnection
        try {
            if (url.host.endsWith("opensubtitles.org")) {
                // OpenSubtitles only asks for a User-Agent; the stream's Referer would be wrong there.
                connection.setRequestProperty("User-Agent", OpenSubtitlesRepository.USER_AGENT)
            } else {
                connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            }
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readBytes() }
            val isGzip = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
            val text = if (isGzip) {
                GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
            } else {
                bytes.toString(Charsets.UTF_8)
            }
            val cues = parseSubtitles(text).filterNot { it.text.isSubtitleAd() }
            if (cues.isEmpty()) throw IOException("No cues")
            return cues.toWebVtt().toByteArray(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun writeHead(
        output: OutputStream,
        code: Int,
        reason: String,
        headers: Map<String, String>,
        contentLength: Long?
    ) {
        val head = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            if (contentLength != null) append("Content-Length: ").append(contentLength).append("\r\n")
            // Receivers are web pages: they need CORS, and Chrome's private network access checks.
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: *\r\n")
            append("Access-Control-Allow-Private-Network: true\r\n")
            append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
    }

    private fun writeError(output: OutputStream, code: Int, reason: String) {
        writeHead(output, code, reason, mapOf("Content-Type" to "text/plain"), 0L)
        output.flush()
    }

    // endregion

    // region Playlists

    /**
     * Points every URI in an HLS playlist (variants, segments, keys, init sections, renditions)
     * back at the proxy, resolved against the playlist's own address after redirects.
     */
    internal fun rewritePlaylist(text: String, base: String, headers: Map<String, String>, host: String): String {
        val prefix = "http://$host/$token"
        fun proxify(reference: String): String {
            val resolved = resolve(base, reference) ?: return reference
            val scheme = Uri.parse(resolved).scheme?.lowercase()
            if (scheme !in PROXIED_SCHEMES) return reference
            return "$prefix/m/${encode(resolved, headers)}/${fileName(resolved, "media")}"
        }
        return text.lineSequence().joinToString("\n") { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> raw
                line.startsWith("#") -> URI_ATTRIBUTE.replace(raw) { match ->
                    "URI=\"${proxify(match.groupValues[1])}\""
                }
                else -> proxify(line)
            }
        }
    }

    private fun resolve(base: String, reference: String): String? =
        base.toHttpUrlOrNull()?.resolve(reference)?.toString()
            ?: runCatching { java.net.URI(base).resolve(reference).toString() }.getOrNull()

    private fun ByteArray.isPlaylist(): Boolean {
        val text = toString(Charsets.UTF_8).trimStart('﻿', ' ', '\r', '\n', '\t')
        return text.startsWith("#EXTM3U")
    }

    // endregion

    // region Helpers

    private fun encode(target: String, headers: Map<String, String>): String {
        val payload = buildString {
            append(target)
            headers.forEach { (name, value) -> append('\n').append(name).append(": ").append(value) }
        }
        return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), BASE64_FLAGS)
    }

    private fun decode(segment: String): Pair<String, Map<String, String>>? = runCatching {
        val lines = Base64.decode(segment, BASE64_FLAGS).toString(Charsets.UTF_8).split('\n')
        val headers = lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(": ")
            if (colon <= 0) null else line.substring(0, colon) to line.substring(colon + 2)
        }.toMap()
        lines.first() to headers
    }.getOrNull()?.takeIf { it.first.isNotBlank() }

    private fun fileName(target: String, fallback: String): String =
        Uri.parse(target).lastPathSegment
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(64)
            ?.takeIf { it.isNotBlank() }
            ?: fallback

    private fun parseRange(header: String?): Pair<Long, Long?>? {
        val match = header?.let { RANGE.matchEntire(it.trim()) } ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull()
        return start to end
    }

    private fun totalLength(
        upstreamHeaders: Map<String, List<String>>,
        target: String,
        start: Long,
        opened: Long,
        requestedEnd: Long?
    ): Long? {
        upstreamHeaders.first("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { return it }
        ContentMetadata.getContentLength(cache.getContentMetadata(target)).takeIf { it > 0 }?.let { return it }
        return if (requestedEnd == null && opened != C.LENGTH_UNSET.toLong()) start + opened else null
    }

    private fun Map<String, List<String>>.first(name: String): String? =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    private fun guessContentType(target: String): String {
        val path = Uri.parse(target).path.orEmpty().lowercase()
        return when {
            path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            path.endsWith(".mp4") || path.endsWith(".m4s") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".vtt") -> "text/vtt"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".key") -> "application/octet-stream"
            else -> "video/mp2t"
        }
    }

    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next == -1) return if (bytes.size() == 0) null else bytes.toString(Charsets.ISO_8859_1.name())
            if (next == '\n'.code) break
            if (next != '\r'.code) bytes.write(next)
            if (bytes.size() > 16 * 1024) throw IOException("Header line too long")
        }
        return bytes.toString(Charsets.ISO_8859_1.name())
    }

    private fun readUpTo(source: DataSource, limit: Int): ByteArray {
        val bytes = ByteArray(limit)
        var filled = 0
        while (filled < limit) {
            val read = source.read(bytes, filled, limit - filled)
            if (read == C.RESULT_END_OF_INPUT) break
            filled += read
        }
        return if (filled == limit) bytes else bytes.copyOf(filled)
    }

    private fun readRest(source: DataSource, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (output.size() < limit) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    // endregion

    companion object {
        private const val TAG = "CastProxy"
        private const val KEY_TOKEN = "cast_proxy_token"
        private const val KEY_PORT = "cast_proxy_port"
        private const val DEFAULT_PORT = 8765
        private const val HLS_MIME = "application/x-mpegURL"
        private const val HLS_FMP4 = "fmp4"
        private const val HLS_TS = "mpeg2_ts"
        private const val SNIFF_BYTES = 16
        private const val MAX_PLAYLIST_BYTES = 16 * 1024 * 1024
        private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        private val PROXIED_SCHEMES = setOf("http", "https", "content", "file")
        private val URI_ATTRIBUTE = Regex("URI=\"([^\"]+)\"")
        private val RANGE = Regex("bytes=(\\d+)-(\\d*)")

        /**
         * This phone's IPv4 address on the network the receiver is on: the interface that shares
         * the receiver's subnet, else the first private address on Wi-Fi/Ethernet.
         */
        fun lanAddressFor(receiver: InetAddress?): String? {
            val candidates = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }
                .getOrDefault(emptyList())
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .flatMap { network ->
                    network.interfaceAddresses
                        .filter { it.address is Inet4Address }
                        .map { network.name.orEmpty() to it }
                }
            val target = receiver as? Inet4Address
            if (target != null) {
                candidates.firstOrNull { (_, address) ->
                    sameSubnet(address.address, target, address.networkPrefixLength.toInt())
                }?.let { return it.second.address.hostAddress }
            }
            return candidates
                .filter { (_, address) -> address.address.isSiteLocalAddress }
                .sortedBy { (name, _) ->
                    when {
                        name.startsWith("wlan") -> 0
                        name.startsWith("eth") || name.startsWith("ap") || name.startsWith("swlan") -> 1
                        else -> 2
                    }
                }
                .firstOrNull()?.second?.address?.hostAddress
        }

        private fun sameSubnet(local: InetAddress, remote: InetAddress, prefixLength: Int): Boolean {
            if (prefixLength !in 1..32) return false
            val a = local.address
            val b = remote.address
            if (a.size != 4 || b.size != 4) return false
            val mask = if (prefixLength == 32) -1 else ((-1L shl (32 - prefixLength)) and 0xFFFFFFFFL).toInt()
            val left = ((a[0].toInt() and 0xFF) shl 24) or ((a[1].toInt() and 0xFF) shl 16) or
                ((a[2].toInt() and 0xFF) shl 8) or (a[3].toInt() and 0xFF)
            val right = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
            return (left and mask) == (right and mask)
        }
    }
}
