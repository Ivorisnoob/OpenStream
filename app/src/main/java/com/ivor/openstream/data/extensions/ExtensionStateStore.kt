package com.ivor.openstream.data.extensions

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class InstallRecord(
    val versionCode: Int = 1,
    val enabled: Boolean = true,
    val installedAt: Long = 0L
)

@Serializable
data class UsageRecord(
    val successes: Int = 0,
    val failures: Int = 0
)

@Serializable
data class CustomRepoRecord(
    val id: String,
    val url: String,
    val name: String = ""
)

/** Local marketplace state: which extensions are installed, which repos were added, how they perform. */
@Singleton
class ExtensionStateStore @Inject constructor(
    private val preferences: SharedPreferences
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val installSerializer = MapSerializer(String.serializer(), InstallRecord.serializer())
    private val usageSerializer = MapSerializer(String.serializer(), UsageRecord.serializer())
    private val repoSerializer = ListSerializer(CustomRepoRecord.serializer())
    private val seededSerializer = ListSerializer(String.serializer())

    fun installs(): Map<String, InstallRecord> = read(KEY_INSTALLS, installSerializer, emptyMap())

    fun usage(): Map<String, UsageRecord> = read(KEY_USAGE, usageSerializer, emptyMap())

    fun customRepos(): List<CustomRepoRecord> = read(KEY_REPOS, repoSerializer, emptyList())

    /** Keys that were already offered as a default install, so removing one makes it stay removed. */
    fun seededKeys(): Set<String> = read(KEY_SEEDED, seededSerializer, emptyList()).toSet()

    @Synchronized
    fun markSeeded(keys: Collection<String>) {
        if (keys.isEmpty()) return
        write(KEY_SEEDED, seededSerializer, (seededKeys() + keys).toList().sorted())
    }

    @Synchronized
    fun putInstall(key: String, record: InstallRecord) {
        write(KEY_INSTALLS, installSerializer, installs() + (key to record))
    }

    @Synchronized
    fun putInstalls(records: Map<String, InstallRecord>) {
        if (records.isEmpty()) return
        write(KEY_INSTALLS, installSerializer, installs() + records)
    }

    @Synchronized
    fun removeInstall(key: String) {
        write(KEY_INSTALLS, installSerializer, installs() - key)
    }

    @Synchronized
    fun removeInstallsForRepo(repoId: String) {
        write(KEY_INSTALLS, installSerializer, installs().filterKeys { !it.startsWith("$repoId/") })
    }

    @Synchronized
    fun recordOutcome(key: String, success: Boolean) {
        val current = usage()[key] ?: UsageRecord()
        val updated = if (success) {
            current.copy(successes = current.successes + 1)
        } else {
            current.copy(failures = current.failures + 1)
        }
        write(KEY_USAGE, usageSerializer, usage() + (key to decay(updated)))
    }

    @Synchronized
    fun addCustomRepo(record: CustomRepoRecord) {
        val existing = customRepos().filterNot { it.id == record.id }
        write(KEY_REPOS, repoSerializer, existing + record)
    }

    @Synchronized
    fun removeCustomRepo(repoId: String) {
        write(KEY_REPOS, repoSerializer, customRepos().filterNot { it.id == repoId })
    }

    fun lastSyncedAt(): Long = preferences.getLong(KEY_LAST_SYNC, 0L)

    fun setLastSyncedAt(timestamp: Long) {
        preferences.edit { putLong(KEY_LAST_SYNC, timestamp) }
    }

    fun legacyInstallState(extensionId: String): Boolean? {
        val installedKey = "extension:$extensionId:installed"
        if (!preferences.contains(installedKey)) return null
        return preferences.getBoolean(installedKey, true) &&
            preferences.getBoolean("extension:$extensionId:enabled", true)
    }

    fun clearLegacyState(extensionIds: Collection<String>) {
        preferences.edit {
            extensionIds.forEach { id ->
                remove("extension:$id:installed")
                remove("extension:$id:enabled")
            }
        }
    }

    /** Keeps the reliability signal responsive to recent behaviour rather than ancient history. */
    private fun decay(record: UsageRecord): UsageRecord =
        if (record.successes + record.failures <= MAX_SAMPLES) {
            record
        } else {
            UsageRecord(successes = record.successes / 2, failures = record.failures / 2)
        }

    private fun <T> read(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        fallback: T
    ): T {
        val raw = preferences.getString(key, null) ?: return fallback
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(fallback)
    }

    private fun <T> write(key: String, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        preferences.edit { putString(key, json.encodeToString(serializer, value)) }
    }

    private companion object {
        const val KEY_INSTALLS = "marketplace.installs"
        const val KEY_USAGE = "marketplace.usage"
        const val KEY_REPOS = "marketplace.repos"
        const val KEY_LAST_SYNC = "marketplace.last_sync"
        const val KEY_SEEDED = "marketplace.seeded"
        const val MAX_SAMPLES = 40
    }
}
