package com.ivor.openstream.presentation.player.session

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.ivor.openstream.R
import androidx.media3.ui.PlayerView
import com.ivor.openstream.ui.theme.ExpressiveShapes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    val session: PlaybackSession
) : ViewModel()

/**
 * The stream keeps playing in this card while the user browses. Tapping it reopens the full
 * player on the same stream; closing it ends playback and saves the position.
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(
    onExpand: (NowPlaying) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel()
) {
    val session = viewModel.session
    val nowPlaying by session.nowPlaying.collectAsState()
    val isPlaying by session.isPlaying.collectAsState()
    val castStatus by session.castStatus.collectAsState()
    val item = nowPlaying ?: return

    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(item.mediaUri) {
        while (true) {
            val active = session.activePlayer
            val duration = active.duration
            progress = if (duration > 0) (active.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }

    // Outside the player screen nothing else pauses on backgrounding; do it here.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // A TV keeps playing when the phone locks or the app goes to the background.
            if (event == Lifecycle.Event.ON_STOP && !session.castStatus.value.isCasting) session.player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Swipe sideways to dismiss (stops playback), swipe up to open the player.
    val scope = rememberCoroutineScope()
    val offsetX = remember(item.mediaUri) { Animatable(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val expandThresholdPx = with(density) { 48.dp.toPx() }

    Surface(
        onClick = { onExpand(item) },
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX.value
                alpha = 1f - (kotlin.math.abs(offsetX.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.8f)
            }
            .pointerInput(item.mediaUri) {
                detectDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragEnd = {
                        val dismissAt = size.width * 0.35f
                        when {
                            kotlin.math.abs(offsetX.value) > dismissAt -> scope.launch {
                                offsetX.animateTo(if (offsetX.value > 0) size.width.toFloat() else -size.width.toFloat())
                                session.stop()
                            }
                            dragY < -expandThresholdPx -> {
                                scope.launch { offsetX.animateTo(0f) }
                                onExpand(item)
                            }
                            else -> scope.launch { offsetX.animateTo(0f) }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f) } }
                ) { change, drag ->
                    change.consume()
                    dragY += drag.y
                    scope.launch { offsetX.snapTo(offsetX.value + drag.x) }
                }
            }
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 112.dp, height = 63.dp)
                        .clip(ExpressiveShapes.medium)
                        .background(Color.Black)
                ) {
                    if (castStatus.isCasting) {
                        AsyncImage(
                            model = (item.stillPath ?: item.backdropPath ?: item.posterPath)
                                ?.let { "https://image.tmdb.org/t/p/w300$it" },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(
                            Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp)
                        )
                    } else AndroidView(
                        factory = { context ->
                            // Inflated for its texture_view surface (only settable from XML).
                            (LayoutInflater.from(context).inflate(R.layout.mini_player_view, null) as PlayerView).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                player = session.player
                            }
                        },
                        onRelease = { view -> view.player = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val detail = castStatus.deviceName?.let { stringResource(R.string.cast_casting_to, it) } ?: item.subtitle
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilledIconButton(
                    onClick = session::togglePlayback,
                    shape = if (isPlaying) ExpressiveShapes.medium else ExpressiveShapes.large,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    AnimatedContent(targetState = isPlaying, label = "miniPlayPause") { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play)
                        )
                    }
                }
                IconButton(onClick = session::stop) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_stop_playback))
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 6.dp)
                    .height(3.dp)
            )
        }
    }
}
