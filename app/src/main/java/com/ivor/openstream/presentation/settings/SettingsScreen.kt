package com.ivor.openstream.presentation.settings

import android.app.Activity
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.ui.platform.LocalUriHandler
import com.ivor.openstream.data.playback.VolumeBooster
import com.ivor.openstream.data.settings.AutoDeleteWatched
import com.ivor.openstream.data.settings.CaptionStyleSettings
import com.ivor.openstream.data.settings.CaptionTextColor
import com.ivor.openstream.data.settings.HomeRows
import com.ivor.openstream.data.settings.PlayerPreferences
import com.ivor.openstream.data.settings.SourceSearchMode
import com.ivor.openstream.data.settings.StartScreen
import com.ivor.openstream.domain.model.StreamingService
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CallToAction
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.R
import com.ivor.openstream.data.backup.LibraryBackup
import com.ivor.openstream.data.settings.AppLocale
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.CacheSize
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.settings.NavBarStyle
import com.ivor.openstream.data.settings.PipAction
import com.ivor.openstream.data.settings.ThemeMode
import com.ivor.openstream.data.settings.VideoGestureCustomization
import com.ivor.openstream.presentation.components.isCompactWidth
import com.ivor.openstream.presentation.downloads.formatBytes

/**
 * Settings: a parent page of categories (Personalization, Playback...), each opening its own page;
 * see [SettingsLayout]. This function owns the state, the pickers and dialogs, and what every
 * category holds.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenMarketplace: () -> Unit,
    onOpenProfiles: () -> Unit = {},
    onOpenVideoShaders: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val cacheBytes by viewModel.cacheBytes.collectAsState()
    val hiddenTitleCount by viewModel.hiddenTitleCount.collectAsState()
    val crashCount by viewModel.crashCount.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()
    val dataUsed by viewModel.streamDataGuard.dataUsedBytes.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }
    var pickingSubtitleLanguages by rememberSaveable { mutableStateOf(false) }
    var pickingAppLanguage by rememberSaveable { mutableStateOf(false) }
    val captionStyle by viewModel.captionStyle.collectAsState()
    val subtitleLanguage by viewModel.subtitleLanguage.collectAsState()
    val audioLanguage by viewModel.audioLanguage.collectAsState()
    val hasKidsLock by viewModel.hasKidsLock.collectAsState()
    val storage by viewModel.storage.collectAsState()
    val storageMove by viewModel.storageMove.collectAsState()
    var pickingSubtitleLanguage by rememberSaveable { mutableStateOf(false) }
    var pickingAudioLanguage by rememberSaveable { mutableStateOf(false) }
    var editingKidsLock by rememberSaveable { mutableStateOf(false) }
    var pickingStorage by rememberSaveable { mutableStateOf(false) }
    var showingLicenses by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) { viewModel.refreshStorage() }

    if (pickingSubtitleLanguage) {
        LanguagePickerDialog(
            title = stringResource(R.string.st_preferred_subtitles),
            current = subtitleLanguage,
            allowOff = true,
            onSelect = {
                viewModel.setSubtitleLanguage(it)
                pickingSubtitleLanguage = false
            },
            onDismiss = { pickingSubtitleLanguage = false }
        )
    }

    if (pickingAudioLanguage) {
        LanguagePickerDialog(
            title = stringResource(R.string.st_preferred_audio),
            current = audioLanguage,
            allowOff = false,
            onSelect = {
                viewModel.setAudioLanguage(it)
                pickingAudioLanguage = false
            },
            onDismiss = { pickingAudioLanguage = false }
        )
    }

    if (editingKidsLock) {
        KidsLockDialog(
            hasLock = hasKidsLock,
            onSet = { pin ->
                viewModel.setKidsLock(pin)
                editingKidsLock = false
            },
            onRemove = {
                viewModel.clearKidsLock()
                editingKidsLock = false
            },
            onDismiss = { editingKidsLock = false }
        )
    }

    // Stays up for the whole move, whoever opened it; the app restarts when it is done.
    val storageState = storage
    if ((pickingStorage || storageMove != null) && storageState != null) {
        StorageLocationDialog(
            volumes = storageState.volumes,
            currentRoot = storageState.currentRoot,
            downloadsBytes = storageState.downloadsBytes,
            moveProgress = storageMove,
            onMove = viewModel::moveDownloads,
            onDismiss = { pickingStorage = false }
        )
    }

    if (showingLicenses) {
        LicensesSheet(onDismiss = { showingLicenses = false })
    }

    if (pickingAppLanguage) {
        AppLanguageDialog(
            current = appSettings.appLanguage,
            onSelect = { tag ->
                pickingAppLanguage = false
                viewModel.setAppLanguage(tag)
                (context as? Activity)?.let { AppLocale.setLanguage(it, tag) }
            },
            onDismiss = { pickingAppLanguage = false }
        )
    }

    if (pickingSubtitleLanguages) {
        SubtitleLanguagesSheet(
            selected = appSettings.subtitleDownloadLanguages,
            fromSites = appSettings.subtitleDownloadFromSites,
            onLanguagesChange = viewModel::setSubtitleDownloadLanguages,
            onFromSitesChange = viewModel::setSubtitleDownloadFromSites,
            onDismiss = { pickingSubtitleLanguages = false }
        )
    }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(LibraryBackup.MIME_TYPE)) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restoreBackup)
    }
    val exportDiagnostics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(viewModel::exportDiagnostics)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            icon = { Icon(Icons.Default.History, contentDescription = null) },
            title = { Text(stringResource(R.string.st_clear_history_confirm)) },
            text = { Text(stringResource(R.string.st_clear_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    viewModel.clearWatchHistory()
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    val entry = EntryFactory(on = stringResource(R.string.misc_on), off = stringResource(R.string.misc_off))
    val gestures = appSettings.videoGestures
    // AMOLED only changes dark surfaces, so the option hides when Light is forced. Only phones show
    // the bottom bar; wider windows use the side rail.
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val amoledAvailable = appSettings.themeMode != ThemeMode.LIGHT
    val navBarAvailable = isCompactWidth

    val categories = listOf(
        // How the app looks.
        SettingsCategory(
            key = "personalization",
            title = stringResource(R.string.st_cat_personalization),
            icon = Icons.Default.Palette,
            cluster = 0,
            groups = listOf(
                SettingsGroupSpec(
                    key = "look",
                    entries = listOfNotNull(
                        entry.choice(
                            key = "theme",
                            icon = Icons.Default.DarkMode,
                            title = stringResource(R.string.st_theme),
                            options = ThemeMode.entries.map { it to stringResource(it.labelRes) },
                            selected = appSettings.themeMode,
                            onSelect = viewModel::setThemeMode
                        ),
                        if (dynamicAvailable) {
                            entry.switch(
                                key = "dynamic-color",
                                icon = Icons.Default.AutoAwesome,
                                title = stringResource(R.string.st_dynamic_color),
                                summary = stringResource(R.string.st_dynamic_hint),
                                checked = appSettings.dynamicColor,
                                onCheckedChange = viewModel::setDynamicColor
                            )
                        } else {
                            null
                        },
                        if (amoledAvailable) {
                            entry.switch(
                                key = "amoled",
                                icon = Icons.Default.Contrast,
                                title = stringResource(R.string.st_amoled_black),
                                summary = stringResource(R.string.st_amoled_black_hint),
                                checked = appSettings.amoledBlack,
                                onCheckedChange = viewModel::setAmoledBlack
                            )
                        } else {
                            null
                        }
                    )
                ),
                SettingsGroupSpec(
                    key = "app",
                    entries = listOfNotNull(
                        entry.navigation(
                            key = "language",
                            icon = Icons.Default.Translate,
                            title = stringResource(R.string.st_language),
                            summary = appSettings.appLanguage?.let { AppLocale.autonym(it) }
                                ?: stringResource(R.string.st_language_system),
                            terms = AppLocale.supportedTags.map { AppLocale.autonym(it) },
                            onClick = { pickingAppLanguage = true }
                        ),
                        entry.switch(
                            key = "haptics",
                            icon = Icons.Default.Vibration,
                            title = stringResource(R.string.st_haptics),
                            summary = stringResource(R.string.st_haptics_hint),
                            checked = appSettings.hapticsEnabled,
                            onCheckedChange = viewModel::setHaptics
                        ),
                        if (navBarAvailable) {
                            entry.switch(
                                key = "floating-nav-bar",
                                icon = Icons.Default.CallToAction,
                                title = stringResource(R.string.st_floating_nav_bar),
                                summary = stringResource(R.string.st_floating_nav_bar_hint),
                                checked = appSettings.navBarStyle == NavBarStyle.FLOATING,
                                onCheckedChange = viewModel::setFloatingNavBar
                            )
                        } else {
                            null
                        }
                    )
                )
            )
        ),

        // What Home shows and where the app opens.
        SettingsCategory(
            key = "home",
            title = stringResource(R.string.st_cat_home),
            icon = Icons.Default.Dashboard,
            cluster = 0,
            groups = listOf(
                SettingsGroupSpec(
                    key = "start",
                    entries = listOf(
                        entry.choice(
                            key = "start-screen",
                            icon = Icons.Default.Launch,
                            title = stringResource(R.string.st_start_screen),
                            summary = stringResource(R.string.st_start_screen_hint),
                            options = StartScreen.entries.map { it to stringResource(startScreenLabel(it)) },
                            selected = appSettings.startScreen,
                            onSelect = viewModel::setStartScreen
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "home-rows",
                    title = stringResource(R.string.st_home_rows),
                    footer = stringResource(R.string.st_home_rows_footer),
                    entries = homeRowEntries(
                        order = HomeRows.ordered(appSettings.homeRowOrder),
                        hidden = appSettings.hiddenHomeRows,
                        onShownChange = viewModel::setHomeRowShown,
                        onMove = viewModel::moveHomeRow
                    )
                ),
                SettingsGroupSpec(
                    key = "home-reset",
                    entries = listOf(
                        entry.navigation(
                            key = "home-reset",
                            icon = Icons.Default.RestartAlt,
                            title = stringResource(R.string.st_home_reset),
                            summary = stringResource(R.string.st_home_reset_hint),
                            onClick = viewModel::resetHomeRows
                        )
                    )
                )
            )
        ),

        // What happens while something plays, and around it.
        SettingsCategory(
            key = "playback",
            title = stringResource(R.string.st_playback),
            icon = Icons.Default.PlayCircle,
            cluster = 1,
            groups = listOfNotNull(
                SettingsGroupSpec(
                    key = "playing",
                    entries = listOf(
                        entry.choice(
                            key = "default-speed",
                            icon = Icons.Default.Speed,
                            title = stringResource(R.string.sheet_default_speed),
                            options = AppSettings.DEFAULT_SPEEDS.map { it to "${formatSpeed(it)}×" },
                            selected = appSettings.defaultSpeed,
                            onSelect = viewModel::setDefaultSpeed
                        ),
                        entry.choice(
                            key = "volume-boost",
                            icon = Icons.Default.VolumeUp,
                            title = stringResource(R.string.sheet_volume_boost),
                            summary = stringResource(R.string.st_volume_boost_hint),
                            options = VolumeBooster.BOOST_PRESETS.map {
                                it to if (it == 100) stringResource(R.string.speed_normal) else "$it%"
                            },
                            selected = appSettings.volumeBoostPercent,
                            onSelect = viewModel::setVolumeBoostPercent
                        ),
                        entry.switch(
                            key = "autoplay-next",
                            icon = Icons.Default.SkipNext,
                            title = stringResource(R.string.sheet_autoplay_next),
                            summary = stringResource(R.string.player_countdown_next),
                            checked = appSettings.autoPlayNext,
                            onCheckedChange = viewModel::setAutoPlayNext
                        ),
                        entry.switch(
                            key = "skip-button",
                            icon = Icons.Default.FastForward,
                            title = stringResource(R.string.sheet_skip_button),
                            summary = stringResource(R.string.player_offer_skip),
                            checked = appSettings.showSkipButton,
                            onCheckedChange = viewModel::setShowSkipButton
                        ),
                        entry.switch(
                            key = "background-playback",
                            icon = Icons.Default.NotificationsActive,
                            title = stringResource(R.string.st_background_playback),
                            summary = stringResource(R.string.st_background_playback_hint),
                            checked = appSettings.keepPlayingInBackground,
                            onCheckedChange = viewModel::setKeepPlayingInBackground
                        )
                    )
                ),
                if (viewModel.isSmoothMotionSupported) {
                    SettingsGroupSpec(
                        key = "smooth-motion",
                        title = stringResource(R.string.st_smooth_motion),
                        footer = stringResource(R.string.st_smooth_motion_footer),
                        entries = listOfNotNull(
                            entry.switch(
                                key = "smooth-motion",
                                icon = Icons.Default.AutoAwesome,
                                title = stringResource(R.string.st_smooth_motion),
                                summary = stringResource(R.string.st_smooth_motion_hint),
                                checked = appSettings.smoothMotionEnabled,
                                onCheckedChange = viewModel::setSmoothMotionEnabled
                            ),
                            if (appSettings.smoothMotionEnabled) {
                                entry.choice(
                                    key = "smooth-motion-fps",
                                    icon = Icons.Default.Speed,
                                    title = stringResource(R.string.st_smooth_motion_max_fps),
                                    summary = stringResource(R.string.st_smooth_motion_max_fps_hint),
                                    options = AppSettings.SMOOTH_MOTION_FPS_OPTIONS.map {
                                        it to stringResource(R.string.st_smooth_motion_fps_val, it)
                                    },
                                    selected = appSettings.smoothMotionMaxFps,
                                    onSelect = viewModel::setSmoothMotionMaxFps
                                )
                            } else {
                                null
                            }
                        )
                    )
                } else {
                    null
                },
                SettingsGroupSpec(
                    key = "pip",
                    title = stringResource(R.string.st_group_pip),
                    footer = stringResource(R.string.st_pip_footer),
                    entries = listOf(
                        entry.choice(
                            key = "pip-left",
                            icon = Icons.Default.PictureInPictureAlt,
                            title = stringResource(R.string.sheet_left_button),
                            options = PipAction.entries.map { it to stringResource(it.labelRes) },
                            selected = appSettings.pipLeftAction,
                            onSelect = viewModel::setPipLeftAction
                        ),
                        entry.choice(
                            key = "pip-right",
                            icon = Icons.Default.PictureInPictureAlt,
                            title = stringResource(R.string.sheet_right_button),
                            options = PipAction.entries.map { it to stringResource(it.labelRes) },
                            selected = appSettings.pipRightAction,
                            onSelect = viewModel::setPipRightAction
                        )
                    )
                )
            )
        ),

        // What every video starts with: caption look, and the languages picked first.
        SettingsCategory(
            key = "subtitles",
            title = stringResource(R.string.st_cat_subtitles),
            icon = Icons.Default.ClosedCaption,
            cluster = 1,
            groups = listOf(
                SettingsGroupSpec(
                    key = "languages",
                    title = stringResource(R.string.st_group_languages),
                    entries = listOf(
                        entry.navigation(
                            key = "subtitle-language",
                            icon = Icons.Default.Subtitles,
                            title = stringResource(R.string.st_preferred_subtitles),
                            summary = when (subtitleLanguage) {
                                null -> stringResource(R.string.st_lang_auto)
                                PlayerPreferences.SUBTITLES_OFF -> stringResource(R.string.misc_off)
                                else -> languageName(subtitleLanguage.orEmpty())
                            },
                            terms = listOf(stringResource(R.string.st_preferred_subtitles_hint)),
                            onClick = { pickingSubtitleLanguage = true }
                        ),
                        entry.navigation(
                            key = "audio-language",
                            icon = Icons.Default.RecordVoiceOver,
                            title = stringResource(R.string.st_preferred_audio),
                            summary = audioLanguage?.let(::languageName) ?: stringResource(R.string.st_lang_auto),
                            terms = listOf(stringResource(R.string.st_preferred_audio_hint), "dub"),
                            onClick = { pickingAudioLanguage = true }
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "caption-style",
                    title = stringResource(R.string.st_group_caption_style),
                    entries = captionEntries(
                        style = captionStyle,
                        onChange = viewModel::setCaptionStyle,
                        entry = entry
                    )
                )
            )
        ),

        // The player's screen: its look, and what touches and holds on it do.
        SettingsCategory(
            key = "player",
            title = stringResource(R.string.st_cat_player),
            icon = Icons.Default.Gesture,
            cluster = 1,
            groups = listOf(
                SettingsGroupSpec(
                    key = "look",
                    entries = listOf(
                        entry.navigation(
                            key = "video-shaders",
                            icon = Icons.Default.Palette,
                            title = stringResource(R.string.sp_video_shaders),
                            summary = stringResource(
                                if (appSettings.videoShaders.masterEnabled) {
                                    R.string.sp_video_shaders_sub_on
                                } else {
                                    R.string.sp_video_shaders_sub_off
                                }
                            ),
                            onClick = onOpenVideoShaders
                        ),
                        entry.choice(
                            key = "controls-hide",
                            icon = Icons.Default.Visibility,
                            title = stringResource(R.string.cz_video_controls_hide),
                            summary = stringResource(R.string.cz_video_controls_hide_sub),
                            options = VideoGestureCustomization.CONTROLS_HIDE_SECONDS.map {
                                it to stringResource(R.string.cz_video_controls_hide_value, it)
                            },
                            selected = gestures.controlsHideSeconds,
                            onSelect = { viewModel.setVideoGestures(gestures.copy(controlsHideSeconds = it)) }
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "gestures",
                    title = stringResource(R.string.st_group_gestures),
                    footer = stringResource(R.string.cz_video_gestures_note),
                    entries = listOfNotNull(
                        entry.choice(
                            key = "seek-step",
                            icon = Icons.Default.Forward10,
                            title = stringResource(R.string.sheet_seek_step),
                            summary = stringResource(R.string.player_double_tap),
                            options = AppSettings.SEEK_STEPS.map { it to "${it}s" },
                            selected = appSettings.seekStepSeconds,
                            onSelect = viewModel::setSeekStep
                        ),
                        entry.switch(
                            key = "hold-speed",
                            icon = Icons.Default.FastForward,
                            title = stringResource(R.string.cz_video_hold_speed),
                            summary = stringResource(R.string.cz_video_hold_speed_sub),
                            checked = gestures.holdToSpeedUp,
                            onCheckedChange = { viewModel.setVideoGestures(gestures.copy(holdToSpeedUp = it)) }
                        ),
                        if (gestures.holdToSpeedUp) {
                            entry.choice(
                                key = "hold-rate",
                                icon = Icons.Default.Speed,
                                title = stringResource(R.string.cz_video_hold_rate),
                                summary = stringResource(R.string.cz_video_hold_rate_sub),
                                options = VideoGestureCustomization.HOLD_SPEEDS.map {
                                    it to stringResource(R.string.cz_video_hold_rate_value, it.toInt())
                                },
                                selected = gestures.holdSpeed,
                                onSelect = { viewModel.setVideoGestures(gestures.copy(holdSpeed = it)) }
                            )
                        } else {
                            null
                        },
                        entry.switch(
                            key = "brightness-swipe",
                            icon = Icons.Default.BrightnessMedium,
                            title = stringResource(R.string.cz_video_brightness),
                            summary = stringResource(R.string.cz_video_brightness_sub),
                            checked = gestures.brightnessSwipe,
                            onCheckedChange = { viewModel.setVideoGestures(gestures.copy(brightnessSwipe = it)) }
                        ),
                        entry.switch(
                            key = "volume-swipe",
                            icon = Icons.Default.VolumeUp,
                            title = stringResource(R.string.cz_video_volume),
                            summary = stringResource(R.string.cz_video_volume_sub),
                            checked = gestures.volumeSwipe,
                            onCheckedChange = { viewModel.setVideoGestures(gestures.copy(volumeSwipe = it)) }
                        )
                    )
                )
            )
        ),

        // How good the picture is, and what it costs on mobile data.
        SettingsCategory(
            key = "quality",
            title = stringResource(R.string.st_section_quality),
            icon = Icons.Default.HighQuality,
            cluster = 1,
            groups = listOf(
                SettingsGroupSpec(
                    key = "stream-quality",
                    entries = listOf(
                        entry.choice(
                            key = "stream-cap",
                            icon = Icons.Default.HighQuality,
                            title = stringResource(R.string.st_stream_cap),
                            summary = stringResource(R.string.st_stream_cap_hint),
                            options = AppSettings.STREAM_CAPS.map {
                                it to when (it) {
                                    0 -> stringResource(R.string.st_stream_cap_none)
                                    2160 -> stringResource(R.string.q_2160p)
                                    else -> "${it}p"
                                }
                            },
                            selected = appSettings.streamMaxHeight,
                            onSelect = viewModel::setStreamMaxHeight
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "mobile-data",
                    title = stringResource(R.string.st_data_mobile),
                    footer = stringResource(R.string.st_data_mobile_used, formatBytes(dataUsed)),
                    entries = listOf(
                        entry.switch(
                            key = "metered-warn",
                            icon = Icons.Default.DataUsage,
                            title = stringResource(R.string.st_data_mobile_warn),
                            summary = stringResource(R.string.st_data_mobile_warn_hint),
                            checked = appSettings.warnBeforeMeteredStream,
                            onCheckedChange = viewModel::setWarnBeforeMeteredStream
                        ),
                        entry.choice(
                            key = "metered-cap",
                            icon = Icons.Default.HighQuality,
                            title = stringResource(R.string.st_data_mobile_cap),
                            summary = stringResource(R.string.st_data_mobile_cap_hint),
                            options = AppSettings.METERED_CAPS.map {
                                it to if (it == 0) stringResource(R.string.st_data_mobile_remember_none) else "${it}p"
                            },
                            selected = appSettings.meteredMaxHeight,
                            onSelect = viewModel::setMeteredMaxHeight
                        )
                    )
                )
            )
        ),

        SettingsCategory(
            key = "downloads",
            title = stringResource(R.string.dl_title),
            icon = Icons.Default.Download,
            cluster = 2,
            groups = listOf(
                SettingsGroupSpec(
                    key = "downloads",
                    entries = listOf(
                        entry.choice(
                            key = "download-quality",
                            icon = Icons.Default.HighQuality,
                            title = stringResource(R.string.sheet_quality),
                            summary = stringResource(R.string.dl_highest_quality),
                            options = AppSettings.DOWNLOAD_HEIGHTS.map { it to "${it}p" },
                            selected = appSettings.downloadMaxHeight,
                            onSelect = viewModel::setDownloadMaxHeight
                        ),
                        entry.navigation(
                            key = "download-subtitles",
                            icon = Icons.Default.ClosedCaption,
                            title = stringResource(R.string.player_subtitles),
                            summary = stringResource(
                                R.string.dl_saved_suffix,
                                subtitleLanguagesSummary(appSettings.subtitleDownloadLanguages)
                            ),
                            onClick = { pickingSubtitleLanguages = true }
                        ),
                        entry.navigation(
                            key = "storage-location",
                            icon = if (storage?.volumes?.firstOrNull { it.root.absolutePath == storage?.currentRoot }?.removable == true) {
                                Icons.Default.SdCard
                            } else {
                                Icons.Default.Smartphone
                            },
                            title = stringResource(R.string.st_storage_location),
                            summary = storageSummary(storage),
                            terms = listOf(stringResource(R.string.st_storage_sd), stringResource(R.string.st_storage_internal)),
                            enabled = storageMove == null,
                            onClick = {
                                viewModel.refreshStorage()
                                pickingStorage = true
                            }
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "downloading",
                    title = stringResource(R.string.st_group_downloading),
                    entries = listOf(
                        entry.switch(
                            key = "wifi-only",
                            icon = Icons.Default.Wifi,
                            title = stringResource(R.string.dl_wifi_only),
                            summary = stringResource(R.string.action_wait_for_unmetered),
                            checked = appSettings.wifiOnlyDownloads,
                            onCheckedChange = viewModel::setWifiOnlyDownloads
                        ),
                        entry.switch(
                            key = "charging-only",
                            icon = Icons.Default.BatteryChargingFull,
                            title = stringResource(R.string.st_charging_only),
                            summary = stringResource(R.string.st_charging_only_hint),
                            checked = appSettings.chargingOnlyDownloads,
                            onCheckedChange = viewModel::setChargingOnlyDownloads
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "smart-downloads",
                    title = stringResource(R.string.st_smart_downloads),
                    entries = listOf(
                        entry.switch(
                            key = "smart-downloads",
                            icon = Icons.Default.Download,
                            title = stringResource(R.string.st_smart_downloads),
                            summary = stringResource(R.string.st_smart_hint),
                            checked = appSettings.smartDownloads,
                            onCheckedChange = viewModel::setSmartDownloads
                        ),
                        entry.choice(
                            key = "smart-ahead",
                            icon = Icons.Default.HighQuality,
                            title = stringResource(R.string.st_smart_ahead),
                            options = AppSettings.SMART_AHEAD_OPTIONS.map { it to it.toString() },
                            selected = appSettings.smartKeepAhead,
                            onSelect = viewModel::setSmartKeepAhead
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "cleanup",
                    title = stringResource(R.string.st_group_cleanup),
                    entries = listOf(
                        entry.choice(
                            key = "auto-delete",
                            icon = Icons.Default.AutoDelete,
                            title = stringResource(R.string.st_auto_delete),
                            summary = stringResource(R.string.st_auto_delete_hint),
                            options = AutoDeleteWatched.entries.map { it to stringResource(autoDeleteLabel(it)) },
                            selected = appSettings.autoDeleteWatched,
                            onSelect = viewModel::setAutoDeleteWatched
                        )
                    )
                )
            )
        ),

        SettingsCategory(
            key = "sources",
            title = stringResource(R.string.st_group_sources),
            icon = Icons.Default.Storefront,
            cluster = 2,
            groups = listOf(
                SettingsGroupSpec(
                    key = "sources",
                    footer = stringResource(R.string.st_extensions_data_only),
                    entries = listOfNotNull(
                        entry.navigation(
                            key = "marketplace",
                            icon = Icons.Default.Storefront,
                            title = stringResource(R.string.st_extension_marketplace),
                            summary = stringResource(
                                R.string.st_source_summary,
                                state.enabledCount, state.installedCount, state.availableCount
                            ),
                            onClick = onOpenMarketplace
                        ),
                        entry.navigation(
                            key = "repositories",
                            icon = Icons.Default.Public,
                            title = stringResource(R.string.st_repositories),
                            summary = stringResource(R.string.mk_repo_connected, state.repoCount),
                            onClick = onOpenMarketplace
                        ),
                        if (state.updateCount > 0) {
                            entry.navigation(
                                key = "extension-updates",
                                icon = Icons.Default.Update,
                                title = stringResource(R.string.st_extension_updates),
                                summary = stringResource(R.string.st_ready_install),
                                badge = state.updateCount.toString(),
                                onClick = onOpenMarketplace
                            )
                        } else {
                            null
                        }
                    )
                ),
                SettingsGroupSpec(
                    key = "search-order",
                    title = stringResource(R.string.sheet_search_order),
                    entries = listOf(
                        entry.choice(
                            key = "search-order",
                            icon = Icons.Default.Sort,
                            title = stringResource(R.string.sheet_search_order),
                            summary = stringResource(
                                when (appSettings.sourceSearchMode) {
                                    SourceSearchMode.ALL_AT_ONCE -> R.string.player_search_all_at_once
                                    SourceSearchMode.IN_ORDER -> R.string.player_search_in_order
                                }
                            ),
                            options = SourceSearchMode.entries.map { it to stringResource(it.labelRes) },
                            selected = appSettings.sourceSearchMode,
                            onSelect = viewModel::setSourceSearchMode
                        )
                    )
                )
            )
        ),

        SettingsCategory(
            key = "network",
            title = stringResource(R.string.st_section_network),
            icon = Icons.Default.Dns,
            cluster = 2,
            groups = listOf(
                SettingsGroupSpec(
                    key = "dns",
                    title = stringResource(R.string.st_dns),
                    footer = stringResource(R.string.st_dns_private_hint),
                    entries = DnsProvider.entries.map { provider ->
                        entry.radio(
                            key = "dns-${provider.name.lowercase()}",
                            icon = if (provider == DnsProvider.SYSTEM) Icons.Default.Dns else Icons.Default.Shield,
                            title = stringResource(provider.labelRes),
                            summary = dnsSupportingText(provider),
                            selected = appSettings.dnsProvider == provider,
                            onClick = { viewModel.setDnsProvider(provider) }
                        )
                    }
                )
            )
        ),

        SettingsCategory(
            key = "notifications",
            title = stringResource(R.string.st_group_notifications),
            icon = Icons.Default.Notifications,
            cluster = 3,
            groups = listOf(
                SettingsGroupSpec(
                    key = "notifications",
                    entries = listOf(
                        entry.switch(
                            key = "episode-notifications",
                            icon = Icons.Default.Notifications,
                            title = stringResource(R.string.st_episode_notify),
                            summary = stringResource(R.string.st_episode_notify_hint),
                            checked = appSettings.episodeNotifications,
                            onCheckedChange = viewModel::setEpisodeNotifications
                        )
                    )
                )
            )
        ),

        // Who is watching, and what they have kept.
        SettingsCategory(
            key = "library",
            title = stringResource(R.string.st_cat_library),
            icon = Icons.Default.VideoLibrary,
            cluster = 3,
            busy = isWorking,
            groups = listOf(
                SettingsGroupSpec(
                    key = "profiles",
                    entries = listOf(
                        entry.navigation(
                            key = "profiles",
                            icon = Icons.Default.People,
                            title = stringResource(R.string.st_profiles),
                            summary = stringResource(R.string.st_profiles_hint),
                            onClick = onOpenProfiles
                        ),
                        entry.navigation(
                            key = "kids-lock",
                            icon = Icons.Default.Lock,
                            title = stringResource(R.string.st_kids_lock),
                            summary = stringResource(if (hasKidsLock) R.string.st_kids_lock_hint_on else R.string.st_kids_lock_hint_off),
                            terms = listOf(stringResource(R.string.pf_kids), stringResource(R.string.pin_title)),
                            onClick = { editingKidsLock = true }
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "backup",
                    title = stringResource(R.string.st_library),
                    entries = listOf(
                        entry.navigation(
                            key = "backup",
                            icon = Icons.Default.Backup,
                            title = stringResource(R.string.st_backup),
                            summary = stringResource(R.string.st_backup_hint),
                            enabled = !isWorking,
                            onClick = { exportBackup.launch("openstream-backup-${fileDate()}.json") }
                        ),
                        entry.navigation(
                            key = "restore",
                            icon = Icons.Default.Restore,
                            title = stringResource(R.string.st_restore),
                            summary = stringResource(R.string.st_merge_note),
                            enabled = !isWorking,
                            onClick = {
                                restoreBackup.launch(arrayOf(LibraryBackup.MIME_TYPE, "text/plain", "application/octet-stream"))
                            }
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "history",
                    title = stringResource(R.string.st_history),
                    entries = listOfNotNull(
                        entry.switch(
                            key = "pause-history",
                            icon = Icons.Default.HistoryToggleOff,
                            title = stringResource(R.string.st_pause_history),
                            summary = stringResource(R.string.st_pause_history_hint),
                            checked = appSettings.pauseWatchHistory,
                            onCheckedChange = viewModel::setPauseWatchHistory
                        ),
                        if (hiddenTitleCount > 0) {
                            entry.navigation(
                                key = "show-hidden",
                                icon = Icons.Default.VisibilityOff,
                                title = stringResource(R.string.st_show_hidden),
                                summary = stringResource(R.string.count_hidden, hiddenTitleCount),
                                onClick = viewModel::unhideAllTitles
                            )
                        } else {
                            null
                        },
                        entry.navigation(
                            key = "clear-history",
                            icon = Icons.Default.History,
                            title = stringResource(R.string.st_clear_history),
                            summary = stringResource(R.string.pf_this_history),
                            onClick = { confirmClearHistory = true }
                        )
                    )
                )
            )
        ),

        SettingsCategory(
            key = "storage",
            title = stringResource(R.string.tv_st_storage),
            icon = Icons.Default.Storage,
            cluster = 4,
            groups = listOf(
                SettingsGroupSpec(
                    key = "storage",
                    entries = listOf(
                        entry.navigation(
                            key = "clear-cache",
                            icon = Icons.Default.Image,
                            title = stringResource(R.string.st_clear_all_cache),
                            summary = cacheBytes?.let {
                                stringResource(R.string.st_cache_used, Formatter.formatShortFileSize(context, it))
                            } ?: stringResource(R.string.st_cache_hint),
                            onClick = viewModel::clearCache
                        ),
                        entry.choice(
                            key = "cache-limit",
                            icon = Icons.Default.Storage,
                            title = stringResource(R.string.st_cache_limit),
                            summary = stringResource(R.string.st_cache_limit_hint),
                            options = CacheSize.entries.map { it to Formatter.formatShortFileSize(context, it.totalBytes) },
                            selected = appSettings.cacheSize,
                            onSelect = viewModel::setCacheSize
                        ),
                        entry.switch(
                            key = "preload-artwork",
                            icon = Icons.Default.Wifi,
                            title = stringResource(R.string.st_preload_artwork),
                            summary = stringResource(R.string.st_preload_artwork_hint),
                            checked = appSettings.preloadArtwork,
                            onCheckedChange = viewModel::setPreloadArtwork
                        )
                    )
                )
            )
        ),

        SettingsCategory(
            key = "about",
            title = stringResource(R.string.st_cat_about),
            icon = Icons.Default.Info,
            cluster = 4,
            groups = listOf(
                SettingsGroupSpec(
                    key = "support",
                    entries = listOf(
                        entry.navigation(
                            key = "check-updates",
                            icon = Icons.Default.SystemUpdate,
                            title = stringResource(R.string.action_check_for_updates),
                            summary = stringResource(R.string.st_check_updates_hint, BuildConfig.VERSION_NAME),
                            onClick = onOpenUpdates
                        ),
                        entry.navigation(
                            key = "report-problem",
                            icon = Icons.Default.Forum,
                            title = stringResource(R.string.st_report_problem),
                            summary = stringResource(R.string.st_report_problem_hint),
                            terms = listOf("Telegram", "bug", "help"),
                            onClick = { runCatching { uriHandler.openUri(COMMUNITY_URL) } }
                        ),
                        entry.navigation(
                            key = "licenses",
                            icon = Icons.Default.Gavel,
                            title = stringResource(R.string.st_licenses),
                            summary = stringResource(R.string.st_licenses_hint),
                            onClick = { showingLicenses = true }
                        )
                    )
                ),
                SettingsGroupSpec(
                    key = "diagnostics",
                    title = stringResource(R.string.st_group_support),
                    footer = stringResource(R.string.up_version_build, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    entries = listOfNotNull(
                        entry.navigation(
                            key = "export-diagnostics",
                            icon = Icons.Default.BugReport,
                            title = stringResource(R.string.st_export_diag),
                            summary = when (crashCount) {
                                0 -> stringResource(R.string.st_diag_hint)
                                1 -> stringResource(R.string.st_crash_include, 1)
                                else -> stringResource(R.string.st_crash_include_other, crashCount)
                            },
                            enabled = !isWorking,
                            onClick = {
                                exportDiagnostics.launch("openstream-diagnostics-${fileDate(withTime = true)}.txt")
                            }
                        ),
                        if (crashCount > 0) {
                            entry.navigation(
                                key = "clear-crashes",
                                icon = Icons.Default.DeleteSweep,
                                title = stringResource(R.string.st_clear_crash),
                                summary = stringResource(R.string.dl_stored_only_device),
                                onClick = viewModel::clearCrashReports
                            )
                        } else {
                            null
                        }
                    )
                )
            )
        )
    )

    SettingsLayout(
        categories = categories,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick
    )
}

/** Builds [SettingEntry]s for the usual kinds of row; [on] and [off] describe a switch's state. */
private class EntryFactory(private val on: String, private val off: String) {

    fun navigation(
        key: String,
        icon: ImageVector,
        title: String,
        summary: String,
        onClick: () -> Unit,
        enabled: Boolean = true,
        badge: String? = null,
        terms: List<String> = emptyList()
    ) = SettingEntry(key, icon, title, summary, terms = terms) { index, count, modifier, highlight ->
        NavigationRow(
            index = index, count = count, icon = icon, title = title, supporting = summary,
            onClick = onClick, modifier = modifier, enabled = enabled, badge = badge, highlight = highlight
        )
    }

    fun switch(
        key: String,
        icon: ImageVector,
        title: String,
        summary: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) = SettingEntry(key, icon, title, summary, value = if (checked) on else off) { index, count, modifier, highlight ->
        SwitchRow(
            index = index, count = count, icon = icon, title = title, supporting = summary,
            checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, highlight = highlight
        )
    }

    fun radio(
        key: String,
        icon: ImageVector,
        title: String,
        summary: String,
        selected: Boolean,
        onClick: () -> Unit
    ) = SettingEntry(key, icon, title, summary, value = if (selected) on else null) { index, count, modifier, highlight ->
        RadioRow(
            index = index, count = count, icon = icon, title = title, supporting = summary,
            selected = selected, onClick = onClick, modifier = modifier, highlight = highlight
        )
    }

    /** [options] pairs each choice with its label, already in words. */
    fun <T> choice(
        key: String,
        icon: ImageVector,
        title: String,
        options: List<Pair<T, String>>,
        selected: T,
        onSelect: (T) -> Unit,
        summary: String? = null
    ): SettingEntry {
        val labels = options.toMap()
        return SettingEntry(
            key = key,
            icon = icon,
            title = title,
            summary = summary,
            value = labels[selected],
            terms = options.map { it.second }
        ) { index, count, modifier, highlight ->
            ChoiceRow(
                index = index, count = count, icon = icon, title = title,
                options = options.map { it.first }, selected = selected,
                label = { labels[it].orEmpty() }, onSelect = onSelect,
                modifier = modifier, supporting = summary, highlight = highlight
            )
        }
    }
}

@Composable
private fun dnsSupportingText(provider: DnsProvider): String = when (provider) {
    DnsProvider.SYSTEM -> stringResource(R.string.st_dns_system)
    DnsProvider.ADGUARD -> stringResource(R.string.st_dns_unfiltered)
    DnsProvider.CLOUDFLARE -> "1.1.1.1"
    DnsProvider.GOOGLE -> "8.8.8.8"
}

/** App language picker: system default plus every language the app is translated into. */
@Composable
private fun AppLanguageDialog(
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.st_language)) },
        text = {
            LazyColumn(Modifier.selectableGroup()) {
                item(key = "system") {
                    RadioRow(
                        index = 0,
                        count = AppLocale.supportedTags.size + 1,
                        icon = Icons.Default.Translate,
                        title = stringResource(R.string.st_language_system),
                        supporting = null,
                        selected = current == null,
                        onClick = { onSelect(null) }
                    )
                }
                AppLocale.supportedTags.forEachIndexed { i, tag ->
                    item(key = tag) {
                        RadioRow(
                            index = i + 1,
                            count = AppLocale.supportedTags.size + 1,
                            icon = Icons.Default.Translate,
                            title = AppLocale.autonym(tag),
                            supporting = null,
                            selected = current == tag,
                            onClick = { onSelect(tag) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Date for suggested file names, e.g. 2026-09-27 or 2026-09-27-1715. */
private fun fileDate(withTime: Boolean = false): String =
    java.text.SimpleDateFormat(if (withTime) "yyyy-MM-dd-HHmm" else "yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date())

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')

/** The OpenStream community, where problems are reported and answered. */
private const val COMMUNITY_URL = "https://t.me/ivorisnoob_chat"

private fun startScreenLabel(screen: StartScreen): Int = when (screen) {
    StartScreen.HOME -> R.string.nav_home
    StartScreen.SEARCH -> R.string.nav_search
    StartScreen.SAVED -> R.string.nav_saved
    StartScreen.DOWNLOADS -> R.string.nav_downloads
    StartScreen.HISTORY -> R.string.nav_history
}

private fun autoDeleteLabel(mode: AutoDeleteWatched): Int = when (mode) {
    AutoDeleteWatched.OFF -> R.string.misc_off
    AutoDeleteWatched.AFTER_WATCHING -> R.string.st_auto_delete_now
    AutoDeleteWatched.AFTER_DAY -> R.string.st_auto_delete_day
    AutoDeleteWatched.AFTER_WEEK -> R.string.st_auto_delete_week
}

/** Where downloads are now, or a warning when that SD card is out. */
@Composable
private fun storageSummary(storage: SettingsViewModel.StorageState?): String {
    val context = LocalContext.current
    storage ?: return stringResource(R.string.st_storage_internal)
    if (storage.chosenMissing) return stringResource(R.string.st_storage_missing)
    val volume = storage.volumes.firstOrNull { it.root.absolutePath == storage.currentRoot }
    val name = stringResource(if (volume?.removable == true) R.string.st_storage_sd else R.string.st_storage_internal)
    return volume?.let {
        "$name · " + stringResource(R.string.st_storage_free, Formatter.formatShortFileSize(context, it.freeBytes), Formatter.formatShortFileSize(context, it.totalBytes))
    } ?: name
}

/** One entry per Home row, in [order], each with its switch and arrows. */
@Composable
private fun homeRowEntries(
    order: List<String>,
    hidden: Set<String>,
    onShownChange: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit
): List<SettingEntry> {
    val on = stringResource(R.string.misc_on)
    val off = stringResource(R.string.misc_off)
    return order.mapIndexed { position, key ->
        val (icon, title) = homeRowLabel(key)
        val shown = key !in hidden
        SettingEntry(
            key = "home-row:$key",
            icon = icon,
            title = title,
            summary = null,
            value = if (shown) on else off
        ) { index, count, modifier, highlight ->
            HomeRowItem(
                index = index,
                count = count,
                icon = icon,
                title = title,
                shown = shown,
                canMoveUp = position > 0,
                canMoveDown = position < order.lastIndex,
                onShownChange = { onShownChange(key, it) },
                onMove = { delta -> onMove(key, delta) },
                modifier = modifier,
                highlight = highlight
            )
        }
    }
}

@Composable
private fun homeRowLabel(key: String): Pair<ImageVector, String> = when (key) {
    HomeRows.CONTINUE_WATCHING -> Icons.Default.History to stringResource(R.string.home_continue_watching)
    HomeRows.TOP_MATCHES -> Icons.Default.AutoAwesome to stringResource(R.string.match_rail)
    HomeRows.TOP10 -> Icons.Default.EmojiEvents to stringResource(R.string.home_top10_week)
    HomeRows.NEW_EPISODES -> Icons.Default.NewReleases to stringResource(R.string.home_new_episodes_week)
    HomeRows.POPULAR_MOVIES -> Icons.Default.Movie to stringResource(R.string.home_popular_movies)
    HomeRows.POPULAR_SERIES -> Icons.Default.Tv to stringResource(R.string.home_popular_series)
    HomeRows.ANIME -> Icons.Default.Animation to stringResource(R.string.home_trending_anime)
    HomeRows.TOP_RATED -> Icons.Default.Star to stringResource(R.string.home_critically_acclaimed)
    HomeRows.ANIME_MOVIES -> Icons.Default.Theaters to stringResource(R.string.home_anime_movies)
    else -> {
        val service = StreamingService.entries.firstOrNull { HomeRows.serviceKey(it) == key }
        Icons.Default.Subscriptions to (
            service?.let { stringResource(R.string.home_popular_on_service, it.displayName) } ?: key
        )
    }
}

/** The caption look: a live preview, then size, plate, colour and position, and a reset. */
@Composable
private fun captionEntries(
    style: CaptionStyleSettings,
    onChange: (CaptionStyleSettings) -> Unit,
    entry: EntryFactory
): List<SettingEntry> {
    val sizeTitle = stringResource(R.string.st_caption_size)
    val backgroundTitle = stringResource(R.string.st_caption_background)
    val positionTitle = stringResource(R.string.st_caption_position)
    val sizeLabel = stringResource(R.string.st_caption_size_value, style.textSizeSp.toInt())
    val backgroundLabel = stringResource(R.string.st_caption_background_value, (style.backgroundOpacity * 100).toInt())
    val raised = (style.verticalOffset * 100).toInt()
    val positionLabel = if (raised == 0) {
        stringResource(R.string.sheet_default)
    } else {
        stringResource(R.string.sheet_caption_position_raised, raised)
    }
    return listOf(
        SettingEntry(
            key = "caption-preview",
            icon = Icons.Default.ClosedCaption,
            title = stringResource(R.string.sheet_caption_style),
            summary = stringResource(R.string.st_caption_preview)
        ) { index, count, modifier, highlight ->
            CaptionPreviewRow(style, index, count, modifier, highlight)
        },
        SettingEntry(key = "caption-size", icon = Icons.Default.FormatSize, title = sizeTitle, summary = null, value = sizeLabel) { index, count, modifier, highlight ->
            SliderRow(
                index = index, count = count, icon = Icons.Default.FormatSize, title = sizeTitle, valueLabel = sizeLabel,
                value = style.textSizeSp,
                range = CaptionStyleSettings.MIN_TEXT_SIZE_SP..CaptionStyleSettings.MAX_TEXT_SIZE_SP,
                steps = ((CaptionStyleSettings.MAX_TEXT_SIZE_SP - CaptionStyleSettings.MIN_TEXT_SIZE_SP) / 2f).toInt() - 1,
                onChange = { onChange(style.copy(textSizeSp = it)) },
                modifier = modifier, highlight = highlight
            )
        },
        SettingEntry(key = "caption-background", icon = Icons.Default.Opacity, title = backgroundTitle, summary = null, value = backgroundLabel) { index, count, modifier, highlight ->
            SliderRow(
                index = index, count = count, icon = Icons.Default.Opacity, title = backgroundTitle, valueLabel = backgroundLabel,
                value = style.backgroundOpacity,
                range = 0f..1f,
                steps = 9,
                onChange = { onChange(style.copy(backgroundOpacity = it)) },
                modifier = modifier, highlight = highlight
            )
        },
        entry.choice(
            key = "caption-color",
            icon = Icons.Default.FormatColorText,
            title = stringResource(R.string.sheet_caption_color),
            options = CaptionTextColor.entries.map { it to stringResource(it.labelRes) },
            selected = style.textColor,
            onSelect = { onChange(style.copy(textColor = it)) }
        ),
        SettingEntry(key = "caption-position", icon = Icons.Default.VerticalAlignTop, title = positionTitle, summary = null, value = positionLabel) { index, count, modifier, highlight ->
            SliderRow(
                index = index, count = count, icon = Icons.Default.VerticalAlignTop, title = positionTitle, valueLabel = positionLabel,
                value = style.verticalOffset,
                range = 0f..CaptionStyleSettings.MAX_VERTICAL_OFFSET,
                steps = CaptionStyleSettings.VERTICAL_OFFSET_STEPS,
                onChange = { onChange(style.copy(verticalOffset = it)) },
                modifier = modifier, highlight = highlight
            )
        },
        entry.navigation(
            key = "caption-reset",
            icon = Icons.Default.RestartAlt,
            title = stringResource(R.string.st_caption_reset),
            summary = stringResource(R.string.st_caption_reset_hint),
            onClick = { onChange(CaptionStyleSettings()) }
        )
    )
}

