package com.ivor.openstream.data.cache

import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The TMDB cache must answer offline and on errors, and never keep an error. */
class MetadataHttpCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var cache: Cache

    @Before
    fun setUp() {
        server.start()
        cache = Cache(folder.newFolder("http"), 1024L * 1024L)
    }

    @After
    fun tearDown() {
        cache.close()
        runCatching { server.shutdown() }
    }

    /** Same interceptor order as NetworkModule, with every host treated as TMDB. */
    private fun client(vararg extra: Interceptor) = OkHttpClient.Builder()
        .cache(cache)
        .addInterceptor(StaleIfErrorInterceptor(appliesTo = { true }))
        .apply { extra.forEach { addInterceptor(it) } }
        .addNetworkInterceptor(MetadataCacheHeadersInterceptor(appliesTo = { true }))
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private fun OkHttpClient.get(path: String = "/3/movie/550"): Pair<Int, String> =
        newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.code to it.body!!.string() }

    @Test
    fun freshAnswerComesFromDiskWithoutAskingAgain() {
        server.enqueue(MockResponse().setBody("first").setHeader("Cache-Control", "no-store"))

        val client = client()
        assertEquals(200 to "first", client.get())
        assertEquals(200 to "first", client.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun refreshAsksTmdbAndStoresTheNewAnswer() {
        server.enqueue(MockResponse().setBody("old"))
        server.enqueue(MockResponse().setBody("new"))
        client().get()

        assertEquals(200 to "new", client(ForceRefreshInterceptor()).get())
        assertEquals(200 to "new", client().get())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun serverErrorAndRateLimitFallBackToTheStoredCopy() {
        server.enqueue(MockResponse().setBody("stored"))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(429))
        client().get()

        val refreshing = client(ForceRefreshInterceptor())
        assertEquals(200 to "stored", refreshing.get())
        assertEquals(200 to "stored", refreshing.get())
    }

    @Test
    fun noConnectionFallsBackToTheStoredCopy() {
        server.enqueue(MockResponse().setBody("stored"))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        client().get()

        assertEquals(200 to "stored", client(ForceRefreshInterceptor()).get())
    }

    @Test
    fun errorsAreNeverCachedAndComeBackIntactWhenNothingIsStored() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.enqueue(MockResponse().setBody("ok"))

        val client = client()
        assertEquals(500 to "boom", client.get())
        assertEquals(200 to "ok", client.get())
        assertEquals(2, server.requestCount)
    }

    @Test(expected = IOException::class)
    fun noConnectionAndNothingStoredStillFails() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        client().get()
    }

    @Test
    fun otherHostsAreLeftAlone() {
        server.enqueue(MockResponse().setBody("a"))
        server.enqueue(MockResponse().setBody("b"))
        val client = OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(StaleIfErrorInterceptor())
            .addNetworkInterceptor(MetadataCacheHeadersInterceptor())
            .build()

        assertEquals(200 to "a", client.get())
        assertEquals(200 to "b", client.get())
    }

    @Test
    fun lifetimesFollowHowOftenEachAnswerChanges() {
        fun fresh(path: String) = MetadataCachePolicy.freshSeconds("https://api.themoviedb.org/3/$path".toHttpUrl())
        val hour = 3_600L
        assertEquals(6 * hour, fresh("movie/550"))
        assertEquals(6 * hour, fresh("tv/1399/season/2"))
        assertEquals(3 * hour, fresh("tv/on_the_air"))
        assertEquals(3 * hour, fresh("discover/movie?sort_by=popularity.desc"))
        assertEquals(3 * hour, fresh("trending/all/week"))
        assertEquals(hour, fresh("search/tv?query=x"))
        assertEquals(7 * 24 * hour, fresh("search/keyword?query=x"))
        assertEquals(7 * 24 * hour, fresh("movie/550/release_dates"))
        assertEquals(7 * 24 * hour, fresh("tv/1399/content_ratings"))
        assertEquals(30 * 24 * hour, fresh("tv/1399/external_ids"))
        assertEquals(24 * hour, fresh("person/287"))
    }
}
