package com.ivor.openstream.data.backup

import com.ivor.openstream.data.local.dao.HiddenTitleDao
import com.ivor.openstream.data.local.dao.WatchLaterDao
import com.ivor.openstream.data.local.dao.WatchProgressDao
import com.ivor.openstream.data.local.entity.HiddenTitleEntity
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.local.entity.WatchProgressEntity
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What a restore added, for the confirmation message. */
data class RestoreSummary(val watchLater: Int, val progress: Int, val hidden: Int, val settingsRestored: Boolean)

class BackupFormatException(message: String) : Exception(message)

/**
 * The user's library as one JSON file: Watch Later, watch progress (which is also History), hidden
 * titles and app settings. Downloads aren't included; they're video files tied to this device.
 * Restoring merges: nothing already on the device is deleted, and progress keeps the newer row.
 */
@Singleton
class LibraryBackup @Inject constructor(
    private val watchLaterDao: WatchLaterDao,
    private val watchProgressDao: WatchProgressDao,
    private val hiddenTitleDao: HiddenTitleDao,
    private val settingsStore: AppSettingsStore
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(output: OutputStream) {
        val backup = BackupFile(
            exportedAt = System.currentTimeMillis(),
            watchLater = watchLaterDao.getAllWatchLaterItems().first().map { it.toBackup() },
            progress = watchProgressDao.observeAll().first().map { it.toBackup() },
            hidden = hiddenTitleDao.observeAll().first().map { it.toBackup() },
            settings = settingsStore.current.toBackup()
        )
        output.bufferedWriter().use { it.write(json.encodeToString(BackupFile.serializer(), backup)) }
    }

    suspend fun restore(input: InputStream): RestoreSummary {
        val text = input.bufferedReader().use { it.readText() }
        val backup = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw BackupFormatException("This isn't an OpenStream backup") }
        if (backup.app != APP_ID) throw BackupFormatException("This isn't an OpenStream backup")
        if (backup.version > VERSION) throw BackupFormatException("This backup is from a newer version of OpenStream")

        backup.watchLater.forEach { watchLaterDao.insertWatchLaterItem(it.toEntity()) }
        var progressAdded = 0
        backup.progress.forEach { row ->
            val existing = watchProgressDao.get(row.id)
            if (existing == null || existing.updatedAt < row.updatedAt) {
                watchProgressDao.upsert(row.toEntity())
                progressAdded++
            }
        }
        backup.hidden.forEach { hiddenTitleDao.insert(it.toEntity()) }
        backup.settings?.let { saved -> settingsStore.update { saved.applyTo(it) } }

        return RestoreSummary(
            watchLater = backup.watchLater.size,
            progress = progressAdded,
            hidden = backup.hidden.size,
            settingsRestored = backup.settings != null
        )
    }

    companion object {
        const val APP_ID = "openstream"
        const val VERSION = 1
        const val MIME_TYPE = "application/json"
    }
}

@Serializable
private data class BackupFile(
    val app: String = LibraryBackup.APP_ID,
    val version: Int = LibraryBackup.VERSION,
    val exportedAt: Long = 0L,
    val watchLater: List<BackupWatchLater> = emptyList(),
    val progress: List<BackupProgress> = emptyList(),
    val hidden: List<BackupHidden> = emptyList(),
    val settings: BackupSettings? = null
)

@Serializable
private data class BackupWatchLater(
    val id: Int,
    val title: String,
    val posterPath: String? = null,
    val mediaType: String,
    val voteAverage: Double = 0.0,
    val dateAdded: Long = 0L
) {
    fun toEntity() = WatchLaterEntity(id, title, posterPath, mediaType, voteAverage, dateAdded)
}

private fun WatchLaterEntity.toBackup() = BackupWatchLater(id, title, posterPath, mediaType, voteAverage, dateAdded)

@Serializable
private data class BackupProgress(
    val id: String,
    val tmdbId: Int,
    val mediaType: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val episodeTitle: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val stillPath: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long
) {
    fun toEntity() = WatchProgressEntity(
        id, tmdbId, mediaType, season, episode, title, episodeTitle, posterPath, backdropPath,
        stillPath, positionMs, durationMs, completed, updatedAt
    )
}

private fun WatchProgressEntity.toBackup() = BackupProgress(
    id, tmdbId, mediaType, season, episode, title, episodeTitle, posterPath, backdropPath,
    stillPath, positionMs, durationMs, completed, updatedAt
)

@Serializable
private data class BackupHidden(val tmdbId: Int, val mediaType: String, val title: String, val hiddenAt: Long = 0L) {
    fun toEntity() = HiddenTitleEntity(tmdbId, mediaType, title, hiddenAt)
}

private fun HiddenTitleEntity.toBackup() = BackupHidden(tmdbId, mediaType, title, hiddenAt)

/** Settings by name, so a backup survives new options being added or old ones renamed. */
@Serializable
private data class BackupSettings(
    val themeMode: String? = null,
    val dynamicColor: Boolean? = null,
    val dnsProvider: String? = null,
    val wifiOnlyDownloads: Boolean? = null,
    val seekStepSeconds: Int? = null,
    val defaultSpeed: Float? = null,
    val autoPlayNext: Boolean? = null,
    val downloadMaxHeight: Int? = null
) {
    fun applyTo(current: AppSettings) = current.copy(
        themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: current.themeMode,
        dynamicColor = dynamicColor ?: current.dynamicColor,
        dnsProvider = DnsProvider.entries.firstOrNull { it.name == dnsProvider } ?: current.dnsProvider,
        wifiOnlyDownloads = wifiOnlyDownloads ?: current.wifiOnlyDownloads,
        seekStepSeconds = seekStepSeconds?.takeIf { it in AppSettings.SEEK_STEPS } ?: current.seekStepSeconds,
        defaultSpeed = defaultSpeed?.takeIf { it in AppSettings.DEFAULT_SPEEDS } ?: current.defaultSpeed,
        autoPlayNext = autoPlayNext ?: current.autoPlayNext,
        downloadMaxHeight = downloadMaxHeight?.takeIf { it in AppSettings.DOWNLOAD_HEIGHTS } ?: current.downloadMaxHeight
    )
}

private fun AppSettings.toBackup() = BackupSettings(
    themeMode = themeMode.name,
    dynamicColor = dynamicColor,
    dnsProvider = dnsProvider.name,
    wifiOnlyDownloads = wifiOnlyDownloads,
    seekStepSeconds = seekStepSeconds,
    defaultSpeed = defaultSpeed,
    autoPlayNext = autoPlayNext,
    downloadMaxHeight = downloadMaxHeight
)
