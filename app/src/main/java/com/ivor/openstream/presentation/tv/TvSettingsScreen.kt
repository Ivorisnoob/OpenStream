package com.ivor.openstream.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.ivor.openstream.R
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.CacheSize
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.presentation.profiles.ProfilesViewModel
import com.ivor.openstream.presentation.settings.SettingsViewModel

private enum class SettingsCategory(@StringRes val label: Int, val icon: ImageVector) {
    PLAYBACK(R.string.st_playback, Icons.Default.PlayCircle),
    MOBILE_DATA(R.string.st_data_mobile, Icons.Default.SignalCellularAlt),
    DOWNLOADS(R.string.dl_title, Icons.Default.Download),
    SUBTITLES(R.string.player_subtitles, Icons.Default.ClosedCaption),
    NETWORK(R.string.tv_st_network, Icons.Default.Public),
    STORAGE(R.string.tv_st_storage, Icons.Default.Storage),
    NOTIFICATIONS(R.string.st_group_notifications, Icons.Default.Notifications),
    PROFILES(R.string.st_profiles, Icons.Default.People),
    SOURCES(R.string.st_group_sources, Icons.Default.Extension)
}

private class ChoiceOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/** One line in the right-hand pane. */
private sealed interface SettingRow {
    val key: String
    val title: String
    val subtitle: String?

    class Toggle(
        override val key: String,
        override val title: String,
        override val subtitle: String?,
        val checked: Boolean,
        val onChange: (Boolean) -> Unit
    ) : SettingRow

    /** OK opens [options] in the pane; [multi] keeps it open so several can be ticked. */
    class Choice(
        override val key: String,
        override val title: String,
        override val subtitle: String?,
        val value: String,
        val options: List<ChoiceOption>,
        val multi: Boolean = false
    ) : SettingRow

    class Action(
        override val key: String,
        override val title: String,
        override val subtitle: String?,
        val trailing: String? = null,
        val opensPage: Boolean = false,
        val onClick: () -> Unit
    ) : SettingRow
}

/**
 * Settings in two panes, the way Android TV's own settings work. Categories on the left select as
 * focus lands on them; Right or OK moves into the category's settings. Toggles flip in place and a
 * choice opens its options in the same pane, with Back returning to the row it came from. Nothing
 * here needs a dialog, a keyboard or a file picker; settings that do (backup, diagnostics, app
 * language, editing profiles) stay on the phone.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSettingsScreen(
    onOpenExtensions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    profilesViewModel: ProfilesViewModel = hiltViewModel()
) {
    var category by remember { mutableStateOf(SettingsCategory.PLAYBACK) }
    var openChoice by remember { mutableStateOf<String?>(null) }
    var refocusRow by remember { mutableStateOf<String?>(null) }
    var detailHasFocus by remember { mutableStateOf(false) }

    val categoryRequesters = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun rowRequester(key: String) = rowRequesters.getOrPut(key) { FocusRequester() }
    val firstOptionRequester = remember { FocusRequester() }

    val rows = settingRows(category, viewModel, profilesViewModel, onOpenExtensions)
    val choice = rows.firstOrNull { it.key == openChoice } as? SettingRow.Choice

    LaunchedEffect(Unit) { runCatching { categoryRequesters.getValue(category).requestFocus() } }
    LaunchedEffect(openChoice) {
        if (openChoice != null) runCatching { firstOptionRequester.requestFocus() }
    }
    LaunchedEffect(refocusRow) {
        val key = refocusRow ?: return@LaunchedEffect
        runCatching { rowRequester(key).requestFocus() }
        refocusRow = null
    }

    fun closeChoice() {
        refocusRow = openChoice
        openChoice = null
    }
    BackHandler(enabled = openChoice != null) { closeChoice() }
    // Back from the right pane returns to the category list before it leaves Settings.
    BackHandler(enabled = openChoice == null && detailHasFocus) {
        runCatching { categoryRequesters.getValue(category).requestFocus() }
    }

    Row(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .width(TvLayout.ContentStart + 260.dp)
                .fillMaxHeight()
                .padding(start = TvLayout.ContentStart, end = 16.dp, top = 36.dp)
        ) {
            androidx.compose.material3.Text(
                text = stringResource(R.string.st_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .semantics { heading() }
            )
            LazyColumn(
                modifier = Modifier.focusRestorer(categoryRequesters.getValue(category)),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(SettingsCategory.entries) { entry ->
                    ListItem(
                        selected = entry == category,
                        onClick = { runCatching { rows.firstOrNull()?.let { rowRequester(it.key).requestFocus() } } },
                        headlineContent = {
                            Text(stringResource(entry.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier
                            .focusRequester(categoryRequesters.getValue(entry))
                            .onFocusChanged {
                                if (it.isFocused && category != entry) {
                                    category = entry
                                    openChoice = null
                                }
                            }
                    )
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                )
                .onFocusChanged { detailHasFocus = it.hasFocus }
                .padding(start = 32.dp, end = TvLayout.ContentEnd, top = 36.dp)
        ) {
            if (choice != null) {
                ChoicePane(choice = choice, firstRequester = firstOptionRequester, onPicked = { closeChoice() })
            } else {
                Column {
                    androidx.compose.material3.Text(
                        text = stringResource(category.label),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .semantics { heading() }
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(rows, key = { it.key }) { row ->
                            SettingListItem(
                                row = row,
                                modifier = Modifier.focusRequester(rowRequester(row.key)),
                                onOpenChoice = { openChoice = row.key }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingListItem(row: SettingRow, modifier: Modifier, onOpenChoice: () -> Unit) {
    ListItem(
        selected = false,
        onClick = {
            when (row) {
                is SettingRow.Toggle -> row.onChange(!row.checked)
                is SettingRow.Choice -> onOpenChoice()
                is SettingRow.Action -> row.onClick()
            }
        },
        headlineContent = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = row.subtitle?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            when (row) {
                // Display only: the row is the focus stop and OK on it flips the switch.
                is SettingRow.Toggle -> Switch(checked = row.checked, onCheckedChange = null)
                is SettingRow.Choice -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.value, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                is SettingRow.Action -> Row(verticalAlignment = Alignment.CenterVertically) {
                    row.trailing?.let { Text(it, maxLines = 1) }
                    if (row.opensPage) Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        },
        modifier = modifier
    )
}

/** A choice's options in place of the rows. A single choice closes as soon as one is picked. */
@Composable
private fun ChoicePane(choice: SettingRow.Choice, firstRequester: FocusRequester, onPicked: () -> Unit) {
    val focusIndex = choice.options.indexOfFirst { it.selected }.coerceAtLeast(0)
    Column {
        androidx.compose.material3.Text(
            text = choice.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        if (choice.subtitle != null) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Text(
                text = choice.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(choice.options.size) { index ->
                val option = choice.options[index]
                ListItem(
                    selected = option.selected,
                    onClick = {
                        option.onSelect()
                        if (!choice.multi) onPicked()
                    },
                    headlineContent = { Text(option.label) },
                    trailingContent = if (option.selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    } else null,
                    modifier = if (index == focusIndex) Modifier.focusRequester(firstRequester) else Modifier
                )
            }
        }
    }
}

@Composable
private fun settingRows(
    category: SettingsCategory,
    viewModel: SettingsViewModel,
    profilesViewModel: ProfilesViewModel,
    onOpenExtensions: () -> Unit
): List<SettingRow> {
    val s by viewModel.appSettings.collectAsState()
    return when (category) {
        SettingsCategory.PLAYBACK -> listOf(
            intChoice(
                key = "stream-quality",
                title = stringResource(R.string.st_stream_cap),
                subtitle = stringResource(R.string.st_stream_cap_hint),
                options = AppSettings.STREAM_CAPS,
                selected = s.streamMaxHeight,
                label = { height ->
                    when (height) {
                        0 -> stringResource(R.string.st_stream_cap_none)
                        2160 -> stringResource(R.string.q_2160p)
                        else -> "${height}p"
                    }
                },
                onSelect = viewModel::setStreamMaxHeight
            ),
            SettingRow.Toggle(
                "autoplay", stringResource(R.string.sheet_autoplay_next), stringResource(R.string.player_countdown_next),
                s.autoPlayNext, viewModel::setAutoPlayNext
            ),
            SettingRow.Toggle(
                "skip", stringResource(R.string.sheet_skip_button), stringResource(R.string.player_offer_skip),
                s.showSkipButton, viewModel::setShowSkipButton
            ),
            SettingRow.Choice(
                key = "speed",
                title = stringResource(R.string.sheet_default_speed),
                subtitle = null,
                value = speedLabel(s.defaultSpeed),
                options = AppSettings.DEFAULT_SPEEDS.map { speed ->
                    ChoiceOption(speedLabel(speed), speed == s.defaultSpeed) { viewModel.setDefaultSpeed(speed) }
                }
            ),
            intChoice(
                key = "seek",
                title = stringResource(R.string.sheet_seek_step),
                subtitle = null,
                options = AppSettings.SEEK_STEPS,
                selected = s.seekStepSeconds,
                label = { "$it s" },
                onSelect = viewModel::setSeekStep
            ),
            SettingRow.Toggle(
                "background-playback", stringResource(R.string.st_background_playback),
                stringResource(R.string.st_background_playback_hint),
                s.keepPlayingInBackground, viewModel::setKeepPlayingInBackground
            )
        )

        SettingsCategory.MOBILE_DATA -> listOf(
            intChoice(
                key = "metered-cap",
                title = stringResource(R.string.st_data_mobile_cap),
                subtitle = stringResource(R.string.st_data_mobile_cap_hint),
                options = AppSettings.METERED_CAPS,
                selected = s.meteredMaxHeight,
                label = { height -> if (height == 0) stringResource(R.string.st_data_mobile_remember_none) else "${height}p" },
                onSelect = viewModel::setMeteredMaxHeight
            ),
            SettingRow.Toggle(
                "metered-warn", stringResource(R.string.st_data_mobile_warn),
                stringResource(R.string.st_data_mobile_warn_hint),
                s.warnBeforeMeteredStream, viewModel::setWarnBeforeMeteredStream
            )
        )

        SettingsCategory.DOWNLOADS -> listOfNotNull(
            SettingRow.Toggle(
                "wifi-only", stringResource(R.string.dl_wifi_only), null,
                s.wifiOnlyDownloads, viewModel::setWifiOnlyDownloads
            ),
            intChoice(
                key = "download-quality",
                title = stringResource(R.string.dl_highest_quality),
                subtitle = null,
                options = AppSettings.DOWNLOAD_HEIGHTS,
                selected = s.downloadMaxHeight,
                label = { height -> if (height == 2160) stringResource(R.string.q_2160p) else "${height}p" },
                onSelect = viewModel::setDownloadMaxHeight
            ),
            SettingRow.Toggle(
                "smart-downloads", stringResource(R.string.st_smart_downloads), stringResource(R.string.st_smart_hint),
                s.smartDownloads, viewModel::setSmartDownloads
            ),
            if (s.smartDownloads) {
                intChoice(
                    key = "smart-ahead",
                    title = stringResource(R.string.st_smart_ahead),
                    subtitle = null,
                    options = listOf(1, 2, 3, 5),
                    selected = s.smartKeepAhead,
                    label = { "$it" },
                    onSelect = viewModel::setSmartKeepAhead
                )
            } else null
        )

        SettingsCategory.SUBTITLES -> {
            val languages = remember { SavedSubtitleRepository.COMMON_LANGUAGES }
            val chosen = s.subtitleDownloadLanguages
            listOf(
                SettingRow.Choice(
                    key = "subtitle-langs",
                    title = stringResource(R.string.st_languages),
                    subtitle = null,
                    value = if (chosen.isEmpty()) stringResource(R.string.st_data_mobile_remember_none)
                    else chosen.joinToString(", ") { SavedSubtitleRepository.languageName(it) },
                    options = languages.map { code ->
                        ChoiceOption(SavedSubtitleRepository.languageName(code), code in chosen) {
                            val current = viewModel.appSettings.value.subtitleDownloadLanguages
                            viewModel.setSubtitleDownloadLanguages(if (code in current) current - code else current + code)
                        }
                    },
                    multi = true
                ),
                SettingRow.Toggle(
                    "subtitle-sites", stringResource(R.string.dl_also_from_sites), null,
                    s.subtitleDownloadFromSites, viewModel::setSubtitleDownloadFromSites
                )
            )
        }

        SettingsCategory.NETWORK -> listOf(
            SettingRow.Choice(
                key = "dns",
                title = stringResource(R.string.st_dns),
                subtitle = stringResource(R.string.st_dns_private_hint),
                value = dnsLabel(s.dnsProvider),
                options = DnsProvider.entries.map { provider ->
                    ChoiceOption(dnsLabel(provider), provider == s.dnsProvider) { viewModel.setDnsProvider(provider) }
                }
            )
        )

        SettingsCategory.STORAGE -> {
            val cacheBytes by viewModel.cacheBytes.collectAsState()
            listOf(
                SettingRow.Choice(
                    key = "cache-size",
                    title = stringResource(R.string.st_cache_limit),
                    subtitle = stringResource(R.string.st_cache_limit_hint),
                    value = cacheLabel(s.cacheSize),
                    options = CacheSize.entries.map { size ->
                        ChoiceOption(cacheLabel(size), size == s.cacheSize) { viewModel.setCacheSize(size) }
                    }
                ),
                SettingRow.Toggle(
                    "preload", stringResource(R.string.st_preload_artwork), stringResource(R.string.st_preload_artwork_hint),
                    s.preloadArtwork, viewModel::setPreloadArtwork
                ),
                SettingRow.Action(
                    key = "clear-cache",
                    title = stringResource(R.string.st_clear_all_cache),
                    subtitle = cacheBytes?.let { stringResource(R.string.st_cache_used, "${it / (1024L * 1024L)} MB") },
                    onClick = viewModel::clearCache
                )
            )
        }

        SettingsCategory.NOTIFICATIONS -> listOf(
            SettingRow.Toggle(
                "episode-notify", stringResource(R.string.st_episode_notify), stringResource(R.string.st_episode_notify_hint),
                s.episodeNotifications, viewModel::setEpisodeNotifications
            )
        )

        SettingsCategory.PROFILES -> {
            val profiles by profilesViewModel.profiles.collectAsState()
            val active by profilesViewModel.activeProfile.collectAsState()
            profiles.map { profile ->
                SettingRow.Action(
                    key = "profile:${profile.id}",
                    title = profile.name,
                    subtitle = listOfNotNull(
                        if (profile.isKids) stringResource(R.string.pf_kids) else null,
                        if (profile.hasPin) stringResource(R.string.pin_on) else null
                    ).joinToString(" · ").takeIf { it.isNotBlank() },
                    trailing = if (profile.id == active?.id) stringResource(R.string.st_enabled) else null,
                    onClick = { profilesViewModel.select(profile.id) }
                )
            }
        }

        SettingsCategory.SOURCES -> {
            val counts by viewModel.state.collectAsState()
            listOf(
            SettingRow.Action(
                key = "extensions",
                title = stringResource(R.string.mk_title),
                subtitle = stringResource(
                    R.string.st_source_summary,
                    counts.enabledCount, counts.installedCount, counts.availableCount
                ),
                opensPage = true,
                onClick = onOpenExtensions
            )
            )
        }
    }
}

@Composable
private fun intChoice(
    key: String,
    title: String,
    subtitle: String?,
    options: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit
) = SettingRow.Choice(
    key = key,
    title = title,
    subtitle = subtitle,
    value = label(selected),
    options = options.map { option -> ChoiceOption(label(option), option == selected) { onSelect(option) } }
)

private fun speedLabel(speed: Float) = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

@Composable
private fun dnsLabel(provider: DnsProvider) = stringResource(
    when (provider) {
        DnsProvider.SYSTEM -> R.string.st_dns_system
        DnsProvider.ADGUARD -> R.string.dns_name_adguard
        DnsProvider.CLOUDFLARE -> R.string.dns_name_cloudflare
        DnsProvider.GOOGLE -> R.string.dns_name_google
    }
)

private fun cacheLabel(size: CacheSize) = when (size) {
    CacheSize.SMALL -> "125 MB"
    CacheSize.STANDARD -> "300 MB"
    CacheSize.LARGE -> "700 MB"
}
