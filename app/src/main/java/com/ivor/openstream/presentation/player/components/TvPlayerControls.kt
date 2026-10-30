package com.ivor.openstream.presentation.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import com.ivor.openstream.R
import com.ivor.openstream.presentation.components.ExpressiveMotion

/**
 * The player chrome for a remote. Everything sits in the lower third over one scrim, the way TV
 * players do: what is playing, the progress bar, then one row of buttons. There is no back button
 * (the remote has one) and nothing a TV cannot use (lock, rotation, fullscreen, Cast, PiP).
 *
 * Focus starts on Play/Pause. On the progress bar Left and Right seek, OK plays or pauses. Every key
 * press reports [onInteraction] so the chrome stays up while the viewer is moving around it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvPlayerControls(
    isVisible: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    title: String,
    subtitle: String,
    sourceLabel: String?,
    qualityLabel: String,
    hasSubtitles: Boolean,
    subtitlesEnabled: Boolean,
    currentTime: Long,
    totalTime: Long,
    seekStepSeconds: Int,
    videoScale: VideoScale,
    onPauseToggle: () -> Unit,
    onForward: () -> Unit,
    onRewind: () -> Unit,
    onNextClick: (() -> Unit)?,
    onSubtitlesClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onVideoScaleClick: () -> Unit,
    onInteraction: () -> Unit,
    playFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val chromeFade = ExpressiveMotion.finiteEffectsSpec()
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(chromeFade),
        exit = fadeOut(chromeFade),
        modifier = modifier
    ) {
        val scrim = MaterialTheme.colorScheme.scrim
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scrim.copy(alpha = 0.45f),
                        0.2f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to scrim.copy(alpha = 0.9f)
                    )
                )
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown) onInteraction()
                    false
                }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 56.dp, bottom = 32.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(16.dp))
                SeekBar(
                    currentTime = currentTime,
                    totalTime = totalTime,
                    onRewind = onRewind,
                    onForward = onForward,
                    onPauseToggle = onPauseToggle
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlayPauseButton(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onClick = onPauseToggle,
                        modifier = Modifier.focusRequester(playFocusRequester)
                    )
                    TvHudIconButton(
                        icon = rewindIcon(seekStepSeconds),
                        contentDescription = stringResource(R.string.player_rewind_seconds, seekStepSeconds),
                        onClick = onRewind
                    )
                    TvHudIconButton(
                        icon = forwardIcon(seekStepSeconds),
                        contentDescription = stringResource(R.string.cd_forward_seconds, seekStepSeconds),
                        onClick = onForward
                    )
                    if (onNextClick != null) {
                        TvHudButton(
                            icon = Icons.Default.SkipNext,
                            label = stringResource(R.string.action_next_episode),
                            onClick = onNextClick
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (hasSubtitles) {
                        TvHudIconButton(
                            icon = if (subtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                            contentDescription = stringResource(R.string.player_subtitles),
                            onClick = onSubtitlesClick
                        )
                    }
                    if (sourceLabel != null) {
                        TvHudButton(
                            icon = Icons.Default.Dns,
                            label = sourceLabel,
                            contentDescription = stringResource(R.string.player_change_source, sourceLabel),
                            onClick = onSourcesClick
                        )
                    }
                    TvHudButton(label = qualityLabel, onClick = onQualityClick)
                    TvHudIconButton(
                        icon = videoScale.icon,
                        contentDescription = stringResource(R.string.cd_video_size, stringResource(videoScale.labelRes)),
                        onClick = onVideoScaleClick
                    )
                    TvHudIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.player_playback_settings),
                        onClick = onSettingsClick
                    )
                }
            }
        }
    }
}

/** Skip intro, recap or credits: a stop of its own, so the remote can reach it without the chrome. */
@Composable
fun TvSkipButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        androidx.tv.material3.Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** The progress bar as one stop in the focus order: thicker with a thumb while focused. */
@Composable
private fun SeekBar(
    currentTime: Long,
    totalTime: Long,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPauseToggle: () -> Unit
) {
    val duration = totalTime.coerceAtLeast(0L)
    val progress = if (duration > 0) (currentTime.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val timeLabel = "${formatTime(currentTime)} / ${formatTime(duration)}"
    val accent = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { onRewind(); true }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { onForward(); true }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (event.nativeKeyEvent.repeatCount == 0) onPauseToggle()
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource)
            .semantics {
                contentDescription = timeLabel
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val trackHeight = if (focused) 8.dp else 4.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
            Box(
                Modifier
                    .width(maxWidth * progress)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(if (focused) accent else Color.White)
            )
            if (focused) {
                Box(
                    Modifier
                        .offset(x = (maxWidth * progress - 10.dp).coerceAtLeast(0.dp))
                        .size(20.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(currentTime),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "-" + formatTime((duration - currentTime).coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        colors = IconButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        if (isBuffering) {
            LoadingIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.details_play),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun TvHudIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TvHudButton(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    contentDescription: String? = null
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .widthIn(max = 220.dp)
            .then(
                if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier
            ),
        contentPadding = if (icon != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        androidx.tv.material3.Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
