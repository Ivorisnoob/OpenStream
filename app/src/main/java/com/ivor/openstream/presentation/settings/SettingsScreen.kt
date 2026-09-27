package com.ivor.openstream.presentation.settings

import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.backup.LibraryBackup
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.settings.ThemeMode
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenMarketplace: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val imageCacheBytes by viewModel.imageCacheBytes.collectAsState()
    val hiddenTitleCount by viewModel.hiddenTitleCount.collectAsState()
    val crashCount by viewModel.crashCount.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(LibraryBackup.MIME_TYPE)) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restoreBackup)
    }
    val exportDiagnostics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(viewModel::exportDiagnostics)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear watch history?") },
            text = { Text("Removes history, Continue Watching and saved positions for every title. Downloads and Watch Later stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    viewModel.clearWatchHistory()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                navigationIcon = {
                    ExpressiveBackButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                MarketplaceEntryCard(
                    state = state,
                    onOpenMarketplace = onOpenMarketplace
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Extension,
                    title = "Installed sources",
                    subtitle = "${state.enabledCount} of ${state.installedCount} installed sources are active",
                    onClick = onOpenMarketplace
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Public,
                    title = "Repositories",
                    subtitle = "${state.repoCount} connected · add community catalogs by URL",
                    onClick = onOpenMarketplace
                )
            }

            if (state.updateCount > 0) {
                item {
                    SettingsRow(
                        icon = Icons.Default.Update,
                        title = "Extension updates",
                        subtitle = "${state.updateCount} waiting to be applied",
                        onClick = onOpenMarketplace
                    )
                }
            }

            item { SafetyNote() }

            item { SectionHeader("Appearance") }
            item {
                ChoiceCard(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    subtitle = null,
                    options = ThemeMode.entries,
                    selected = appSettings.themeMode,
                    label = { it.label },
                    onSelect = viewModel::setThemeMode
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(
                        icon = Icons.Default.ColorLens,
                        title = "Dynamic color",
                        subtitle = "Match colors to your wallpaper",
                        checked = appSettings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor
                    )
                }
            }

            item { SectionHeader("Playback") }
            item {
                ChoiceCard(
                    icon = Icons.Default.Forward10,
                    title = "Seek step",
                    subtitle = "How far double-tap and the skip buttons jump",
                    options = AppSettings.SEEK_STEPS,
                    selected = appSettings.seekStepSeconds,
                    label = { "${it}s" },
                    onSelect = viewModel::setSeekStep
                )
            }
            item {
                ChoiceCard(
                    icon = Icons.Default.Speed,
                    title = "Default speed",
                    subtitle = "Every title starts at this speed",
                    options = AppSettings.DEFAULT_SPEEDS,
                    selected = appSettings.defaultSpeed,
                    label = { if (it == 1f) "Normal" else "${formatSpeed(it)}×" },
                    onSelect = viewModel::setDefaultSpeed,
                    maxPerRow = 3
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.SkipNext,
                    title = "Auto-play next episode",
                    subtitle = "Count down into the next episode when one ends",
                    checked = appSettings.autoPlayNext,
                    onCheckedChange = viewModel::setAutoPlayNext
                )
            }

            item { SectionHeader("Network") }
            item {
                ChoiceCard(
                    icon = Icons.Default.Dns,
                    title = "DNS",
                    subtitle = dnsDescription(appSettings),
                    options = DnsProvider.entries,
                    selected = appSettings.dnsProvider,
                    label = { it.label },
                    onSelect = viewModel::setDnsProvider
                )
            }

            item { SectionHeader("Downloads") }
            item {
                ChoiceCard(
                    icon = Icons.Default.HighQuality,
                    title = "Download quality",
                    subtitle = "Highest quality a download picks when a source offers several",
                    options = AppSettings.DOWNLOAD_HEIGHTS,
                    selected = appSettings.downloadMaxHeight,
                    label = { "${it}p" },
                    onSelect = viewModel::setDownloadMaxHeight
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Wifi,
                    title = "Download on Wi-Fi only",
                    subtitle = "Downloads wait until you're on an unmetered network",
                    checked = appSettings.wifiOnlyDownloads,
                    onCheckedChange = viewModel::setWifiOnlyDownloads
                )
            }

            item { SectionHeader("Backup") }
            item {
                SettingsRow(
                    icon = Icons.Default.Backup,
                    title = "Back up library",
                    subtitle = "Save Watch Later, history, progress, hidden titles and settings to a file",
                    onClick = { exportBackup.launch("openstream-backup-${fileDate()}.json") },
                    enabled = !isWorking
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Restore,
                    title = "Restore from backup",
                    subtitle = "Merges into this device; nothing here is deleted",
                    onClick = { restoreBackup.launch(arrayOf(LibraryBackup.MIME_TYPE, "text/plain", "application/octet-stream")) },
                    enabled = !isWorking
                )
            }

            item { SectionHeader("Storage and privacy") }
            item {
                SettingsRow(
                    icon = Icons.Default.Image,
                    title = "Clear image cache",
                    subtitle = imageCacheBytes?.let { "${Formatter.formatShortFileSize(context, it)} of artwork stored" }
                        ?: "Posters and backdrops kept for faster loading",
                    onClick = viewModel::clearImageCache
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.History,
                    title = "Clear watch history",
                    subtitle = "History, Continue Watching and resume positions",
                    onClick = { confirmClearHistory = true }
                )
            }
            if (hiddenTitleCount > 0) {
                item {
                    SettingsRow(
                        icon = Icons.Default.VisibilityOff,
                        title = "Hidden titles",
                        subtitle = "$hiddenTitleCount hidden from Home · tap to show them again",
                        onClick = viewModel::unhideAllTitles
                    )
                }
            }

            item { SectionHeader("Help") }
            item {
                SettingsRow(
                    icon = Icons.Default.BugReport,
                    title = "Export diagnostics",
                    subtitle = if (crashCount > 0) {
                        "Includes ${if (crashCount == 1) "1 crash report" else "$crashCount crash reports"} · attach it to a bug report"
                    } else {
                        "Device details and the app's recent log, to attach to a bug report"
                    },
                    onClick = { exportDiagnostics.launch("openstream-diagnostics-${fileDate(withTime = true)}.txt") },
                    enabled = !isWorking
                )
            }
            if (crashCount > 0) {
                item {
                    SettingsRow(
                        icon = Icons.Default.DeleteSweep,
                        title = "Clear crash reports",
                        subtitle = "Stored only on this device",
                        onClick = viewModel::clearCrashReports
                    )
                }
            }

            item {
                Text(
                    text = "OpenStream ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MarketplaceEntryCard(
    state: SettingsUiState,
    onOpenMarketplace: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.extraLarge)
            .clickable(onClick = onOpenMarketplace),
        shape = ExpressiveShapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                shape = ExpressiveShapes.medium,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Extension marketplace",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Browse the top charts, install new sources, and connect community repositories.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat(label = "Available", value = state.availableCount.toString())
                HeroStat(label = "Installed", value = state.installedCount.toString())
                HeroStat(label = "Enabled", value = state.enabledCount.toString())
            }
            Button(onClick = onOpenMarketplace, shape = ExpressiveShapes.small) {
                Text("Open marketplace")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Surface(
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.large)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = ExpressiveShapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Date for suggested file names, e.g. 2026-09-27 or 2026-09-27-1715. */
private fun fileDate(withTime: Boolean = false): String =
    java.text.SimpleDateFormat(if (withTime) "yyyy-MM-dd-HHmm" else "yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date())

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')

private fun dnsDescription(settings: AppSettings): String =
    if (settings.dnsProvider == DnsProvider.SYSTEM) {
        "Uses your network's DNS. Switch to a private resolver if titles or artwork don't load."
    } else {
        "Looks up servers through ${settings.dnsProvider.label} over HTTPS, so ISP DNS blocks don't stop TMDB. Falls back to your network's DNS if it can't be reached."
    }

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 4.dp, top = 12.dp)
            .semantics { heading() }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    maxPerRow: Int = if (options.size > 3) 2 else options.size
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(icon)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    subtitle?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // Four long labels don't fit one row on a phone, so larger sets wrap.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = maxPerRow
            ) {
                options.forEach { option ->
                    ToggleButton(
                        checked = option == selected,
                        onCheckedChange = { onSelect(option) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label(option), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.large)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Surface(
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(10.dp).size(22.dp)
        )
    }
}

@Composable
private fun SafetyNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Shield, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Data-only extensions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Repositories publish manifests, never executable code. Each manifest configures a resolver that already ships with the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f)
                )
            }
        }
    }
}
