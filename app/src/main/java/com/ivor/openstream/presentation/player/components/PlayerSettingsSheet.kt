@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.ivor.openstream.presentation.player.components

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.Locale

/** A video rendition exposed by the current stream. */
data class QualityOption(
    val label: String,
    val width: Int,
    val height: Int,
    val bitrate: Int = -1,
    val isAuto: Boolean = false
)

/** A subtitle track, either embedded in the stream or sideloaded from a URL. */
data class SubtitleOption(
    val label: String,
    val trackIndex: Int,
    val groupIndex: Int,
    val isDisabled: Boolean = false,
    val url: String? = null,
    val subLabel: String? = null
)

enum class SubtitleLoadingState { IDLE, LOADING, SUCCESS, ERROR }

enum class PlayerSettingsPage(val title: String) {
    MAIN("Playback"),
    SOURCES("Sources"),
    QUALITY("Quality"),
    SPEED("Speed"),
    SUBTITLES("Subtitles"),
    CAPTIONS("Caption style")
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
    val captionSettings: CaptionStyleSettings
)

class PlayerSettingsActions(
    val sources: SourcesPageActions,
    val onQualitySelected: (QualityOption) -> Unit,
    val onSpeedSelected: (Float) -> Unit,
    val onSubtitleSelected: (SubtitleOption?) -> Unit,
    val onCaptionSettingsChange: (CaptionStyleSettings) -> Unit
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
        title = page.title,
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
                PlayerSettingsPage.MAIN -> mainPage(model, onNavigate = { page = it })
                PlayerSettingsPage.SOURCES -> sourcesPage(model.serversState, sourceFilter, sourceActions)
                PlayerSettingsPage.QUALITY -> qualityPage(
                    model = model,
                    actions = actions,
                    onDone = goHome,
                    onOpenSources = { page = PlayerSettingsPage.SOURCES }
                )
                PlayerSettingsPage.SPEED -> speedPage(model, actions)
                PlayerSettingsPage.SUBTITLES -> subtitlesPage(model, actions, onDone = goHome)
                PlayerSettingsPage.CAPTIONS -> captionsPage(model, actions)
            }
        }
    }
}

// region Main page

private fun LazyListScope.mainPage(
    model: PlayerSettingsModel,
    onNavigate: (PlayerSettingsPage) -> Unit
) {
    val isResolving = model.serversState is ServersState.Resolving
    val hasSubtitles = model.subtitleOptions.isNotEmpty()
    val rows = buildList {
        add(
            MainRow(
                icon = Icons.Default.Dns,
                title = "Source",
                value = model.sourceLabel ?: if (isResolving) "Searching…" else "Offline",
                supporting = model.sourceSummary,
                enabled = model.canChangeSource,
                busy = isResolving,
                onClick = { onNavigate(PlayerSettingsPage.SOURCES) }
            )
        )
        add(
            MainRow(
                icon = Icons.Default.HighQuality,
                title = "Quality",
                value = qualityDisplayLabel(model.selectedQuality, model.activeVideoHeight),
                onClick = { onNavigate(PlayerSettingsPage.QUALITY) }
            )
        )
        add(
            MainRow(
                icon = Icons.Default.Speed,
                title = "Speed",
                value = formatSpeedLabel(model.currentSpeed),
                onClick = { onNavigate(PlayerSettingsPage.SPEED) }
            )
        )
        add(
            MainRow(
                icon = Icons.Default.ClosedCaption,
                title = "Subtitles",
                value = when {
                    !hasSubtitles -> "None available"
                    model.selectedSubtitle == null || model.selectedSubtitle.isDisabled -> "Off"
                    else -> model.selectedSubtitle.label
                },
                enabled = hasSubtitles,
                onClick = { onNavigate(PlayerSettingsPage.SUBTITLES) }
            )
        )
        add(
            MainRow(
                icon = Icons.Default.FormatSize,
                title = "Caption style",
                value = "${model.captionSettings.textSizeSp.toInt()} sp",
                onClick = { onNavigate(PlayerSettingsPage.CAPTIONS) }
            )
        )
    }

    itemsIndexed(rows, key = { _, row -> row.title }) { index, row ->
        SegmentedListItem(
            onClick = row.onClick,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
            enabled = row.enabled,
            colors = ListItemDefaults.segmentedColors(),
            leadingContent = { PanelIcon(row.icon, busy = row.busy) },
            supportingContent = row.supporting?.let { text ->
                { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.value,
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
            Text(row.title, fontWeight = FontWeight.SemiBold)
        }
    }
}

private class MainRow(
    val icon: ImageVector,
    val title: String,
    val value: String,
    val supporting: String? = null,
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
                title = "Waiting for video tracks",
                body = "Quality options appear once the stream starts."
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
                title = "This source has one fixed quality",
                body = "Pick another source for a different resolution.",
                actionLabel = "Sources".takeIf { model.canChangeSource },
                onAction = onOpenSources,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
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
                    Text(if (speed == 1f) "Normal" else "${formatSpeed(speed)}×", maxLines = 1)
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
                    placeholder = { Text("Search languages") },
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
                    title = option?.label ?: "Off",
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
                    text = "No language matches \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
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
                text = "Where are you going?",
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
            label = "Text size",
            valueLabel = "${settings.textSizeSp.toInt()} sp",
            value = settings.textSizeSp,
            range = CaptionStyleSettings.MIN_TEXT_SIZE_SP..CaptionStyleSettings.MAX_TEXT_SIZE_SP,
            onChange = { actions.onCaptionSettingsChange(settings.copy(textSizeSp = it)) }
        )
    }
    item(key = "caption-background") {
        LabeledSlider(
            label = "Background",
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
                    contentDescription = "Could not load",
                    tint = MaterialTheme.colorScheme.error
                )
                selected -> Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
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

private fun formatSpeedLabel(speed: Float): String =
    if (speed == 1f) "Normal" else "${formatSpeed(speed)}×"

fun qualityDisplayLabel(selected: QualityOption?, activeVideoHeight: Int): String = when {
    selected != null && !selected.isAuto -> selected.label
    activeVideoHeight > 0 -> "Auto · ${activeVideoHeight}p"
    else -> "Auto"
}

private fun qualityOptionDescription(option: QualityOption, activeVideoHeight: Int): String {
    if (option.isAuto) {
        return if (activeVideoHeight > 0) "Adapts to your connection · ${activeVideoHeight}p now"
        else "Adapts to your connection"
    }
    return buildList {
        if (option.width > 0 && option.height > 0) add("${option.width} × ${option.height}")
        if (option.bitrate > 0) add(String.format(Locale.US, "%.1f Mbps", option.bitrate / 1_000_000f))
    }.joinToString(" · ").ifEmpty { "Fixed track" }
}
