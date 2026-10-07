package com.ivor.openstream.data.extensions

import android.content.Context
import com.ivor.openstream.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Fetches a repository index and any extension lists it links to. */
@Singleton
class ExtensionRepoClient @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient,
    private val parser: ExtensionIndexParser,
    @ApplicationContext private val context: Context
) {

    suspend fun fetch(url: String): CachedRepoSnapshot = withContext(Dispatchers.IO) {
        val strings = ExtensionIndexParser.RepoParseStrings(
            indexError = context.getString(R.string.st_index_not_json),
            manifestIdError = context.getString(R.string.st_addon_no_id),
            noStreamsNote = context.getString(R.string.st_addon_no_streams),
            needsConfigNote = context.getString(R.string.st_addon_needs_config)
        )
        val repo = parser.parseRepo(get(url), sourceUrl = url, strings = strings)
        val linked = repo.extensionLists
            .mapNotNull { RepoUrlNormalizer.normalize(it) }
            .flatMap { listUrl ->
                runCatching { parser.parseExtensionList(get(listUrl)) }.getOrElse { emptyList() }
            }

        val entries = (repo.extensions + linked).distinctBy { it.id }
        if (entries.isEmpty() && repo.extensionLists.isNotEmpty()) {
            throw IOException(context.getString(R.string.st_repo_lists_unreadable))
        }

        CachedRepoSnapshot(
            url = url,
            name = repo.name?.trim().orEmpty().ifEmpty { defaultName(url) },
            description = repo.description?.trim().orEmpty(),
            iconUrl = repo.iconUrl?.trim()?.takeIf { it.isNotEmpty() },
            website = repo.website?.trim()?.takeIf { it.isNotEmpty() },
            fetchedAt = System.currentTimeMillis(),
            extensions = entries
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from ${response.request.url.host}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Empty response from ${response.request.url.host}")
            return body
        }
    }

    private fun defaultName(url: String): String =
        url.substringAfter("://").substringBefore('/').ifEmpty { "Repository" }
}
