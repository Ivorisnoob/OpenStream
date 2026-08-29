package com.ivor.openstream.presentation.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.Locale

private val ExpressiveDefaultEffects = CubicBezierEasing(0.34f, 0.80f, 0.34f, 1.00f)
private const val DurationEffectsDefault = 200

private val TopScrimColors = listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
private val BottomScrimColors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isFullscreen: Boolean = false,
    title: String,
    subtitle: String = "",
    sourceLabel: String? = null,
    qualityLabel: String = "Auto",
    hasSubtitles: Boolean = false,
    currentTime: Long,
    totalTime: Long,
    onPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onForward: () -> Unit,
    onRewind: () -> Unit,
    onNextClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onSourcesClick: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    onSubtitlesClick: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val duration = totalTime.coerceAtLeast(0L)
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val currentProgress = if (duration > 0) {
        (currentTime.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val sliderValue = if (isDragging) dragProgress else currentProgress

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)),
        exit = fadeOut(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(TopScrimColors))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(
                        start = if (isFullscreen) 16.dp else 4.dp,
                        end = if (isFullscreen) 16.dp else 4.dp,
                        top = 8.dp,
                        bottom = 18.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveBackButton(
                    onClick = onBackClick,
                    containerColor = Color.Black.copy(alpha = 0.42f),
                    contentColor = Color.White
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isFullscreen) {
                    sourceLabel?.let { label ->
                        PlayerHudChip(
                            icon = Icons.Default.Dns,
                            label = label,
                            contentDescription = "Playback source: $label",
                            onClick = onSourcesClick
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    PlayerHudChip(
                        icon = Icons.Default.HighQuality,
                        label = qualityLabel,
                        contentDescription = "Video quality: $qualityLabel",
                        onClick = onQualityClick
                    )
                    Spacer(Modifier.width(8.dp))
                } else if (sourceLabel != null) {
                    PlayerHudIconButton(
                        icon = Icons.Default.Dns,
                        contentDescription = "Playback source: $sourceLabel",
                        onClick = onSourcesClick
                    )
                }

                PlayerHudIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Playback settings",
                    onClick = onSettingsClick
                )
                PlayerHudIconButton(
                    icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                    onClick = onFullscreenToggle
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(BottomScrimColors))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        horizontal = if (isFullscreen) 24.dp else 12.dp,
                        vertical = if (isFullscreen) 18.dp else 8.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isFullscreen) 10.dp else 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            isDragging = true
                            dragProgress = it
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            if (duration > 0) onSeek((dragProgress * duration).toLong())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onRewind,
                        modifier = Modifier.size(if (isFullscreen) 48.dp else 40.dp),
                        shape = ExpressiveShapes.medium,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 seconds")
                    }

                    Spacer(Modifier.width(if (isFullscreen) 14.dp else 10.dp))

                    FilledIconButton(
                        onClick = onPauseToggle,
                        modifier = Modifier.size(if (isFullscreen) 68.dp else 54.dp),
                        shape = ExpressiveShapes.extraLarge,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isBuffering) {
                            LoadingIndicator(
                                modifier = Modifier.size(if (isFullscreen) 34.dp else 28.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                                        (scaleOut() + fadeOut())
                                },
                                label = "PlayPauseIcon"
                            ) { playing ->
                                Icon(
                                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    modifier = Modifier.size(if (isFullscreen) 34.dp else 28.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(if (isFullscreen) 14.dp else 10.dp))

                    FilledTonalIconButton(
                        onClick = onForward,
                        modifier = Modifier.size(if (isFullscreen) 48.dp else 40.dp),
                        shape = ExpressiveShapes.medium,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds")
                    }

                    onNextClick?.let { next ->
                        Spacer(Modifier.width(if (isFullscreen) 12.dp else 6.dp))
                        IconButton(onClick = next) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next episode",
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    if (isFullscreen && hasSubtitles) {
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = onSubtitlesClick) {
                            Icon(
                                Icons.Default.ClosedCaption,
                                contentDescription = "Subtitle settings",
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHudChip(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        shape = ExpressiveShapes.small,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.Black.copy(alpha = 0.42f),
            labelColor = Color.White,
            leadingIconContentColor = Color.White
        ),
        modifier = Modifier.widthIn(max = 140.dp)
    )
}

@Composable
private fun PlayerHudIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
