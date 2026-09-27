package com.ivor.openstream.presentation.settings

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import coil3.SingletonImageLoader
import com.ivor.openstream.data.repository.HiddenTitlesRepository
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.settings.ThemeMode
import com.ivor.openstream.di.downloadRequirements
import com.ivor.openstream.domain.repository.ExtensionRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val installedCount: Int = 0,
    val enabledCount: Int = 0,
    val availableCount: Int = 0,
    val updateCount: Int = 0,
    val repoCount: Int = 0,
    val isSyncing: Boolean = false
)

@OptIn(UnstableApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionRepository: ExtensionRepository,
    private val appSettingsStore: AppSettingsStore,
    private val downloadManager: DownloadManager,
    private val watchProgressRepository: WatchProgressRepository,
    private val hiddenTitlesRepository: HiddenTitlesRepository
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = extensionRepository.catalog
        .map { catalog ->
            SettingsUiState(
                installedCount = catalog.installed.size,
                enabledCount = catalog.enabled.size,
                availableCount = catalog.extensions.size,
                updateCount = catalog.updatable.size,
                repoCount = catalog.repos.size,
                isSyncing = catalog.isSyncing
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    val appSettings: StateFlow<AppSettings> = appSettingsStore.settings

    val hiddenTitleCount: StateFlow<Int> = hiddenTitlesRepository.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _imageCacheBytes = MutableStateFlow<Long?>(null)
    val imageCacheBytes: StateFlow<Long?> = _imageCacheBytes.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refreshImageCacheSize()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { extensionRepository.refresh(force = true) }
        }
    }

    fun setThemeMode(mode: ThemeMode) = appSettingsStore.update { it.copy(themeMode = mode) }

    fun setDynamicColor(enabled: Boolean) = appSettingsStore.update { it.copy(dynamicColor = enabled) }

    fun setDnsProvider(provider: DnsProvider) = appSettingsStore.update { it.copy(dnsProvider = provider) }

    fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        appSettingsStore.update { it.copy(wifiOnlyDownloads = wifiOnly) }
        downloadManager.requirements = downloadRequirements(wifiOnly)
    }

    fun clearImageCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
            refreshImageCacheSize()
            _messages.tryEmit("Image cache cleared")
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            watchProgressRepository.clearAll()
            _messages.tryEmit("Watch history and progress cleared")
        }
    }

    fun unhideAllTitles() {
        viewModelScope.launch {
            hiddenTitlesRepository.unhideAll()
            _messages.tryEmit("Hidden titles are back on Home")
        }
    }

    private fun refreshImageCacheSize() {
        viewModelScope.launch {
            _imageCacheBytes.value = withContext(Dispatchers.IO) {
                runCatching { SingletonImageLoader.get(context).diskCache?.size }.getOrNull()
            }
        }
    }
}
