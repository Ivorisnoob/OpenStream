@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.ivor.openanime.presentation.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Represents available quality options parsed from ExoPlayer tracks.
 */
data class QualityOption(
    val label: String,
    val width: Int,
    val height: Int,
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

private enum class SettingsPage {
    MAIN, QUALITY, SPEED, SUBTITLES
}

val SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@Composable
fun PlayerSettingsSheet(
    onDismiss: () -> Unit,
    qualityOptions: List<QualityOption>,
    selectedQuality: QualityOption?,
    onQualitySelected: (QualityOption) -> Unit,
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitle: SubtitleOption?,
    onSubtitleSelected: (SubtitleOption?) -> Unit,
    subtitleLoadingState: SubtitleLoadingState = SubtitleLoadingState.IDLE
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState == SettingsPage.MAIN) {
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
                SettingsPage.MAIN -> MainSettingsMenu(
                    currentQualityLabel = selectedQuality?.label ?: "Auto",
                    currentSpeedLabel = formatSpeedLabel(currentSpeed),
                    currentSubtitleLabel = selectedSubtitle?.label ?: "Off",
                    hasSubtitles = subtitleOptions.isNotEmpty(),
                    onQualityClick = { currentPage = SettingsPage.QUALITY },
                    onSpeedClick = { currentPage = SettingsPage.SPEED },
                    onSubtitlesClick = { currentPage = SettingsPage.SUBTITLES }
                )

                SettingsPage.QUALITY -> QualitySettingsMenu(
                    options = qualityOptions,
                    selected = selectedQuality,
                    onSelect = { option ->
                        onQualitySelected(option)
                        currentPage = SettingsPage.MAIN
                    },
                    onBack = { currentPage = SettingsPage.MAIN }
                )

                SettingsPage.SPEED -> SpeedSettingsMenu(
                    currentSpeed = currentSpeed,
                    onSelect = { speed ->
                        onSpeedSelected(speed)
                        currentPage = SettingsPage.MAIN
                    },
                    onBack = { currentPage = SettingsPage.MAIN }
                )

                SettingsPage.SUBTITLES -> SubtitleSettingsMenu(
                    options = subtitleOptions,
                    selected = selectedSubtitle,
                    onSelect = { option ->
                        onSubtitleSelected(option)
                        currentPage = SettingsPage.MAIN
                    },
                    loadingState = subtitleLoadingState,
                    onBack = { currentPage = SettingsPage.MAIN }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MainSettingsMenu(
    currentQualityLabel: String,
    currentSpeedLabel: String,
    currentSubtitleLabel: String,
    hasSubtitles: Boolean,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSubtitlesClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        ListItem(
            headlineContent = { Text("Quality") },
            supportingContent = { Text(currentQualityLabel) },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.HighQuality,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onQualityClick)
        )

        ListItem(
            headlineContent = { Text("Playback Speed") },
            supportingContent = { Text(currentSpeedLabel) },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onSpeedClick)
        )

        if (hasSubtitles) {
            ListItem(
                headlineContent = { Text("Subtitles / CC") },
                supportingContent = { Text(currentSubtitleLabel) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onSubtitlesClick)
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
    onSelect: (QualityOption) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SubPageHeader(title = "Quality", onBack = onBack)

        if (options.isEmpty()) {
            Text(
                text = "No quality options available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            options.forEach { option ->
                val isSelected = option == selected
                ListItem(
                    headlineContent = {
                        Text(
                            text = option.label,
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
                    modifier = Modifier.clickable { onSelect(option) }
                )
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

@Composable
private fun SubtitleSettingsMenu(
    options: List<SubtitleOption>,
    selected: SubtitleOption?,
    onSelect: (SubtitleOption?) -> Unit,
    loadingState: SubtitleLoadingState,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // Sort options: English (Extracted) > English > Others
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

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
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

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
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

private fun formatSpeedLabel(speed: Float): String {
    return if (speed == 1.0f) "Normal" else "${speed}x"
}
