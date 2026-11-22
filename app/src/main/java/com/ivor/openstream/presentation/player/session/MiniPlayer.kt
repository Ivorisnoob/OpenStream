package com.ivor.openstream.presentation.player.session

import com.ivor.openstream.ui.theme.onScrim
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlin.math.abs
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivor.openstream.presentation.components.ExpressiveMotion
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
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
import io.github.ivorisnoob.smoothmotion.ui.bindSmoothMotion
import io.github.ivorisnoob.smoothmotion.ui.unbindSmoothMotion
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
    val nativeHdr by session.nativeHdr.collectAsState()
    val generation by session.playerGeneration.collectAsState()
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

    val reduceMotion = ExpressiveMotion.reducedMotion()
    val smoothedProgress by animateFloatAsState(
        progress,
        animationSpec = tween(if (reduceMotion || !isPlaying) 0 else 500, easing = LinearEasing),
        label = "Mini-player progress"
    )

    // Outside the player screen nothing else pauses on backgrounding; do it here, unless the user
    // asked for background playback (the media notification takes over then).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // A TV keeps playing when the phone locks or the app goes to the background.
            if (event == Lifecycle.Event.ON_STOP &&
                !session.castStatus.value.isCasting &&
                !session.keepPlayingInBackground
            ) {
                session.player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()
    val position = remember(item.key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dragPosition by remember(item.key) { mutableStateOf<Offset?>(null) }
    var settleJob by remember(item.key) { mutableStateOf<Job?>(null) }
    var transitionStarted by remember(item.key) { mutableStateOf(false) }
    var cardWidth by remember(item.key) { mutableIntStateOf(0) }
    val latestExpand by rememberUpdatedState(onExpand)
    val haptics = LocalHapticFeedback.current
    val returnSpring by rememberUpdatedState(ExpressiveMotion.spatialSpec<Offset>())
    val density = LocalDensity.current
    val expandDistance = with(density) { 44.dp.toPx() }
    val flingSpeed = with(density) { 900.dp.toPx() }

    fun settle(target: Offset, velocity: Offset = Offset.Zero, dismiss: Boolean = false) {
        settleJob?.cancel()
        val initialPosition = dragPosition ?: position.value
        transitionStarted = dismiss
        settleJob = scope.launch {
            position.snapTo(initialPosition)
            dragPosition = null
            position.animateTo(target, returnSpring, initialVelocity = velocity)
            if (dismiss && session.nowPlaying.value?.key == item.key) session.stop()
        }
    }

    fun openPlayer() {
        if (transitionStarted) return
        transitionStarted = true
        latestExpand(item)
    }

    // Track the pointer in the stationary parent so moving the card does not feed back into drag deltas.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { cardWidth = it.width }
            .pointerInput(item.key, expandDistance, flingSpeed) {
                val tracker = VelocityTracker()
                var axis: DragAxis? = null
                var total = Offset.Zero
                var start = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        if (!transitionStarted) {
                            settleJob?.cancel()
                            start = dragPosition ?: position.value
                            dragPosition = start
                            tracker.resetTracking()
                            axis = null
                            total = Offset.Zero
                        }
                    },
                    onDragEnd = {
                        if (dragPosition != null && !transitionStarted) {
                            val velocity = tracker.calculateVelocity()
                            val current = dragPosition ?: Offset.Zero
                            val x = current.x
                            val flyingSideways = abs(velocity.x) > flingSpeed &&
                                abs(total.x) > expandDistance / 3f && velocity.x * x > 0
                            val flyingUp = velocity.y < -flingSpeed && total.y < -expandDistance / 3f
                            when {
                                axis == DragAxis.Vertical && (total.y < -expandDistance || flyingUp) -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    openPlayer()
                                }
                                axis == DragAxis.Horizontal && (abs(x) > size.width * 0.30f || flyingSideways) -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    val edge = if (x < 0) -size.width.toFloat() else size.width.toFloat()
                                    settle(Offset(edge, 0f), Offset(velocity.x, 0f), dismiss = true)
                                }
                                else -> settle(Offset.Zero, when (axis) {
                                    DragAxis.Horizontal -> Offset(velocity.x, 0f)
                                    DragAxis.Vertical -> Offset(0f, velocity.y * 0.55f)
                                    null -> Offset.Zero
                                })
                            }
                        }
                    },
                    onDragCancel = { if (!transitionStarted && dragPosition != null) settle(Offset.Zero) }
                ) { change, delta ->
                    if (!transitionStarted && dragPosition != null) {
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        total += delta
                        if (axis == null) {
                            axis = if (abs(total.x) >= abs(total.y)) DragAxis.Horizontal else DragAxis.Vertical
                        }
                        dragPosition = when (axis) {
                            DragAxis.Horizontal -> Offset(start.x + total.x, start.y)
                            DragAxis.Vertical -> Offset(
                                start.x,
                                (start.y + total.y * 0.55f).coerceIn(-size.height * 0.65f, 0f)
                            )
                            null -> start
                        }
                    }
                }
            }
    ) {
        Surface(
            onClick = ::openPlayer,
            shape = ExpressiveShapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                val shift = dragPosition ?: position.value
                if (!reduceMotion) {
                    translationX = shift.x
                    translationY = shift.y.coerceAtMost(0f)
                    alpha = 1f - (abs(shift.x) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
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
                        if (castStatus.isCasting || nativeHdr) {
                            AsyncImage(
                                model = (item.stillPath ?: item.backdropPath ?: item.posterPath)
                                    ?.let { "https://image.tmdb.org/t/p/w300$it" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (castStatus.isCasting) Icon(
                                Icons.Default.CastConnected,
                                // Decorative: the row text already says where it's casting to.
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onScrim,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                                        CircleShape
                                    )
                                    .padding(6.dp)
                                    .size(28.dp)
                            )
                        } else androidx.compose.runtime.key(generation) {
                            AndroidView(
                                factory = { context ->
                                    // Inflated for its texture_view surface (only settable from XML).
                                    (LayoutInflater.from(context).inflate(R.layout.mini_player_view, null) as PlayerView).apply {
                                        layoutParams = FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                        player = session.player
                                        session.smoothMotion?.let { bindSmoothMotion(it) }
                                    }
                                },
                                update = { view ->
                                    val sm = session.smoothMotion
                                    if (sm != null) {
                                        view.bindSmoothMotion(sm)
                                    } else {
                                        view.unbindSmoothMotion()
                                    }
                                },
                                onRelease = { view ->
                                    view.unbindSmoothMotion()
                                    view.player = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                    IconButton(onClick = {
                        if (!transitionStarted) settle(Offset(cardWidth.toFloat(), 0f), dismiss = true)
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_stop_playback))
                    }
                }
                LinearWavyProgressIndicator(
                    progress = { smoothedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp)
                )
            }
        }
    }
}

private enum class DragAxis { Horizontal, Vertical }
