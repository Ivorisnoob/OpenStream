package com.ivor.openstream.data.backup

import com.ivor.openstream.data.local.dao.CustomListDao
import com.ivor.openstream.data.local.dao.HiddenTitleDao
import com.ivor.openstream.data.local.dao.WatchLaterDao
import com.ivor.openstream.data.local.dao.WatchProgressDao
import com.ivor.openstream.data.local.entity.CustomListEntity
import com.ivor.openstream.data.local.entity.CustomListItemEntity
import com.ivor.openstream.data.local.entity.HiddenTitleEntity
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.local.entity.WatchProgressEntity
import com.ivor.openstream.data.repository.ProfileRepository
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.settings.PipAction
import com.ivor.openstream.data.settings.SourceSearchMode
import com.ivor.openstream.data.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What a restore added, for the confirmation message. */
data class RestoreSummary(
    val watchLater: Int,
    val progress: Int,
    val hidden: Int,
    val settingsRestored: Boolean,
    val lists: Int = 0,
    val profiles: Int = 0
)

class BackupFormatException(message: String) : Exception(message)

/**
 * The user's library as one JSON file: every profile with its Watch Later, custom lists, watch
 * progress (which is also History) and hidden titles, plus app settings. Downloads aren't included;
 * they're video files tied to this device.
 * Restoring merges: nothing already on the device is deleted, profiles and lists merge by name,
 * and progress keeps the newer row. A version 1 backup (from before profiles) goes into the
 * active profile.
 */
@Singleton
class LibraryBackup @Inject constructor(
    private val watchLaterDao: WatchLaterDao,
    private val watchProgressDao: WatchProgressDao,
    private val hiddenTitleDao: HiddenTitleDao,
    private val customListDao: CustomListDao,
    private val profiles: ProfileRepository,
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
            profiles = profiles.all().map { profile ->
                BackupProfile(
                    name = profile.name,
                    avatar = profile.avatar,
                    isKids = profile.isKids,
                    library = libraryOf(profile.id)
                )
            },
            settings = settingsStore.current.toBackup()
        )
        output.bufferedWriter().use { it.write(json.encodeToString(BackupFile.serializer(), backup)) }
    }

    private suspend fun libraryOf(profileId: Long) = BackupLibrary(
        watchLater = watchLaterDao.getAllWatchLaterItems(profileId).first().map { it.toBackup() },
        progress = watchProgressDao.observeAll(profileId).first().map { it.toBackup() },
        hidden = hiddenTitleDao.observeAll(profileId).first().map { it.toBackup() },
        lists = customListDao.allItems(profileId).groupBy { it.listId }.let { itemsByList ->
            customListDao.allLists(profileId).map { list ->
                BackupList(
                    name = list.name,
                    createdAt = list.createdAt,
                    items = itemsByList[list.id].orEmpty().map { it.toBackup() }
                )
            }
        }
    )

    suspend fun restore(input: InputStream): RestoreSummary {
        val text = input.bufferedReader().use { it.readText() }
        val backup = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw BackupFormatException("This isn't an OpenStream backup") }
        if (backup.app != APP_ID) throw BackupFormatException("This isn't an OpenStream backup")
        if (backup.version > VERSION) throw BackupFormatException("This backup is from a newer version of OpenStream")

        var total = MergeCounts()
        backup.profiles.forEach { profile ->
            val profileId = profiles.findOrCreate(profile.name, profile.avatar, profile.isKids)
            total += restoreLibrary(profile.library, profileId)
        }
        // Version 1 kept one library at the top level: it belongs to whoever is restoring.
        val legacy = BackupLibrary(backup.watchLater, backup.progress, backup.hidden, backup.lists)
        if (!legacy.isEmpty()) total += restoreLibrary(legacy, settingsStore.activeProfileId.value)
        backup.settings?.let { saved -> settingsStore.update { saved.applyTo(it) } }

        return RestoreSummary(
            watchLater = total.watchLater,
            progress = total.progress,
            hidden = total.hidden,
            settingsRestored = backup.settings != null,
            lists = total.lists,
            profiles = backup.profiles.size
        )
    }

    private suspend fun restoreLibrary(library: BackupLibrary, profileId: Long): MergeCounts {
        library.watchLater.forEach { watchLaterDao.insertWatchLaterItem(it.toEntity(profileId)) }
        var progressAdded = 0
        library.progress.forEach { row ->
            val existing = watchProgressDao.get(profileId, row.id)
            if (existing == null || existing.updatedAt < row.updatedAt) {
                watchProgressDao.upsert(row.toEntity(profileId))
                progressAdded++
            }
        }
        library.hidden.forEach { hiddenTitleDao.insert(it.toEntity(profileId)) }
        library.lists.forEach { list ->
            val listId = customListDao.findByName(profileId, list.name)
                ?: customListDao.insertList(CustomListEntity(name = list.name, createdAt = list.createdAt, profileId = profileId))
            list.items.forEach { customListDao.addItem(it.toEntity(listId)) }
        }
        return MergeCounts(library.watchLater.size, progressAdded, library.hidden.size, library.lists.size)
    }

    private data class MergeCounts(val watchLater: Int = 0, val progress: Int = 0, val hidden: Int = 0, val lists: Int = 0) {
        operator fun plus(other: MergeCounts) = MergeCounts(
            watchLater + other.watchLater,
            progress + other.progress,
            hidden + other.hidden,
            lists + other.lists
        )
    }

    companion object {
        const val APP_ID = "openstream"
        /** 2 added profiles; 1 had a single top-level library. */
        const val VERSION = 2
        const val MIME_TYPE = "application/json"
    }
}

@Serializable
private data class BackupFile(
    val app: String = LibraryBackup.APP_ID,
    val version: Int = LibraryBackup.VERSION,
    val exportedAt: Long = 0L,
    val profiles: List<BackupProfile> = emptyList(),
    // Version 1: one library, no profiles.
    val watchLater: List<BackupWatchLater> = emptyList(),
    val progress: List<BackupProgress> = emptyList(),
    val hidden: List<BackupHidden> = emptyList(),
    val lists: List<BackupList> = emptyList(),
    val settings: BackupSettings? = null
)

@Serializable
private data class BackupProfile(
    val name: String,
    val avatar: String = ProfileRepository.DEFAULT_AVATAR,
    val isKids: Boolean = false,
    val library: BackupLibrary = BackupLibrary()
)

@Serializable
private data class BackupLibrary(
    val watchLater: List<BackupWatchLater> = emptyList(),
    val progress: List<BackupProgress> = emptyList(),
    val hidden: List<BackupHidden> = emptyList(),
    val lists: List<BackupList> = emptyList()
) {
    fun isEmpty() = watchLater.isEmpty() && progress.isEmpty() && hidden.isEmpty() && lists.isEmpty()
}

@Serializable
private data class BackupList(
    val name: String,
    val createdAt: Long = 0L,
    val items: List<BackupListItem> = emptyList()
)

@Serializable
private data class BackupListItem(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val voteAverage: Double = 0.0,
    val addedAt: Long = 0L
) {
    fun toEntity(listId: Long) = CustomListItemEntity(listId, tmdbId, mediaType, title, posterPath, voteAverage, addedAt)
}

private fun CustomListItemEntity.toBackup() = BackupListItem(tmdbId, mediaType, title, posterPath, voteAverage, addedAt)

@Serializable
private data class BackupWatchLater(
    val id: Int,
    val title: String,
    val posterPath: String? = null,
    val mediaType: String,
    val voteAverage: Double = 0.0,
    val dateAdded: Long = 0L
) {
    fun toEntity(profileId: Long) = WatchLaterEntity(id, title, posterPath, mediaType, voteAverage, dateAdded, profileId)
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
    fun toEntity(profileId: Long) = WatchProgressEntity(
        id, tmdbId, mediaType, season, episode, title, episodeTitle, posterPath, backdropPath,
        stillPath, positionMs, durationMs, completed, updatedAt, profileId
    )
}

private fun WatchProgressEntity.toBackup() = BackupProgress(
    id, tmdbId, mediaType, season, episode, title, episodeTitle, posterPath, backdropPath,
    stillPath, positionMs, durationMs, completed, updatedAt
)

@Serializable
private data class BackupHidden(val tmdbId: Int, val mediaType: String, val title: String, val hiddenAt: Long = 0L) {
    fun toEntity(profileId: Long) = HiddenTitleEntity(tmdbId, mediaType, title, hiddenAt, profileId)
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
    val downloadMaxHeight: Int? = null,
    val pipLeftAction: String? = null,
    val pipRightAction: String? = null,
    val subtitleDownloadLanguages: List<String>? = null,
    val subtitleDownloadFromSites: Boolean? = null,
    val sourceSearchMode: String? = null
) {
    fun applyTo(current: AppSettings) = current.copy(
        themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: current.themeMode,
        dynamicColor = dynamicColor ?: current.dynamicColor,
        dnsProvider = DnsProvider.entries.firstOrNull { it.name == dnsProvider } ?: current.dnsProvider,
        wifiOnlyDownloads = wifiOnlyDownloads ?: current.wifiOnlyDownloads,
        seekStepSeconds = seekStepSeconds?.takeIf { it in AppSettings.SEEK_STEPS } ?: current.seekStepSeconds,
        defaultSpeed = defaultSpeed?.takeIf { it in AppSettings.DEFAULT_SPEEDS } ?: current.defaultSpeed,
        autoPlayNext = autoPlayNext ?: current.autoPlayNext,
        downloadMaxHeight = downloadMaxHeight?.takeIf { it in AppSettings.DOWNLOAD_HEIGHTS } ?: current.downloadMaxHeight,
        pipLeftAction = PipAction.entries.firstOrNull { it.name == pipLeftAction } ?: current.pipLeftAction,
        pipRightAction = PipAction.entries.firstOrNull { it.name == pipRightAction } ?: current.pipRightAction,
        subtitleDownloadLanguages = subtitleDownloadLanguages?.filter { it.isNotBlank() }?.distinct() ?: current.subtitleDownloadLanguages,
        subtitleDownloadFromSites = subtitleDownloadFromSites ?: current.subtitleDownloadFromSites,
        sourceSearchMode = SourceSearchMode.entries.firstOrNull { it.name == sourceSearchMode } ?: current.sourceSearchMode
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
    downloadMaxHeight = downloadMaxHeight,
    pipLeftAction = pipLeftAction.name,
    pipRightAction = pipRightAction.name,
    subtitleDownloadLanguages = subtitleDownloadLanguages,
    subtitleDownloadFromSites = subtitleDownloadFromSites,
    sourceSearchMode = sourceSearchMode.name
)
