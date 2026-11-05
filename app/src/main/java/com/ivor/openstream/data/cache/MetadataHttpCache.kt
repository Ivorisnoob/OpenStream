package com.ivor.openstream.data.cache

import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * How long each kind of TMDB answer counts as fresh in the HTTP cache.
 *
 * TMDB's own cache headers are not relied on (they are not a documented contract); every
 * successful answer is stamped with these lifetimes by [MetadataCacheHeadersInterceptor] instead.
 * Lists that move during the day stay fresh for hours, things that almost never change (ratings,
 * external ids) for days.
 */
object MetadataCachePolicy {
    const val TMDB_HOST = "api.themoviedb.org"

    /** How old a cached answer may be and still be shown when TMDB can't be reached. */
    val MAX_STALE_SECONDS: Long = TimeUnit.DAYS.toSeconds(30)

    private val HOUR = TimeUnit.HOURS.toSeconds(1)
    private val DAY = TimeUnit.DAYS.toSeconds(1)

    fun isTmdb(url: HttpUrl): Boolean = url.host == TMDB_HOST

    /** Seconds an answer for [url] stays fresh. */
    fun freshSeconds(url: HttpUrl): Long {
        // "/3/movie/550/release_dates" -> ["movie", "550", "release_dates"]
        val segments = url.pathSegments.dropWhile { it == "3" }.filter { it.isNotEmpty() }
        val first = segments.firstOrNull() ?: return HOUR
        return when {
            segments.lastOrNull() in SLOW_CHANGING -> 7 * DAY
            segments.lastOrNull() == "external_ids" -> 30 * DAY
            first == "search" && segments.getOrNull(1) == "keyword" -> 7 * DAY
            first == "search" -> HOUR
            first == "person" -> DAY
            first == "discover" || first == "trending" -> 3 * HOUR
            // Seasons and title pages: new episodes and air dates show up within a few hours.
            (first == "tv" || first == "movie") && segments.getOrNull(1)?.toIntOrNull() != null -> 6 * HOUR
            // tv/on_the_air, tv/airing_today, tv/top_rated and other lists.
            else -> 3 * HOUR
        }
    }

    private val SLOW_CHANGING = setOf("release_dates", "content_ratings")
}

/**
 * Network interceptor: gives every successful TMDB answer the lifetime [MetadataCachePolicy]
 * picks, so OkHttp's cache can store it. Error answers are left alone and never cached.
 */
class MetadataCacheHeadersInterceptor(
    private val appliesTo: (HttpUrl) -> Boolean = MetadataCachePolicy::isTmdb
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.method != "GET" || !appliesTo(request.url)) return response
        // 304 too: its headers replace the stored ones when a refresh revalidates.
        if (response.code != 200 && response.code != 304) return response
        return response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .apply { if (response.header("Vary")?.trim() == "*") removeHeader("Vary") }
            .header("Cache-Control", "public, max-age=${MetadataCachePolicy.freshSeconds(request.url)}")
            .build()
    }
}

/**
 * Application interceptor: when TMDB can't be reached, times out, rate limits or errors, answer
 * from the cache instead, however stale (up to [maxStaleSeconds]). Offline or with TMDB down,
 * every page the user has seen still opens. A request the caller cancelled is not retried.
 */
class StaleIfErrorInterceptor(
    private val maxStaleSeconds: Long = MetadataCachePolicy.MAX_STALE_SECONDS,
    private val appliesTo: (HttpUrl) -> Boolean = MetadataCachePolicy::isTmdb
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" || !appliesTo(request.url)) return chain.proceed(request)

        val response = try {
            chain.proceed(request)
        } catch (error: IOException) {
            if (chain.call().isCanceled()) throw error
            return cached(chain) ?: throw error
        }
        if (response.code < 500 && response.code != 429) return response

        // Keep the error to hand back if nothing is cached; its body is small.
        val errorBody = response.body?.let { body -> body.bytes().toResponseBody(body.contentType()) }
        response.close()
        return cached(chain) ?: response.newBuilder().body(errorBody).build()
    }

    private fun cached(chain: Interceptor.Chain): Response? {
        val request = chain.request().newBuilder()
            .cacheControl(
                CacheControl.Builder()
                    .onlyIfCached()
                    .maxStale(maxStaleSeconds.toInt(), TimeUnit.SECONDS)
                    .build()
            )
            .build()
        val response = try {
            chain.proceed(request)
        } catch (_: IOException) {
            return null
        }
        // Nothing usable stored: OkHttp answers 504 without touching the network.
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        return response
    }
}

/**
 * Makes the cache check with TMDB before answering (pull to refresh). A request that already says
 * how to use the cache, such as [StaleIfErrorInterceptor]'s offline retry, is left as it is.
 */
class ForceRefreshInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Cache-Control") != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build())
    }
}
