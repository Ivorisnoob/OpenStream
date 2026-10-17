package com.ivor.openstream.presentation.player.components
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale

import androidx.compose.animation.AnimatedContent
import com.ivor.openstream.R
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.PictureInPictureAlt
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
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.presentation.components.ExpressiveMotion
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.Locale

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
    seekButton = 48.dp,
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
    qualityLabel: String = stringResource(R.string.sheet_auto),
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
    onLockClick: () -> Unit = {},
    isRotationLocked: Boolean = false,
    onRotationLockToggle: () -> Unit = {},
    seekStepSeconds: Int = 10,
    videoScale: VideoScale = VideoScale.FIT,
    onVideoScaleClick: () -> Unit = {},
    /** Opens the Cast device picker; null hides the button (no Google Play services). */
    onCastClick: (() -> Unit)? = null,
    /** Pops the video out into picture-in-picture; null hides the button. */
    onPictureInPictureClick: (() -> Unit)? = null,
    /** Focus target for the control bar, so a D-pad can move focus from the video onto it. */
    controlsFocusRequester: FocusRequester? = null,
    onBackClick: () -> Unit
) {
    val metrics = if (isFullscreen) FullscreenMetrics else InlineMetrics
    // Video chrome fades on the theme's effects spec (instant with reduced motion).
    val chromeFade = ExpressiveMotion.finiteEffectsSpec()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(chromeFade),
        exit = fadeOut(chromeFade),
        modifier = modifier
    ) {
        val scrim = MaterialTheme.colorScheme.scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scrim.copy(alpha = 0.72f),
                        0.3f to scrim.copy(alpha = 0.28f),
                        0.7f to scrim.copy(alpha = 0.28f),
                        1f to scrim.copy(alpha = 0.8f)
                    )
                )
                // Explicitly a focus group while shown, so the video surface can hand focus over.
                .then(
                    controlsFocusRequester?.let { requester ->
                        Modifier.focusRequester(requester).focusGroup()
                    } ?: Modifier
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
                onLockClick = onLockClick,
                isRotationLocked = isRotationLocked,
                onRotationLockToggle = onRotationLockToggle,
                onCastClick = onCastClick,
                onPictureInPictureClick = onPictureInPictureClick,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            TransportControls(
                metrics = metrics,
                seekStepSeconds = seekStepSeconds,
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
    onLockClick: () -> Unit,
    isRotationLocked: Boolean,
    onRotationLockToggle: () -> Unit,
    onCastClick: (() -> Unit)?,
    onPictureInPictureClick: (() -> Unit)?,
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
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 8.dp)
        ) {
            Text(
                text = title,
                style = if (isFullscreen) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = if (isFullscreen) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isFullscreen) {
            HudIconButton(Icons.Default.Lock, stringResource(R.string.player_lock_screen), onLockClick)
            HudIconButton(
                icon = if (isRotationLocked) Icons.Default.ScreenLockRotation else Icons.Default.ScreenRotation,
                contentDescription = if (isRotationLocked) stringResource(R.string.player_unlock_rotation) else stringResource(R.string.player_lock_rotation),
                onClick = onRotationLockToggle
            )
            sourceLabel?.let { label ->
                HudChip(icon = Icons.Default.Dns, label = label, onClick = onSourcesClick)
            }
            HudChip(label = qualityLabel, onClick = onQualityClick)
        } else if (sourceLabel != null && (onCastClick == null || onPictureInPictureClick == null)) {
            // Inline space is tight: with Cast and PiP showing, sources stay under Settings.
            HudIconButton(Icons.Default.Dns, stringResource(R.string.player_change_source, sourceLabel), onSourcesClick)
        }
        if (hasSubtitles) {
            HudIconButton(
                icon = if (subtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                contentDescription = stringResource(R.string.player_subtitles),
                onClick = onSubtitlesClick
            )
        }
        onPictureInPictureClick?.let { HudIconButton(Icons.Default.PictureInPictureAlt, stringResource(R.string.player_pip), it) }
        onCastClick?.let { HudIconButton(Icons.Default.Cast, stringResource(R.string.player_cast_to_tv), it) }
        HudIconButton(Icons.Default.Settings, stringResource(R.string.player_playback_settings), onSettingsClick)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportControls(
    metrics: ControlMetrics,
    seekStepSeconds: Int,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onRewind: () -> Unit,
    onPauseToggle: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hoisted: AnimatedContent's transitionSpec isn't a composable context.
    val morphSpec = ExpressiveMotion.finiteEffectsSpec()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(metrics.centerGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeekButton(rewindIcon(seekStepSeconds), stringResource(R.string.player_rewind_seconds, seekStepSeconds), metrics, onRewind)

        val playInteractionSource = remember { MutableInteractionSource() }
        val isPlayFocused by playInteractionSource.collectIsFocusedAsState()
        FilledIconButton(
            onClick = onPauseToggle,
            interactionSource = playInteractionSource,
            modifier = Modifier
                .size(metrics.playButton)
                .then(
                    if (isPlayFocused) {
                        Modifier
                            .scale(1.12f)
                            .border(3.dp, MaterialTheme.colorScheme.onPrimary, if (isPlaying) ExpressiveShapes.large else ExpressiveShapes.extraLarge)
                    } else Modifier
                ),
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
                        (scaleIn(morphSpec) + fadeIn(morphSpec)) togetherWith
                            (scaleOut() + fadeOut())
                    },
                    label = "PlayPauseIcon"
                ) { playing ->
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) stringResource(R.string.cd_pause) else stringResource(R.string.details_play),
                        modifier = Modifier.size(metrics.playIcon)
                    )
                }
            }
        }

        SeekButton(forwardIcon(seekStepSeconds), stringResource(R.string.cd_forward_seconds, seekStepSeconds), metrics, onForward)
    }
}

/** Material has numbered icons for 5, 10 and 30 seconds; other steps use the plain arrows. */
private fun rewindIcon(seconds: Int): ImageVector = when (seconds) {
    5 -> Icons.Default.Replay5
    10 -> Icons.Default.Replay10
    30 -> Icons.Default.Replay30
    else -> Icons.Default.FastRewind
}

private fun forwardIcon(seconds: Int): ImageVector = when (seconds) {
    5 -> Icons.Default.Forward5
    10 -> Icons.Default.Forward10
    30 -> Icons.Default.Forward30
    else -> Icons.Default.FastForward
}

@Composable
private fun SeekButton(
    icon: ImageVector,
    contentDescription: String,
    metrics: ControlMetrics,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(metrics.seekButton)
            .then(
                if (isFocused) {
                    Modifier
                        .scale(1.15f)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else Modifier
            ),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.32f),
            contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
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
        inactiveTrackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.28f)
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
            color = MaterialTheme.colorScheme.inverseOnSurface,
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
                // TalkBack announces position as a percentage; the time text beside it
                // carries the exact value, so no new string resource is needed.
                .semantics {
                    contentDescription = "${formatTime(shownTime)} / ${formatTime(duration)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(playedProgress, 0f..1f)
                }
        )
        onNextClick?.let { next ->
            HudIconButton(Icons.Default.SkipNext, stringResource(R.string.action_next_episode), next)
        }
        // The inline player is already 16:9, so resizing only matters in fullscreen.
        if (isFullscreen) {
            HudIconButton(
                icon = videoScale.icon,
                contentDescription = stringResource(R.string.cd_video_size, stringResource(videoScale.labelRes)),
                onClick = onVideoScaleClick
            )
        }
        HudIconButton(
            icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = if (isFullscreen) stringResource(R.string.player_exit_fullscreen) else stringResource(R.string.player_enter_fullscreen),
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ExpressiveShapes.small,
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.14f),
        contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
        border = if (isFocused) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (isFocused) 8.dp else 0.dp,
        modifier = Modifier
            .padding(end = 8.dp)
            .widthIn(max = 160.dp)
            .then(if (isFocused) Modifier.scale(1.08f) else Modifier)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .then(
                if (isFocused) {
                    Modifier
                        .scale(1.15f)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else Modifier
            )
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
        )
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
