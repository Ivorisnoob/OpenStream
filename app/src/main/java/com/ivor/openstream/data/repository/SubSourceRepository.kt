package com.ivor.openstream.data.repository

import android.util.Log
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.IdMappingService
import com.ivor.openstream.domain.model.MediaIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Subtitles from SubSource (subsource.net), keyless. SubSource has no public API contract: this
 * mirrors what its own website calls (api.subsource.net/v1), which can change without notice.
 *
 * Search by IMDb id -> the title's `link` (`/subtitles/<slug>` for movies, `/series/<slug>` for
 * shows, where a season is `/subtitles/<slug>/season-N`) -> the list, filtered by language. A
 * subtitle's URL here is its detail endpoint; [resolveDownloadUrl] trades that for a short-lived
 * download token when the file is actually needed, and the file comes back as a zip.
 */
@Singleton
class SubSourceRepository @Inject constructor(
    private val idMappingService: IdMappingService,
    @Named("StreamingClient") private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun search(identity: MediaIdentity): List<SubtitleDto> = withContext(Dispatchers.IO) {
        runCatching { searchOrThrow(identity) }
            .onFailure { Log.w(TAG, "SubSource search failed: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private suspend fun searchOrThrow(identity: MediaIdentity): List<SubtitleDto> {
        val imdbId = idMappingService.enrich(identity).imdbId?.takeIf { it.startsWith("tt") } ?: return emptyList()
        val isMovie = identity.tmdbType == "movie"
        val link = findLink(imdbId) ?: return emptyList()
        val listPath = if (isMovie) {
            link
        } else {
            link.replaceFirst("/series/", "/subtitles/").trimEnd('/') + "/season-${identity.season}"
        }
        val episodePattern = if (isMovie) null else episodeRegex(identity.season, identity.episode)

        return coroutineScope {
            languages().map { (name, code) ->
                async { list(listPath, name).map { it to code } }
            }.awaitAll().flatten()
        }
            .filter { (row, _) -> row.releaseType != "trailer" }
            .filter { (row, _) -> episodePattern == null || episodePattern.containsMatchIn(row.releaseInfo) }
            // Multi-episode packs ("S01E02-E05") hold several files; the wrong one could be picked.
            .filter { (row, _) -> episodePattern == null || !EPISODE_RANGE.containsMatchIn(row.releaseInfo) }
            .distinctBy { (row, _) -> row.id }
            .groupBy { (row, _) -> row.language }
            .flatMap { (_, sameLanguage) -> sameLanguage.take(MAX_PER_LANGUAGE) }
            .map { (row, code) ->
                SubtitleDto(
                    id = "ss_${row.id}",
                    url = "$API/subtitle/${row.link}",
                    display = languageLabel(row.language) + if (row.hearingImpaired) " (SDH)" else "",
                    language = code,
                    isHearingImpaired = row.hearingImpaired,
                    source = SOURCE_NAME,
                    release = row.releaseInfo
                )
            }
    }

    private fun findLink(imdbId: String): String? {
        val body = buildJsonObject {
            put("query", imdbId)
            put("signal", "{}")
            put("includeSeasons", false)
            put("limit", 15)
        }.toString()
        val request = Request.Builder()
            .url("$API/movie/search")
            .header("User-Agent", BROWSER_USER_AGENT)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        return json.parseToJsonElement(text).jsonObject["results"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("link")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("/") }
    }

    private fun list(path: String, language: String): List<Row> {
        val request = Request.Builder()
            .url("$API$path?language=$language")
            .header("User-Agent", BROWSER_USER_AGENT)
            .build()
        val text = client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
        return json.parseToJsonElement(text).jsonObject["subtitles"]?.jsonArray.orEmpty().mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            Row(
                id = row["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                language = row["language"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                releaseType = row["release_type"]?.jsonPrimitive?.contentOrNull,
                releaseInfo = row["release_info"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                hearingImpaired = row["hearing_impaired"]?.jsonPrimitive?.intOrNull == 1,
                link = row["link"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            )
        }.filter { it.language.equals(language, ignoreCase = true) }
    }

    /** English, plus the phone's language when it isn't English: SubSource names languages in English. */
    private fun languages(): List<Pair<String, String>> {
        val device = Locale.getDefault()
        val deviceName = device.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
        return listOfNotNull(
            "english" to "en",
            (deviceName to device.language).takeIf { deviceName.isNotBlank() && device.language != "en" }
        )
    }

    private fun languageLabel(name: String) =
        name.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ENGLISH) }

    private data class Row(
        val id: Int,
        val language: String,
        val releaseType: String?,
        val releaseInfo: String,
        val hearingImpaired: Boolean,
        val link: String
    )

    companion object {
        const val SOURCE_NAME = "SubSource"
        private const val TAG = "SubSource"
        private const val API = "https://api.subsource.net/v1"
        private const val MAX_PER_LANGUAGE = 4
        private val EPISODE_RANGE = Regex("(?i)e\\d+\\s*-\\s*e?\\d+")

        /** `S01E05`, `s1.e5`, `1x05` or a bare `E05`, not followed by another digit. */
        internal fun episodeRegex(season: Int, episode: Int): Regex = Regex(
            "(?i)(s0*$season[ ._-]*e0*$episode(?!\\d))|((?<![a-z0-9])$season x0*$episode(?!\\d))|((?<![a-z0-9])e0*$episode(?!\\d))"
                .replace(" x", "x")
        )

        fun isSubSourceUrl(url: String): Boolean =
            url.startsWith("$API/subtitle/") && !url.startsWith("$API/subtitle/download/")

        /** Trades a subtitle's detail URL for its file's download URL. Blocking. */
        fun resolveDownloadUrl(client: OkHttpClient, detailUrl: String): String {
            val request = Request.Builder().url(detailUrl).header("User-Agent", BROWSER_USER_AGENT).build()
            val text = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("SubSource returned HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val token = Json.parseToJsonElement(text).jsonObject["subtitle"]?.jsonObject
                ?.get("download_token")?.jsonPrimitive?.contentOrNull
                ?: throw IOException("SubSource gave no download token")
            return "$API/subtitle/download/$token"
        }
    }
}
