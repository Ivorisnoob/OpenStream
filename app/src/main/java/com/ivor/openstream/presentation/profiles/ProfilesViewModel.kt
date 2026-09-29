package com.ivor.openstream.presentation.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.repository.ProfileRepository
import com.ivor.openstream.domain.model.Profile
import com.ivor.openstream.presentation.player.session.PlaybackSession
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val playbackSession: PlaybackSession
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
            if (!repository.delete(profile.id)) _messages.tryEmit("Keep at least one profile")
            else _messages.tryEmit("Deleted ${profile.name}")
        }
    }
}
