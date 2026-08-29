@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.ivor.openstream.presentation.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ivor.openstream.presentation.player.CaptionStyleSettings
import com.ivor.openstream.ui.theme.ExpressiveShapes

/**
 * Represents available quality options parsed from ExoPlayer tracks.
 */
data class QualityOption(
    val label: String,
    val width: Int,
    val height: Int,
    val bitrate: Int = -1,
    val isAuto: Boolean = false
)

/**
 * Represents an available subtitle/CC track.
 */
data class SubtitleOption(
    val label: String,
    val trackIndex: Int,
    val groupIndex: Int,
    val isDisabled: Boolean = false,
    val url: String? = null,
    val subLabel: String? = null
)

enum class SubtitleLoadingState { IDLE, LOADING, SUCCESS, ERROR }

enum class PlayerSettingsPage {
    MAIN, QUALITY, SPEED, SUBTITLES, CAPTIONS
}

val SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@Composable
fun PlayerSettingsDialog(
    onDismiss: () -> Unit,
    initialPage: PlayerSettingsPage = PlayerSettingsPage.MAIN,
    sourceLabel: String?,
    sourceSummary: String?,
    sourceCount: Int,
    canChangeSource: Boolean,
    isResolvingSources: Boolean,
    onSourceClick: () -> Unit,
    qualityOptions: List<QualityOption>,
    selectedQuality: QualityOption?,
    activeVideoHeight: Int,
    onQualitySelected: (QualityOption) -> Unit,
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitle: SubtitleOption?,
    onSubtitleSelected: (SubtitleOption?) -> Unit,
    subtitleLoadingState: SubtitleLoadingState = SubtitleLoadingState.IDLE,
    captionSettings: CaptionStyleSettings = CaptionStyleSettings(),
    onCaptionSettingsChange: (CaptionStyleSettings) -> Unit = {}
) {
    var currentPage by remember(initialPage) { mutableStateOf(initialPage) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ExpressiveShapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = 560.dp)
                .clip(ExpressiveShapes.extraLarge)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (currentPage == PlayerSettingsPage.MAIN) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Playback",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Tune this session without leaving the story",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (targetState == PlayerSettingsPage.MAIN) {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut())
                        } else {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it } + fadeOut())
                        }
                    },
                    label = "SettingsPageTransition"
                ) { page ->
                    when (page) {
                        PlayerSettingsPage.MAIN -> MainSettingsMenu(
                            sourceLabel = sourceLabel,
                            sourceSummary = sourceSummary,
                            sourceCount = sourceCount,
                            canChangeSource = canChangeSource,
                            isResolvingSources = isResolvingSources,
                            currentQualityLabel = qualityDisplayLabel(selectedQuality, activeVideoHeight),
                            currentSpeedLabel = formatSpeedLabel(currentSpeed),
                            currentSubtitleLabel = selectedSubtitle?.label ?: "Off",
                            captionStyleLabel = "${captionSettings.textSizeSp.toInt()}sp · ${(captionSettings.backgroundOpacity * 100).toInt()}% bg",
                            hasSubtitles = subtitleOptions.isNotEmpty(),
                            onSourceClick = onSourceClick,
                            onQualityClick = { currentPage = PlayerSettingsPage.QUALITY },
                            onSpeedClick = { currentPage = PlayerSettingsPage.SPEED },
                            onSubtitlesClick = { currentPage = PlayerSettingsPage.SUBTITLES },
                            onCaptionStyleClick = { currentPage = PlayerSettingsPage.CAPTIONS }
                        )

                        PlayerSettingsPage.QUALITY -> QualitySettingsMenu(
                            options = qualityOptions,
                            selected = selectedQuality,
                            activeVideoHeight = activeVideoHeight,
                            canChangeSource = canChangeSource,
                            onChangeSource = onSourceClick,
                            onSelect = { option ->
                                onQualitySelected(option)
                                currentPage = PlayerSettingsPage.MAIN
                            },
                            onBack = { currentPage = PlayerSettingsPage.MAIN }
                        )

                        PlayerSettingsPage.SPEED -> SpeedSettingsMenu(
                            currentSpeed = currentSpeed,
                            onSelect = { speed ->
                                onSpeedSelected(speed)
                                currentPage = PlayerSettingsPage.MAIN
                            },
                            onBack = { currentPage = PlayerSettingsPage.MAIN }
                        )

                        PlayerSettingsPage.SUBTITLES -> SubtitleSettingsMenu(
                            options = subtitleOptions,
                            selected = selectedSubtitle,
                            onSelect = { option ->
                                onSubtitleSelected(option)
                                currentPage = PlayerSettingsPage.MAIN
                            },
                            loadingState = subtitleLoadingState,
                            onBack = { currentPage = PlayerSettingsPage.MAIN }
                        )

                        PlayerSettingsPage.CAPTIONS -> CaptionStyleMenu(
                            settings = captionSettings,
                            onChange = onCaptionSettingsChange,
                            onBack = { currentPage = PlayerSettingsPage.MAIN }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MainSettingsMenu(
    sourceLabel: String?,
    sourceSummary: String?,
    sourceCount: Int,
    canChangeSource: Boolean,
    isResolvingSources: Boolean,
    currentQualityLabel: String,
    currentSpeedLabel: String,
    currentSubtitleLabel: String,
    captionStyleLabel: String,
    hasSubtitles: Boolean,
    onSourceClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onCaptionStyleClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            CurrentSourceCard(
                label = sourceLabel,
                summary = sourceSummary,
                sourceCount = sourceCount,
                canChange = canChangeSource,
                isResolving = isResolvingSources,
                onClick = onSourceClick
            )
        }
        item {
            SettingsAction(
                icon = Icons.Default.HighQuality,
                title = "Video quality",
                value = currentQualityLabel,
                supportingText = "Adaptive tracks inside this source",
                onClick = onQualityClick
            )
        }
        item {
            SettingsAction(
                icon = Icons.Default.Speed,
                title = "Playback speed",
                value = currentSpeedLabel,
                supportingText = "Change pace without changing pitch",
                onClick = onSpeedClick
            )
        }
        item {
            SettingsAction(
                icon = Icons.Default.ClosedCaption,
                title = "Subtitles",
                value = if (hasSubtitles) currentSubtitleLabel else "None found",
                supportingText = if (hasSubtitles) "Language and release source" else "This source has no caption tracks",
                enabled = hasSubtitles,
                onClick = onSubtitlesClick
            )
        }
        item {
            SettingsAction(
                icon = Icons.Default.FormatSize,
                title = "Caption appearance",
                value = captionStyleLabel,
                supportingText = "Text size and background contrast",
                onClick = onCaptionStyleClick
            )
        }
    }
}

@Composable
private fun CurrentSourceCard(
    label: String?,
    summary: String?,
    sourceCount: Int,
    canChange: Boolean,
    isResolving: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.large)
            .clickable(enabled = canChange, onClick = onClick),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = ExpressiveShapes.medium, color = MaterialTheme.colorScheme.primary) {
                if (isResolving) {
                    LoadingIndicator(
                        modifier = Modifier.padding(10.dp).size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "CURRENT SOURCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    label ?: if (isResolving) "Finding a route…" else "Unavailable",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    summary ?: if (canChange) "$sourceCount sources available" else "Stored on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    maxLines = 1
                )
            }
            if (canChange) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Change source")
            }
        }
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    title: String,
    value: String,
    supportingText: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.5f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
    }
}

@Composable
private fun SubPageHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun QualitySettingsMenu(
    options: List<QualityOption>,
    selected: QualityOption?,
    activeVideoHeight: Int,
    canChangeSource: Boolean,
    onChangeSource: () -> Unit,
    onSelect: (QualityOption) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SubPageHeader(title = "Video quality", onBack = onBack)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = ExpressiveShapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Source tracks, not source labels",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (activeVideoHeight > 0) {
                        "The player is currently receiving ${activeVideoHeight}p. Auto can move between the tracks listed below as your connection changes."
                    } else {
                        "Auto uses every rendition exposed by the current source and adapts to your connection."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
                )
            }
        }

        if (options.isEmpty()) {
            Text(
                text = "Waiting for video tracks…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(options, key = { "${it.label}-${it.width}-${it.height}" }) { option ->
                    val isSelected = option == selected
                    ListItem(
                        headlineContent = {
                            Text(
                                text = option.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        supportingContent = {
                            Text(
                                qualityOptionDescription(option, activeVideoHeight),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSelect(option) }
                    )
                }
            }
        }

        if (options.count { !it.isAuto } <= 1) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = ExpressiveShapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.68f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Only one fixed track here", fontWeight = FontWeight.Bold)
                        Text(
                            "For another resolution, choose a source that exposes more renditions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.76f)
                        )
                    }
                    if (canChangeSource) {
                        Spacer(Modifier.width(12.dp))
                        FilledTonalButton(onClick = onChangeSource, shape = ExpressiveShapes.small) {
                            Text("Sources")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedSettingsMenu(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SubPageHeader(title = "Playback Speed", onBack = onBack)

        Column {
             SPEED_OPTIONS.forEach { speed ->
                val isSelected = speed == currentSpeed
                ListItem(
                    headlineContent = {
                        Text(
                            text = formatSpeedLabel(speed),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSelect(speed) }
                )
            }
        }
    }
}

@Composable
private fun SubtitleSettingsMenu(
    options: List<SubtitleOption>,
    selected: SubtitleOption?,
    onSelect: (SubtitleOption?) -> Unit,
    loadingState: SubtitleLoadingState,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // Sort options logic (same as before)
    val sortedOptions = remember(options) {
        options.filter { !it.isDisabled }.sortedWith(
            compareByDescending<SubtitleOption> { it.label == "English (Extracted)" }
                .thenByDescending { it.label.contains("English", ignoreCase = true) }
                .thenBy { it.label }
        )
    }
    
    val filteredOptions = if (searchQuery.isEmpty()) {
        sortedOptions
    } else {
        sortedOptions.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SubPageHeader(title = "Subtitles / CC", onBack = onBack)

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search languages...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            // "Off" option
            item {
                val isOffSelected = selected == null || selected.isDisabled
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Off",
                            fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingContent = {
                        if (isOffSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSelect(null) }
                )
            }

            items(filteredOptions) { option ->
                val isSelected = option == selected
                ListItem(
                    headlineContent = {
                        Text(
                            text = option.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    supportingContent = option.subLabel?.let { 
                        { Text(text = it, style = MaterialTheme.typography.labelSmall) }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                when (loadingState) {
                                    SubtitleLoadingState.LOADING -> {
                                        LoadingIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    SubtitleLoadingState.ERROR -> {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = "Error",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else if (option.url != null) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Sideloadable",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSelect(option) }
                )
            }

            if (filteredOptions.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Text(
                        text = "No languages found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionStyleMenu(
    settings: CaptionStyleSettings,
    onChange: (CaptionStyleSettings) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SubPageHeader(title = "Caption style", onBack = onBack)

        // Live preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(ExpressiveShapes.medium)
                .background(Color.Black)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "The quick brown fox",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = settings.textSizeSp.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = settings.backgroundOpacity),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Text size
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Text size", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${settings.textSizeSp.toInt()}sp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = settings.textSizeSp,
                onValueChange = { onChange(settings.copy(textSizeSp = it)) },
                valueRange = CaptionStyleSettings.MIN_TEXT_SIZE_SP..CaptionStyleSettings.MAX_TEXT_SIZE_SP
            )
        }

        // Background opacity
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Background", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${(settings.backgroundOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = settings.backgroundOpacity,
                onValueChange = { onChange(settings.copy(backgroundOpacity = it)) },
                valueRange = 0f..1f
            )
        }
    }
}

private fun formatSpeedLabel(speed: Float): String {
    return if (speed == 1.0f) "Normal" else "${speed}x"
}

fun qualityDisplayLabel(selected: QualityOption?, activeVideoHeight: Int): String = when {
    selected?.isAuto != false && activeVideoHeight > 0 -> "Auto · ${activeVideoHeight}p now"
    selected != null -> selected.label
    activeVideoHeight > 0 -> "Auto · ${activeVideoHeight}p now"
    else -> "Auto"
}

private fun qualityOptionDescription(option: QualityOption, activeVideoHeight: Int): String {
    if (option.isAuto) {
        return if (activeVideoHeight > 0) {
            "Recommended · currently ${activeVideoHeight}p"
        } else {
            "Recommended · adapts to your connection"
        }
    }

    return buildList {
        if (option.width > 0 && option.height > 0) add("${option.width} × ${option.height}")
        if (option.bitrate > 0) add(String.format("%.1f Mbps", option.bitrate / 1_000_000f))
        if (isEmpty()) add("Fixed ${option.label} track")
    }.joinToString(" · ")
}
