package com.ivor.openstream.presentation.player.components
import com.ivor.openstream.ui.theme.onScrim
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.HighQuality
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
    centerGap = 16.dp,
    seekButton = 48.dp,
    seekIcon = 26.dp,
    playButton = 60.dp,
    playIcon = 32.dp
)

private val FullscreenMetrics = ControlMetrics(
    edgePadding = 16.dp,
    centerGap = 28.dp,
    seekButton = 56.dp,
    seekIcon = 32.dp,
    playButton = 72.dp,
    playIcon = 42.dp
)

/**
 * Keep transport between the title and timeline. Short players use one footer row to leave
 * space for the video. Taller players give the timeline a row of its own.
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
    /** Takes the shader look off the picture and back, to compare; null hides the button. */
    onShaderLookToggle: (() -> Unit)? = null,
    /** Whether the shader look is on the picture, which is the toggle's selected state. */
    shaderLookShown: Boolean = true,
    currentTime: Long,
    totalTime: Long,
    onPauseToggle: () -> Unit,
    onChromeInteraction: (Boolean) -> Unit = {},
    onHeaderHeight: (Dp) -> Unit = {},
    onFooterHeight: (Dp) -> Unit = {},
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
    val chromeFade = ExpressiveMotion.finiteEffectsSpec()
    val latestInteraction by rememberUpdatedState(onChromeInteraction)
    var overflowOpen by remember { mutableStateOf(false) }
    var draggingTimeline by remember { mutableStateOf(false) }
    LaunchedEffect(overflowOpen, draggingTimeline, isVisible) {
        latestInteraction(isVisible && (overflowOpen || draggingTimeline))
        if (!isVisible) overflowOpen = false
    }
    DisposableEffect(Unit) { onDispose { latestInteraction(false) } }
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(chromeFade),
        exit = fadeOut(chromeFade),
        modifier = modifier
    ) {
        val scrim = MaterialTheme.colorScheme.scrim
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scrim.copy(alpha = 0.84f),
                        0.33f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to scrim.copy(alpha = 0.88f)
                    )
                )
                .then(
                    controlsFocusRequester?.let { Modifier.focusRequester(it).focusGroup() } ?: Modifier
                )
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        ) {
            val footerHasToolbar = maxHeight >= 248.dp
            val expanded = isFullscreen && footerHasToolbar
            val showTextActions = footerHasToolbar && maxWidth >= 640.dp
            val metrics = if (expanded) FullscreenMetrics else InlineMetrics
            val captionIcon = if (subtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled
            val fullscreenIcon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen
            val fullscreenLabel = stringResource(
                if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_enter_fullscreen
            )
            val rotationIcon = if (isRotationLocked) Icons.Default.ScreenLockRotation else Icons.Default.ScreenRotation
            val rotationLabel = stringResource(
                if (isRotationLocked) R.string.player_unlock_rotation else R.string.player_lock_rotation
            )
            val shaderIcon = if (shaderLookShown) Icons.Rounded.AutoFixHigh else Icons.Rounded.AutoFixOff
            val shaderLabel = stringResource(R.string.player_shader_look)
            val qualityAction = stringResource(R.string.player_quality_action, qualityLabel)
            val showCastInHeader = maxWidth >= 440.dp
            val hasOverflow = isFullscreen || onPictureInPictureClick != null ||
                (!showCastInHeader && onCastClick != null) ||
                !footerHasToolbar ||
                (!showTextActions && onShaderLookToggle != null)

            Column(Modifier.fillMaxSize()) {
                PlayerHeader(
                    title = title,
                    subtitle = subtitle,
                    expanded = expanded,
                    onBackClick = onBackClick,
                    modifier = Modifier
                        .onSizeChanged { onHeaderHeight(with(density) { it.height.toDp() }) }
                        .padding(horizontal = metrics.edgePadding)
                ) {
                    if (!footerHasToolbar && hasSubtitles) {
                        HudIconButton(captionIcon, stringResource(R.string.player_subtitles), onSubtitlesClick)
                    }
                    if (showCastInHeader) onCastClick?.let {
                        HudIconButton(Icons.Default.Cast, stringResource(R.string.player_cast_to_tv), it)
                    }
                    HudIconButton(Icons.Default.Settings, stringResource(R.string.player_playback_settings), onSettingsClick)
                    if (hasOverflow) Box {
                        HudIconButton(Icons.Default.MoreVert, stringResource(R.string.misc_more_options)) { overflowOpen = true }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            val close = { overflowOpen = false }
                            if (!footerHasToolbar) {
                                sourceLabel?.let { label ->
                                    PlayerMenuItem(Icons.Default.Dns, stringResource(R.string.player_change_source, label), close, onSourcesClick)
                                }
                                PlayerMenuItem(Icons.Default.HighQuality, qualityAction, close, onQualityClick)
                            }
                            if (!showCastInHeader) onCastClick?.let {
                                PlayerMenuItem(Icons.Default.Cast, stringResource(R.string.player_cast_to_tv), close, it)
                            }
                            onPictureInPictureClick?.let {
                                PlayerMenuItem(Icons.Default.PictureInPictureAlt, stringResource(R.string.player_pip), close, it)
                            }
                            if (isFullscreen) {
                                PlayerMenuItem(Icons.Default.Lock, stringResource(R.string.player_lock_screen), close, onLockClick)
                                PlayerMenuItem(rotationIcon, rotationLabel, close, onRotationLockToggle)
                                if (!footerHasToolbar) {
                                    val label = stringResource(R.string.cd_video_size, stringResource(videoScale.labelRes))
                                    PlayerMenuItem(videoScale.icon, label, close, onVideoScaleClick)
                                }
                            }
                            if (!showTextActions) onShaderLookToggle?.let {
                                val label = stringResource(
                                    if (shaderLookShown) R.string.player_hide_shader_look else R.string.player_show_shader_look
                                )
                                PlayerMenuItem(shaderIcon, label, close, it)
                            }
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TransportControls(
                        metrics = metrics,
                        seekStepSeconds = seekStepSeconds,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onRewind = onRewind,
                        onPauseToggle = onPauseToggle,
                        onForward = onForward
                    )
                }

                Column(
                    Modifier.fillMaxWidth()
                        .onSizeChanged { onFooterHeight(with(density) { it.height.toDp() }) }
                        .padding(horizontal = metrics.edgePadding, vertical = if (expanded) 8.dp else 0.dp)
                ) {
                    Timeline(
                        expanded = footerHasToolbar,
                        currentTime = currentTime,
                        totalTime = totalTime,
                        onSeek = onSeek,
                        onTimelineDrag = { draggingTimeline = it }
                    ) {
                        if (!footerHasToolbar) {
                            onNextClick?.let { HudIconButton(Icons.Default.SkipNext, stringResource(R.string.action_next_episode), it) }
                            HudIconButton(fullscreenIcon, fullscreenLabel, onFullscreenToggle)
                        }
                    }
                    if (footerHasToolbar) {
                        Row(
                            Modifier.fillMaxWidth().background(scrim.copy(alpha = 0.42f), ExpressiveShapes.large)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasSubtitles) HudIconButton(captionIcon, stringResource(R.string.player_subtitles), onSubtitlesClick)
                            sourceLabel?.let { label ->
                                if (showTextActions) HudChip(label = label, icon = Icons.Default.Dns, onClick = onSourcesClick)
                                else HudIconButton(Icons.Default.Dns, stringResource(R.string.player_change_source, label), onSourcesClick)
                            }
                            if (showTextActions) HudChip(label = qualityLabel, onClick = onQualityClick)
                            else HudIconButton(Icons.Default.HighQuality, qualityAction, onQualityClick)
                            if (showTextActions) onShaderLookToggle?.let {
                                HudIconToggleButton(shaderIcon, shaderLabel, shaderLookShown) { _ -> it() }
                            }
                            Spacer(Modifier.weight(1f))
                            onNextClick?.let { HudIconButton(Icons.Default.SkipNext, stringResource(R.string.action_next_episode), it) }
                            if (isFullscreen) {
                                val label = stringResource(R.string.cd_video_size, stringResource(videoScale.labelRes))
                                HudIconButton(videoScale.icon, label, onVideoScaleClick)
                            }
                            HudIconButton(fullscreenIcon, fullscreenLabel, onFullscreenToggle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit
) {
    Row(modifier.fillMaxWidth().padding(vertical = if (expanded) 8.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
        ExpressiveBackButton(onClick = onBackClick, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onScrim)
        Column(Modifier.weight(1f).padding(start = 6.dp, end = 8.dp)) {
            Text(
                text = title,
                style = if (expanded) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onScrim,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = if (expanded) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onScrim.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

@Composable
private fun PlayerMenuItem(icon: ImageVector, label: String, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            close()
            action()
        }
    )
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
internal fun rewindIcon(seconds: Int): ImageVector = when (seconds) {
    5 -> Icons.Default.Replay5
    10 -> Icons.Default.Replay10
    30 -> Icons.Default.Replay30
    else -> Icons.Default.FastRewind
}

internal fun forwardIcon(seconds: Int): ImageVector = when (seconds) {
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
    expanded: Boolean,
    currentTime: Long,
    totalTime: Long,
    onSeek: (Long) -> Unit,
    onTimelineDrag: (Boolean) -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val duration = totalTime.coerceAtLeast(0L)
    var isDragging by remember { mutableStateOf(false) }
    var dragTarget by remember { mutableLongStateOf(0L) }
    var dragOrigin by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current
    val dragStateChanged by rememberUpdatedState(onTimelineDrag)
    DisposableEffect(Unit) { onDispose { dragStateChanged(false) } }
    val playedProgress = if (duration > 0) (currentTime.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownTime = if (isDragging) dragTarget else currentTime

    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onScrim.copy(alpha = 0.28f)
    )
    val interactionSource = remember { MutableInteractionSource() }
    val sliderState = rememberSliderState()
    // Playback drives the slider except while the user is scrubbing it.
    LaunchedEffect(playedProgress, isDragging) {
        if (!isDragging) sliderState.value = playedProgress
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TimelineTime(if (expanded) formatTime(shownTime) else "${formatTime(shownTime)} / ${formatTime(duration)}")
        Slider(
            state = sliderState,
            onValueChange = {
                if (!isDragging) {
                    dragOrigin = currentTime
                    dragTarget = currentTime
                    isDragging = true
                    onTimelineDrag(true)
                }
                val target = snapSeekPosition((it * duration).toLong(), dragOrigin, duration)
                if (duration > 0L) {
                    if (target * 50 / duration != dragTarget * 50 / duration) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    sliderState.value = target.toFloat() / duration
                }
                dragTarget = target
            },
            onValueChangeFinished = {
                if (isDragging && duration > 0) onSeek(dragTarget)
                isDragging = false
                onTimelineDrag(false)
            },
            enabled = duration > 0,
            colors = colors,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    thumbSize = DpSize(4.dp, if (expanded) 22.dp else 18.dp)
                )
            },
            track = { state ->
                val markerColor = MaterialTheme.colorScheme.onScrim
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 3.dp,
                    modifier = Modifier.height(if (expanded) 6.dp else 4.dp).drawWithContent {
                        drawContent()
                        if (isDragging && duration > 0) {
                            val fraction = (dragOrigin.toFloat() / duration).coerceIn(0f, 1f)
                            val x = size.width * if (layoutDirection == LayoutDirection.Ltr) fraction else 1f - fraction
                            drawLine(markerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                        }
                    }
                )
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (expanded) 12.dp else 8.dp)
                // TalkBack announces position as a percentage; the time text beside it
                // carries the exact value, so no new string resource is needed.
                .semantics {
                    contentDescription = "${formatTime(shownTime)} / ${formatTime(duration)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(playedProgress, 0f..1f)
                }
        )
        if (expanded) TimelineTime(formatTime(duration))
        actions()
    }
}

@Composable
private fun TimelineTime(time: String) {
    val style: TextStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    Text(
        text = time,
        style = style,
        color = MaterialTheme.colorScheme.onScrim,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp),
        maxLines = 1
    )
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
            .padding(end = 6.dp)
            .widthIn(max = 144.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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

/**
 * [HudIconButton] with an on and off state: filled in the theme's container colour while on, so
 * the state reads at a glance over any picture, and announced as a toggle.
 */
@Composable
private fun HudIconToggleButton(
    icon: ImageVector,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // The container morphs between its off and on shapes, so the state reads in its outline too.
        shapes = IconButtonDefaults.toggleableShapes(),
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = Color.White,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
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
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current
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
