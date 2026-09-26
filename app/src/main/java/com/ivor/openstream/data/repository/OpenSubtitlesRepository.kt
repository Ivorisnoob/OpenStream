package com.ivor.openstream.data.repository

import android.util.Log
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.data.streaming.IdMappingService
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.repository.SubtitleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Subtitles from OpenSubtitles' public REST search (rest.opensubtitles.org), which needs no API
 * key, only a User-Agent. It looks titles up by IMDb id, so the TMDB id is mapped first.
 *
 * Files come back gzipped (`.gz`); the player decompresses them when one is selected.
 * This relies on a legacy endpoint OpenSubtitles still serves but no longer documents.
 */
@Singleton
class OpenSubtitlesRepository @Inject constructor(
    private val idMappingService: IdMappingService,
    @Named("StreamingClient") private val client: OkHttpClient,
    private val json: Json
) : SubtitleRepository {

    override suspend fun search(identity: MediaIdentity): List<SubtitleDto> = withContext(Dispatchers.IO) {
        val imdb = idMappingService.enrich(identity).imdbId
            ?.removePrefix("tt")
            ?.toLongOrNull()
            ?: return@withContext emptyList()

        // Path segments must be in alphabetical order or the API rejects the query.
        val base = if (identity.tmdbType == "movie") {
            "imdbid-$imdb"
        } else {
            "episode-${identity.episode}/imdbid-$imdb/season-${identity.season}"
        }
        // The unfiltered query caps its results and often leaves English out, so ask for it separately.
        val results = coroutineScope {
            val english = async { query("$base/sublanguageid-eng") }
            val all = async { query(base) }
            english.await() + all.await()
        }

        results
            .filter { it.format in SUPPORTED_FORMATS && it.url.isNotBlank() }
            .distinctBy { it.fileId }
            .sortedWith(
                compareByDescending<OpenSubtitle> { it.languageCode == "en" }
                    .thenBy { it.languageName }
                    .thenByDescending { it.downloads }
            )
            .groupBy { it.languageName }
            .flatMap { (_, sameLanguage) -> sameLanguage.take(MAX_PER_LANGUAGE) }
            .map { subtitle ->
                SubtitleDto(
                    id = "os_${subtitle.fileId}",
                    url = subtitle.url,
                    display = subtitle.languageName + if (subtitle.hearingImpaired) " (SDH)" else "",
                    language = subtitle.languageCode,
                    isHearingImpaired = subtitle.hearingImpaired,
                    source = SOURCE_NAME,
                    release = subtitle.release
                )
            }
    }

    private fun query(path: String): List<OpenSubtitle> = runCatching {
        val request = Request.Builder()
            .url("$BASE_URL/search/$path")
            .header("User-Agent", USER_AGENT)
            .header("X-User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching emptyList()
            val root = json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonArray
                ?: return@runCatching emptyList()
            root.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                OpenSubtitle(
                    fileId = item.text("IDSubtitleFile") ?: return@mapNotNull null,
                    url = item.text("SubDownloadLink").orEmpty(),
                    format = item.text("SubFormat").orEmpty().lowercase(),
                    languageName = item.text("LanguageName") ?: "Unknown",
                    languageCode = item.text("ISO639"),
                    hearingImpaired = item.text("SubHearingImpaired") == "1",
                    downloads = item.text("SubDownloadsCnt")?.toIntOrNull() ?: 0,
                    release = item.text("MovieReleaseName")?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
        }
    }.onFailure { Log.w(TAG, "OpenSubtitles search failed for $path", it) }.getOrDefault(emptyList())

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private data class OpenSubtitle(
        val fileId: String,
        val url: String,
        val format: String,
        val languageName: String,
        val languageCode: String?,
        val hearingImpaired: Boolean,
        val downloads: Int,
        val release: String?
    )

    companion object {
        const val SOURCE_NAME = "OpenSubtitles"
        const val USER_AGENT = "TemporaryUserAgent"
        private const val TAG = "OpenSubtitles"
        private const val BASE_URL = "https://rest.opensubtitles.org"
        private const val MAX_PER_LANGUAGE = 3
        private val SUPPORTED_FORMATS = setOf("srt", "vtt")
    }
}
