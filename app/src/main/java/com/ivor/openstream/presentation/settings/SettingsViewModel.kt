package com.ivor.openstream.presentation.settings

import io.github.ivorisnoob.smoothmotion.media3.SmoothMotion
import android.content.Context
import com.ivor.openstream.R
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import com.ivor.openstream.data.backup.BackupFormatException
import com.ivor.openstream.data.backup.LibraryBackup
import com.ivor.openstream.data.cache.AppCaches
import com.ivor.openstream.data.downloads.SmartDownloads
import com.ivor.openstream.data.playback.StreamDataGuard
import com.ivor.openstream.data.notifications.EpisodeAlarm
import com.ivor.openstream.data.diagnostics.Diagnostics
import com.ivor.openstream.data.repository.HiddenTitlesRepository
import com.ivor.openstream.data.downloads.DownloadStorage
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.AutoDeleteWatched
import com.ivor.openstream.data.settings.CaptionStyleSettings
import com.ivor.openstream.data.settings.HomeRows
import com.ivor.openstream.data.settings.PlayerPreferences
import com.ivor.openstream.data.settings.SourceSearchMode
import com.ivor.openstream.data.settings.StartScreen
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.CacheSize
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.settings.NavBarStyle
import com.ivor.openstream.data.settings.PipAction
import com.ivor.openstream.data.settings.ThemeMode
import com.ivor.openstream.data.settings.VideoGestureCustomization
import com.ivor.openstream.data.settings.VideoShaderCustomization
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
    private val playbackSession: com.ivor.openstream.presentation.player.session.PlaybackSession,
    private val watchProgressRepository: WatchProgressRepository,
    private val hiddenTitlesRepository: HiddenTitlesRepository,
    private val libraryBackup: LibraryBackup,
    private val diagnostics: Diagnostics,
    private val smartDownloads: SmartDownloads,
    private val appCaches: AppCaches,
    val streamDataGuard: StreamDataGuard,
    private val playerPreferences: PlayerPreferences,
    private val downloadStorage: DownloadStorage
) : ViewModel() {

    // region Subtitles and audio (shared with the player)

    val captionStyle: StateFlow<CaptionStyleSettings> = playerPreferences.captionStyle
    val subtitleLanguage: StateFlow<String?> = playerPreferences.subtitleLanguage
    val audioLanguage: StateFlow<String?> = playerPreferences.audioLanguage

    fun setCaptionStyle(style: CaptionStyleSettings) = playerPreferences.setCaptionStyle(style)

    /** A language code, [PlayerPreferences.SUBTITLES_OFF], or null to let each stream decide. */
    fun setSubtitleLanguage(language: String?) = playerPreferences.setSubtitleLanguage(language)

    fun setAudioLanguage(language: String?) = playerPreferences.setAudioLanguage(language)

    // endregion

    // region Kids lock

    val hasKidsLock: StateFlow<Boolean> = appSettingsStore.hasKidsLock

    fun setKidsLock(pin: String) {
        appSettingsStore.setKidsLock(pin)
        _messages.tryEmit(context.getString(R.string.st_kids_lock_set))
    }

    fun clearKidsLock() {
        appSettingsStore.clearKidsLock()
        _messages.tryEmit(context.getString(R.string.st_kids_lock_removed))
    }

    // endregion

    // region Download storage

    /** Where downloads can go and where they are now; null until measured. */
    data class StorageState(
        val volumes: List<DownloadStorage.Volume>,
        val currentRoot: String,
        val downloadsBytes: Long,
        val chosenMissing: Boolean
    )

    private val _storage = MutableStateFlow<StorageState?>(null)
    val storage: StateFlow<StorageState?> = _storage.asStateFlow()

    /** Progress of a move between volumes, 0..1; null while none is running. */
    private val _storageMove = MutableStateFlow<Float?>(null)
    val storageMove: StateFlow<Float?> = _storageMove.asStateFlow()

    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = withContext(Dispatchers.IO) {
                StorageState(
                    volumes = downloadStorage.volumes(),
                    currentRoot = downloadStorage.currentRoot().absolutePath,
                    downloadsBytes = downloadStorage.downloadsSize(),
                    chosenMissing = downloadStorage.chosenVolumeMissing()
                )
            }
        }
    }

    /**
     * Moves every download to [volume], then restarts the app so the download cache opens there.
     * Downloads pause for the move and pick up again after the restart.
     */
    fun moveDownloads(volume: DownloadStorage.Volume) {
        if (_storageMove.value != null) return
        _storageMove.value = 0f
        viewModelScope.launch {
            downloadManager.pauseDownloads()
            val moved = runCatching {
                downloadStorage.moveTo(volume) { copied, total ->
                    _storageMove.value = if (total > 0) copied.toFloat() / total else 1f
                }
            }
            if (moved.isSuccess) {
                AppRestart.restart(context)
            } else {
                downloadManager.resumeDownloads()
                _storageMove.value = null
                _messages.tryEmit(context.getString(R.string.st_storage_move_failed))
                refreshStorage()
            }
        }
    }

    // endregion

    // region Home screen and app behaviour

    fun setStartScreen(screen: StartScreen) = appSettingsStore.update { it.copy(startScreen = screen) }

    fun setHomeRowShown(key: String, shown: Boolean) = appSettingsStore.update {
        it.copy(hiddenHomeRows = if (shown) it.hiddenHomeRows - key else it.hiddenHomeRows + key)
    }

    fun moveHomeRow(key: String, delta: Int) = appSettingsStore.update {
        it.copy(homeRowOrder = HomeRows.move(it.homeRowOrder, key, delta))
    }

    fun resetHomeRows() = appSettingsStore.update { it.copy(homeRowOrder = emptyList(), hiddenHomeRows = emptySet()) }

    fun setHaptics(enabled: Boolean) = appSettingsStore.update { it.copy(hapticsEnabled = enabled) }

    fun setPauseWatchHistory(paused: Boolean) = appSettingsStore.update { it.copy(pauseWatchHistory = paused) }

    fun setAutoDeleteWatched(mode: AutoDeleteWatched) = appSettingsStore.update { it.copy(autoDeleteWatched = mode) }

    fun setSourceSearchMode(mode: SourceSearchMode) = appSettingsStore.update { it.copy(sourceSearchMode = mode) }

    fun setVolumeBoostPercent(percent: Int) = appSettingsStore.setVolumeBoostPercent(percent)

    // endregion

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

    /** Artwork plus title info on disk; null until measured. */
    private val _cacheBytes = MutableStateFlow<Long?>(null)
    val cacheBytes: StateFlow<Long?> = _cacheBytes.asStateFlow()

    private val _crashCount = MutableStateFlow(0)
    val crashCount: StateFlow<Int> = _crashCount.asStateFlow()

    /** True while a backup, restore or diagnostics export is writing or reading a file. */
    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refreshCacheSize()
        viewModelScope.launch { _crashCount.value = withContext(Dispatchers.IO) { diagnostics.crashCount } }
    }

    fun exportBackup(uri: Uri) = runFileJob(failure = context.getString(R.string.st_could_not_save_backup)) {
        openOutput(uri).use { libraryBackup.export(it) }
        context.getString(R.string.st_backed_up)
    }

    fun restoreBackup(uri: Uri) = runFileJob(failure = context.getString(R.string.st_could_not_restore)) {
        val summary = openInput(uri).use { libraryBackup.restore(it) }
        // Restored settings may flip Wi-Fi-only or charging-only; the download manager has to hear about it.
        withContext(Dispatchers.Main) {
            downloadManager.requirements = downloadRequirements(appSettingsStore.current)
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

    fun setAmoledBlack(enabled: Boolean) = appSettingsStore.update { it.copy(amoledBlack = enabled) }

    fun setFloatingNavBar(floating: Boolean) = appSettingsStore.update {
        it.copy(navBarStyle = if (floating) NavBarStyle.FLOATING else NavBarStyle.DOCKED)
    }

    fun setDnsProvider(provider: DnsProvider) = appSettingsStore.update { it.copy(dnsProvider = provider) }

    fun setSeekStep(seconds: Int) = appSettingsStore.update { it.copy(seekStepSeconds = seconds) }

    fun setDefaultSpeed(speed: Float) = appSettingsStore.update { it.copy(defaultSpeed = speed) }

    fun setAutoPlayNext(enabled: Boolean) = appSettingsStore.update { it.copy(autoPlayNext = enabled) }
    fun setShowSkipButton(enabled: Boolean) = appSettingsStore.update { it.copy(showSkipButton = enabled) }

    fun setDownloadMaxHeight(height: Int) = appSettingsStore.update { it.copy(downloadMaxHeight = height) }
    fun setStreamMaxHeight(height: Int) = appSettingsStore.setStreamMaxHeight(height)

    fun setPipLeftAction(action: PipAction) = appSettingsStore.update { it.copy(pipLeftAction = action) }

    fun setPipRightAction(action: PipAction) = appSettingsStore.update { it.copy(pipRightAction = action) }

    fun setSubtitleDownloadLanguages(languages: List<String>) =
        appSettingsStore.update { it.copy(subtitleDownloadLanguages = languages.distinct()) }

    fun setSubtitleDownloadFromSites(enabled: Boolean) =
        appSettingsStore.update { it.copy(subtitleDownloadFromSites = enabled) }

    fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        appSettingsStore.update { it.copy(wifiOnlyDownloads = wifiOnly) }
        downloadManager.requirements = downloadRequirements(appSettingsStore.current)
    }

    fun setChargingOnlyDownloads(chargingOnly: Boolean) {
        appSettingsStore.update { it.copy(chargingOnlyDownloads = chargingOnly) }
        downloadManager.requirements = downloadRequirements(appSettingsStore.current)
    }

    fun setSmartDownloads(enabled: Boolean) {
        appSettingsStore.setSmartDownloads(enabled)
        if (enabled) smartDownloads.reconcileNow()
    }

    fun setSmartKeepAhead(count: Int) = appSettingsStore.setSmartKeepAhead(count)

    fun setKeepPlayingInBackground(enabled: Boolean) = appSettingsStore.setKeepPlayingInBackground(enabled)

    val isSmoothMotionSupported: Boolean = SmoothMotion.isSupported(context)
    fun setSmoothMotionEnabled(enabled: Boolean) = appSettingsStore.setSmoothMotionEnabled(enabled)
    fun setSmoothMotionMaxFps(fps: Int) = appSettingsStore.setSmoothMotionMaxFps(fps)
    fun setVideoShaders(shaders: VideoShaderCustomization) = appSettingsStore.setVideoShaders(shaders)
    fun setVideoGestures(gestures: VideoGestureCustomization) = appSettingsStore.setVideoGestures(gestures)

    fun setWarnBeforeMeteredStream(enabled: Boolean) = appSettingsStore.setWarnBeforeMeteredStream(enabled)

    fun setMeteredMaxHeight(height: Int) = appSettingsStore.setMeteredMaxHeight(height)


    /** Saving to the public Downloads folder needs a permission on Android 9 and older. */

    fun clearCache() {
        viewModelScope.launch {
            appCaches.clearAll()
            refreshCacheSize()
            _messages.tryEmit(context.getString(R.string.st_cache_cleared_all))
        }
    }

    fun setCacheSize(size: CacheSize) = appSettingsStore.setCacheSize(size)

    fun setPreloadArtwork(enabled: Boolean) = appSettingsStore.setPreloadArtwork(enabled)

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

    private fun refreshCacheSize() {
        viewModelScope.launch { _cacheBytes.value = appCaches.sizeBytes() }
    }

    /** False when the player in use has no shader stage; a look then needs a restart. */
    val shadersApplyNow: Boolean get() = playbackSession.shadersApplyNow
}
