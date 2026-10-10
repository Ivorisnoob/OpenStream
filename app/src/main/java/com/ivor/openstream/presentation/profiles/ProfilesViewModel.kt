package com.ivor.openstream.presentation.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.R
import com.ivor.openstream.data.repository.ProfileRepository
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.model.Profile
import com.ivor.openstream.presentation.player.session.PlaybackSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Profiles for the picker, the Home avatar and Settings → Profiles. */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val playbackSession: PlaybackSession,
    private val settings: AppSettingsStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = repository.profiles
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeProfile: StateFlow<Profile?> = repository.activeProfile

    /** Set once someone is picked on launch; the picker isn't asked for again this run. */
    private val launchChoiceMade = MutableStateFlow(false)

    /** "Who's watching?" on launch: only when there is more than one profile to choose from. */
    val showLaunchPicker: StateFlow<Boolean> = combine(repository.profiles, launchChoiceMade) { list, chosen ->
        !chosen && (list?.size ?: 0) >= 2
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Makes [id] the active profile. Switching ends what's playing, since it belongs to the last one. */
    fun select(id: Long) {
        launchChoiceMade.value = true
        if (repository.activeProfileId.value == id) return
        playbackSession.stop()
        repository.switchTo(id)
    }

    fun dismissLaunchPicker() {
        launchChoiceMade.value = true
    }

    fun create(name: String, avatar: String, isKids: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.create(name, avatar, isKids) }
    }

    fun update(id: Long, name: String, avatar: String, isKids: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.update(id, name, avatar, isKids) }
    }

    fun delete(profile: Profile) {
        viewModelScope.launch {
            if (profile.id == repository.activeProfileId.value) playbackSession.stop()
            if (!repository.delete(profile.id)) _messages.tryEmit(context.getString(R.string.pf_keep_one))
            else _messages.tryEmit(context.getString(R.string.pf_deleted, profile.name))
        }
    }

    /** Stores a new PIN for [id]; emits pin_saved, or pin_mismatch when the code isn't 4-8 digits. */
    fun setPin(id: Long, pin: String) {
        viewModelScope.launch {
            if (repository.setPin(id, pin)) _messages.tryEmit(context.getString(R.string.pin_saved))
            else _messages.tryEmit(context.getString(R.string.pin_mismatch))
        }
    }

    fun clearPin(id: Long) {
        viewModelScope.launch {
            repository.clearPin(id)
            _messages.tryEmit(context.getString(R.string.pin_removed))
        }
    }

    /** Checks a PIN without emitting messages; the caller formats Locked/Wrong itself. */
    suspend fun checkPin(profile: Profile, pin: String): PinCheck {
        if (repository.verifyPin(profile, pin)) return PinCheck.Ok
        val secondsLeft = (settings.pinLockoutUntil(profile.id) - System.currentTimeMillis() + 999) / 1000
        return if (secondsLeft > 0) PinCheck.Locked(secondsLeft) else PinCheck.Wrong
    }

    sealed interface PinCheck {
        data object Ok : PinCheck
        data object Wrong : PinCheck
        data class Locked(val secondsLeft: Long) : PinCheck
    }
}
