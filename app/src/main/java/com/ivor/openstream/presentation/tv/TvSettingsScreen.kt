package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.ivor.openstream.R
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.CacheSize
import com.ivor.openstream.data.settings.DnsProvider
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.presentation.profiles.ProfilesViewModel
import com.ivor.openstream.presentation.settings.SettingsViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed

/**
 * The 10-foot settings list.
 *
 * Every control is a row or a chip: a remote cannot open dialogs, dropdown menus or a soft
 * keyboard, so no setting here ever builds one. Choices open an inline strip of chips under
 * their row; toggles flip in place; the only navigation is a row that opens the Sources screen.
 *
 * Settings that exist on the phone but need a file picker or typed text (backup/restore,
 * diagnostics export, custom app language, managing profile names/avatars/PINs) are phone-only
 * and deliberately absent here.
 */
@Composable
fun TvSettingsScreen(
    onOpenExtensions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    profilesViewModel: ProfilesViewModel = hiltViewModel()
) {
    val appSettings by viewModel.appSettings.collectAsState()
    val profiles by profilesViewModel.profiles.collectAsState()
    val activeProfile by profilesViewModel.activeProfile.collectAsState()
    val dimens = LocalTvDimens.current
    val firstRowRequester = remember { FocusRequester() }
    var expanded by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { runCatching { firstRowRequester.requestFocus() } }

    fun toggle(key: String) {
        expanded = if (expanded == key) null else key
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dimens.gutter,
            end = dimens.gutter,
            top = dimens.paddingMedium,
            bottom = dimens.gutter
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.listRowGap)
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.st_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = dimens.paddingSmall)
            )
        }

        // Playback -------------------------------------------------------------

        item(key = "playback-header") {
            TvSectionLabel(stringResource(R.string.st_playback))
        }

        item(key = "stream-quality") {
            TvChoiceGroupRow(
                title = stringResource(R.string.st_stream_cap),
                subtitle = stringResource(R.string.st_stream_cap_hint),
                options = AppSettings.STREAM_CAPS,
                selected = appSettings.streamMaxHeight,
                label = { height ->
                    when (height) {
                        0 -> stringResource(R.string.st_stream_cap_none)
                        2160 -> stringResource(R.string.q_2160p)
                        else -> "${height}p"
                    }
                },
                focusRequester = firstRowRequester,
                expanded = expanded == "stream-quality",
                onToggle = { toggle("stream-quality") },
                onSelect = viewModel::setStreamMaxHeight
            )
        }

        item(key = "autoplay") {
            TvSwitchRow(
                title = stringResource(R.string.sheet_autoplay_next),
                subtitle = stringResource(R.string.player_countdown_next),
                checked = appSettings.autoPlayNext,
                onCheckedChange = viewModel::setAutoPlayNext
            )
        }

        item(key = "skip") {
            TvSwitchRow(
                title = stringResource(R.string.sheet_skip_button),
                subtitle = stringResource(R.string.player_offer_skip),
                checked = appSettings.showSkipButton,
                onCheckedChange = viewModel::setShowSkipButton
            )
        }

        item(key = "speed") {
            TvFloatChoiceGroupRow(
                title = stringResource(R.string.sheet_default_speed),
                options = AppSettings.DEFAULT_SPEEDS,
                selected = appSettings.defaultSpeed,
                label = { speed ->
                    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
                },
                expanded = expanded == "speed",
                onToggle = { toggle("speed") },
                onSelect = viewModel::setDefaultSpeed
            )
        }

        item(key = "seek") {
            TvChoiceGroupRow(
                title = stringResource(R.string.sheet_seek_step),
                options = AppSettings.SEEK_STEPS,
                selected = appSettings.seekStepSeconds,
                label = { "$it s" },
                expanded = expanded == "seek",
                onToggle = { toggle("seek") },
                onSelect = viewModel::setSeekStep
            )
        }

        // Mobile data ----------------------------------------------------------

        item(key = "metered-header") {
            TvSectionLabel(stringResource(R.string.st_data_mobile))
        }

        item(key = "metered-cap") {
            TvChoiceGroupRow(
                title = stringResource(R.string.st_data_mobile_cap),
                subtitle = stringResource(R.string.st_data_mobile_cap_hint),
                options = AppSettings.METERED_CAPS,
                selected = appSettings.meteredMaxHeight,
                label = { height ->
                    if (height == 0) stringResource(R.string.st_data_mobile_remember_none) else "${height}p"
                },
                expanded = expanded == "metered",
                onToggle = { toggle("metered") },
                onSelect = viewModel::setMeteredMaxHeight
            )
        }

        item(key = "metered-warn") {
            TvSwitchRow(
                title = stringResource(R.string.st_data_mobile_warn),
                subtitle = stringResource(R.string.st_data_mobile_warn_hint),
                checked = appSettings.warnBeforeMeteredStream,
                onCheckedChange = viewModel::setWarnBeforeMeteredStream
            )
        }

        // Downloads -----------------------------------------------------------

        item(key = "downloads-header") {
            TvSectionLabel(stringResource(R.string.dl_title))
        }

        item(key = "wifi-only") {
            TvSwitchRow(
                title = stringResource(R.string.dl_wifi_only),
                checked = appSettings.wifiOnlyDownloads,
                onCheckedChange = viewModel::setWifiOnlyDownloads
            )
        }

        item(key = "download-quality") {
            TvChoiceGroupRow(
                title = stringResource(R.string.dl_highest_quality),
                options = AppSettings.DOWNLOAD_HEIGHTS,
                selected = appSettings.downloadMaxHeight,
                label = { height ->
                    when (height) {
                        2160 -> stringResource(R.string.q_2160p)
                        else -> "${height}p"
                    }
                },
                expanded = expanded == "download-quality",
                onToggle = { toggle("download-quality") },
                onSelect = viewModel::setDownloadMaxHeight
            )
        }

        item(key = "smart-downloads") {
            TvSwitchRow(
                title = stringResource(R.string.st_smart_downloads),
                subtitle = stringResource(R.string.st_smart_hint),
                checked = appSettings.smartDownloads,
                onCheckedChange = viewModel::setSmartDownloads
            )
        }

        item(key = "smart-ahead") {
            if (appSettings.smartDownloads) {
                TvChoiceGroupRow(
                    title = stringResource(R.string.st_smart_ahead),
                    options = listOf(1, 2, 3, 5),
                    selected = appSettings.smartKeepAhead,
                    label = { "$it" },
                    expanded = expanded == "smart-ahead",
                    onToggle = { toggle("smart-ahead") },
                    onSelect = viewModel::setSmartKeepAhead
                )
            }
        }

        // Network --------------------------------------------------------------

        item(key = "network-header") {
            TvSectionLabel(stringResource(R.string.st_dns))
        }

        item(key = "dns") {
            TvEnumChoiceGroupRow(
                title = stringResource(R.string.st_dns),
                subtitle = stringResource(R.string.st_dns_private_hint),
                options = DnsProvider.entries,
                selected = appSettings.dnsProvider,
                label = { provider ->
                    stringResource(
                        when (provider) {
                            DnsProvider.SYSTEM -> R.string.st_dns_system
                            DnsProvider.ADGUARD -> R.string.dns_name_adguard
                            DnsProvider.CLOUDFLARE -> R.string.dns_name_cloudflare
                            DnsProvider.GOOGLE -> R.string.dns_name_google
                        }
                    )
                },
                expanded = expanded == "dns",
                onToggle = { toggle("dns") },
                onSelect = viewModel::setDnsProvider
            )
        }

        // Subtitles ------------------------------------------------------------

        item(key = "subtitles-header") {
            TvSectionLabel(stringResource(R.string.player_subtitles))
        }

        item(key = "subtitle-langs") {
            TvLanguageGroupRow(
                title = stringResource(R.string.st_languages),
                selected = appSettings.subtitleDownloadLanguages,
                expanded = expanded == "subtitle-langs",
                onToggle = { toggle("subtitle-langs") },
                onToggleLanguage = { code, add ->
                    val current = appSettings.subtitleDownloadLanguages
                    viewModel.setSubtitleDownloadLanguages(
                        if (add) current + code else current - code
                    )
                }
            )
        }

        item(key = "subtitle-sites") {
            TvSwitchRow(
                title = stringResource(R.string.dl_also_from_sites),
                checked = appSettings.subtitleDownloadFromSites,
                onCheckedChange = viewModel::setSubtitleDownloadFromSites
            )
        }

        // Storage --------------------------------------------------------------

        item(key = "storage-header") {
            TvSectionLabel(stringResource(R.string.st_cache_limit))
        }

        item(key = "cache-size") {
            TvEnumChoiceGroupRow(
                title = stringResource(R.string.st_cache_limit),
                subtitle = stringResource(R.string.st_cache_limit_hint),
                options = CacheSize.entries,
                selected = appSettings.cacheSize,
                label = { size ->
                    when (size) {
                        CacheSize.SMALL -> "125 MB"
                        CacheSize.STANDARD -> "300 MB"
                        CacheSize.LARGE -> "700 MB"
                    }
                },
                expanded = expanded == "cache-size",
                onToggle = { toggle("cache-size") },
                onSelect = viewModel::setCacheSize
            )
        }

        item(key = "preload") {
            TvSwitchRow(
                title = stringResource(R.string.st_preload_artwork),
                subtitle = stringResource(R.string.st_preload_artwork_hint),
                checked = appSettings.preloadArtwork,
                onCheckedChange = viewModel::setPreloadArtwork
            )
        }

        item(key = "clear-cache") {
            TvActionRow(
                title = stringResource(R.string.st_clear_all_cache),
                subtitle = viewModel.cacheBytes.collectAsState().value?.let { bytes ->
                    stringResource(R.string.st_cache_used, formatMegabytes(bytes))
                },
                onClick = viewModel::clearCache
            )
        }

        // Notifications --------------------------------------------------------

        item(key = "notifications-header") {
            TvSectionLabel(stringResource(R.string.st_group_notifications))
        }

        item(key = "episode-notify") {
            TvSwitchRow(
                title = stringResource(R.string.st_episode_notify),
                subtitle = stringResource(R.string.st_episode_notify_hint),
                checked = appSettings.episodeNotifications,
                onCheckedChange = viewModel::setEpisodeNotifications
            )
        }

        // Library --------------------------------------------------------------

        item(key = "library-header") {
            TvSectionLabel(stringResource(R.string.st_library))
        }

        item(key = "background-playback") {
            TvSwitchRow(
                title = stringResource(R.string.st_background_playback),
                subtitle = stringResource(R.string.st_background_playback_hint),
                checked = appSettings.keepPlayingInBackground,
                onCheckedChange = viewModel::setKeepPlayingInBackground
            )
        }

        // Profiles -------------------------------------------------------------

        item(key = "profiles-header") {
            TvSectionLabel(stringResource(R.string.st_profiles))
        }

        itemsIndexed(profiles, key = { _, profile -> "profile:${profile.id}" }) { _, profile ->
            TvListRow(
                onClick = { profilesViewModel.select(profile.id) },
                title = profile.name,
                subtitle = listOfNotNull(
                    if (profile.isKids) stringResource(R.string.pf_kids) else null,
                    if (profile.hasPin) stringResource(R.string.pin_on) else null
                ).joinToString(" · ").takeIf { it.isNotBlank() },
                trailingText = if (profile.id == activeProfile?.id) {
                    stringResource(R.string.st_enabled)
                } else {
                    null
                },
                selected = profile.id == activeProfile?.id
            )
        }

        // Sources --------------------------------------------------------------

        item(key = "sources-header") {
            TvSectionLabel(stringResource(R.string.st_group_sources))
        }

        item(key = "extensions-nav") {
            TvListRow(
                onClick = onOpenExtensions,
                title = stringResource(R.string.mk_title),
                subtitle = stringResource(R.string.st_source_summary),
                leadingIcon = Icons.Default.SkipNext
            )
        }
    }
}

@Composable
private fun TvActionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    TvListRow(
        onClick = onClick,
        title = title,
        subtitle = subtitle
    )
}

/** Bytes, as a whole megabyte count, for the cache-size row. */
private fun formatMegabytes(bytes: Long): String {
    val mb = bytes / (1024L * 1024L)
    return "$mb MB"
}

/** A group heading, so a long settings list stays scannable from the couch. */
@Composable
private fun TvSectionLabel(text: String) {
    val dimens = LocalTvDimens.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = dimens.paddingMedium, bottom = dimens.paddingSmall / 2)
    )
}

/**
 * A row that opens an inline strip of options.
 *
 * The row shows the current value on the right, so the answer to "what is it set to" is
 * visible without expanding anything; expanding only changes what OK does.
 */
@Composable
private fun TvChoiceGroupRow(
    title: String,
    options: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    onToggle: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null
) {
    TvListRow(
        onClick = onToggle,
        title = title,
        subtitle = subtitle,
        selected = expanded,
        focusRequester = focusRequester,
        trailing = { Text(label(selected), style = MaterialTheme.typography.titleSmall) },
        modifier = modifier.fillMaxWidth()
    )
    if (expanded) {
        TvOptionStrip(options = options, selected = selected, label = label, onSelect = onSelect)
    }
}

/** Same shape for float-valued options (playback speeds). */
@Composable
private fun TvFloatChoiceGroupRow(
    title: String,
    options: List<Float>,
    selected: Float,
    label: @Composable (Float) -> String,
    onSelect: (Float) -> Unit,
    onToggle: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    TvListRow(
        onClick = onToggle,
        title = title,
        subtitle = subtitle,
        selected = expanded,
        trailing = { Text(label(selected), style = MaterialTheme.typography.titleSmall) },
        modifier = modifier.fillMaxWidth()
    )
    if (expanded) {
        val dimens = LocalTvDimens.current
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.paddingSmall),
            horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
        ) {
            items(options) { option ->
                TvChoiceChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = label(option)
                )
            }
        }
    }
}

/** Same shape for enum-valued options (DNS providers, cache sizes). */
@Composable
private fun <T> TvEnumChoiceGroupRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onToggle: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    TvListRow(
        onClick = onToggle,
        title = title,
        subtitle = subtitle,
        selected = expanded,
        trailing = { Text(label(selected), style = MaterialTheme.typography.titleSmall) },
        modifier = modifier.fillMaxWidth()
    )
    if (expanded) {
        val dimens = LocalTvDimens.current
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.paddingSmall),
            horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
        ) {
            items(options) { option ->
                TvChoiceChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = label(option)
                )
            }
        }
    }
}

/** A toggle row; OK flips it, which is the whole interaction on a remote. */
@Composable
private fun TvSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    TvListRow(
        onClick = { onCheckedChange(!checked) },
        title = title,
        subtitle = subtitle,
        leading = leading?.let { { Icon(it, contentDescription = null) } },
        modifier = modifier.fillMaxWidth(),
        trailing = { TvTogglePill(checked = checked) }
    )
}

/**
 * The on/off indicator for a TV toggle row.
 *
 * A wide track with a filled state rather than M3's `Switch`: at 10 feet the switch's
 * thumb is the one thing on screen you cannot read the state of, and a toggle you have to
 * squint at is a toggle you get wrong.
 */
@Composable
private fun TvTogglePill(checked: Boolean) {
    val dimens = LocalTvDimens.current
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val thumbColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(
        modifier = Modifier
            .width(dimens.minTarget * 0.72f)
            .height(dimens.chipHeight * 0.52f)
    ) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        // Thumb travel is inset so the thumb never clips the rounded track ends.
        val inset = size.height * 0.18f
        val thumbRadius = size.height / 2f - inset
        val centerX = if (checked) size.width - thumbRadius - inset else thumbRadius + inset
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = androidx.compose.ui.geometry.Offset(centerX, size.height / 2f)
        )
    }
}

@Composable
private fun TvOptionStrip(
    options: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit
) {
    val dimens = LocalTvDimens.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
    ) {
        items(options) { option ->
            TvChoiceChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = label(option)
            )
        }
    }
}

/**
 * Subtitle languages as chips.
 *
 * The phone opens a sheet with a search field; a remote has neither, so the full list of
 * subtitle languages renders as an inline chip strip that expands under its row.
 */
@Composable
private fun TvLanguageGroupRow(
    title: String,
    selected: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onToggleLanguage: (code: String, add: Boolean) -> Unit
) {
    val dimens = LocalTvDimens.current
    val languages = remember { SavedSubtitleRepository.COMMON_LANGUAGES }
    TvListRow(
        onClick = onToggle,
        title = title,
        selected = expanded,
        trailing = {
            Text(
                text = if (selected.isEmpty()) {
                    stringResource(R.string.st_data_mobile_remember_none)
                } else {
                    selected.size.toString()
                },
                style = MaterialTheme.typography.titleSmall
            )
        }
    )
    if (expanded) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.paddingSmall),
            horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
        ) {
            items(languages) { code ->
                TvChoiceChip(
                    selected = code in selected,
                    onClick = { onToggleLanguage(code, code !in selected) },
                    label = SavedSubtitleRepository.languageName(code)
                )
            }
        }
    }
}
