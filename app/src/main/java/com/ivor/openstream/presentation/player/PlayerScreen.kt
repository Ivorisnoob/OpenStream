package com.ivor.openstream.presentation.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import android.app.DownloadManager
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.presentation.player.components.ExoPlayerView
import com.ivor.openstream.presentation.player.components.CastDeviceSheet
import com.ivor.openstream.presentation.player.components.CastPlaybackView
import com.ivor.openstream.presentation.player.components.SourcesPageActions
import com.ivor.openstream.presentation.player.components.SourcesPanel
import com.ivor.openstream.presentation.player.components.UpNextOverlay
import com.ivor.openstream.presentation.player.components.PlayerInfoPanel
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes
import androidx.compose.runtime.key

// Expressive Motion Tokens
private val ExpressiveDefaultSpatial = CubicBezierEasing(0.38f, 1.21f, 0.22f, 1.00f)
private val ExpressiveDefaultEffects = CubicBezierEasing(0.34f, 0.80f, 0.34f, 1.00f)
private const val DurationSpatialDefault = 500
private const val DurationEffectsDefault = 200

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    mediaType: String,
    tmdbId: Int,
    season: Int,
    episode: Int,
    downloadId: String? = null,
    onBackClick: () -> Unit,
    onEpisodeClick: (season: Int, episode: Int) -> Unit,
    onOpenDetails: (mediaType: String, id: Int) -> Unit = { _, _ -> },
    onOpenTitle: (id: Int, mediaType: String) -> Unit = { _, _ -> },
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Collect specific state updates
    val nextEpisodes by viewModel.nextEpisodes.collectAsState()
    val isLoadingEpisodes by viewModel.isLoadingEpisodes.collectAsState()
    val remoteSubtitles by viewModel.remoteSubtitles.collectAsState()
    val mediaDetails by viewModel.mediaDetails.collectAsState()
    val currentEpisode by viewModel.currentEpisode.collectAsState()
    val captionSettings by viewModel.captionSettings.collectAsState()
    val serversState by viewModel.serversState.collectAsState()
    val activeServer by viewModel.activeServer.collectAsState()
    val currentDownload by viewModel.currentDownload.collectAsState()
    val startPositionMs by viewModel.startPositionMs.collectAsState()
    val nextEpisode by viewModel.nextEpisode.collectAsState()
    val preferredAudioLanguage by viewModel.preferredAudioLanguage.collectAsState()
    val seasonEpisodes by viewModel.seasonEpisodes.collectAsState()
    val episodeProgress by viewModel.episodeProgress.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val sleepTimer by viewModel.sleepTimer.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val preferredSubtitleLanguage by viewModel.preferredSubtitleLanguage.collectAsState()
    val skipSegments by viewModel.skipSegments.collectAsState()
    val castStatus by viewModel.castStatus.collectAsState()
    val castError by viewModel.castError.collectAsState()
    val castLoading by viewModel.castLoading.collectAsState()
    val castSubtitles by viewModel.castSubtitles.collectAsState()
    val isCasting = castStatus.isCasting
    var showCastSheet by remember { mutableStateOf(false) }
    val enterPictureInPicture = rememberEnterPictureInPicture()

    var localVideoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var isResolvingLocalUri by remember { mutableStateOf(downloadId != null) }
    var showServerPicker by rememberSaveable { mutableStateOf(false) }
    var resumePositionMs by rememberSaveable(tmdbId, season, episode, downloadId) {
        mutableLongStateOf(0L)
    }
    var sessionPlaybackSpeed by rememberSaveable(tmdbId, season, episode, downloadId) {
        mutableFloatStateOf(viewModel.appSettings.value.defaultSpeed)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    val providerSubtitles = remember(activeServer) {
        activeServer?.subtitles.orEmpty().mapIndexed { index, subtitle ->
            SubtitleDto(
                id = "provider_${activeServer?.id}_$index",
                url = subtitle.url,
                display = subtitle.label,
                language = subtitle.language,
                source = activeServer?.providerName
            )
        }
    }
    val allSubtitles = remember(remoteSubtitles, providerSubtitles) {
        (providerSubtitles + remoteSubtitles).distinctBy { it.url }
    }
    // The TV gets the same subtitles, starting with the remembered language.
    LaunchedEffect(allSubtitles, preferredSubtitleLanguage) {
        viewModel.setCastSubtitleCandidates(allSubtitles, preferredSubtitleLanguage)
    }
    // Hold playback until the saved position is known so a resume never starts from zero.
    val videoUrl = (localVideoUrl ?: activeServer?.url)?.takeIf { startPositionMs != null }
    var showUpNext by remember(tmdbId, season, episode) { mutableStateOf(false) }

    LaunchedEffect(videoUrl) {
        videoUrl?.let { viewModel.onMediaLoaded(it, downloadId) }
    }

    LaunchedEffect(startPositionMs) {
        val saved = startPositionMs ?: return@LaunchedEffect
        if (resumePositionMs == 0L && saved > 0L) resumePositionMs = saved
    }

    // Fullscreen state
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var isRotationLocked by rememberSaveable { mutableStateOf(false) }
    val isInPictureInPicture = rememberIsInPictureInPicture()
    // Picture-in-picture shows the bare video, exactly like fullscreen minus the chrome.
    val isImmersive = isFullscreen || isInPictureInPicture
    var isVideoPlaying by remember { mutableStateOf(false) }
    var togglePlaybackSignal by remember { mutableIntStateOf(0) }

    PictureInPictureEffect(
        enabled = videoUrl != null && !isCasting,
        isPlaying = isVideoPlaying,
        onTogglePlayback = { togglePlaybackSignal++ }
    )

    // Trigger data fetch
    LaunchedEffect(tmdbId, season, episode, downloadId) {
        if (downloadId != null) {
            isResolvingLocalUri = true
            val uri = viewModel.getPlaybackUri(downloadId)
            if (uri != null) {
                localVideoUrl = uri
            }
            isResolvingLocalUri = false
        }
        viewModel.loadSeasonDetails(
            mediaType = mediaType,
            tmdbId = tmdbId,
            seasonNumber = season,
            currentEpisodeNumber = episode,
            resolveStreams = downloadId == null
        )
    }

    LaunchedEffect(Unit) {
        viewModel.playerEvents.collect { message ->
            val needsSourceAction = message.startsWith("No more healthy servers")
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (needsSourceAction) "Sources" else null,
                duration = if (needsSourceAction) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                showServerPicker = true
            }
        }
    }
    
    // Dynamic Title for Player HUD
    val playerTitle = if (mediaType == "movie") mediaDetails?.name ?: "Movie" else mediaDetails?.name ?: "Show"
    val playerSubtitle = if (mediaType == "movie") "" else {
        val epName = currentEpisode?.name ?: "Episode $episode"
        "S$season:E$episode • $epName"
    }

    // Fullscreen management
    fun enterFullscreen() {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val window = act.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            setCutoutMode(window, drawIntoCutout = true)
        }
        isFullscreen = true
    }

    // Fullscreen follows the sensor between both landscape sides; locking pins the current side.
    fun toggleRotationLock() {
        val act = activity ?: return
        isRotationLocked = !isRotationLocked
        act.requestedOrientation = if (isRotationLocked) {
            @Suppress("DEPRECATION")
            val rotation = act.windowManager.defaultDisplay.rotation
            if (rotation == android.view.Surface.ROTATION_270) {
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun exitFullscreen() {
        isRotationLocked = false
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = act.window
            WindowCompat.setDecorFitsSystemWindows(window, true)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            setCutoutMode(window, drawIntoCutout = false)
        }
        isFullscreen = false
    }

    // The video is on the TV: the phone shows remote controls inline, not a fullscreen player.
    LaunchedEffect(isCasting) {
        if (isCasting && isFullscreen) exitFullscreen()
    }

    // Handle back press in fullscreen -- exit fullscreen instead of navigating back
    BackHandler(enabled = isFullscreen) {
        exitFullscreen()
    }

    // Clean up on dispose
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = act.window
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                setCutoutMode(window, drawIntoCutout = false)
                // Hand brightness back to the system after the player's swipe gesture.
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    val onNextClick: (() -> Unit)? = nextEpisode?.let { target ->
        { onEpisodeClick(target.season, target.episode) }
    }

    val sourceActions = SourcesPageActions(
        onSelect = { server ->
            viewModel.selectServer(server.id)
            showServerPicker = false
        },
        onRetry = viewModel::retryResolution,
        onDownload = { server ->
            viewModel.downloadVideo(server)
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isImmersive) PaddingValues(0.dp) else WindowInsets.statusBars.asPaddingValues())
        ) {
            // 1. Video Player Area - Always present, size depends on isFullscreen
            val videoModifier = if (isImmersive) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp) // Keeps it from becoming too large on tablets
                    .aspectRatio(16f / 9f)
            }

            Box(
                modifier = videoModifier
                    .background(Color.Black)
            ) {
            AnimatedContent(
                targetState = videoUrl != null,
                transitionSpec = {
                    fadeIn(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)) togetherWith 
                    fadeOut(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects))
                },
                label = "PlayerState"
            ) { hasUrl ->
                // Capture the live url so the exiting transition frame (where
                // targetState still says "has url" but it was just cleared on an
                // episode switch) can't dereference a null and crash.
                val currentUrl = videoUrl
                if (hasUrl && currentUrl != null && isCasting) {
                    CastPlaybackView(
                        castPlayer = viewModel.castPlayer ?: return@AnimatedContent,
                        deviceName = castStatus.deviceName.orEmpty(),
                        title = playerTitle,
                        subtitle = playerSubtitle,
                        artworkUrl = (currentEpisode?.stillPath ?: mediaDetails?.backdropPath)
                            ?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        isLoading = castLoading,
                        error = castError,
                        subtitles = castSubtitles,
                        seekStepSeconds = appSettings.seekStepSeconds,
                        onSelectSubtitle = viewModel::selectCastSubtitle,
                        onRetry = viewModel::retryCast,
                        onChooseSource = { showServerPicker = true }.takeIf { downloadId == null },
                        onNextClick = onNextClick,
                        onBackClick = onBackClick,
                        onCastClick = { showCastSheet = true },
                        onPlaybackEnded = {
                            val stoppedBySleepTimer = viewModel.consumeEndedBySleepTimer()
                            showUpNext = nextEpisode != null && appSettings.autoPlayNext && !stoppedBySleepTimer
                        },
                        onPlaybackReady = viewModel::onPlaybackReady,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (hasUrl && currentUrl != null) {
                    key(activeServer?.id ?: currentUrl) {
                        ExoPlayerView(
                            videoUrl = currentUrl,
                            title = playerTitle,
                            subtitle = playerSubtitle,
                            requestHeaders = activeServer?.headers.orEmpty(),
                            exoPlayer = viewModel.player,
                            applyRequestHeaders = viewModel::applyRequestHeaders,
                            isFullscreen = isFullscreen,
                            onFullscreenToggle = {
                                if (isFullscreen) exitFullscreen() else enterFullscreen()
                            },
                            onBackClick = {
                                if (isFullscreen) exitFullscreen() else onBackClick()
                            },
                            modifier = Modifier.fillMaxSize(),
                            remoteSubtitles = allSubtitles,
                            sourceLabel = if (downloadId != null) {
                                "Offline copy"
                            } else {
                                activeServer?.name
                            },
                            sourceSummary = if (downloadId != null) {
                                "Stored on this device"
                            } else {
                                activeServer?.let { "${it.providerName} · ${it.sourceSummary()}" }
                            },
                            serversState = serversState,
                            canChangeSource = downloadId == null,
                            initialPositionMs = resumePositionMs,
                            onPositionChanged = { resumePositionMs = it },
                            onPlaybackEnded = {
                                // Always consumed, so a sleep-timer stop never leaks into the next ending.
                                val stoppedBySleepTimer = viewModel.consumeEndedBySleepTimer()
                                showUpNext = nextEpisode != null && appSettings.autoPlayNext && !stoppedBySleepTimer
                            },
                            isRotationLocked = isRotationLocked,
                            onRotationLockToggle = { toggleRotationLock() },
                            sleepTimer = sleepTimer,
                            onSleepTimerChange = viewModel::setSleepTimer,
                            seekStepSeconds = appSettings.seekStepSeconds,
                            preferredSubtitleLanguage = preferredSubtitleLanguage,
                            onSubtitleLanguageChosen = viewModel::setPreferredSubtitleLanguage,
                            skipSegments = skipSegments,
                            episodes = if (mediaType == "movie") emptyList() else seasonEpisodes,
                            currentEpisodeNumber = episode,
                            episodeProgress = episodeProgress,
                            onEpisodeSelected = { picked -> onEpisodeClick(picked.seasonNumber, picked.episodeNumber) },
                            onIsPlayingChanged = { isVideoPlaying = it },
                            isInPictureInPicture = isInPictureInPicture,
                            togglePlaybackSignal = togglePlaybackSignal,
                            initialPlaybackSpeed = sessionPlaybackSpeed,
                            onPlaybackSpeedChanged = { sessionPlaybackSpeed = it },
                            sourceActions = sourceActions,
                            onNextClick = onNextClick,
                            captionSettings = captionSettings,
                            originalLanguage = mediaDetails?.originalLanguage,
                            preferredAudioLanguage = preferredAudioLanguage,
                            onAudioLanguageChosen = viewModel::setPreferredAudioLanguage,
                            onCaptionSettingsChange = viewModel::updateCaptionSettings,
                            onPlaybackError = {
                                if (downloadId == null) {
                                    viewModel.onPlaybackError()
                                    showServerPicker = viewModel.activeServer.value == null
                                }
                            },
                            onPlaybackReady = viewModel::onPlaybackReady,
                            onCastClick = { showCastSheet = true }.takeIf { castStatus.supported },
                            onPictureInPictureClick = enterPictureInPicture
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        // Resolution / Loading Overlay (Cinematic)
                        AnimatedContent(
                            targetState = true,
                            label = "LoadingOverlay"
                        ) { _ ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            ) {
                                // Blurred Backdrop
                                val backdropPath = mediaDetails?.backdropPath
                                if (backdropPath != null) {
                                    AsyncImage(
                                        model = "https://image.tmdb.org/t/p/w1280$backdropPath",
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(20.dp)
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Black.copy(alpha = 0.5f),
                                                            Color.Black.copy(alpha = 0.8f)
                                                        )
                                                    ),
                                                    blendMode = BlendMode.SrcOver
                                                )
                                            },
                                        contentScale = ContentScale.Crop,
                                        alpha = 0.7f
                                    )
                                }

                                // Centered loading content. Inline it has to fit a 16:9 box about
                                // 200dp tall, so type and spacing stay compact there.
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 64.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(if (isFullscreen) 12.dp else 6.dp)
                                ) {
                                    if (serversState !is ServersState.Empty) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(if (isFullscreen) 56.dp else 40.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = playerTitle,
                                        color = Color.White,
                                        style = if (isFullscreen) {
                                            MaterialTheme.typography.headlineSmall
                                        } else {
                                            MaterialTheme.typography.titleMedium
                                        },
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (playerSubtitle.isNotEmpty()) {
                                        Text(
                                            text = playerSubtitle,
                                            color = Color.White.copy(alpha = 0.7f),
                                            style = if (isFullscreen) {
                                                MaterialTheme.typography.titleSmall
                                            } else {
                                                MaterialTheme.typography.bodySmall
                                            },
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = when (val state = serversState) {
                                            is ServersState.Resolving ->
                                                "Searching sources… ${state.servers.size} found"
                                            is ServersState.Empty -> "No servers responded"
                                            is ServersState.Ready -> "Choose a server to continue"
                                            ServersState.Idle -> if (isResolvingLocalUri) {
                                                "Opening offline video…"
                                            } else {
                                                "Preparing sources…"
                                            }
                                        },
                                        color = Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    if (serversState is ServersState.Empty) {
                                        Button(
                                            onClick = viewModel::retryResolution,
                                            shape = ExpressiveShapes.medium
                                        ) {
                                            Text("Retry sources")
                                        }
                                    } else if (serversState is ServersState.Ready) {
                                        Button(
                                            onClick = { showServerPicker = true },
                                            shape = ExpressiveShapes.medium
                                        ) {
                                            Text("Choose a source")
                                        }
                                    }
                                }

                                // Back button
                                Box(modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(if (isFullscreen) 16.dp else 12.dp)
                                ) {
                                    ExpressiveBackButton(
                                        onClick = onBackClick,
                                        containerColor = Color.White.copy(alpha = 0.1f),
                                        contentColor = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val target = nextEpisode
            if (showUpNext && target != null && !isInPictureInPicture) {
                UpNextOverlay(
                    target = target,
                    onPlayNow = {
                        showUpNext = false
                        onEpisodeClick(target.season, target.episode)
                    },
                    onCancel = { showUpNext = false },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(if (isFullscreen) 32.dp else 12.dp)
                )
            }

            // Once a stream plays, Sources is a page inside the player's settings panel.
            SourcesPanel(
                // While casting there is no in-player settings panel, so sources open here too.
                visible = showServerPicker && (videoUrl == null || isCasting) && downloadId == null,
                isFullscreen = isFullscreen,
                state = serversState,
                actions = sourceActions,
                onDismiss = { showServerPicker = false }
            )
        }

            // Under the video in portrait: what's playing, what's next, and the rest of the season.
            AnimatedVisibility(
                visible = !isImmersive,
                enter = fadeIn(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)) +
                    slideInVertically(tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)) { it / 4 },
                exit = fadeOut(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)) +
                    slideOutVertically(tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)) { it / 4 }
            ) {
                PlayerInfoPanel(
                    mediaType = mediaType,
                    season = season,
                    episode = episode,
                    details = mediaDetails,
                    currentEpisode = currentEpisode,
                    seasonEpisodes = seasonEpisodes,
                    isLoadingEpisodes = isLoadingEpisodes,
                    episodeProgress = episodeProgress,
                    nextEpisode = nextEpisode,
                    download = currentDownload,
                    canDownload = downloadId == null && activeServer?.isDownloadable == true,
                    isSaved = isSaved,
                    onPlayEpisode = onEpisodeClick,
                    onDownload = { activeServer?.let(viewModel::downloadVideo) },
                    onRemoveDownload = { currentDownload?.let { viewModel.removeDownload(it.downloadId) } },
                    onToggleSaved = viewModel::toggleSaved,
                    onOpenDetails = { onOpenDetails(mediaType, tmdbId) },
                    onOpenTitle = onOpenTitle
                )
            }
        }

        if (showCastSheet) {
            CastDeviceSheet(
                selector = viewModel.castRouteSelector,
                status = castStatus,
                onStopCasting = viewModel::stopCasting,
                onDismiss = { showCastSheet = false }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )
    }
}

/**
 * Lets fullscreen video use the area beside a notch or punch-hole instead of letterboxing around it.
 * Android 15+ already draws there for edge-to-edge apps; this covers older versions.
 */
private fun setCutoutMode(window: android.view.Window, drawIntoCutout: Boolean) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return
    val mode = when {
        !drawIntoCutout -> android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else -> android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = mode }
}
