package com.ivor.openstream.data.streaming.providers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.data.streaming.vidkingRequestHeaders
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class VidkingWebViewProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) : StreamProvider {
    override val id: String = "vidking-webview"
    override val displayName: String = "Vidking · Web fallback"
    override val priority: Int = 100
    override val isEnabled: Boolean = true

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> =
        withContext(Dispatchers.Main.immediate) {
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

                val timeoutRunnable = Runnable {
                    if (!finished) {
                        finished = true
                        val servers = streams.values.toVideoServers(subtitles.values.toList())
                        cleanup()
                        if (continuation.isActive) continuation.resume(Result.success(servers))
                    }
                }

                fun completeAfterSettle() {
                    settleRunnable?.let(handler::removeCallbacks)
                    settleRunnable = Runnable {
                        if (!finished) {
                            finished = true
                            val servers = streams.values.toVideoServers(subtitles.values.toList())
                            handler.removeCallbacks(timeoutRunnable)
                            cleanup()
                            if (continuation.isActive) continuation.resume(Result.success(servers))
                        }
                    }.also { handler.postDelayed(it, SETTLE_DELAY_MS) }
                }

                fun addStream(url: String, quality: String?, headers: Map<String, String>) {
                    if (finished || !url.isPlayableStream()) return
                    streams[url] = SniffedStream(
                        url = url,
                        quality = quality ?: inferQuality(url),
                        headers = vidkingRequestHeaders() + headers.filterKeys {
                            it.equals("Referer", true) || it.equals("Origin", true) || it.equals("User-Agent", true)
                        }
                    )
                    completeAfterSettle()
                }

                fun addSubtitle(url: String, label: String? = null) {
                    if (finished || !url.isSubtitle()) return
                    subtitles[url] = StreamSubtitle(
                        url = url,
                        label = label ?: "Detected subtitle ${subtitles.size + 1}",
                        headers = vidkingRequestHeaders()
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
                    addJavascriptInterface(bridge, BRIDGE_NAME)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            view.evaluateJavascript(INJECTION_SCRIPT, null)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString().orEmpty()
                            if (url.isPlayableStream()) {
                                handler.post { addStream(url, inferQuality(url), request?.requestHeaders.orEmpty()) }
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
                webView.loadUrl(identity.embedUrl())
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
                is kotlinx.serialization.json.JsonArray -> element.forEach { walk(it, inheritedQuality) }
                else -> Unit
            }
        }

        walk(root)
        return candidates.distinctBy { it.url }
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull

    private fun MediaIdentity.embedUrl(): String = if (tmdbType == "movie") {
        "$VIDKING_EMBED_BASE/movie/$tmdbId?autoPlay=true"
    } else {
        "$VIDKING_EMBED_BASE/tv/$tmdbId/$season/$episode?autoPlay=true"
    }

    private fun Collection<SniffedStream>.toVideoServers(
        subtitles: List<StreamSubtitle>
    ): List<VideoServer> = map { stream ->
        VideoServer(
            id = "vidking-web-${stream.url.hashCode()}",
            providerId = id,
            providerName = "Vidking",
            name = "Web fallback",
            url = stream.url,
            quality = StreamQuality.parse(stream.quality),
            audio = StreamAudio.parse(stream.quality),
            headers = stream.headers,
            subtitles = subtitles,
            isDownloadable = !stream.url.substringBefore('?').endsWith(".mpd", ignoreCase = true)
        )
    }

    private fun String.isPlayableStream(): Boolean {
        val normalized = lowercase().substringBefore('#')
        if (BLOCKED_HOST_MARKERS.any(normalized::contains)) return false
        return normalized.substringBefore('?').endsWith(".m3u8") ||
            normalized.substringBefore('?').endsWith(".mp4") ||
            normalized.substringBefore('?').endsWith(".mpd") ||
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
        const val BRIDGE_NAME = "OpenStreamSniffer"
        const val VIDKING_EMBED_BASE = "https://www.vidking.net/embed"
        const val TIMEOUT_MS = 15_000L
        const val SETTLE_DELAY_MS = 1_500L
        val BLOCKED_HOST_MARKERS = listOf("googleads", "doubleclick", "telemetry")

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
                    this.addEventListener('load', function() { report(this.responseText); });
                    return originalOpen.apply(this, arguments);
                };
                const originalFetch = window.fetch;
                window.fetch = function() {
                    return originalFetch.apply(this, arguments).then(function(response) {
                        response.clone().text().then(report).catch(function() {});
                        return response;
                    });
                };
            })();
            """.trimIndent()
    }
}
