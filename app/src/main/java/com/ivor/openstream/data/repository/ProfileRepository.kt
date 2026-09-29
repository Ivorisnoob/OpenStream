package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.ProfileDao
import com.ivor.openstream.data.local.entity.ProfileEntity
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profiles and which one is active. The active id lives in [AppSettingsStore]; every library
 * repository follows it. There is always at least one profile.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val settings: AppSettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val profiles: StateFlow<List<Profile>?> = dao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The active profile; null only until the table has loaded. */
    val activeProfile: StateFlow<Profile?> = combine(profiles, settings.activeProfileId) { list, id ->
        list?.let { all -> all.firstOrNull { it.id == id } ?: all.firstOrNull() }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val activeProfileId: StateFlow<Long> get() = settings.activeProfileId

    val isKidsActive: Boolean get() = activeProfile.value?.isKids == true

    init {
        // A deleted or unknown active id falls back to the first profile; an empty table gets one.
        scope.launch {
            if (dao.all().isEmpty()) {
                dao.insert(ProfileEntity(id = ProfileEntity.DEFAULT_ID, name = DEFAULT_NAME, avatar = DEFAULT_AVATAR, isKids = false))
            }
            val all = dao.all()
            if (all.none { it.id == settings.activeProfileId.value }) all.firstOrNull()?.let { settings.setActiveProfile(it.id) }
        }
    }

    fun switchTo(id: Long) = settings.setActiveProfile(id)

    suspend fun create(name: String, avatar: String, isKids: Boolean): Long =
        dao.insert(ProfileEntity(name = name.trim(), avatar = avatar, isKids = isKids))

    suspend fun update(id: Long, name: String, avatar: String, isKids: Boolean) =
        dao.update(id, name.trim(), avatar, isKids)

    /**
     * Deletes a profile and its library. The last profile can't be deleted; deleting the active
     * one switches to the first remaining. Returns false when nothing was deleted.
     */
    suspend fun delete(id: Long): Boolean {
        val all = dao.all()
        if (all.size <= 1 || all.none { it.id == id }) return false
        dao.deleteWithData(id)
        if (settings.activeProfileId.value == id) all.first { it.id != id }.let { settings.setActiveProfile(it.id) }
        return true
    }

    suspend fun all(): List<Profile> = dao.all().map { it.toDomain() }

    /** Finds a profile by name or makes it; used when restoring backups. */
    suspend fun findOrCreate(name: String, avatar: String, isKids: Boolean): Long =
        dao.findByName(name.trim()) ?: create(name, avatar, isKids)

    private fun ProfileEntity.toDomain() = Profile(id, name, avatar, isKids)

    companion object {
        const val DEFAULT_NAME = "Main"
        const val DEFAULT_AVATAR = "fox"
    }
}
