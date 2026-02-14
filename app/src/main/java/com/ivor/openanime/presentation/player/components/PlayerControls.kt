package com.ivor.openanime.presentation.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.ivor.openanime.presentation.components.ExpressiveBackButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isFullscreen: Boolean = false,
    title: String,
    currentTime: Long,
    totalTime: Long,
    onPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onForward: () -> Unit,
    onRewind: () -> Unit,
    onNextClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onFullscreenToggle: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val duration = if (totalTime > 0) totalTime else 0L
    
    // Local state for dragging the slider
    var isDragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (duration > 0) currentTime.toFloat() / duration.toFloat() else 0f
    val sliderValue = if (isDragging) dragProgress else currentProgress

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.4f))
                .fillMaxSize()
        ) {
            // Top Bar (Title and Options)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(if (isFullscreen) 16.dp else 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveBackButton(
                            onClick = onBackClick,
                            containerColor = Color.Transparent, // Already inside a gradient background
                            contentColor = Color.White
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            }

            // Center Controls (Play/Pause, Rewind, Forward) -- hidden when buffering
            if (!isBuffering) {
                val centerIconSize = if (isFullscreen) 64.dp else 48.dp
                val sideIconSize = if (isFullscreen) 40.dp else 32.dp
                val iconSize = if (isFullscreen) 32.dp else 24.dp
                val spacing = if (isFullscreen) 32.dp else 16.dp

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRewind, modifier = Modifier.size(sideIconSize)) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    IconButton(
                        onClick = onPauseToggle,
                        modifier = Modifier
                            .size(centerIconSize)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.extraLarge
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    IconButton(onClick = onForward, modifier = Modifier.size(sideIconSize)) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (onNextClick != null) {
                         IconButton(onClick = onNextClick, modifier = Modifier.size(sideIconSize)) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Episode",
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // "Next Episode" Button Overlay (Bottom Right, above seekbar)
            if (onNextClick != null && !isBuffering) {
                 Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 80.dp, end = 16.dp)
                ) {
                    Button(
                        onClick = onNextClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Text("Next Episode")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                    }
                }
            }

            // Bottom Controls (Seekbar, Time, Fullscreen)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(
                        start = if (isFullscreen) 16.dp else 8.dp,
                        end = if (isFullscreen) 16.dp else 8.dp,
                        top = if (isFullscreen) 16.dp else 0.dp,
                        bottom = if (isFullscreen) 8.dp else 0.dp
                    )
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { 
                        isDragging = true
                        dragProgress = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onSeek((dragProgress * duration).toLong())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time labels
                    Text(
                        text = "${formatTime(if (isDragging) (dragProgress * duration).toLong() else currentTime)} / ${formatTime(duration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )

                    // Fullscreen button
                    IconButton(onClick = onFullscreenToggle) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
