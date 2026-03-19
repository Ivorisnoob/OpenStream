package com.ivor.openanime.presentation.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import com.ivor.openanime.presentation.components.ExpressiveBackButton
import com.ivor.openanime.ui.theme.ExpressiveShapes
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Expressive Motion Tokens
private val ExpressiveDefaultEffects = CubicBezierEasing(0.34f, 0.80f, 0.34f, 1.00f)
private const val DurationEffectsDefault = 200

// Scrim gradient that fades over 120dp so artwork breathes between top and bottom zones
private val TopScrimColors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
private val BottomScrimColors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))

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

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (duration > 0) currentTime.toFloat() / duration.toFloat() else 0f
    val sliderValue = if (isDragging) dragProgress else currentProgress

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)),
        exit = fadeOut(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Top Bar: Back · Title · Settings · Fullscreen ───────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(TopScrimColors))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveBackButton(onClick = onBackClick)

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Settings — utility action lives in top bar
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }

                    // Fullscreen toggle — utility, not a core playback control
                    IconButton(onClick = onFullscreenToggle) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit
                                          else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }

            // ── Centre: Buffering indicator ──────────────────────────────────
            if (isBuffering) {
                androidx.compose.material3.LoadingIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // ── Bottom Bar: Scrubber · Playback controls ──────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(BottomScrimColors))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    // Progress row: current time · Slider · total time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )

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
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Playback controls row — clear size hierarchy
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind — secondary action, tonal container
                        FilledTonalIconButton(
                            onClick = onRewind,
                            modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                            shape = ExpressiveShapes.medium,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10 seconds",
                                modifier = Modifier.size(IconButtonDefaults.largeIconSize)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Play/Pause — dominant primary action with animated icon swap
                        FilledIconButton(
                            onClick = onPauseToggle,
                            modifier = Modifier.size(72.dp),
                            shape = ExpressiveShapes.extraLarge,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) +
                                        fadeIn()) togetherWith
                                        (scaleOut() + fadeOut())
                                },
                                label = "PlayPauseIcon"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Forward — secondary action, tonal container
                        FilledTonalIconButton(
                            onClick = onForward,
                            modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                            shape = ExpressiveShapes.medium,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10 seconds",
                                modifier = Modifier.size(IconButtonDefaults.largeIconSize)
                            )
                        }

                        // Skip next — optional, subordinate
                        if (onNextClick != null) {
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(onClick = onNextClick) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next episode",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
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
