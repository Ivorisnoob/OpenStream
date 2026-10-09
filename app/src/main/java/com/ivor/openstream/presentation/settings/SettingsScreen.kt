package com.ivor.openstream.presentation.settings

import com.ivor.openstream.presentation.components.CenteredListBox
import com.ivor.openstream.presentation.downloads.formatBytes
import android.app.Activity
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureInPictureAlt
import com.ivor.openstream.data.settings.PipAction
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.R
import com.ivor.openstream.data.backup.LibraryBackup
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.AppLocale
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.settings.ThemeMode
import com.ivor.openstream.presentation.components.ConnectedChoiceGroup
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes

/**
 * Settings as grouped segmented lists, one group per topic, with connected button groups for
 * single choices. Sources come first because they decide whether anything plays at all.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenMarketplace: () -> Unit,
    onOpenProfiles: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val imageCacheBytes by viewModel.imageCacheBytes.collectAsState()
    val hiddenTitleCount by viewModel.hiddenTitleCount.collectAsState()
    val crashCount by viewModel.crashCount.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }
    var pickingSubtitleLanguages by rememberSaveable { mutableStateOf(false) }
    var pickingAppLanguage by rememberSaveable { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Rows sit on surfaceBright over a surfaceContainer page, as in the system Settings app, so
        // unselected rows keep a visible edge in light and dark themes.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.st_title)) },
                subtitle = { Text(stringResource(R.string.up_version_format, BuildConfig.VERSION_NAME)) },
                navigationIcon = {
                    ExpressiveBackButton(onClick = onBackClick, modifier = Modifier.padding(start = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        CenteredListBox(Modifier.fillMaxSize(), minGutter = 16.dp, maxContentWidth = 720.dp) { gutter ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = gutter,
                    end = gutter,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item(key = "sources") {
                    val sourceRows = if (state.updateCount > 0) 3 else 2
                    SettingsGroup(
                        title = stringResource(R.string.st_group_sources),
                        footer = stringResource(R.string.st_extensions_data_only)
                    ) {
                        NavigationRow(
                            index = 0, count = sourceRows,
                            icon = Icons.Default.Storefront,
                            title = stringResource(R.string.st_extension_marketplace),
                            supporting = stringResource(
                                R.string.st_source_summary,
                                state.enabledCount, state.installedCount, state.availableCount
                            ),
                            onClick = onOpenMarketplace
                        )
                        NavigationRow(
                            index = 1, count = sourceRows,
                            icon = Icons.Default.Public,
                            title = stringResource(R.string.st_repositories),
                            supporting = stringResource(R.string.mk_repo_connected, state.repoCount),
                            onClick = onOpenMarketplace
                        )
                        if (state.updateCount > 0) {
                            NavigationRow(
                                index = 2, count = sourceRows,
                                icon = Icons.Default.Update,
                                title = stringResource(R.string.st_extension_updates),
                                supporting = stringResource(R.string.st_ready_install),
                                badge = state.updateCount.toString(),
                                onClick = onOpenMarketplace
                            )
                        }
                    }
                }

                item(key = "appearance") {
                    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val count = if (dynamicAvailable) 3 else 2
                    SettingsGroup(title = stringResource(R.string.st_appearance)) {
                        ChoiceRow(
                            index = 0, count = count,
                            icon = Icons.Default.DarkMode,
                            title = stringResource(R.string.st_theme),
                            options = ThemeMode.entries,
                            selected = appSettings.themeMode,
                            label = { stringResource(it.labelRes) },
                            onSelect = viewModel::setThemeMode
                        )
                        NavigationRow(
                            index = 1, count = count,
                            icon = Icons.Default.Translate,
                            title = stringResource(R.string.st_language),
                            supporting = appSettings.appLanguage?.let { AppLocale.autonym(it) }
                                ?: stringResource(R.string.st_language_system),
                            onClick = { pickingAppLanguage = true }
                        )
                        if (dynamicAvailable) {
                            SwitchRow(
                                index = 2, count = count,
                                icon = Icons.Default.AutoAwesome,
                                title = stringResource(R.string.st_dynamic_color),
                                supporting = stringResource(R.string.st_dynamic_hint),
                                checked = appSettings.dynamicColor,
                                onCheckedChange = viewModel::setDynamicColor
                            )
                        }
                    }
                }

                item(key = "playback") {
                    SettingsGroup(title = stringResource(R.string.st_playback)) {
                        ChoiceRow(
                            index = 0, count = 5,
                            icon = Icons.Default.Forward10,
                            title = stringResource(R.string.sheet_seek_step),
                            supporting = stringResource(R.string.player_double_tap),
                            options = AppSettings.SEEK_STEPS,
                            selected = appSettings.seekStepSeconds,
                            label = { "${it}s" },
                            onSelect = viewModel::setSeekStep
                        )
                        ChoiceRow(
                            index = 1, count = 5,
                            icon = Icons.Default.Speed,
                            title = stringResource(R.string.sheet_default_speed),
                            options = AppSettings.DEFAULT_SPEEDS,
                            selected = appSettings.defaultSpeed,
                            label = { "${formatSpeed(it)}×" },
                            onSelect = viewModel::setDefaultSpeed
                        )
                        SwitchRow(
                            index = 2, count = 5,
                            icon = Icons.Default.SkipNext,
                            title = stringResource(R.string.sheet_autoplay_next),
                            supporting = stringResource(R.string.player_countdown_next),
                            checked = appSettings.autoPlayNext,
                            onCheckedChange = viewModel::setAutoPlayNext
                        )
                        SwitchRow(
                            index = 3, count = 5,
                            icon = Icons.Default.FastForward,
                            title = stringResource(R.string.sheet_skip_button),
                            supporting = stringResource(R.string.player_offer_skip),
                            checked = appSettings.showSkipButton,
                            onCheckedChange = viewModel::setShowSkipButton
                        )
                        SwitchRow(
                            index = 4, count = 5,
                            icon = Icons.Default.NotificationsActive,
                            title = stringResource(R.string.st_background_playback),
                            supporting = stringResource(R.string.st_background_playback_hint),
                            checked = appSettings.keepPlayingInBackground,
                            onCheckedChange = viewModel::setKeepPlayingInBackground
                        )
                    }
                }

                item(key = "mobile-data") {
                    val dataUsed by viewModel.streamDataGuard.dataUsedBytes.collectAsState()
                    SettingsGroup(
                        title = stringResource(R.string.st_data_mobile),
                        footer = stringResource(R.string.st_data_mobile_used, formatBytes(dataUsed))
                    ) {
                        SwitchRow(
                            index = 0, count = 2,
                            icon = Icons.Default.DataUsage,
                            title = stringResource(R.string.st_data_mobile_warn),
                            supporting = stringResource(R.string.st_data_mobile_warn_hint),
                            checked = appSettings.warnBeforeMeteredStream,
                            onCheckedChange = viewModel::setWarnBeforeMeteredStream
                        )
                        ChoiceRow(
                            index = 1, count = 2,
                            icon = Icons.Default.HighQuality,
                            title = stringResource(R.string.st_data_mobile_cap),
                            supporting = stringResource(R.string.st_data_mobile_cap_hint),
                            options = AppSettings.METERED_CAPS,
                            selected = appSettings.meteredMaxHeight,
                            label = { if (it == 0) stringResource(R.string.st_data_mobile_remember_none) else "${it}p" },
                            onSelect = viewModel::setMeteredMaxHeight
                        )
                    }
                }

                item(key = "pip") {
                    SettingsGroup(
                        title = stringResource(R.string.st_group_pip),
                        footer = stringResource(R.string.st_pip_footer)
                    ) {
                        ChoiceRow(
                            index = 0, count = 2,
                            icon = Icons.Default.PictureInPictureAlt,
                            title = stringResource(R.string.sheet_left_button),
                            options = PipAction.entries,
                            selected = appSettings.pipLeftAction,
                            label = { stringResource(it.labelRes) },
                            onSelect = viewModel::setPipLeftAction
                        )
                        ChoiceRow(
                            index = 1, count = 2,
                            icon = Icons.Default.PictureInPictureAlt,
                            title = stringResource(R.string.sheet_right_button),
                            options = PipAction.entries,
                            selected = appSettings.pipRightAction,
                            label = { stringResource(it.labelRes) },
                            onSelect = viewModel::setPipRightAction
                        )
                    }
                }

                item(key = "downloads") {
                    SettingsGroup(title = stringResource(R.string.dl_title)) {
                        ChoiceRow(
                            index = 0, count = 5,
                            icon = Icons.Default.HighQuality,
                            title = stringResource(R.string.sheet_quality),
                            supporting = stringResource(R.string.dl_highest_quality),
                            options = AppSettings.DOWNLOAD_HEIGHTS,
                            selected = appSettings.downloadMaxHeight,
                            label = { "${it}p" },
                            onSelect = viewModel::setDownloadMaxHeight
                        )
                        NavigationRow(
                            index = 1, count = 5,
                            icon = Icons.Default.ClosedCaption,
                            title = stringResource(R.string.player_subtitles),
                            supporting = stringResource(
                                R.string.dl_saved_suffix,
                                subtitleLanguagesSummary(appSettings.subtitleDownloadLanguages)
                            ),
                            onClick = { pickingSubtitleLanguages = true }
                        )
                        SwitchRow(
                            index = 2, count = 5,
                            icon = Icons.Default.Wifi,
                            title = stringResource(R.string.dl_wifi_only),
                            supporting = stringResource(R.string.action_wait_for_unmetered),
                            checked = appSettings.wifiOnlyDownloads,
                            onCheckedChange = viewModel::setWifiOnlyDownloads
                        )
                        SwitchRow(
                            index = 3, count = 5,
                            icon = Icons.Default.Download,
                            title = stringResource(R.string.st_smart_downloads),
                            supporting = stringResource(R.string.st_smart_hint),
                            checked = appSettings.smartDownloads,
                            onCheckedChange = viewModel::setSmartDownloads
                        )
                        ChoiceRow(
                            index = 4, count = 5,
                            icon = Icons.Default.HighQuality,
                            title = stringResource(R.string.st_smart_ahead),
                            options = AppSettings.SMART_AHEAD_OPTIONS,
                            selected = appSettings.smartKeepAhead,
                            label = { it.toString() },
                            onSelect = viewModel::setSmartKeepAhead
                        )
                    }
                }

                item(key = "network") {
                    val providers = DnsProvider.entries
                    SettingsGroup(
                        title = stringResource(R.string.st_dns),
                        footer = stringResource(R.string.st_dns_private_hint)
                    ) {
                        providers.forEachIndexed { index, provider ->
                            RadioRow(
                                index = index, count = providers.size,
                                icon = if (provider == DnsProvider.SYSTEM) Icons.Default.Dns else Icons.Default.Shield,
                                title = stringResource(provider.labelRes),
                                supporting = dnsSupportingText(provider),
                                selected = appSettings.dnsProvider == provider,
                                onClick = { viewModel.setDnsProvider(provider) }
                            )
                        }
                    }
                }

                item(key = "profiles") {
                    SettingsGroup(title = stringResource(R.string.st_group_notifications)) {
                        SwitchRow(
                            index = 0, count = 1,
                            icon = Icons.Default.Notifications,
                            title = stringResource(R.string.st_episode_notify),
                            supporting = stringResource(R.string.st_episode_notify_hint),
                            checked = appSettings.episodeNotifications,
                            onCheckedChange = viewModel::setEpisodeNotifications
                        )
                    }
                }

                item(key = "profiles-list") {
                    SettingsGroup(title = stringResource(R.string.st_profiles)) {
                        NavigationRow(
                            index = 0, count = 1,
                            icon = Icons.Default.People,
                            title = stringResource(R.string.st_profiles),
                            supporting = stringResource(R.string.st_profiles_hint),
                            onClick = onOpenProfiles
                        )
                    }
                }

                item(key = "library") {
                    val count = if (hiddenTitleCount > 0) 4 else 3
                    SettingsGroup(title = stringResource(R.string.st_library), busy = isWorking) {
                        NavigationRow(
                            index = 0, count = count,
                            icon = Icons.Default.Backup,
                            title = stringResource(R.string.st_backup),
                            supporting = stringResource(R.string.st_backup_hint),
                            enabled = !isWorking,
                            onClick = { exportBackup.launch("openstream-backup-${fileDate()}.json") }
                        )
                        NavigationRow(
                            index = 1, count = count,
                            icon = Icons.Default.Restore,
                            title = stringResource(R.string.st_restore),
                            supporting = stringResource(R.string.st_merge_note),
                            enabled = !isWorking,
                            onClick = {
                                restoreBackup.launch(arrayOf(LibraryBackup.MIME_TYPE, "text/plain", "application/octet-stream"))
                            }
                        )
                        if (hiddenTitleCount > 0) {
                            NavigationRow(
                                index = 2, count = count,
                                icon = Icons.Default.VisibilityOff,
                                title = stringResource(R.string.st_show_hidden),
                                supporting = stringResource(R.string.count_hidden, hiddenTitleCount),
                                onClick = viewModel::unhideAllTitles
                            )
                        }
                        NavigationRow(
                            index = count - 1, count = count,
                            icon = Icons.Default.History,
                            title = stringResource(R.string.st_clear_history),
                            supporting = stringResource(R.string.pf_this_history),
                            onClick = { confirmClearHistory = true }
                        )
                    }
                }

                item(key = "storage-help") {
                    val count = if (crashCount > 0) 3 else 2
                    SettingsGroup(title = stringResource(R.string.st_storage_help)) {
                        NavigationRow(
                            index = 0, count = count,
                            icon = Icons.Default.Image,
                            title = stringResource(R.string.st_clear_cache),
                            supporting = imageCacheBytes?.let { stringResource(R.string.st_cache_size, Formatter.formatShortFileSize(context, it)) }
                                ?: stringResource(R.string.st_posters_cache),
                            onClick = viewModel::clearImageCache
                        )
                        NavigationRow(
                            index = 1, count = count,
                            icon = Icons.Default.BugReport,
                            title = stringResource(R.string.st_export_diag),
                            supporting = when (crashCount) {
                                0 -> stringResource(R.string.st_diag_hint)
                                1 -> stringResource(R.string.st_crash_include, 1)
                                else -> stringResource(R.string.st_crash_include_other, crashCount)
                            },
                            enabled = !isWorking,
                            onClick = { exportDiagnostics.launch("openstream-diagnostics-${fileDate(withTime = true)}.txt") }
                        )
                        if (crashCount > 0) {
                            NavigationRow(
                                index = 2, count = count,
                                icon = Icons.Default.DeleteSweep,
                                title = stringResource(R.string.st_clear_crash),
                                supporting = stringResource(R.string.dl_stored_only_device),
                                onClick = viewModel::clearCrashReports
                            )
                        }
                    }
                }

                item(key = "version") {
                    Text(
                        text = stringResource(R.string.up_version_build, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// region Building blocks

/** A titled group of segmented rows with an optional explanatory footer. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsGroup(
    title: String,
    footer: String? = null,
    busy: Boolean = false,
    rows: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            if (busy) LoadingIndicator(modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = rows)
        footer?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun settingsRowColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceBright,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
)

@Composable
private fun RowIcon(icon: ImageVector) {
    Surface(
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavigationRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    badge: String? = null
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        enabled = enabled,
        colors = settingsRowColors(),
        leadingContent = { RowIcon(icon) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badge?.let { Badge { Text(it) } }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(),
        leadingContent = { RowIcon(icon) },
        supportingContent = { Text(supporting) },
        // The whole row toggles; the switch only shows the state.
        trailingContent = { Switch(checked = checked, onCheckedChange = null) }
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RadioRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(),
        leadingContent = { RowIcon(icon) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        }
    ) {
        Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

/** A row whose choices sit in a connected button group under the title. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ChoiceRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    supporting: String? = null
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(),
        verticalAlignment = Alignment.Top,
        leadingContent = { RowIcon(icon) },
        supportingContent = {
            Column {
                supporting?.let { Text(it) }
                ConnectedChoiceGroup(
                    options = options,
                    selected = selected,
                    label = label,
                    onSelect = onSelect,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

// endregion

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
            LazyColumn {
                item(key = "system") {
                    RadioRow(
                        index = 0,
                        count = AppLocale.supportedTags.size + 1,
                        icon = Icons.Default.Translate,
                        title = stringResource(R.string.st_language_system),
                        supporting = "",
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
                            supporting = "",
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
