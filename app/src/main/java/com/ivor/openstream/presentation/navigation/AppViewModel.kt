package com.ivor.openstream.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.network.NetworkMonitor
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.NavBarStyle
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-wide state the navigation shell needs: connectivity, the tab bar style and launcher shortcut targets. */
@HiltViewModel
class AppViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
    appSettingsStore: AppSettingsStore,
    private val watchProgressRepository: WatchProgressRepository
) : ViewModel() {

    /** Starts optimistic so the offline banner never flashes while the first reading arrives. */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** How the phone tab bar is drawn; starts from the stored value so it never flips on launch. */
    val navBarStyle: StateFlow<NavBarStyle> = appSettingsStore.settings
        .map { it.navBarStyle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, appSettingsStore.current.navBarStyle)

    /** The title the Continue Watching shortcut should resume, if any. */
    suspend fun latestContinueWatching(): WatchProgress? =
        watchProgressRepository.continueWatching(limit = 1).first().firstOrNull()
}
