package com.ivor.openstream.data.streaming.providers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.data.streaming.VIDKING_ORIGIN
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.StreamSubtitle
import com.ivor.openstream.domain.model.VideoServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Describes one embeddable web player. URL templates accept `{tmdbId}`, `{imdbId}`, `{season}`
 * and `{episode}`; a template that needs an id the title does not have is skipped.
 */
data class WebEmbedSpec(
    val id: String,
    val name: String,
    val movieUrl: String?,
    val tvUrl: String?,
    val priority: Int,
    val isFallback: Boolean,
    /** Label shown as the server's provider; defaults to [name]. */
    val providerName: String = name
) {
    fun embedUrl(identity: MediaIdentity): String? {
        val template = (if (identity.tmdbType == "movie") movieUrl else tvUrl)
            ?.takeIf { it.startsWith("https://") }
            ?: return null
        if ("{imdbId}" in template && identity.imdbId.isNullOrBlank()) return null
        return template
            .replace("{tmdbId}", identity.tmdbId.toString())
            .replace("{imdbId}", identity.imdbId.orEmpty())
            .replace("{season}", identity.season.toString())
            .replace("{episode}", identity.episode.toString())
    }

    companion object {
        /** The original Vidking embed, kept under its historic id so saved preferences still match. */
        val VIDKING_FALLBACK = WebEmbedSpec(
            id = "vidking-webview",
            name = "Web fallback",
            providerName = "Vidking",
            movieUrl = "$VIDKING_ORIGIN/embed/movie/{tmdbId}?autoPlay=true",
            tvUrl = "$VIDKING_ORIGIN/embed/tv/{tmdbId}/{season}/{episode}?autoPlay=true",
            priority = 100,
            isFallback = true
        )
    }
}

class WebEmbedProvider(
    private val resolver: WebEmbedResolver,
    private val spec: WebEmbedSpec
) : StreamProvider {
    override val id: String = spec.id
    override val displayName: String = spec.name
    override val priority: Int = spec.priority
    override val isEnabled: Boolean = true
    override val isFallback: Boolean = spec.isFallback

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> =
        runCatching { resolver.resolve(spec, identity) }
}

/**
 * Loads an embed page in an off-screen WebView and records the media requests its player makes.
 *
 * This relies on undocumented third-party pages: hosts change markup, domains and anti-bot rules
 * without notice. It is therefore used as a data-driven engine that repositories can add, retire
 * or reprioritise without an app release.
 */
@Singleton
class WebEmbedResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(spec: WebEmbedSpec, identity: MediaIdentity): List<VideoServer> {
        val embedUrl = spec.embedUrl(identity) ?: return emptyList()
        val embedHost = Uri.parse(embedUrl).host.orEmpty()
        val defaultHeaders = originHeaders(embedUrl)

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                val streams = linkedMapOf<String, SniffedStream>()
                val subtitles = linkedMapOf<String, StreamSubtitle>()
                var finished = false
                var settleRunnable: Runnable? = null
                lateinit var webView: WebView

                fun cleanup() {
                    settleRunnable?.let(handler::removeCallbacks)
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.removeJavascriptInterface(BRIDGE_NAME)
                    webView.destroy()
                }

                fun finish() {
                    if (finished) return
                    finished = true
                    val servers = streams.values.toVideoServers(spec, subtitles.values.toList())
                    Log.i(TAG, "${spec.id}: ${servers.size} stream(s) from $embedHost")
                    cleanup()
                    if (continuation.isActive) continuation.resume(servers)
                }

                val timeoutRunnable = Runnable { finish() }

                fun completeAfterSettle() {
                    settleRunnable?.let(handler::removeCallbacks)
                    settleRunnable = Runnable {
                        handler.removeCallbacks(timeoutRunnable)
                        finish()
                    }.also { handler.postDelayed(it, SETTLE_DELAY_MS) }
                }

                fun addStream(url: String, quality: String?, requestHeaders: Map<String, String>) {
                    if (finished || !url.isPlayableStream()) return
                    streams[url] = SniffedStream(
                        url = url,
                        quality = quality ?: inferQuality(url),
                        headers = defaultHeaders + requestHeaders.filterKeys { name ->
                            PASSTHROUGH_HEADERS.any { it.equals(name, ignoreCase = true) }
                        }
                    )
                    completeAfterSettle()
                }

                fun addSubtitle(url: String, label: String? = null) {
                    if (finished || !url.isSubtitle()) return
                    subtitles[url] = StreamSubtitle(
                        url = url,
                        label = label ?: "Detected subtitle ${subtitles.size + 1}",
                        headers = defaultHeaders
                    )
                }

                val bridge = MetadataBridge { payload ->
                    handler.post {
                        parseMetadata(payload).forEach { candidate ->
                            if (candidate.url.isSubtitle()) {
                                addSubtitle(candidate.url, candidate.label)
                            } else {
                                addStream(candidate.url, candidate.quality, emptyMap())
                            }
                        }
                    }
                }

                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.userAgentString = BROWSER_USER_AGENT
                    // Popup ads call window.open; without multi-window support those calls go nowhere.
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    addJavascriptInterface(bridge, BRIDGE_NAME)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            view.evaluateJavascript(INJECTION_SCRIPT, null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            // Stay on the player: follow server redirects, drop ad navigations.
                            if (!request.isForMainFrame || request.isRedirect) return false
                            val host = request.url.host.orEmpty()
                            return !host.sharesRootWith(embedHost)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString().orEmpty()
                            if (url.isPlayableStream()) {
                                val headers = request?.requestHeaders.orEmpty()
                                handler.post { addStream(url, inferQuality(url), headers) }
                            } else if (url.isSubtitle()) {
                                handler.post { addSubtitle(url) }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    handler.post {
                        if (!finished) {
                            finished = true
                            handler.removeCallbacks(timeoutRunnable)
                            cleanup()
                        }
                    }
                }

                handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
                webView.loadUrl(embedUrl, mapOf("Referer" to defaultHeaders.getValue("Referer")))
            }
        }
    }

    private fun parseMetadata(payload: String): List<MetadataCandidate> {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return emptyList()
        val candidates = mutableListOf<MetadataCandidate>()

        fun walk(element: JsonElement, inheritedQuality: String? = null) {
            when (element) {
                is JsonObject -> {
                    val quality = element.string("quality") ?: element.string("label") ?: inheritedQuality
                    listOf("url", "file", "src").forEach { key ->
                        element.string(key)?.let { url ->
                            if (url.isPlayableStream() || url.isSubtitle()) {
                                candidates += MetadataCandidate(url, quality, element.string("label"))
                            }
                        }
                    }
                    element.values.forEach { walk(it, quality) }
                }
                is JsonArray -> element.forEach { walk(it, inheritedQuality) }
                else -> Unit
            }
        }

        walk(root)
        return candidates.distinctBy { it.url }
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull

    private fun Collection<SniffedStream>.toVideoServers(
        spec: WebEmbedSpec,
        subtitles: List<StreamSubtitle>
    ): List<VideoServer> = map { stream ->
        VideoServer(
            id = "${spec.id}-${stream.url.hashCode()}",
            providerId = spec.id,
            providerName = spec.providerName,
            name = spec.name,
            url = stream.url,
            quality = StreamQuality.parse(stream.quality),
            audio = StreamAudio.parse(stream.quality),
            headers = stream.headers,
            subtitles = subtitles,
            isDownloadable = !stream.url.substringBefore('?').endsWith(".mpd", ignoreCase = true)
        )
    }

    private fun originHeaders(embedUrl: String): Map<String, String> {
        val uri = Uri.parse(embedUrl)
        val origin = "${uri.scheme}://${uri.host}"
        return mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to "$origin/",
            "Origin" to origin
        )
    }

    private fun String.sharesRootWith(other: String): Boolean {
        fun root(host: String) = host.split('.').takeLast(2).joinToString(".")
        return root(this).equals(root(other), ignoreCase = true)
    }

    private fun String.isPlayableStream(): Boolean {
        val normalized = lowercase().substringBefore('#')
        if (BLOCKED_HOST_MARKERS.any(normalized::contains)) return false
        val path = normalized.substringBefore('?')
        return path.endsWith(".m3u8") ||
            path.endsWith(".mp4") ||
            path.endsWith(".mpd") ||
            "/manifest" in normalized
    }

    private fun String.isSubtitle(): Boolean {
        val normalized = lowercase().substringBefore('?')
        return normalized.endsWith(".vtt") || normalized.endsWith(".srt") || "subtitle" in normalized
    }

    private fun inferQuality(url: String): String? =
        Regex("(?:^|\\D)(2160|1440|1080|720|480|360)(?:p|\\D|$)", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.plus("p")

    private class MetadataBridge(
        private val onPayload: (String) -> Unit
    ) {
        @JavascriptInterface
        fun onMetadataFound(payload: String) = onPayload(payload)
    }

    private data class SniffedStream(
        val url: String,
        val quality: String?,
        val headers: Map<String, String>
    )

    private data class MetadataCandidate(
        val url: String,
        val quality: String?,
        val label: String?
    )

    private companion object {
        const val TAG = "WebEmbedResolver"
        const val BRIDGE_NAME = "OpenStreamSniffer"
        const val TIMEOUT_MS = 17_000L
        const val SETTLE_DELAY_MS = 1_500L
        val PASSTHROUGH_HEADERS = listOf("Referer", "Origin", "User-Agent")
        val BLOCKED_HOST_MARKERS = listOf("googleads", "doubleclick", "telemetry", "/ads/", "vast")

        /**
         * Reports XHR/fetch bodies (players often receive their source list as JSON) and nudges
         * players that wait for a click. Only same-origin frames can be reached from here; the
         * network interception above still sees requests made inside cross-origin iframes.
         */
        val INJECTION_SCRIPT =
            """
            (function() {
                if (window.__openStreamSnifferInstalled) return;
                window.__openStreamSnifferInstalled = true;
                const report = function(text) {
                    try { window.$BRIDGE_NAME.onMetadataFound(text); } catch (_) {}
                };
                const originalOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function() {
                    this.addEventListener('load', function() {
                        try { report(this.responseText); } catch (_) {}
                    });
                    return originalOpen.apply(this, arguments);
                };
                const originalFetch = window.fetch;
                window.fetch = function() {
                    return originalFetch.apply(this, arguments).then(function(response) {
                        response.clone().text().then(report).catch(function() {});
                        return response;
                    });
                };
                window.open = function() { return null; };
                let attempts = 0;
                const nudge = setInterval(function() {
                    attempts++;
                    document.querySelectorAll('video').forEach(function(video) {
                        video.muted = true;
                        const played = video.play();
                        if (played && played.catch) played.catch(function() {});
                    });
                    const selectors = '.vjs-big-play-button, .jw-display-icon-container, .plyr__control--overlaid, ' +
                        '#play, .play-button, .play-btn, [aria-label="Play"], button[class*="play"]';
                    const button = document.querySelector(selectors);
                    if (button) { try { button.click(); } catch (_) {} }
                    if (attempts >= 8) clearInterval(nudge);
                }, 1200);
            })();
            """.trimIndent()
    }
}
