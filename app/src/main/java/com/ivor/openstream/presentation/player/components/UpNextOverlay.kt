package com.ivor.openstream.presentation.player.components

import androidx.compose.animation.core.Animatable
import com.ivor.openstream.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ivor.openstream.presentation.components.ExpressiveMotion
import com.ivor.openstream.presentation.player.NextEpisodeTarget
import com.ivor.openstream.ui.theme.ExpressiveShapes

private const val UP_NEXT_COUNTDOWN_MS = 8_000

/**
 * Shown when an episode ends. Counts down and continues automatically, the way every streaming
 * app does; the ring around the play button is the countdown so no extra number is needed.
 */
@Composable
fun UpNextOverlay(
    target: NextEpisodeTarget,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    autoAdvance: Boolean = true,
    /** A TV: Play takes the focus on arrival and the focus stays here until this closes. */
    dpadControls: Boolean = false,
    modifier: Modifier = Modifier
) {
    val playFocus = remember { FocusRequester() }
    if (dpadControls) {
        LaunchedEffect(target) {
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
        }
    }
    val latestPlayNow by rememberUpdatedState(onPlayNow)
    val countdown = remember(target) { Animatable(1f) }
    // Same 8s timing with reduced motion, but the ring jumps instead of sweeping.
    val reducedCountdown = ExpressiveMotion.reducedMotion()
    if (autoAdvance) {
        LaunchedEffect(target) {
            if (reducedCountdown) {
                delay(UP_NEXT_COUNTDOWN_MS.toLong())
                countdown.snapTo(0f)
            } else {
                countdown.animateTo(0f, tween(UP_NEXT_COUNTDOWN_MS, easing = LinearEasing))
            }
            latestPlayNow()
        }
    }

    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .then(
                if (dpadControls) {
                    Modifier
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusGroup()
                } else {
                    Modifier
                }
            ),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (target.stillPath != null) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w300${target.stillPath}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 96.dp, height = 56.dp)
                        .clip(ExpressiveShapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = stringResource(R.string.player_up_next).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "S${target.season} · E${target.episode}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                target.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .tvFocusRing(dpadControls, MaterialTheme.colorScheme.onSurface, ExpressiveShapes.medium)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                CircularProgressIndicator(
                    progress = { countdown.value },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
                FilledIconButton(
                    onClick = onPlayNow,
                    modifier = Modifier
                        .size(48.dp)
                        .focusRequester(playFocus)
                        .tvFocusRing(dpadControls, MaterialTheme.colorScheme.onSurface, CircleShape),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play_next_now))
                }
            }
        }
    }
}

/** A remote has no pointer, so its position has to be drawn; the stock focus tint is too faint. */
@Composable
private fun Modifier.tvFocusRing(enabled: Boolean, color: Color, shape: Shape): Modifier {
    if (!enabled) return this
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else 0.dp, if (focused) color else Color.Transparent, shape)
}
