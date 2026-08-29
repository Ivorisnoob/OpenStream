package com.ivor.openstream.data.streaming.providers

import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.streaming.vidkingRequestHeaders
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.StreamSubtitle
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class VidkingServerSpec(
    val id: String,
    val name: String,
    val endpoint: String,
    val priority: Int,
    val language: String? = null,
    val qualityFilter: String? = null
)

@Singleton
class VidkingDirectApi @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient,
    private val json: Json
) {
    private val seedMutex = Mutex()
    private val seeds = mutableMapOf<Int, SeedCacheEntry>()

    suspend fun resolve(spec: VidkingServerSpec, identity: MediaIdentity): List<VideoServer> =
        withContext(Dispatchers.IO) {
            val firstSeed = seed(identity.tmdbId)
            val response = requestSources(spec, identity, firstSeed)
            val payload = if (response.code == 401) {
                response.close()
                val refreshedSeed = seed(identity.tmdbId, forceRefresh = true)
                requestSources(spec, identity, refreshedSeed).use { retry ->
                    if (!retry.isSuccessful) throw IOException("${spec.name} returned HTTP ${retry.code}")
                    decryptPayload(retry.body?.string().orEmpty(), refreshedSeed.value, identity.tmdbId)
                }
            } else {
                response.use {
                    if (!it.isSuccessful) throw IOException("${spec.name} returned HTTP ${it.code}")
                    decryptPayload(it.body?.string().orEmpty(), firstSeed.value, identity.tmdbId)
                }
            }
            payload.toVideoServers(spec)
        }

    private fun requestSources(
        spec: VidkingServerSpec,
        identity: MediaIdentity,
        seed: SeedCacheEntry
    ) = client.newCall(
        Request.Builder()
            .url(
                BuildConfig.VIDKING_API_BASE_URL.toHttpUrl().newBuilder()
                    .addPathSegments(spec.endpoint)
                    .addQueryParameter("title", identity.title)
                    .addQueryParameter("mediaType", identity.tmdbType)
                    .addQueryParameter("year", identity.year?.toString().orEmpty())
                    .addQueryParameter("episodeId", identity.episode.toString())
                    .addQueryParameter("seasonId", identity.season.toString())
                    .addQueryParameter("tmdbId", identity.tmdbId.toString())
                    .addQueryParameter("imdbId", identity.imdbId.orEmpty())
                    .addQueryParameter("enc", "2")
                    .addQueryParameter("seed", seed.value)
                    .addQueryParameter("_t", System.currentTimeMillis().toString())
                    .apply {
                        spec.language?.let { addQueryParameter("language", it) }
                    }
                    .build()
            )
            .headers(
                okhttp3.Headers.Builder()
                    .apply { vidkingRequestHeaders().forEach { (name, value) -> add(name, value) } }
                    .add("Cache-Control", "no-cache, no-store, must-revalidate")
                    .add("Pragma", "no-cache")
                    .build()
            )
            .build()
    ).execute()

    private suspend fun seed(mediaId: Int, forceRefresh: Boolean = false): SeedCacheEntry =
        seedMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = seeds[mediaId]
            if (!forceRefresh && cached != null && cached.expiresAt - 5_000 > now) {
                return@withLock cached
            }

            val url = BuildConfig.VIDKING_API_BASE_URL.toHttpUrl().newBuilder()
                .addPathSegment("seed")
                .addQueryParameter("mediaId", mediaId.toString())
                .build()
            val request = Request.Builder().url(url).build()
            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Vidking seed returned HTTP ${response.code}")
                json.decodeFromString<SeedResponse>(response.body?.string().orEmpty())
            }
            SeedCacheEntry(payload.seed, now + payload.ttlMs).also { seeds[mediaId] = it }
        }

    private fun decryptPayload(body: String, seed: String, mediaId: Int): SourcesPayload {
        if (body.isBlank()) throw IOException("Vidking returned an empty payload")
        return json.decodeFromString(VidkingPayloadCipher.decrypt(body, seed, mediaId))
    }

    private fun SourcesPayload.toVideoServers(spec: VidkingServerSpec): List<VideoServer> {
        val requestHeaders = vidkingRequestHeaders()
        val streamSubtitles = subtitles.mapNotNull { subtitle ->
            val url = subtitle.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StreamSubtitle(
                url = url,
                label = subtitle.label ?: subtitle.lang ?: "Subtitle",
                language = subtitle.lang,
                headers = requestHeaders
            )
        }
        return sources
            .asSequence()
            .filter { source ->
                spec.qualityFilter?.let { filter ->
                    source.quality?.equals(filter, ignoreCase = true) == true
                } ?: true
            }
            .mapNotNull { source ->
                val url = source.url?.takeIf { it.startsWith("http") }
                    ?: return@mapNotNull null
                val descriptor = listOfNotNull(
                    source.quality,
                    source.type,
                    spec.language,
                    spec.qualityFilter
                ).joinToString(" ")
                VideoServer(
                    id = "vidking-${spec.id}-${url.hashCode()}",
                    providerId = "vidking-${spec.id}",
                    providerName = "Vidking",
                    name = spec.name,
                    url = url,
                    quality = StreamQuality.parse(source.quality),
                    audio = StreamAudio.parse(descriptor),
                    headers = requestHeaders,
                    subtitles = streamSubtitles,
                    isDownloadable = !url.substringBefore('?').endsWith(".mpd", ignoreCase = true)
                )
            }.toList()
    }

    @Serializable
    private data class SeedResponse(
        val seed: String,
        val ttlMs: Long = 30_000
    )

    private data class SeedCacheEntry(
        val value: String,
        val expiresAt: Long
    )

    @Serializable
    private data class SourcesPayload(
        val sources: List<SourceDto> = emptyList(),
        val subtitles: List<SubtitleDto> = emptyList()
    )

    @Serializable
    private data class SourceDto(
        val url: String? = null,
        val quality: String? = null,
        val type: String? = null
    )

    @Serializable
    private data class SubtitleDto(
        val url: String? = null,
        val lang: String? = null,
        @SerialName("label") val label: String? = null
    )
}
