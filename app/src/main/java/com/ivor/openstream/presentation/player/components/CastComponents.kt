package com.ivor.openstream.presentation.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import coil3.compose.AsyncImage
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.presentation.player.session.CastError
import com.ivor.openstream.presentation.player.session.CastStatus
import com.ivor.openstream.presentation.player.session.CastSubtitleOption
import com.ivor.openstream.presentation.player.session.CastSubtitles
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.delay

/**
 * Stands in for the video while it plays on a TV: artwork, what's playing where, and remote
 * controls for the receiver. Sized like the inline player (16:9), so it fits the same slot.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CastPlaybackView(
    castPlayer: Player,
    deviceName: String,
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isLoading: Boolean,
    error: CastError?,
    subtitles: CastSubtitles,
    seekStepSeconds: Int,
    onSelectSubtitle: (CastSubtitleOption?) -> Unit,
    onRetry: () -> Unit,
    onChooseSource: (() -> Unit)?,
    onNextClick: (() -> Unit)?,
    onBackClick: () -> Unit,
    onCastClick: () -> Unit,
    onPlaybackEnded: () -> Unit,
    onPlaybackReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(castPlayer.isPlaying) }
    var playWhenReady by remember { mutableStateOf(castPlayer.playWhenReady) }
    var isBuffering by remember { mutableStateOf(castPlayer.playbackState == Player.STATE_BUFFERING) }
    var position by remember { mutableLongStateOf(castPlayer.currentPosition) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var showSubtitleMenu by remember { mutableStateOf(false) }

    val latestEnded by rememberUpdatedState(onPlaybackEnded)
    val latestReady by rememberUpdatedState(onPlaybackReady)
    DisposableEffect(castPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                playWhenReady = ready
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                when (playbackState) {
                    Player.STATE_READY -> latestReady()
                    Player.STATE_ENDED -> latestEnded()
                }
            }
        }
        castPlayer.addListener(listener)
        onDispose { castPlayer.removeListener(listener) }
    }
    LaunchedEffect(castPlayer) {
        while (true) {
            if (!scrubbing) position = castPlayer.currentPosition.coerceAtLeast(0L)
            duration = castPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            delay(500)
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(12.dp)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

        Column(Modifier.fillMaxSize()) {
            // Top: back, where it's playing, subtitles, cast device.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                ExpressiveBackButton(
                    onClick = onBackClick,
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Playing on $deviceName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (subtitle.isNotBlank()) "$title · $subtitle" else title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                if (subtitles.options.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { showSubtitleMenu = true }) {
                            Icon(
                                if (subtitles.activeId != null) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                                contentDescription = "Subtitles on the TV",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(expanded = showSubtitleMenu, onDismissRequest = { showSubtitleMenu = false }) {
                            SubtitleMenuItem("Off", selected = subtitles.activeId == null) {
                                showSubtitleMenu = false
                                onSelectSubtitle(null)
                            }
                            subtitles.options.forEach { option ->
                                SubtitleMenuItem(option.label, selected = option.id == subtitles.activeId) {
                                    showSubtitleMenu = false
                                    onSelectSubtitle(option)
                                }
                            }
                        }
                    }
                }
                IconButton(onClick = onCastClick) {
                    Icon(Icons.Default.CastConnected, contentDescription = "Cast device: $deviceName", tint = Color.White)
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (error != null && !isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(onClick = onRetry, shape = ExpressiveShapes.medium) { Text("Retry") }
                            if (onChooseSource != null && error.fromStream) {
                                OutlinedButton(onClick = onChooseSource, shape = ExpressiveShapes.medium) {
                                    Text("Other source", color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    val seekMs = seekStepSeconds * 1_000L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { castPlayer.seekTo((castPlayer.currentPosition - seekMs).coerceAtLeast(0L)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Back $seekStepSeconds seconds")
                        }
                        Box(contentAlignment = Alignment.Center) {
                            FilledIconButton(
                                onClick = { if (playWhenReady) castPlayer.pause() else castPlayer.play() },
                                shape = if (isPlaying) ExpressiveShapes.large else ExpressiveShapes.extraLarge,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.size(64.dp)
                            ) {
                                AnimatedContent(targetState = playWhenReady, label = "castPlayPause") { playing ->
                                    Icon(
                                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playing) "Pause on TV" else "Play on TV",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            if (isLoading || isBuffering) {
                                LoadingIndicator(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(84.dp)
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val target = castPlayer.currentPosition + seekMs
                                castPlayer.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward $seekStepSeconds seconds")
                        }
                        if (onNextClick != null) {
                            FilledTonalIconButton(onClick = onNextClick, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next episode")
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp)
            ) {
                Text(
                    text = formatTime(if (scrubbing) (scrubValue * duration).toLong() else position),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                Slider(
                    value = if (scrubbing) scrubValue else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = {
                        scrubbing = true
                        scrubValue = it
                    },
                    onValueChangeFinished = {
                        if (duration > 0) {
                            val target = (scrubValue * duration).toLong()
                            castPlayer.seekTo(target)
                            position = target
                        }
                        scrubbing = false
                    },
                    enabled = duration > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SubtitleMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected") else Spacer(Modifier.width(24.dp))
        }
    )
}

/**
 * Lists Cast receivers on the network (scanning while open) and, while casting, offers to stop.
 * Picking a device starts a session through the Cast framework; [CastStatus] reports the rest.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CastDeviceSheet(
    selector: MediaRouteSelector?,
    status: CastStatus,
    onStopCasting: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val router = remember(context) { MediaRouter.getInstance(context.applicationContext) }
    var routes by remember { mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList()) }

    DisposableEffect(selector) {
        if (selector == null) return@DisposableEffect onDispose {}
        fun refresh() {
            routes = router.routes.filter { !it.isDefaultOrBluetooth && it.isEnabled && it.matchesSelector(selector) }
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
        onDispose { router.removeCallback(callback) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = if (status.isCasting) "Casting to ${status.deviceName}" else "Cast to a TV",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() }
            )
            if (status.isCasting) {
                Text(
                    text = "Keep this phone on the same Wi-Fi network: the TV streams through it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Button(
                    onClick = {
                        onStopCasting()
                        onDismiss()
                    },
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .fillMaxWidth()
                ) {
                    Text("Stop casting")
                }
                return@Column
            }

            if (routes.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    LoadingIndicator(modifier = Modifier.size(40.dp))
                    Text(
                        text = if (status.connecting) "Connecting…" else "Looking for Chromecast and Google TV devices on this Wi-Fi…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            } else {
                LazyColumn {
                    items(routes, key = { it.id }) { route ->
                        val connecting = route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
                        ListItem(
                            headlineContent = { Text(route.name) },
                            supportingContent = {
                                (if (connecting) "Connecting…" else route.description)?.let { Text(it) }
                            },
                            leadingContent = {
                                Icon(
                                    if (route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_TV) Icons.Default.Tv else Icons.Default.Cast,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                if (connecting) LoadingIndicator(modifier = Modifier.size(32.dp))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                router.selectRoute(route)
                                onDismiss()
                            }
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Close")
            }
        }
    }
}
