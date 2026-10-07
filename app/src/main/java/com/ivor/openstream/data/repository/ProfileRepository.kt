package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.ProfileDao
import com.ivor.openstream.data.local.entity.ProfileEntity
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.ProfilePin
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

    /** Sets (or replaces) the profile's PIN from a plaintext code. Returns false for bad codes. */
    suspend fun setPin(id: Long, pin: String): Boolean {
        if (!ProfilePin.isValidPin(pin)) return false
        val salt = ProfilePin.generateSalt()
        dao.setPin(id, ProfilePin.hash(pin, salt), salt)
        settings.clearPinLockout(id)
        return true
    }

    suspend fun clearPin(id: Long) {
        dao.clearPin(id)
        settings.clearPinLockout(id)
    }

    /**
     * Checks a PIN, counting failures: 5 wrong tries lock the profile for a minute.
     * Returns true only on a correct code.
     */
    suspend fun verifyPin(profile: Profile, pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (settings.pinLockoutUntil(profile.id) > now) return false
        val entity = dao.get(profile.id)
        val ok = entity?.pinHash != null && entity.pinSalt != null &&
            ProfilePin.verify(pin, entity.pinHash, entity.pinSalt)
        if (ok) {
            settings.clearPinLockout(profile.id)
        } else {
            val attempts = settings.pinAttempts(profile.id) + 1
            settings.setPinAttempts(profile.id, attempts)
            if (attempts >= MAX_PIN_ATTEMPTS) {
                settings.setPinLockout(profile.id, now + PIN_LOCKOUT_MS)
                settings.setPinAttempts(profile.id, 0)
            }
        }
        return ok
    }

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

    private fun ProfileEntity.toDomain() = Profile(id, name, avatar, isKids, hasPin = pinHash != null)

    companion object {
        const val DEFAULT_NAME = "Main"
        const val DEFAULT_AVATAR = "fox"
        const val MAX_PIN_ATTEMPTS = 5
        const val PIN_LOCKOUT_MS = 60_000L
    }
}
