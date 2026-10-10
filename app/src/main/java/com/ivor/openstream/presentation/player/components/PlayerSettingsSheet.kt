@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.ivor.openstream.presentation.player.components

import androidx.annotation.StringRes
import com.ivor.openstream.R
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.openstream.presentation.player.CaptionStyleSettings
import com.ivor.openstream.presentation.player.ServersState
import com.ivor.openstream.presentation.player.session.SleepTimer
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.domain.model.WatchProgress
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.AutoAwesome
import io.github.ivorisnoob.smoothmotion.media3.SmoothMotion
import io.github.ivorisnoob.smoothmotion.media3.describe
import com.ivor.openstream.presentation.player.sourceSummary
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.Locale

/** A video rendition exposed by the current stream. */
data class QualityOption(
    val label: String,
    val width: Int,
    val height: Int,
    val bitrate: Int = -1,
    val isAuto: Boolean = false,
    /** Codec of the rendition as the stream declares it (hvc1, avc1, av01, ...). */
    val codec: String? = null
)

/** A subtitle track, either embedded in the stream or sideloaded from a URL. */
data class SubtitleOption(
    val label: String,
    val trackIndex: Int,
    val groupIndex: Int,
    val isDisabled: Boolean = false,
    val url: String? = null,
    val subLabel: String? = null,
    /** ISO 639 code when the track or file declares one; used to remember the user's choice. */
    val language: String? = null
)

/** Stored as the preferred subtitle language when the user turned subtitles off. */
const val SUBTITLES_OFF = "off"

enum class SubtitleLoadingState { IDLE, LOADING, SUCCESS, ERROR }

/** An audio track carried inside the stream (HLS/DASH renditions or MP4 tracks). */
data class AudioOption(
    val label: String,
    val language: String?,
    val groupIndex: Int,
    val trackIndex: Int,
    val detail: String?,
    val isSelected: Boolean
)

/**
 * Whether an audio track is the title's original language or a dub. Streams use ISO 639-2
 * (`jpn`) as often as TMDB's ISO 639-1 (`ja`), so both sides are compared as three-letter codes.
 */
enum class AudioKind(val label: String, @StringRes val labelRes: Int) {
    ORIGINAL("Original", R.string.player_original),
    DUB("Dub", R.string.player_dub)
}

fun AudioOption.kind(originalLanguage: String?): AudioKind? {
    val track = language?.takeUnless { it.isBlank() || it == "und" } ?: return null
    val original = originalLanguage?.takeUnless { it.isBlank() } ?: return null
    return if (iso3(track) == iso3(original)) AudioKind.ORIGINAL else AudioKind.DUB
}

/** True when both codes name the same language, whether written as `en`, `eng` or `en-US`. */
fun sameLanguage(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    return iso3(a) == iso3(b)
}

private fun iso3(code: String): String =
    runCatching { Locale.forLanguageTag(code.replace('_', '-')).isO3Language }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: code.lowercase()

enum class PlayerSettingsPage(val title: String, @StringRes val titleRes: Int) {
    MAIN("Playback", R.string.st_playback),
    SOURCES("Sources", R.string.sheet_sources),
    AUDIO("Audio", R.string.sheet_audio),
    QUALITY("Quality", R.string.sheet_quality),
    SPEED("Speed", R.string.sheet_speed),
    SUBTITLES("Subtitles", R.string.sheet_subtitles),
    CAPTIONS("Caption style", R.string.sheet_caption_style),
    SLEEP("Sleep timer", R.string.sheet_sleep_timer),
    EPISODES("Episodes", R.string.details_episodes)
}

val SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

/** Everything the settings surface shows and changes, gathered so both hosts share one contract. */
class PlayerSettingsModel(
    val sourceLabel: String?,
    val sourceSummary: String?,
    val canChangeSource: Boolean,
    val serversState: ServersState,
    val qualityOptions: List<QualityOption>,
    val selectedQuality: QualityOption?,
    val activeVideoHeight: Int,
    val currentSpeed: Float,
    val subtitleOptions: List<SubtitleOption>,
    val selectedSubtitle: SubtitleOption?,
    val subtitleLoadingState: SubtitleLoadingState,
    val captionSettings: CaptionStyleSettings,
    val audioOptions: List<AudioOption>,
    val originalLanguage: String?,
    /** Shift applied to sideloaded subtitle cues; positive shows them later. */
    val subtitleOffsetMs: Long = 0L,
    val sleepTimer: SleepTimer? = null,
    /** This season's episodes, for the in-player list (empty for movies). */
    val episodes: List<EpisodeDto> = emptyList(),
    val currentEpisode: Int = 0,
    val episodeProgress: Map<Pair<Int, Int>, WatchProgress> = emptyMap(),
    val smoothMotion: SmoothMotion? = null
)

class PlayerSettingsActions(
    val sources: SourcesPageActions,
    val onQualitySelected: (QualityOption) -> Unit,
    val onSpeedSelected: (Float) -> Unit,
    val onSubtitleSelected: (SubtitleOption?) -> Unit,
    val onCaptionSettingsChange: (CaptionStyleSettings) -> Unit,
    val onAudioSelected: (AudioOption) -> Unit,
    val onSubtitleOffsetChange: (Long) -> Unit = {},
    val onSleepTimerChange: (SleepTimer?) -> Unit = {},
    val onEpisodeSelected: (EpisodeDto) -> Unit = {},
    val onSmoothMotionToggle: ((Boolean) -> Unit)? = null
)

/**
 * Playback settings. Inline, it is a bottom sheet below the video. In fullscreen it is a side
 * panel drawn inside the player itself: a sheet or dialog opens its own window, which would bring
 * the hidden system bars back and break immersive mode.
 */
@Composable
fun PlayerSettingsHost(
    visible: Boolean,
    isFullscreen: Boolean,
    initialPage: PlayerSettingsPage,
    model: PlayerSettingsModel,
    actions: PlayerSettingsActions,
    onDismiss: () -> Unit
) {
    PlayerPanelHost(visible = visible, isFullscreen = isFullscreen, onDismiss = onDismiss) { listModifier, onClose ->
        SettingsContent(
            initialPage = initialPage,
            model = model,
            actions = actions,
            onClose = onClose,
            onDismiss = onDismiss,
            listModifier = listModifier
        )
    }
}

@Composable
private fun ColumnScope.SettingsContent(
    initialPage: PlayerSettingsPage,
    model: PlayerSettingsModel,
    actions: PlayerSettingsActions,
    onClose: (() -> Unit)?,
    onDismiss: () -> Unit,
    listModifier: Modifier
) {
    var page by remember(initialPage) { mutableStateOf(initialPage) }
    val sourceFilter = rememberSourceFilter()
    // Picking a source switches the stream, so the panel gets out of the way.
    val sourceActions = remember(actions, onDismiss) {
        SourcesPageActions(
            onSelect = { server ->
                actions.sources.onSelect(server)
                onDismiss()
            },
            onRetry = actions.sources.onRetry,
            onDownload = actions.sources.onDownload
        )
    }
    val goHome = { page = PlayerSettingsPage.MAIN }
    BackHandler(enabled = page != PlayerSettingsPage.MAIN, onBack = goHome)

    PanelHeader(
        title = stringResource(page.titleRes),
        subtitle = if (page == PlayerSettingsPage.SOURCES) sourcesStatus(model.serversState) else null,
        onClose = onClose,
        onBack = goHome.takeIf { page != PlayerSettingsPage.MAIN },
        actions = {
            if (page == PlayerSettingsPage.SOURCES) SourcesRefreshAction(model.serversState, actions.sources.onRetry)
        }
    )

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != PlayerSettingsPage.MAIN
            (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut()) using
                SizeTransform(clip = false)
        },
        label = "SettingsPage",
        modifier = listModifier
    ) { current ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            when (current) {
                PlayerSettingsPage.MAIN -> mainPage(model, actions, onNavigate = { page = it })
                PlayerSettingsPage.SOURCES -> sourcesPage(model.serversState, sourceFilter, sourceActions)
                PlayerSettingsPage.AUDIO -> audioPage(model, actions, sourceActions, onDone = goHome)
                PlayerSettingsPage.QUALITY -> qualityPage(
                    model = model,
                    actions = actions,
                    onDone = goHome,
                    onOpenSources = { page = PlayerSettingsPage.SOURCES }
                )
                PlayerSettingsPage.SPEED -> speedPage(model, actions)
                PlayerSettingsPage.SUBTITLES -> subtitlesPage(model, actions, onDone = goHome)
                PlayerSettingsPage.CAPTIONS -> captionsPage(model, actions)
                PlayerSettingsPage.SLEEP -> sleepPage(model, actions, onDone = goHome)
                PlayerSettingsPage.EPISODES -> episodesPage(model, onSelect = { episode ->
                    actions.onEpisodeSelected(episode)
                    onDismiss()
                })
            }
        }
    }
}

// region Main page

private fun LazyListScope.mainPage(
    model: PlayerSettingsModel,
    actions: PlayerSettingsActions,
    onNavigate: (PlayerSettingsPage) -> Unit
) {
    val isResolving = model.serversState is ServersState.Resolving
    val hasSubtitles = model.subtitleOptions.isNotEmpty()
    val rows = buildList {
        if (model.episodes.isNotEmpty()) {
            add(
                MainRow(
                    icon = Icons.Default.VideoLibrary,
                    key = "episodes",
                    title = { stringResource(R.string.details_episodes) },
                    value = { stringResource(R.string.misc_episode_number, model.currentEpisode) },
                    onClick = { onNavigate(PlayerSettingsPage.EPISODES) }
                )
            )
        }
        add(
            MainRow(
                icon = Icons.Default.Dns,
                key = "source",
                title = { stringResource(R.string.sheet_source) },
                value = {
                    model.sourceLabel ?: if (isResolving) stringResource(R.string.sheet_searching)
                    else stringResource(R.string.sheet_offline_source)
                },
                supporting = { model.sourceSummary },
                enabled = model.canChangeSource,
                busy = isResolving,
                onClick = { onNavigate(PlayerSettingsPage.SOURCES) }
            )
        )
        val selectedAudio = model.audioOptions.firstOrNull { it.isSelected }
        val otherLanguages = model.serversState.otherLanguageServers()
        add(
            MainRow(
                icon = Icons.Default.RecordVoiceOver,
                key = "audio",
                title = { stringResource(R.string.sheet_audio) },
                value = {
                    selectedAudio?.let { audio ->
                        listOfNotNull(
                            audio.label,
                            audio.kind(model.originalLanguage)?.labelRes?.let { stringResource(it) }
                        ).joinToString(" · ")
                    } ?: stringResource(R.string.sheet_default)
                },
                supporting = {
                    when {
                        otherLanguages.isNotEmpty() -> stringResource(R.string.audio_more_sources, otherLanguages.size)
                        model.audioOptions.size > 1 -> stringResource(R.string.audio_languages_count, model.audioOptions.size)
                        else -> null
                    }
                },
                enabled = model.audioOptions.size > 1 || otherLanguages.isNotEmpty(),
                onClick = { onNavigate(PlayerSettingsPage.AUDIO) }
            )
        )
        add(
            MainRow(
                icon = Icons.Default.HighQuality,
                key = "quality",
                title = { stringResource(R.string.sheet_quality) },
                value = { qualityDisplayLabel(model.selectedQuality, model.activeVideoHeight) },
                onClick = { onNavigate(PlayerSettingsPage.QUALITY) }
            )
        )
        if (model.smoothMotion != null) {
            val sm = model.smoothMotion
            add(
                MainRow(
                    icon = Icons.Default.AutoAwesome,
                    key = "smooth-motion",
                    title = { stringResource(R.string.st_smooth_motion) },
                    value = {
                        if (sm.enabled) sm.status.value.describe()
                        else stringResource(R.string.misc_off)
                    },
                    onClick = { actions.onSmoothMotionToggle?.invoke(!sm.enabled) }
                )
            )
        }
        add(
            MainRow(
                icon = Icons.Default.Speed,
                key = "speed",
                title = { stringResource(R.string.sheet_speed) },
                value = { formatSpeedLabel(model.currentSpeed) },
                onClick = { onNavigate(PlayerSettingsPage.SPEED) }
            )
        )
        add(
            MainRow(
                icon = Icons.Default.ClosedCaption,
                key = "subtitles",
                title = { stringResource(R.string.sheet_subtitles) },
                value = {
                    when {
                        !hasSubtitles -> stringResource(R.string.misc_none_available)
                        model.selectedSubtitle == null || model.selectedSubtitle.isDisabled ->
                            stringResource(R.string.misc_off)
                        else -> model.selectedSubtitle.label
                    }
                },
                enabled = hasSubtitles,
                onClick = { onNavigate(PlayerSettingsPage.SUBTITLES) }
            )
        )
        add(
            MainRow(
                icon = Icons.Default.FormatSize,
                key = "caption-style",
                title = { stringResource(R.string.sheet_caption_style) },
                value = { "${model.captionSettings.textSizeSp.toInt()} sp" },
                onClick = { onNavigate(PlayerSettingsPage.CAPTIONS) }
            )
        )
        add(
            MainRow(
                icon = Icons.Default.Bedtime,
                key = "sleep",
                title = { stringResource(R.string.sheet_sleep_timer) },
                value = {
                    when (val timer = model.sleepTimer) {
                        null -> stringResource(R.string.misc_off)
                        is SleepTimer.EndOfEpisode -> stringResource(R.string.player_end_of_episode)
                        is SleepTimer.After -> timer.remainingLabel()
                    }
                },
                onClick = { onNavigate(PlayerSettingsPage.SLEEP) }
            )
        )
    }

    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
        SegmentedListItem(
            onClick = row.onClick,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
            enabled = row.enabled,
            colors = ListItemDefaults.segmentedColors(),
            leadingContent = { PanelIcon(row.icon, busy = row.busy) },
            supportingContent = row.supporting?.let { get ->
                { get()?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.value(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                    if (row.enabled) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        ) {
            Text(row.title(), fontWeight = FontWeight.SemiBold)
        }
    }
}

private class MainRow(
    val icon: ImageVector,
    val key: String,
    val title: @Composable () -> String,
    val value: @Composable () -> String,
    val supporting: @Composable (() -> String?)? = null,
    val enabled: Boolean = true,
    val busy: Boolean = false,
    val onClick: () -> Unit
)

// endregion

// region Quality

private fun LazyListScope.qualityPage(
    model: PlayerSettingsModel,
    actions: PlayerSettingsActions,
    onDone: () -> Unit,
    onOpenSources: () -> Unit
) {
    val options = model.qualityOptions
    if (options.none { !it.isAuto }) {
        item(key = "waiting") {
            PanelNotice(
                title = stringResource(R.string.player_waiting_tracks),
                body = stringResource(R.string.player_quality_once_started)
            )
        }
    }

    itemsIndexed(options, key = { _, option -> option.label }) { index, option ->
        val selected = if (option.isAuto) {
            model.selectedQuality?.isAuto != false
        } else {
            model.selectedQuality?.label == option.label
        }
        SelectableRow(
            selected = selected,
            index = index,
            count = options.size,
            title = option.label,
            supporting = qualityOptionDescription(option, model.activeVideoHeight),
            onClick = {
                actions.onQualitySelected(option)
                onDone()
            }
        )
    }

    if (options.count { !it.isAuto } == 1) {
        item(key = "single-track") {
            PanelNotice(
                title = stringResource(R.string.player_fixed_quality),
                body = stringResource(R.string.player_pick_resolution),
                actionLabel = if (model.canChangeSource) stringResource(R.string.sheet_sources) else null,
                onAction = onOpenSources,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

// endregion

// region Audio

private fun LazyListScope.audioPage(
    model: PlayerSettingsModel,
    actions: PlayerSettingsActions,
    sourceActions: SourcesPageActions,
    onDone: () -> Unit
) {
    val options = model.audioOptions
    if (options.isNotEmpty()) {
        item(key = "audio-this-stream") { AudioGroupLabel(stringResource(R.string.audio_in_stream)) }
    }
    itemsIndexed(options, key = { _, option -> "${option.groupIndex}:${option.trackIndex}" }) { index, option ->
        val kind = option.kind(model.originalLanguage)
        SelectableRow(
            selected = option.isSelected,
            index = index,
            count = options.size,
            title = option.label,
            supporting = listOfNotNull(
                when (kind) {
                    AudioKind.ORIGINAL -> stringResource(R.string.player_original_audio)
                    AudioKind.DUB -> stringResource(R.string.player_dub)
                    null -> null
                },
                option.detail
            ).joinToString(" · ").ifEmpty { null },
            onClick = {
                actions.onAudioSelected(option)
                onDone()
            }
        )
    }
    // Dubs usually come from a separate route rather than as a track in the same stream.
    val others = model.serversState.otherLanguageServers()
    if (others.isNotEmpty()) {
        item(key = "audio-other-sources") { AudioGroupLabel(stringResource(R.string.audio_from_other)) }
        itemsIndexed(others, key = { _, server -> "server:${server.id}" }) { index, server ->
            val haptics = LocalHapticFeedback.current
            SelectableRow(
                selected = false,
                index = index,
                count = others.size,
                title = server.audioLanguage.orEmpty(),
                supporting = listOfNotNull(
                    if (server.audioLanguage.equals(model.originalLanguageName(), ignoreCase = true)) {
                        stringResource(R.string.player_original_audio)
                    } else {
                        stringResource(R.string.player_dub)
                    },
                    stringResource(R.string.audio_from_server, server.name),
                    server.sourceSummary(LocalContext.current)
                ).joinToString(" · "),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    sourceActions.onSelect(server)
                }
            )
        }
    }
    item(key = "audio-note") {
        Text(
            text = stringResource(R.string.player_remember_choice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        )
    }
}

// endregion

// region Speed

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.speedPage(
    model: PlayerSettingsModel,
    actions: PlayerSettingsActions
) {
    item(key = "speed-value") {
        Text(
            text = if (model.currentSpeed == 1f) "1×" else "${formatSpeed(model.currentSpeed)}×",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
    item(key = "speed-options") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4
        ) {
            SPEED_OPTIONS.forEach { speed ->
                ToggleButton(
                    checked = speed == model.currentSpeed,
                    onCheckedChange = { actions.onSpeedSelected(speed) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (speed == 1f) stringResource(R.string.speed_normal) else "${formatSpeed(speed)}×", maxLines = 1)
                }
            }
        }
    }
}

// endregion

// region Subtitles

private fun LazyListScope.subtitlesPage(
    model: PlayerSettingsModel,
    actions: PlayerSettingsActions,
    onDone: () -> Unit
) {
    val sorted = model.subtitleOptions
        .filter { !it.isDisabled }
        .sortedWith(
            compareByDescending<SubtitleOption> { it.label.contains("English", ignoreCase = true) }
                .thenBy { it.label }
        )

    val active = model.selectedSubtitle
    // Only sideloaded files are timed by the app; embedded tracks are rendered by the player.
    if (active != null && !active.isDisabled && active.url != null) {
        item(key = "subtitle-sync") {
            SubtitleSyncRow(
                offsetMs = model.subtitleOffsetMs,
                onChange = actions.onSubtitleOffsetChange
            )
        }
    }

    item(key = "subtitle-list") {
        var query by remember { mutableStateOf("") }
        val filtered = if (query.isBlank()) sorted else sorted.filter { it.label.contains(query, ignoreCase = true) }
        val showSearch = sorted.size > 8
        val entries: List<SubtitleOption?> = listOf(null) + filtered

        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.dl_search_languages)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = ExpressiveShapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
            entries.forEachIndexed { index, option ->
                val selected = if (option == null) {
                    model.selectedSubtitle == null || model.selectedSubtitle.isDisabled
                } else {
                    option == model.selectedSubtitle
                }
                SelectableRow(
                    selected = selected,
                    index = index,
                    count = entries.size,
                    title = option?.label ?: stringResource(R.string.misc_off),
                    supporting = option?.subLabel,
                    loadingState = if (selected && option != null) model.subtitleLoadingState else null,
                    onClick = {
                        actions.onSubtitleSelected(option)
                        onDone()
                    }
                )
            }
            if (filtered.isEmpty() && query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.dl_no_language_match, query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SubtitleSyncRow(offsetMs: Long, onChange: (Long) -> Unit) {
    Surface(
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sheet_sync_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        offsetMs == 0L -> stringResource(R.string.player_in_time)
                        offsetMs > 0L -> stringResource(R.string.sync_later, formatOffset(offsetMs))
                        else -> stringResource(R.string.sync_earlier, formatOffset(-offsetMs))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (offsetMs != 0L) {
                TextButton(onClick = { onChange(0L) }) { Text(stringResource(R.string.action_reset)) }
            }
            FilledTonalIconButton(onClick = { onChange(offsetMs - SUBTITLE_OFFSET_STEP_MS) }) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.player_sub_earlier))
            }
            FilledTonalIconButton(onClick = { onChange(offsetMs + SUBTITLE_OFFSET_STEP_MS) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.player_sub_later))
            }
        }
    }
}

private const val SUBTITLE_OFFSET_STEP_MS = 250L

private fun formatOffset(ms: Long): String = String.format(Locale.US, "%.2f s", ms / 1000f)

// endregion

// region Episodes

private fun LazyListScope.episodesPage(
    model: PlayerSettingsModel,
    onSelect: (EpisodeDto) -> Unit
) {
    itemsIndexed(model.episodes, key = { _, episode -> "episode:${episode.id}" }) { index, episode ->
        val isCurrent = episode.episodeNumber == model.currentEpisode
        val progress = model.episodeProgress[episode.seasonNumber to episode.episodeNumber]
        SegmentedListItem(
            selected = isCurrent,
            onClick = { if (!isCurrent) onSelect(episode) },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = model.episodes.size),
            colors = ListItemDefaults.segmentedColors(),
            leadingContent = { EpisodeThumb(episode, progress) },
            supportingContent = {
                Text(
                    text = when {
                        isCurrent -> stringResource(R.string.er_now_playing)
                        progress?.completed == true -> stringResource(R.string.details_watched)
                        else -> episode.runtime?.takeIf { it > 0 }?.let { stringResource(R.string.misc_runtime_min, it) } ?: stringResource(R.string.misc_episode_number, episode.episodeNumber)
                    },
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        ) {
            Text(
                text = "${episode.episodeNumber}. ${episode.name}",
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// endregion

// region Sleep timer

private val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60, 90)

private fun LazyListScope.sleepPage(
    model: PlayerSettingsModel,
    actions: PlayerSettingsActions,
    onDone: () -> Unit
) {
    val current = model.sleepTimer
    // Choice payload: minutes, null for off, -1 for end of episode. Titles resolve
    // in the item content below so no composable call happens in this builder.
    val choices: List<Pair<Int?, () -> SleepTimer?>> = buildList {
        add(null to { null })
        SLEEP_TIMER_MINUTES.forEach { minutes ->
            add(minutes to { SleepTimer.After(minutes, System.currentTimeMillis() + minutes * 60_000L) })
        }
        add(-1 to { SleepTimer.EndOfEpisode })
    }
    itemsIndexed(choices, key = { index, _ -> "sleep:$index" }) { index, (minutes, build) ->
        val selectedMinutes = SLEEP_TIMER_MINUTES.getOrNull(index - 1)
        val selected = when {
            index == 0 -> current == null
            selectedMinutes != null -> (current as? SleepTimer.After)?.minutes == selectedMinutes
            else -> current == SleepTimer.EndOfEpisode
        }
        SelectableRow(
            selected = selected,
            index = index,
            count = choices.size,
            title = when (minutes) {
                null -> stringResource(R.string.misc_off)
                -1 -> stringResource(R.string.player_end_of_episode)
                else -> stringResource(R.string.sleep_minutes, minutes)
            },
            supporting = (current as? SleepTimer.After)?.takeIf { selected }?.remainingLabel(),
            onClick = {
                actions.onSleepTimerChange(build())
                onDone()
            }
        )
    }
}

@Composable
private fun SleepTimer.After.remainingLabel(): String {
    val minutesLeft = ((endsAtMs - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
    return stringResource(R.string.details_minutes_left, minutesLeft)
}

// endregion

// region Caption style

private fun LazyListScope.captionsPage(
    model: PlayerSettingsModel,
    actions: PlayerSettingsActions
) {
    val settings = model.captionSettings
    item(key = "caption-preview") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(Color.Black, ExpressiveShapes.large)
                .padding(vertical = 28.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.pf_where_going),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = settings.textSizeSp.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = settings.backgroundOpacity), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
    item(key = "caption-size") {
        LabeledSlider(
            label = stringResource(R.string.sheet_text_size),
            valueLabel = "${settings.textSizeSp.toInt()} sp",
            value = settings.textSizeSp,
            range = CaptionStyleSettings.MIN_TEXT_SIZE_SP..CaptionStyleSettings.MAX_TEXT_SIZE_SP,
            onChange = { actions.onCaptionSettingsChange(settings.copy(textSizeSp = it)) }
        )
    }
    item(key = "caption-background") {
        LabeledSlider(
            label = stringResource(R.string.sheet_background),
            valueLabel = "${(settings.backgroundOpacity * 100).toInt()}%",
            value = settings.backgroundOpacity,
            range = 0f..1f,
            onChange = { actions.onCaptionSettingsChange(settings.copy(backgroundOpacity = it)) }
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

// endregion

// region Shared rows

@Composable
internal fun SelectableRow(
    selected: Boolean,
    index: Int,
    count: Int,
    title: String,
    supporting: String?,
    onClick: () -> Unit,
    loadingState: SubtitleLoadingState? = null
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(),
        supportingContent = supporting?.let { text -> { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            when {
                loadingState == SubtitleLoadingState.LOADING -> LoadingIndicator(modifier = Modifier.size(24.dp))
                loadingState == SubtitleLoadingState.ERROR -> Icon(
                    Icons.Default.Error,
                    contentDescription = stringResource(R.string.cd_could_not_load),
                    tint = MaterialTheme.colorScheme.error
                )
                selected -> Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// endregion

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')

@Composable
private fun formatSpeedLabel(speed: Float): String =
    if (speed == 1f) stringResource(R.string.speed_normal) else "${formatSpeed(speed)}×"

@Composable
fun qualityDisplayLabel(selected: QualityOption?, activeVideoHeight: Int): String = when {
    selected != null && !selected.isAuto -> selected.label
    activeVideoHeight > 0 -> "${stringResource(R.string.sheet_auto)} · ${activeVideoHeight}p"
    else -> stringResource(R.string.sheet_auto)
}

@Composable
private fun qualityOptionDescription(option: QualityOption, activeVideoHeight: Int): String {
    if (option.isAuto) {
        val adaptive = stringResource(R.string.player_adapts_connection)
        return if (activeVideoHeight > 0) stringResource(R.string.quality_active_now, adaptive, activeVideoHeight)
        else adaptive
    }
    return buildList {
        if (option.width > 0 && option.height > 0) add("${option.width} × ${option.height}")
        if (option.bitrate > 0) add(String.format(Locale.US, "%.1f Mbps", option.bitrate / 1_000_000f))
        option.codec?.let { add(codecName(it)) }
    }.joinToString(" · ").ifEmpty { stringResource(R.string.track_fixed) }
}

/** Short name for a codec string from the stream (avc1.640028 -> H.264, hvc1.1.6... -> H.265). */
private fun codecName(codec: String): String = when {
    codec.startsWith("avc1", ignoreCase = true) ||
        codec.startsWith("avc3", ignoreCase = true) -> "H.264"
    codec.startsWith("hvc1", ignoreCase = true) ||
        codec.startsWith("hev1", ignoreCase = true) -> "H.265"
    codec.startsWith("av01", ignoreCase = true) -> "AV1"
    codec.startsWith("vp09", ignoreCase = true) -> "VP9"
    codec.startsWith("vp8", ignoreCase = true) -> "VP8"
    else -> codec.substringBefore('.')
}

@Composable
private fun AudioGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp)
    )
}

/** One server per spoken language the other sources offer, excluding what is playing now. */
private fun ServersState.otherLanguageServers(): List<VideoServer> {
    val activeId = when (this) {
        is ServersState.Resolving -> activeId
        is ServersState.Ready -> activeId
        else -> null
    }
    val active = availableServers.firstOrNull { it.id == activeId }
    return availableServers
        .filter { it.audioLanguage != null && it.id != activeId && it.audioLanguage != active?.audioLanguage }
        .distinctBy { it.audioLanguage }
}

private fun PlayerSettingsModel.originalLanguageName(): String? =
    originalLanguage?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }

