package com.ivor.openstream.presentation.settings

import android.content.Context
import com.ivor.openstream.R
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import coil3.SingletonImageLoader
import com.ivor.openstream.data.backup.BackupFormatException
import com.ivor.openstream.data.backup.LibraryBackup
import com.ivor.openstream.data.downloads.SmartDownloads
import com.ivor.openstream.data.playback.StreamDataGuard
import com.ivor.openstream.data.notifications.EpisodeAlarm
import com.ivor.openstream.data.diagnostics.Diagnostics
import com.ivor.openstream.data.repository.HiddenTitlesRepository
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.settings.PipAction
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
    private val hiddenTitlesRepository: HiddenTitlesRepository,
    private val libraryBackup: LibraryBackup,
    private val diagnostics: Diagnostics,
    private val smartDownloads: SmartDownloads,
    val streamDataGuard: StreamDataGuard
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

    private val _crashCount = MutableStateFlow(0)
    val crashCount: StateFlow<Int> = _crashCount.asStateFlow()

    /** True while a backup, restore or diagnostics export is writing or reading a file. */
    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refreshImageCacheSize()
        viewModelScope.launch { _crashCount.value = withContext(Dispatchers.IO) { diagnostics.crashCount } }
    }

    fun exportBackup(uri: Uri) = runFileJob(failure = context.getString(R.string.st_could_not_save_backup)) {
        openOutput(uri).use { libraryBackup.export(it) }
        context.getString(R.string.st_backed_up)
    }

    fun restoreBackup(uri: Uri) = runFileJob(failure = context.getString(R.string.st_could_not_restore)) {
        val summary = openInput(uri).use { libraryBackup.restore(it) }
        // Restored settings may flip Wi-Fi-only; the download manager has to hear about it.
        withContext(Dispatchers.Main) {
            downloadManager.requirements = downloadRequirements(appSettingsStore.current.wifiOnlyDownloads)
        }
        buildList {
            if (summary.watchLater > 0) add(context.getString(R.string.restore_saved, summary.watchLater))
            if (summary.progress > 0) add(context.getString(R.string.restore_progress, summary.progress))
            if (summary.hidden > 0) add(context.getString(R.string.restore_hidden, summary.hidden))
            if (summary.lists > 0) add(context.getString(R.string.restore_lists, summary.lists))
        }.let { parts ->
            if (parts.isEmpty()) context.getString(R.string.st_restored_settings)
            else context.getString(R.string.st_restored_counts, parts.joinToString(", "))
        }
    }

    fun exportDiagnostics(uri: Uri) = runFileJob(failure = context.getString(R.string.st_diag_failed)) {
        openOutput(uri).use { diagnostics.export(it) }
        context.getString(R.string.st_diag_saved)
    }

    fun clearCrashReports() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { diagnostics.clearCrashes() }
            _crashCount.value = 0
            _messages.tryEmit(context.getString(R.string.st_crash_cleared))
        }
    }

    private fun runFileJob(failure: String, block: suspend () -> String) {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            val message = runCatching { withContext(Dispatchers.IO) { block() } }
                .getOrElse { error -> (error as? BackupFormatException)?.let { context.getString(it.resId) } ?: failure }
            _isWorking.value = false
            _messages.tryEmit(message)
        }
    }

    private fun openOutput(uri: Uri) =
        context.contentResolver.openOutputStream(uri) ?: error("No output stream for $uri")

    private fun openInput(uri: Uri) =
        context.contentResolver.openInputStream(uri) ?: error("No input stream for $uri")

    fun refresh() {
        viewModelScope.launch {
            runCatching { extensionRepository.refresh(force = true) }
        }
    }

    fun setThemeMode(mode: ThemeMode) = appSettingsStore.update { it.copy(themeMode = mode) }

    fun setAppLanguage(tag: String?) = appSettingsStore.setAppLanguage(tag)

    fun setEpisodeNotifications(enabled: Boolean) {
        appSettingsStore.setEpisodeNotifications(enabled)
        if (enabled) {
            EpisodeAlarm.schedule(context)
        } else {
            EpisodeAlarm.cancel(context)
        }
    }

    fun setDynamicColor(enabled: Boolean) = appSettingsStore.update { it.copy(dynamicColor = enabled) }

    fun setDnsProvider(provider: DnsProvider) = appSettingsStore.update { it.copy(dnsProvider = provider) }

    fun setSeekStep(seconds: Int) = appSettingsStore.update { it.copy(seekStepSeconds = seconds) }

    fun setDefaultSpeed(speed: Float) = appSettingsStore.update { it.copy(defaultSpeed = speed) }

    fun setAutoPlayNext(enabled: Boolean) = appSettingsStore.update { it.copy(autoPlayNext = enabled) }
    fun setShowSkipButton(enabled: Boolean) = appSettingsStore.update { it.copy(showSkipButton = enabled) }

    fun setDownloadMaxHeight(height: Int) = appSettingsStore.update { it.copy(downloadMaxHeight = height) }

    fun setPipLeftAction(action: PipAction) = appSettingsStore.update { it.copy(pipLeftAction = action) }

    fun setPipRightAction(action: PipAction) = appSettingsStore.update { it.copy(pipRightAction = action) }

    fun setSubtitleDownloadLanguages(languages: List<String>) =
        appSettingsStore.update { it.copy(subtitleDownloadLanguages = languages.distinct()) }

    fun setSubtitleDownloadFromSites(enabled: Boolean) =
        appSettingsStore.update { it.copy(subtitleDownloadFromSites = enabled) }

    fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        appSettingsStore.update { it.copy(wifiOnlyDownloads = wifiOnly) }
        downloadManager.requirements = downloadRequirements(wifiOnly)
    }

    fun setSmartDownloads(enabled: Boolean) {
        appSettingsStore.setSmartDownloads(enabled)
        if (enabled) smartDownloads.reconcileNow()
    }

    fun setSmartKeepAhead(count: Int) = appSettingsStore.setSmartKeepAhead(count)

    fun setKeepPlayingInBackground(enabled: Boolean) = appSettingsStore.setKeepPlayingInBackground(enabled)

    fun setWarnBeforeMeteredStream(enabled: Boolean) = appSettingsStore.setWarnBeforeMeteredStream(enabled)

    fun setMeteredMaxHeight(height: Int) = appSettingsStore.setMeteredMaxHeight(height)

    fun clearImageCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
            refreshImageCacheSize()
            _messages.tryEmit(context.getString(R.string.st_cache_cleared))
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            watchProgressRepository.clearAll()
            _messages.tryEmit(context.getString(R.string.st_history_cleared))
        }
    }

    fun unhideAllTitles() {
        viewModelScope.launch {
            hiddenTitlesRepository.unhideAll()
            _messages.tryEmit(context.getString(R.string.home_hidden_titles_back))
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
