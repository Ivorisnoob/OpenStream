package com.ivor.openstream.data.streaming

import android.content.SharedPreferences
import android.net.Uri
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRequestHeaderStore @Inject constructor(
    private val preferences: SharedPreferences,
    private val json: Json
) {
    @Volatile
    private var latestHeaders: Map<String, String> = load(LATEST_KEY)

    fun register(url: String, headers: Map<String, String>) {
        if (headers.isEmpty()) return
        val host = Uri.parse(url).host ?: return
        latestHeaders = headers
        preferences.edit()
            .putString(hostKey(host), json.encodeToString(headers))
            .putString(LATEST_KEY, json.encodeToString(headers))
            .apply()
    }

    fun headersFor(uri: Uri): Map<String, String> {
        val host = uri.host
        return if (host == null) emptyMap() else load(hostKey(host)).ifEmpty { latestHeaders }
    }

    private fun load(key: String): Map<String, String> = runCatching {
        preferences.getString(key, null)
            ?.let { json.decodeFromString<Map<String, String>>(it) }
            .orEmpty()
    }.getOrDefault(emptyMap())

    private fun hostKey(host: String): String = "download_headers:$host"

    private companion object {
        const val LATEST_KEY = "download_headers:latest"
    }
}
