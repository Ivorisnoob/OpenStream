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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.Locale

private val ExpressiveDefaultEffects = CubicBezierEasing(0.34f, 0.80f, 0.34f, 1.00f)
private const val DurationEffectsDefault = 200

/** Sizing for the two layouts: the compact inline player and the immersive fullscreen one. */
private data class ControlMetrics(
    val edgePadding: Dp,
    val centerGap: Dp,
    val seekButton: Dp,
    val seekIcon: Dp,
    val playButton: Dp,
    val playIcon: Dp
)

private val InlineMetrics = ControlMetrics(
    edgePadding = 4.dp,
    centerGap = 28.dp,
    seekButton = 44.dp,
    seekIcon = 26.dp,
    playButton = 60.dp,
    playIcon = 32.dp
)

private val FullscreenMetrics = ControlMetrics(
    edgePadding = 16.dp,
    centerGap = 56.dp,
    seekButton = 56.dp,
    seekIcon = 32.dp,
    playButton = 80.dp,
    playIcon = 42.dp
)

/**
 * Player chrome in three bands over the video: identity and tools on top, transport in the
 * optical centre, and the timeline on the bottom edge. The same structure scales between the
 * inline player and fullscreen, so muscle memory carries over when the phone is rotated.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    subtitlesEnabled: Boolean = false,
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
    videoScale: VideoScale = VideoScale.FIT,
    onVideoScaleClick: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val metrics = if (isFullscreen) FullscreenMetrics else InlineMetrics

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)),
        exit = fadeOut(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        0.3f to Color.Black.copy(alpha = 0.28f),
                        0.7f to Color.Black.copy(alpha = 0.28f),
                        1f to Color.Black.copy(alpha = 0.8f)
                    )
                )
                // Fullscreen ignores the display cutout: controls keep their normal edge margin instead of
                // shifting sideways around the notch, and the bars are hidden anyway.
        ) {
            TopBar(
                isFullscreen = isFullscreen,
                metrics = metrics,
                title = title,
                subtitle = subtitle,
                sourceLabel = sourceLabel,
                qualityLabel = qualityLabel,
                hasSubtitles = hasSubtitles,
                subtitlesEnabled = subtitlesEnabled,
                onBackClick = onBackClick,
                onSourcesClick = onSourcesClick,
                onQualityClick = onQualityClick,
                onSubtitlesClick = onSubtitlesClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            TransportControls(
                metrics = metrics,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onRewind = onRewind,
                onPauseToggle = onPauseToggle,
                onForward = onForward,
                modifier = Modifier.align(Alignment.Center)
            )

            Timeline(
                isFullscreen = isFullscreen,
                metrics = metrics,
                currentTime = currentTime,
                totalTime = totalTime,
                onSeek = onSeek,
                onNextClick = onNextClick,
                onFullscreenToggle = onFullscreenToggle,
                videoScale = videoScale,
                onVideoScaleClick = onVideoScaleClick,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun TopBar(
    isFullscreen: Boolean,
    metrics: ControlMetrics,
    title: String,
    subtitle: String,
    sourceLabel: String?,
    qualityLabel: String,
    hasSubtitles: Boolean,
    subtitlesEnabled: Boolean,
    onBackClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.edgePadding, vertical = if (isFullscreen) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveBackButton(
            onClick = onBackClick,
            containerColor = Color.Transparent,
            contentColor = Color.White
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 8.dp)
        ) {
            Text(
                text = title,
                style = if (isFullscreen) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = if (isFullscreen) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isFullscreen) {
            sourceLabel?.let { label ->
                HudChip(icon = Icons.Default.Dns, label = label, onClick = onSourcesClick)
            }
            HudChip(label = qualityLabel, onClick = onQualityClick)
        } else if (sourceLabel != null) {
            HudIconButton(Icons.Default.Dns, "Change source: $sourceLabel", onSourcesClick)
        }
        if (hasSubtitles) {
            HudIconButton(
                icon = if (subtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                contentDescription = "Subtitles",
                onClick = onSubtitlesClick
            )
        }
        HudIconButton(Icons.Default.Settings, "Playback settings", onSettingsClick)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportControls(
    metrics: ControlMetrics,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onRewind: () -> Unit,
    onPauseToggle: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(metrics.centerGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeekButton(Icons.Default.Replay10, "Rewind 10 seconds", metrics, onRewind)

        FilledIconButton(
            onClick = onPauseToggle,
            modifier = Modifier.size(metrics.playButton),
            shape = if (isPlaying) ExpressiveShapes.large else ExpressiveShapes.extraLarge,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isBuffering) {
                LoadingIndicator(
                    modifier = Modifier.size(metrics.playIcon),
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
                        modifier = Modifier.size(metrics.playIcon)
                    )
                }
            }
        }

        SeekButton(Icons.Default.Forward10, "Forward 10 seconds", metrics, onForward)
    }
}

@Composable
private fun SeekButton(
    icon: ImageVector,
    contentDescription: String,
    metrics: ControlMetrics,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(metrics.seekButton),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.32f),
            contentColor = Color.White
        )
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(metrics.seekIcon))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Timeline(
    isFullscreen: Boolean,
    metrics: ControlMetrics,
    currentTime: Long,
    totalTime: Long,
    onSeek: (Long) -> Unit,
    onNextClick: (() -> Unit)?,
    onFullscreenToggle: () -> Unit,
    videoScale: VideoScale,
    onVideoScaleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = totalTime.coerceAtLeast(0L)
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val playedProgress = if (duration > 0) (currentTime.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownTime = if (isDragging) (dragProgress * duration).toLong() else currentTime

    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = Color.White.copy(alpha = 0.28f)
    )
    val interactionSource = remember { MutableInteractionSource() }
    val sliderState = rememberSliderState()
    // Playback drives the slider except while the user is scrubbing it.
    LaunchedEffect(playedProgress, isDragging) {
        if (!isDragging) sliderState.value = playedProgress
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = metrics.edgePadding + 8.dp,
                end = metrics.edgePadding,
                bottom = if (isFullscreen) 8.dp else 0.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${formatTime(shownTime)} / ${formatTime(duration)}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Slider(
            state = sliderState,
            onValueChange = {
                isDragging = true
                dragProgress = it
                sliderState.value = it
            },
            onValueChangeFinished = {
                isDragging = false
                if (duration > 0) onSeek((dragProgress * duration).toLong())
            },
            enabled = duration > 0,
            colors = colors,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    thumbSize = DpSize(4.dp, if (isFullscreen) 22.dp else 18.dp)
                )
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 3.dp,
                    modifier = Modifier.height(if (isFullscreen) 6.dp else 4.dp)
                )
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        onNextClick?.let { next ->
            HudIconButton(Icons.Default.SkipNext, "Next episode", next)
        }
        // The inline player is already 16:9, so resizing only matters in fullscreen.
        if (isFullscreen) {
            HudIconButton(
                icon = videoScale.icon,
                contentDescription = "Video size: ${videoScale.label}. Tap to change",
                onClick = onVideoScaleClick
            )
        }
        HudIconButton(
            icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
            onClick = onFullscreenToggle
        )
    }
}

@Composable
private fun HudChip(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        shape = ExpressiveShapes.small,
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White,
        modifier = Modifier
            .padding(end = 8.dp)
            .widthIn(max = 160.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HudIconButton(
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
