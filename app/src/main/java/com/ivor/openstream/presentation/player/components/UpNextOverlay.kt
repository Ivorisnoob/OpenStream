package com.ivor.openstream.presentation.player.components

import androidx.compose.animation.core.Animatable
import com.ivor.openstream.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ivor.openstream.presentation.components.ExpressiveMotion
import com.ivor.openstream.presentation.player.NextEpisodeTarget
import com.ivor.openstream.ui.theme.ExpressiveShapes

private const val UP_NEXT_COUNTDOWN_MS = 8_000L

/**
 * Shown when an episode ends.
 *
 * The countdown is a *prompt*, not an autopilot: when the ring empties the overlay keeps
 * waiting and focus stays on Play. Advancing is always an OK press. Auto-playing after the
 * countdown (with no way to stop it other than finding the Cancel button in time) was reported
 * as the app "skipping" on its own, and on a TV it also moves focus while the viewer is still
 * reading, which is worse than the phone case.
 *
 * [countdownDurationMs] is passed in so the TV overlay can present a longer, deliberate prompt.
 */
@Composable
fun UpNextOverlay(
    target: NextEpisodeTarget,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    countdownDurationMs: Long = UP_NEXT_COUNTDOWN_MS
) {
    val countdown = remember(target) { Animatable(1f) }
    // Same timing with reduced motion, but the ring jumps instead of sweeping.
    val reducedCountdown = ExpressiveMotion.reducedMotion()
    LaunchedEffect(target, countdownDurationMs) {
        if (reducedCountdown) {
            delay(countdownDurationMs)
            countdown.snapTo(0f)
        } else {
            countdown.animateTo(0f, tween(countdownDurationMs.toInt(), easing = LinearEasing))
        }
        // Deliberately stops here: no latestPlayNow(). Nothing advances without OK.
    }

    Surface(
        modifier = modifier.widthIn(max = 360.dp),
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
                    modifier = Modifier.padding(top = 2.dp)
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
                    modifier = Modifier.size(48.dp),
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
