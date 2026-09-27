package com.ivor.openstream.data.streaming.anime

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Fetches pages from sites behind Cloudflare's automatic JavaScript challenge.
 *
 * Requests go through OkHttp with the WebView's cookies and user agent (what `cf_clearance` is
 * bound to). When Cloudflare answers 403/503, the site is opened once in a hidden WebView so the
 * challenge can run, and the request is retried with the fresh clearance. Challenges that need
 * the user (Turnstile) are not shown; the request then fails.
 */
@Singleton
class CloudflareClearance @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("StreamingClient") private val client: OkHttpClient
) {
    private val clearing = Mutex()

    @Volatile
    private var agent: String? = null

    fun userAgent(): String =
        agent ?: runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?.also { agent = it }
            ?: BROWSER_USER_AGENT

    suspend fun fetch(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        val first = request(url, referer)
        if (first.first == 200) return@withContext first.second
        if (first.first != 403 && first.first != 503) throw IOException("${url.toHttpUrl().host} returned HTTP ${first.first}")

        val origin = url.toHttpUrl().let { "${it.scheme}://${it.host}/" }
        clearing.withLock { clear(origin) }
        val retry = request(url, referer)
        if (retry.first != 200) throw IOException("${url.toHttpUrl().host} returned HTTP ${retry.first}")
        retry.second
    }

    private fun request(url: String, referer: String?): Pair<Int, String> {
        val cookies = CookieManager.getInstance().getCookie(url)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .apply {
                if (!cookies.isNullOrBlank()) header("Cookie", cookies)
                if (referer != null) header("Referer", referer)
            }
            .build()
        return client.newCall(request).execute().use { it.code to (it.body?.string().orEmpty()) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun clear(origin: String) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(context)
            var done = false

            fun finish() {
                if (done) return
                done = true
                handler.removeCallbacksAndMessages(null)
                CookieManager.getInstance().flush()
                webView.stopLoading()
                webView.destroy()
                if (continuation.isActive) continuation.resume(Unit)
            }

            // Poll for the clearance cookie; the challenge redirects once it passes.
            val poll = object : Runnable {
                override fun run() {
                    val cookies = CookieManager.getInstance().getCookie(origin).orEmpty()
                    if ("cf_clearance=" in cookies) finish() else handler.postDelayed(this, POLL_MS)
                }
            }

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val title = view.title.orEmpty()
                    if (!title.contains("Just a moment") && !title.contains("Attention Required") &&
                        "cf_clearance=" in CookieManager.getInstance().getCookie(origin).orEmpty()
                    ) {
                        finish()
                    }
                }
            }
            handler.postDelayed(poll, POLL_MS)
            handler.postDelayed({ finish() }, TIMEOUT_MS)
            continuation.invokeOnCancellation { handler.post { finish() } }
            webView.loadUrl(origin)
        }
    }

    private companion object {
        const val POLL_MS = 500L
        const val TIMEOUT_MS = 15_000L
    }
}
