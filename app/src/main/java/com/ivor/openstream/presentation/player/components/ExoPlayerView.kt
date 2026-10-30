package com.ivor.openstream.presentation.player.components

import android.app.Activity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.animateDpAsState
import com.ivor.openstream.presentation.player.session.SleepTimer
import com.ivor.openstream.domain.model.SkipSegment
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.domain.model.WatchProgress
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.data.repository.OpenSubtitlesRepository
import com.ivor.openstream.presentation.player.CaptionStyleSettings
import com.ivor.openstream.presentation.player.ServersState
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import com.ivor.openstream.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.focusable
import io.github.ivorisnoob.smoothmotion.media3.SmoothMotion
import io.github.ivorisnoob.smoothmotion.ui.bindSmoothMotion
import io.github.ivorisnoob.smoothmotion.ui.unbindSmoothMotion
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.ivor.openstream.data.settings.VideoGestureCustomization
import com.ivor.openstream.data.settings.VideoShaderCustomization
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.roundToInt
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.mutableIntStateOf
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.domain.model.forDuration
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.data.subtitles.SubtitleCue
import com.ivor.openstream.data.repository.SubSourceRepository
import com.ivor.openstream.data.subtitles.isSubtitleAd
import com.ivor.openstream.data.subtitles.parseSubtitles

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExoPlayerView(
    videoUrl: String,
    title: String,
    requestHeaders: Map<String, String>,
    /** The app-wide player from `PlaybackSession`; this view attaches to it but never releases it. */
    exoPlayer: ExoPlayer,
    /** Applies the stream's headers to the shared player's requests before a new item loads. */
    applyRequestHeaders: (Map<String, String>) -> Unit,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    remoteSubtitles: List<SubtitleDto> = emptyList(),
    subtitle: String = "",
    sourceLabel: String? = null,
    sourceSummary: String? = null,
    canChangeSource: Boolean = true,
    serversState: ServersState = ServersState.Idle,
    sourceActions: SourcesPageActions = SourcesPageActions({}, {}, {}),
    initialPositionMs: Long = 0L,
    onPositionChanged: (Long) -> Unit = {},
    onProgressChanged: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onPlaybackEnded: () -> Unit = {},
    onIsPlayingChanged: (Boolean) -> Unit = {},
    isInPictureInPicture: Boolean = false,
    togglePlaybackSignal: Int = 0,
    initialPlaybackSpeed: Float = 1f,
    onPlaybackSpeedChanged: (Float) -> Unit = {},
    onNextClick: (() -> Unit)? = null,
    captionSettings: CaptionStyleSettings = CaptionStyleSettings(),
    /** TMDB original language, used to tell original audio from a dub. */
    originalLanguage: String? = null,
    preferredAudioLanguage: String? = null,
    onAudioLanguageChosen: (String?) -> Unit = {},
    onCaptionSettingsChange: (CaptionStyleSettings) -> Unit = {},
    onPlaybackError: (PlaybackException?) -> Unit = {},
    onPlaybackReady: () -> Unit = {},
    isRotationLocked: Boolean = false,
    onRotationLockToggle: () -> Unit = {},
    sleepTimer: SleepTimer? = null,
    onSleepTimerChange: (SleepTimer?) -> Unit = {},
    seekStepSeconds: Int = 10,
    /** Last subtitle language picked, [SUBTITLES_OFF], or null if never chosen. */
    preferredSubtitleLanguage: String? = null,
    onSubtitleLanguageChosen: (String) -> Unit = {},
    /** Intro/recap/credits times from AniSkip; empty falls back to a manual skip early on. */
    skipSegments: List<SkipSegment> = emptyList(),
    showSkipButton: Boolean = true,
    /** Up Next is on screen: it takes the skip button's corner and, on a TV, the focus. */
    upNextVisible: Boolean = false,
    /** Leave the player screen with playback continuing in the mini player (swipe down inline). */
    onMinimize: () -> Unit = onBackClick,
    /** This season's episodes; a swipe up in fullscreen opens them. */
    episodes: List<EpisodeDto> = emptyList(),
    currentEpisodeNumber: Int = 0,
    episodeProgress: Map<Pair<Int, Int>, WatchProgress> = emptyMap(),
    onEpisodeSelected: (EpisodeDto) -> Unit = {},
    /** Opens the Cast device picker; null hides the Cast button. */
    onCastClick: (() -> Unit)? = null,
    /** Pops the video out into picture-in-picture; null hides the button. */
    onPictureInPictureClick: (() -> Unit)? = null,
    /** Downloads a sideloaded subtitle as text (gzip/zip handled); throws when it can't. */
    loadSubtitleText: suspend (url: String, headers: Map<String, String>) -> String = { _, _ -> throw IllegalStateException("No subtitle loader") },
    /** Container hint from the source (HLS for hosts whose URLs don't end in .m3u8). */
    mimeType: String? = null,
    /** Playback continues when the app leaves the foreground (the media notification takes over). */
    keepPlayingInBackground: Boolean = true,
    /** Artwork for the media notification and lock screen; a TMDB image URL. */
    artworkUri: String? = null,
    /** Optional SmoothMotion engine for real-time frame interpolation. */
    smoothMotion: SmoothMotion? = null,
    /** D-pad and remote-key handling, for the Android TV player surface. */
    dpadControls: Boolean = false,
    volumeBoostPercent: Int = 100,
    onVolumeBoostChange: (Int) -> Unit = {},
    onOpenExternalPlayer: (() -> Unit)? = null,
    /** The look the picture is drawn in; the master switch lives in Settings. */
    videoShaders: VideoShaderCustomization = VideoShaderCustomization(),
    /** A shader slider mid-drag: on the picture now, stored when the finger lifts. */
    onPreviewVideoShaders: (VideoShaderCustomization) -> Unit = {},
    onVideoShadersChange: (VideoShaderCustomization) -> Unit = {},
    /** Which of the player's optional gestures are on, and how long the controls stay up. */
    videoGestures: VideoGestureCustomization = VideoGestureCustomization(),
    /**
     * The saved position this session started from, when that is worth offering to skip. Null while
     * nothing was resumed; it is the starting point, not the position, so it does not follow
     * playback.
     */
    resumedFromMs: Long? = null,
    onPlayFromStart: () -> Unit = {}
) {
    val context = LocalContext.current
    // Read in composition for the track parser below, which runs in listener and
    // effect callbacks where stringResource cannot be called directly.
    val autoQualityLabel = stringResource(R.string.sheet_auto)
    val externalSourceLabel = stringResource(R.string.sub_external_source)
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) break
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }

    LaunchedEffect(preferredAudioLanguage) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(preferredAudioLanguage)
            .build()
    }

    // Player State
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var totalTime by remember { mutableLongStateOf(0L) }
    var bufferPercentage by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // UI State
    var areControlsVisible by remember { mutableStateOf(true) }
    // Bumped by every remote key press on the TV chrome, to restart its hide countdown.
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsInitialPage by remember { mutableStateOf(PlayerSettingsPage.MAIN) }

    // Settings State
    var playbackSpeed by remember { mutableFloatStateOf(initialPlaybackSpeed) }
    var qualityOptions by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<QualityOption?>(null) }
    var activeVideoHeight by remember { mutableIntStateOf(0) }
    var subtitleOptions by remember { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    var audioOptions by remember { mutableStateOf<List<AudioOption>>(emptyList()) }
    var selectedSubtitle by remember { mutableStateOf<SubtitleOption?>(null) }

    // Subtitle rendering state -- rendered in Compose, not PlayerView
    var currentSubtitleText by remember { mutableStateOf("") }
    var manualCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleLoadingState by remember { mutableStateOf<SubtitleLoadingState>(SubtitleLoadingState.IDLE) }

    // Gesture State
    var brightness by remember { mutableFloatStateOf(0.5f) } // 0.0 to 1.0, read at each swipe start
    var volume by remember { mutableFloatStateOf(exoPlayer.volume) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var gestureOverlayTimeout by remember { mutableLongStateOf(0L) }
    var seekFeedbackDirection by remember { mutableIntStateOf(0) }
    var seekFeedbackSequence by remember { mutableIntStateOf(0) }
    var seekStackSeconds by remember { mutableIntStateOf(0) }
    var videoScale by rememberSaveable { mutableStateOf(VideoScale.FIT) }
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var unlockButtonSequence by remember { mutableIntStateOf(0) }
    var isSpeedBoosted by remember { mutableStateOf(false) }
    // Vertical swipes: which job this swipe does, decided where it starts, and how far it has gone.
    var verticalMode by remember { mutableStateOf(VerticalSwipe.NONE) }
    var verticalTravel by remember { mutableFloatStateOf(0f) }
    // Follows the finger during a fullscreen/minimize swipe, then springs back.
    val swipeOffset by animateFloatAsState(
        targetValue = if (verticalMode == VerticalSwipe.FULLSCREEN) (verticalTravel * 0.25f).coerceIn(-120f, 120f) else 0f,
        label = "swipeOffset"
    )
    val latestIsFullscreen by rememberUpdatedState(isFullscreen)
    val latestFullscreenToggle by rememberUpdatedState(onFullscreenToggle)
    val latestMinimize by rememberUpdatedState(onMinimize)
    val hasEpisodes by rememberUpdatedState(episodes.isNotEmpty())
    var playPauseFeedback by remember { mutableIntStateOf(0) }
    var showPlayPauseFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(playPauseFeedback) {
        if (playPauseFeedback > 0) {
            showPlayPauseFeedback = true
            delay(600)
            showPlayPauseFeedback = false
        }
    }
    // Horizontal swipe to scrub: where the swipe started and where it would seek to on release.
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubStartMs by remember { mutableLongStateOf(0L) }
    var scrubTargetMs by remember { mutableLongStateOf(0L) }
    /** Positive delays sideloaded subtitles, negative shows them earlier. */
    var subtitleOffsetMs by remember { mutableLongStateOf(0L) }
    /** Set once this video's subtitle is settled, by the user or the remembered choice. */
    var subtitleChoiceMade by remember { mutableStateOf(false) }
    var scaleFeedbackSequence by remember { mutableIntStateOf(0) }
    var showScaleFeedback by remember { mutableStateOf(false) }

    // Hold to speed up: the rate, how far the held finger has slid, and the moment between the
    // long press firing and the hold having lasted long enough to mean it.
    var boostSpeed by remember { mutableFloatStateOf(BOOST_SPEED_MIN) }
    var boostResidue by remember { mutableFloatStateOf(0f) }
    var boostPending by remember { mutableStateOf(false) }
    // The platform's long press, which is only the trigger: the boost's own window is measured
    // from the same press, so the wait after it is what is left of that window.
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis
    // The lock's confirmation, and the hold that opens it again.
    var showLockedPill by remember { mutableStateOf(false) }
    var lockedPillSequence by remember { mutableIntStateOf(0) }
    // Zoom to fill, from the panel's switch. A new video starts letterboxed: how much of a picture a
    // window can afford to crop is this picture's answer, not a setting that outlives it.
    var zoomToFill by rememberSaveable(videoUrl) { mutableStateOf(false) }
    // The video's shape and the surface's, which together say what a fill would crop away.
    var videoAspect by remember { mutableStateOf<Float?>(null) }
    var surfaceWidth by remember { mutableIntStateOf(0) }
    var surfaceHeight by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    fun changeVideoScale(scale: VideoScale, announce: Boolean = true) {
        videoScale = scale
        // The panel's switch and the picture are two views of one choice, so they move together.
        zoomToFill = scale == VideoScale.ZOOM
        if (announce) {
            showScaleFeedback = true
            scaleFeedbackSequence++
        }
    }

    /** Whether a fill would crop no more than [MAX_ACCEPTABLE_CROP] of the picture. */
    fun canZoomToFill(): Boolean {
        val video = videoAspect ?: return false
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return false
        return isZoomToFillAvailable(video, surfaceWidth.toFloat() / surfaceHeight)
    }

    // The switch, the HUD's cycle and the pinch are three ways to one choice; this is what keeps
    // the picture in step with the switch. A fill the source cannot spare is refused, and one that
    // stops being affordable - a rotation, a window that narrows, a shape that arrives late - is
    // given back rather than left sitting there cropped past the cap.
    LaunchedEffect(zoomToFill, videoAspect, surfaceWidth, surfaceHeight) {
        if (zoomToFill) {
            when {
                // Nothing has said what shape the picture is yet: wait for it rather than
                // refusing a fill that may be perfectly fine.
                videoAspect == null || surfaceWidth <= 0 -> Unit
                canZoomToFill() -> {
                    if (videoScale != VideoScale.ZOOM) changeVideoScale(VideoScale.ZOOM)
                }
                // The picture comes back out with the switch when a fill can no longer be
                // afforded. Said quietly: nothing the viewer did brought this on.
                videoScale == VideoScale.ZOOM -> changeVideoScale(VideoScale.FIT, announce = false)
                else -> zoomToFill = false
            }
        } else if (videoScale == VideoScale.ZOOM) {
            changeVideoScale(VideoScale.FIT)
        }
    }

    LaunchedEffect(lockedPillSequence) {
        if (lockedPillSequence > 0) {
            delay(SCREEN_LOCK_PILL_MS)
            showLockedPill = false
        }
    }

    // The chip offering to skip a resumed position. It goes as soon as it is used, and on its own
    // after a while, since it is an offer rather than part of the player. The clock only runs while
    // the video plays, so a slow start does not use up the time it is on screen.
    var showResumeChip by remember(resumedFromMs) { mutableStateOf(resumedFromMs != null) }
    LaunchedEffect(showResumeChip, isPlaying, isBuffering) {
        if (showResumeChip && isPlaying && !isBuffering) {
            delay(RESUME_CHIP_VISIBLE_MS)
            showResumeChip = false
        }
    }

    LaunchedEffect(scaleFeedbackSequence) {
        if (scaleFeedbackSequence > 0) {
            delay(900)
            showScaleFeedback = false
        }
    }

    // Read through State so the gesture handlers, created once, see a changed setting.
    val currentSeekStep by rememberUpdatedState(seekStepSeconds)
    val currentGestures by rememberUpdatedState(videoGestures)

    // Seeks in the same direction while the feedback is still up add together (10s, 20s, 30s...).
    fun showSeekFeedback(direction: Int) {
        seekStackSeconds = if (direction == seekFeedbackDirection) seekStackSeconds + currentSeekStep else currentSeekStep
        seekFeedbackDirection = direction
        seekFeedbackSequence++
    }

    fun seekStep(direction: Int) {
        exoPlayer.seekTo((exoPlayer.currentPosition + direction * currentSeekStep * 1_000L).coerceAtLeast(0L))
        showSeekFeedback(direction)
        currentTime = exoPlayer.currentPosition
    }

    /** Starts the hold's rate change, from wherever the held finger's slide has reached. */
    fun startSpeedBoost() {
        // The request is answered either way, so the guard below leaves nothing pending behind it.
        boostPending = false
        // Asked by the hold's own clock and by a slide that sets off inside it; a boost already
        // running means the other caller got here first, and re-coercing the rate under a finger
        // that is already sliding would move it.
        if (isSpeedBoosted) return
        // Starts at the rate the setting asks for; sliding the held finger still reaches every
        // other rate, so that is where it begins, not a cap.
        boostSpeed = currentGestures.holdSpeed.coerceIn(BOOST_SPEED_MIN, BOOST_SPEED_MAX)
        boostResidue = 0f
        isSpeedBoosted = true
        areControlsVisible = false
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        exoPlayer.setPlaybackParameters(
            exoPlayer.playbackParameters.withSpeed(boostSpeed)
        )
    }

    // The rest of the hold's own window, counted from the long press the platform has already waited
    // out. A release before it is up clears the flag and cancels this delay, so a tap that lingered
    // a moment past the long press never changes the rate.
    LaunchedEffect(boostPending, longPressTimeoutMs) {
        if (boostPending) {
            delay((SPEED_BOOST_HOLD_MS - longPressTimeoutMs).coerceAtLeast(0L))
            // Read again rather than left to the cancellation above: a release in the delay's last
            // moment is applied before this suspend resumes, so a boost started off a finger that
            // is already gone would be a boost nothing is left to end.
            if (boostPending) startSpeedBoost()
        }
    }

    /** Offers the unlock badge. The badge is what takes the offer away again if it goes unused. */
    fun revealUnlockButton() {
        // Only a reveal that brings the badge back starts its clock again; a second one while it is
        // already up - a second hand, a key, another tap - leaves the badge, and any hold on it,
        // exactly where they are.
        if (!showUnlockButton) unlockButtonSequence++
        showUnlockButton = true
    }

    /** Takes the lock off: the badge's ring and the screen reader's action both land here. */
    fun unlockScreen() {
        isLocked = false
        showUnlockButton = false
        areControlsVisible = true
    }

    fun togglePlaybackWithFeedback() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        playPauseFeedback++
        areControlsVisible = true
    }

    /**
     * Remote keys on Android TV. OK toggles playback, left/right seek, up/down reveal the controls
     * (which then take focus, so those keys navigate them instead). Media keys from headsets, the
     * notification and Android Auto land here too.
     */
    fun dpadAction(event: KeyEvent): Boolean {
        if (isLocked) return false
        val code = event.nativeKeyEvent.keyCode
        val isDown = event.type == KeyEventType.KeyDown
        return when (code) {
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_SPACE,
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                // Only on the press, and only for the first press of an auto-repeat.
                if (!isDown || event.nativeKeyEvent.repeatCount > 0) return false
                togglePlaybackWithFeedback()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (!isDown || event.nativeKeyEvent.repeatCount > 0) return false
                if (!exoPlayer.isPlaying) exoPlayer.play()
                areControlsVisible = true
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (!isDown || event.nativeKeyEvent.repeatCount > 0) return false
                if (exoPlayer.isPlaying) exoPlayer.pause()
                areControlsVisible = true
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (!isDown || event.nativeKeyEvent.repeatCount > 0) return false
                onNextClick?.invoke()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (!isDown) return false
                seekStep(1)
                areControlsVisible = true
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
            android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (!isDown) return false
                seekStep(-1)
                areControlsVisible = true
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isDown) return false
                seekStep(-1)
                areControlsVisible = true
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isDown) return false
                seekStep(1)
                areControlsVisible = true
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!isDown) return false
                areControlsVisible = true
                true
            }
            else -> false
        }
    }

    fun handleDpadKey(event: KeyEvent): Boolean = dpadAction(event)

    // While locked, back does nothing except show the unlock button.
    BackHandler(enabled = isLocked) { revealUnlockButton() }
    // Android TV: back hides the chrome first, then leaves the player.
    BackHandler(enabled = dpadControls && !isLocked) {
        if (areControlsVisible) areControlsVisible = false else onBackClick()
    }

    LaunchedEffect(seekFeedbackSequence) {
        if (seekFeedbackSequence > 0) {
            // Long enough for a follow-up tap to land after the double-tap timeout.
            delay(1_000)
            seekFeedbackDirection = 0
        }
    }

    // The video's shape, which zoom-to-fill's crop judgement needs. With the effect graph installed
    // the player itself reports no size, so Smooth motion's own reading is the source there and the
    // player's size is the fallback for when Smooth motion is not installed at all.
    val latestSmoothMotion by rememberUpdatedState(smoothMotion)
    LaunchedEffect(smoothMotion) {
        smoothMotion?.videoAspectRatio?.collect { videoAspect = it }
    }

    // Reset subtitle state when switching videos
    LaunchedEffect(videoUrl) {
        activeVideoHeight = 0
        // The shape is this video's business too, and nothing has said what it is yet.
        videoAspect = null
        subtitleChoiceMade = false
        selectedSubtitle = null
        manualCues = emptyList()
        currentSubtitleText = ""
        subtitleLoadingState = SubtitleLoadingState.IDLE
    }

    LaunchedEffect(selectedSubtitle) { subtitleOffsetMs = 0L }

    LaunchedEffect(selectedSubtitle, requestHeaders) {
        val urlStr = selectedSubtitle?.url
        if (urlStr != null) {
            subtitleLoadingState = SubtitleLoadingState.LOADING
            try {
                val raw = loadSubtitleText(urlStr, requestHeaders)
                manualCues = withContext(Dispatchers.Default) { parseSubtitles(raw).filterNot { it.text.isSubtitleAd() } }
                subtitleLoadingState = if (manualCues.isNotEmpty()) SubtitleLoadingState.SUCCESS else SubtitleLoadingState.ERROR
                Log.i("PlayerSubtitles", "Parsed ${manualCues.size} cues for manual sync")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PlayerSubtitles", "Failed to load sideloaded subtitles: ${e.message}")
                manualCues = emptyList()
                subtitleLoadingState = SubtitleLoadingState.ERROR
            }
        } else {
            manualCues = emptyList()
            subtitleLoadingState = SubtitleLoadingState.IDLE
        }
    }

    // Helper: parse available tracks from ExoPlayer. Runs in listener and effect
    // callbacks too, so it uses the hoisted context/resources rather than stringResource.
    fun parseTracksFromPlayer(tracks: Tracks) {
        val qualities = mutableListOf<QualityOption>()
        val subtitles = mutableListOf<SubtitleOption>()
        val audios = mutableListOf<AudioOption>()

        // Always add Auto as the first quality option
        qualities.add(QualityOption(label = autoQualityLabel, width = 0, height = 0, isAuto = true))

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            val trackType = group.type

            when (trackType) {
                C.TRACK_TYPE_VIDEO -> {
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        if (format.height > 0) {
                            val label = "${format.height}p"
                            val codec = format.codecs?.takeIf { it.isNotBlank() }
                            // Avoid duplicates: a repeated resolution over a codec that already
                            // has a row says nothing new, unless the codec differs.
                            if (qualities.none { it.label == label && it.codec == codec }) {
                                qualities.add(
                                    QualityOption(
                                        label = label,
                                        width = format.width,
                                        height = format.height,
                                        bitrate = format.bitrate,
                                        codec = codec
                                    )
                                )
                            }
                        }
                    }
                }

                C.TRACK_TYPE_AUDIO -> {
                    for (trackIndex in 0 until group.length) {
                        if (!group.isTrackSupported(trackIndex)) continue
                        val format = group.getTrackFormat(trackIndex)
                        Log.d(
                            "PlayerAudio",
                            "track g=$groupIndex t=$trackIndex id=${format.id} lang=${format.language} " +
                                "label=${format.label} channels=${format.channelCount} codecs=${format.codecs}"
                        )
                        // MPEG-TS language descriptors are often filler bytes (e.g. "```"); only
                        // trust codes Android recognises as a real language.
                        val languageName = format.language?.let(::displayLanguageOrNull)
                        val language = format.language.takeIf { languageName != null }
                        val channels = when (format.channelCount) {
                            1 -> "Mono"
                            2 -> "Stereo"
                            6 -> "5.1"
                            8 -> "7.1"
                            else -> null
                        }
                        audios += AudioOption(
                            label = languageName
                                ?: format.label?.takeIf { it.any(Char::isLetter) }
                                ?: if (group.length == 1 && audios.isEmpty()) context.getString(R.string.track_default) else context.getString(R.string.track_number, audios.size + 1),
                            language = language,
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            detail = listOfNotNull(
                                format.label?.takeIf { languageName != null && it != languageName },
                                channels
                            ).joinToString(" · ").ifEmpty { null },
                            isSelected = group.isTrackSelected(trackIndex)
                        )
                    }
                }

                C.TRACK_TYPE_TEXT -> {
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val trackId = format.id ?: "none"
                        // Match with endsWith to handle HLS group prefixes like "1:195..."
                        val remoteMatch = remoteSubtitles.find { 
                            it.id == trackId || trackId.endsWith(":${it.id}") 
                        }
                        
                        val label = when {
                            remoteMatch != null -> remoteMatch.display ?: remoteMatch.language?.uppercase() ?: displayLanguageOrNull("en").orEmpty()
                            format.label == "English (Extracted)" || trackId == "extracted" -> context.getString(R.string.track_extracted)
                            format.label != null -> format.label!!
                            format.language != null -> {
                                val lang = format.language!!
                                val locale = if (lang.length <= 3) java.util.Locale(lang) 
                                             else try { java.util.Locale.forLanguageTag(lang.replace("_", "-")) } catch(e:Exception) { java.util.Locale.ENGLISH }
                                
                                val display = locale.getDisplayLanguage(java.util.Locale.getDefault())
                                if (display.isNotEmpty() && !display.equals(lang, ignoreCase = true)) display else lang.uppercase()
                            }
                            else -> context.getString(R.string.track_number, subtitles.size + 1)
                        }

                        subtitles.add(
                            SubtitleOption(
                                label = label,
                                trackIndex = trackIndex,
                                groupIndex = groupIndex,
                                url = remoteMatch?.url,
                                subLabel = remoteMatch?.let { "${it.release ?: ""} (${it.source ?: ""})".trim() }.takeIf { it?.isNotEmpty() == true },
                                language = remoteMatch?.language ?: format.language
                            )
                        )
                    }
                }
            }
        }

        // 3. Merge in any remote subtitles that weren't matched to a track
        for (remote in remoteSubtitles) {
            if (subtitles.none { it.url == remote.url }) {
                subtitles.add(
                    SubtitleOption(
                        label = remote.display ?: remote.language?.uppercase() ?: displayLanguageOrNull("en").orEmpty(),
                        trackIndex = -1, // No internal track
                        groupIndex = -1,
                        url = remote.url,
                        subLabel = "${remote.release ?: ""} (${remote.source ?: externalSourceLabel})".trim(),
                        language = remote.language
                    )
                )
            }
        }

        // Sort qualities by height descending (Auto stays first)
        qualityOptions = listOf(qualities.first()) + qualities.drop(1).sortedByDescending { it.height }
        subtitleOptions = subtitles
        // One row per language/label pair; HLS often repeats a language per bitrate rendition.
        audioOptions = audios
            .groupBy { it.label to it.detail }
            .map { (_, same) -> same.firstOrNull { it.isSelected } ?: same.first() }

        // If no quality was explicitly selected, stay on Auto
        if (selectedQuality == null) {
            selectedQuality = qualityOptions.firstOrNull()
        }

        // Auto-select extracted subtitle if none selected, unless the user has a remembered choice.
        if (selectedSubtitle == null && preferredSubtitleLanguage == null) {
            val extracted = subtitles.find { it.label == "English (Extracted)" }
            if (extracted != null) {
                Log.i("PlayerSubtitles", "Auto-selecting extracted subtitle: ${extracted.label}")
                selectedSubtitle = extracted
                
                // Programmatically apply selection if player is ready
                val override = TrackSelectionOverride(
                    tracks.groups[extracted.groupIndex].mediaTrackGroup,
                    listOf(extracted.trackIndex)
                )
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .addOverride(override)
                    .build()
            }
        }
    }

    /** Shows [option] (null turns subtitles off), whether it is a stream track or a sideloaded file. */
    fun applySubtitle(option: SubtitleOption?) {
        selectedSubtitle = option
        if (option == null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            currentSubtitleText = ""
        } else if (option.trackIndex != -1) {
            // Enable internal track
            val tracks = exoPlayer.currentTracks
            if (option.groupIndex < tracks.groups.size) {
                val override = TrackSelectionOverride(
                    tracks.groups[option.groupIndex].mediaTrackGroup,
                    listOf(option.trackIndex)
                )
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .addOverride(override)
                    .build()
            }
        } else {
            // Purely external - disable internal text tracks to avoid mixing
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            currentSubtitleText = ""
        }
    }

    // Sideloaded files can arrive after the tracks were read; list them as soon as they do.
    LaunchedEffect(remoteSubtitles) { parseTracksFromPlayer(exoPlayer.currentTracks) }

    // Apply the remembered subtitle choice once per video, as soon as a matching track or file shows up.
    LaunchedEffect(subtitleOptions, preferredSubtitleLanguage) {
        val preferred = preferredSubtitleLanguage ?: return@LaunchedEffect
        if (subtitleChoiceMade) return@LaunchedEffect
        if (preferred == SUBTITLES_OFF) {
            subtitleChoiceMade = true
            applySubtitle(null)
            return@LaunchedEffect
        }
        val match = subtitleOptions
            .filter { !it.isDisabled && sameLanguage(it.language, preferred) }
            // Stream tracks first: they need no download and stay in sync.
            .minByOrNull { if (it.trackIndex != -1) 0 else 1 }
            ?: return@LaunchedEffect
        subtitleChoiceMade = true
        applySubtitle(match)
    }

        // Notification and lock-screen metadata: what the notification shows comes from the media item.
        val mediaMetadata = remember(title, subtitle, artworkUri) {
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle.takeIf { it.isNotEmpty() })
                .setArtworkUri(artworkUri?.let(android.net.Uri::parse))
                .build()
        }

        fun buildMediaItem(): MediaItem.Builder =
            MediaItem.Builder()
                .setUri(videoUrl)
                .setMimeType(mimeType)
                .setMediaMetadata(mediaMetadata)

    LaunchedEffect(videoUrl, remoteSubtitles) {
        val currentMediaItem = exoPlayer.currentMediaItem
        val currentUri = currentMediaItem?.localConfiguration?.uri
        val newUri = android.net.Uri.parse(videoUrl)
        
        // Gzipped community subtitles can't be read by the player itself; they load on demand instead.
        val embeddable = remoteSubtitles.filterNot { it.isSideloadOnly() }

        fun buildSubtitleConfigs(subs: List<SubtitleDto>): List<MediaItem.SubtitleConfiguration> {
            return subs.filterNot { it.isSideloadOnly() }.map { sub ->
                // More robust MIME type detection
                val isSrt = sub.url.lowercase().contains("srt") || sub.url.lowercase().contains("subrip")
                val format = if (isSrt) "application/x-subrip" else "text/vtt"
                
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(format)
                    .setLanguage(sub.language ?: "en")
                    .setLabel(sub.display ?: displayLanguageOrNull("en").orEmpty())
                    .setId(sub.id)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                    .build()
            }
        }

        // CASE 1: Video URL changed (Episode switch) -> Full Reset
        if (currentUri != newUri) {
            applyRequestHeaders(requestHeaders)
            val mediaItemBuilder = buildMediaItem()
            val configs = buildSubtitleConfigs(remoteSubtitles)
            if (configs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(configs)
            }

            val mediaItem = mediaItemBuilder.build()
            exoPlayer.setMediaItem(mediaItem, initialPositionMs.coerceAtLeast(0L))
            exoPlayer.prepare()
            exoPlayer.play()
            isBuffering = true
        } 
        // CASE 2: Subtitles arrived later (API finish) -> Hot Update
        else if (embeddable.isNotEmpty() &&
                 currentMediaItem?.localConfiguration?.subtitleConfigurations?.size != embeddable.size) {
            
            val currentPosition = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.isPlaying
            
            val mediaItemBuilder = buildMediaItem()
            val configs = buildSubtitleConfigs(remoteSubtitles)
            mediaItemBuilder.setSubtitleConfigurations(configs)
            
            // Replace media item without resetting position if possible
            exoPlayer.setMediaItem(mediaItemBuilder.build(), false)
            exoPlayer.prepare() // Need to re-prepare to discover new text tracks
            if (wasPlaying) exoPlayer.play()
            Log.i("PlayerSubtitles", "Sideloaded ${configs.size} subtitles successfully")
        }
    }

    LaunchedEffect(exoPlayer, initialPlaybackSpeed) {
        playbackSpeed = initialPlaybackSpeed
        exoPlayer.setPlaybackParameters(
            exoPlayer.playbackParameters.withSpeed(initialPlaybackSpeed)
        )
    }

    val latestPositionChanged by rememberUpdatedState(onPositionChanged)
    val latestProgressChanged by rememberUpdatedState(onProgressChanged)
    val latestPlaybackEnded by rememberUpdatedState(onPlaybackEnded)
    val latestIsPlayingChanged by rememberUpdatedState(onIsPlayingChanged)

    // Play/pause requests from outside the player surface (the picture-in-picture window).
    LaunchedEffect(togglePlaybackSignal) {
        if (togglePlaybackSignal > 0) {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
    }

    // Polling for position updates. The parent keeps a lightweight checkpoint so
    // changing route after an error can resume instead of restarting the episode.
    LaunchedEffect(exoPlayer) {
        var lastReportedPosition = initialPositionMs
        while (true) {
            currentTime = exoPlayer.currentPosition
            totalTime = exoPlayer.duration.coerceAtLeast(0L)
            bufferPercentage = exoPlayer.bufferedPercentage
            if (kotlin.math.abs(currentTime - lastReportedPosition) >= 1_000L) {
                latestPositionChanged(currentTime)
                latestProgressChanged(currentTime, totalTime)
                lastReportedPosition = currentTime
            }
            // Safety net: if player is actively playing, clear buffering state
            if (exoPlayer.isPlaying && isBuffering) {
                isBuffering = false
            }
            delay(200)
        }
    }

    // Auto-hide controls. On a TV each remote key press while the chrome is up restarts the
    // countdown, so moving through the bar one stop at a time never races it.
    LaunchedEffect(areControlsVisible, isPlaying, videoGestures.controlsHideSeconds, controlsInteraction) {
        if (areControlsVisible && isPlaying) {
            delay(if (dpadControls) 6000 else videoGestures.controlsHideSeconds * 1_000L)
            areControlsVisible = false
        }
    }

    // Keep the screen awake only while video is actively playing. The flag is
    // cleared on pause/stop and guaranteed cleared when the player leaves composition.
    DisposableEffect(isPlaying) {
        val window = activity?.window
        if (isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Pause when the app leaves the foreground so audio never keeps playing behind the launcher,
    // unless the user asked for background playback (the media notification takes over then).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer, keepPlayingInBackground) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !keepPlayingInBackground) {
                exoPlayer.pause()
                latestProgressChanged(
                    exoPlayer.currentPosition.coerceAtLeast(0L),
                    exoPlayer.duration.coerceAtLeast(0L)
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val latestPlaybackError by rememberUpdatedState(onPlaybackError)
    val latestPlaybackReady by rememberUpdatedState(onPlaybackReady)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        // Only show buffering overlay if player is not already playing
                        // HLS streams can report buffering on video while audio plays fine
                        isBuffering = !exoPlayer.isPlaying
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        totalTime = exoPlayer.duration
                        latestPlaybackReady()
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        isBuffering = false
                        areControlsVisible = true
                        latestProgressChanged(exoPlayer.duration, exoPlayer.duration)
                        latestPlaybackEnded()
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlayerError", "ExoPlayer Error: ${error.message}", error)
                isBuffering = false
                latestPlaybackError(error)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                latestIsPlayingChanged(playing)
                // If player starts producing output, it is not buffering
                if (playing) {
                    isBuffering = false
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                parseTracksFromPlayer(tracks)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                activeVideoHeight = videoSize.height
                // The stand-in for when nothing has installed a different pipeline: with the effect
                // graph in place Smooth motion's own reading is what carries the shape.
                if (videoSize.height > 0 && latestSmoothMotion == null) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                // Render subtitle cues in Compose instead of relying on PlayerView's SubtitleView
                val text = cueGroup.cues.joinToString("\n") { cue ->
                    cue.text?.toString() ?: ""
                }.trim()
                if (cueGroup.cues.isNotEmpty()) {
                    Log.d("PlayerSubtitles", "onCues: ${cueGroup.cues.size} cues, first='${cueGroup.cues.first().text}'")
                }
                currentSubtitleText = text
            }
        }
        exoPlayer.addListener(listener)
        // Re-attaching to a stream that kept playing (from the mini player): pick up its state.
        isPlaying = exoPlayer.isPlaying
        if (exoPlayer.playbackState == Player.STATE_READY) isBuffering = false
        parseTracksFromPlayer(exoPlayer.currentTracks)
        onDispose {
            latestPositionChanged(exoPlayer.currentPosition.coerceAtLeast(0L))
            latestProgressChanged(
                exoPlayer.currentPosition.coerceAtLeast(0L),
                exoPlayer.duration.coerceAtLeast(0L)
            )
            exoPlayer.removeListener(listener)
        }
    }

    // Android TV: the remote's D-pad and media keys drive playback when the surface is focused.
    // While a control inside has focus, the D-pad and OK belong to that control; only the
    // remote's media keys still go to the player.
    val videoHasFocus = remember { mutableStateOf(false) }
    val videoIsFocused = remember { mutableStateOf(false) }
    val videoFocusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    val dpadModifier = if (dpadControls) {
        Modifier
            .focusRequester(videoFocusRequester)
            .onFocusChanged {
                videoHasFocus.value = it.hasFocus
                videoIsFocused.value = it.isFocused
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!videoHasFocus.value) return@onPreviewKeyEvent false
                if (!videoIsFocused.value && !event.isMediaKey()) return@onPreviewKeyEvent false
                handleDpadKey(event)
            }
    } else {
        Modifier
    }
    // The video surface takes the focus when a new stream starts (a TV never leaves it orphaned).
    LaunchedEffect(dpadControls, videoUrl) {
        if (dpadControls) {
            runCatching { videoFocusRequester.requestFocus() }
            areControlsVisible = true
        }
    }
    // Skip intro / recap / credits. AniSkip segments show whenever playback is inside one; without
    // them, a manual jump is offered early in the video while the controls are up.
    var manualSkipUsed by remember(videoUrl) { mutableStateOf(false) }
    val fittingSegments = remember(skipSegments, totalTime / 10_000) { skipSegments.forDuration(totalTime) }
    val activeSegment = fittingSegments.firstOrNull { currentTime >= it.startMs && currentTime < it.endMs - 1_000 }
    val offerManualSkip = skipSegments.isEmpty() && !manualSkipUsed && areControlsVisible &&
        totalTime > MANUAL_SKIP_MS * 4 && currentTime in 5_000L..MANUAL_SKIP_WINDOW_MS
    val skipVisible = showSkipButton && (activeSegment != null || offerManualSkip) && !isLocked &&
        !isInPictureInPicture && !showSettingsDialog && !upNextVisible
    val skipFocusRequester = remember { FocusRequester() }
    var skipFocused by remember { mutableStateOf(false) }
    // Controls showing on a TV hands the focus to the control bar so D-pad navigates it. Hidden
    // again, it goes to the skip button if there is one, otherwise back to the video so the next key
    // press still reaches the player. Up Next, when it shows, holds the focus itself.
    LaunchedEffect(areControlsVisible, dpadControls, showSettingsDialog, upNextVisible) {
        if (!dpadControls || showSettingsDialog || upNextVisible) return@LaunchedEffect
        withFrameNanos { }
        runCatching {
            when {
                areControlsVisible -> controlsFocusRequester.requestFocus()
                skipVisible -> skipFocusRequester.requestFocus()
                else -> videoFocusRequester.requestFocus()
            }
        }
    }
    // A skip button appearing takes the focus only from the bare video, never from a control being
    // used, where Up from the progress bar reaches it. Leaving, it hands back the focus it held.
    LaunchedEffect(skipVisible, dpadControls) {
        if (!dpadControls || showSettingsDialog || upNextVisible) return@LaunchedEffect
        withFrameNanos { }
        runCatching {
            when {
                skipVisible && !areControlsVisible -> skipFocusRequester.requestFocus()
                !skipVisible && skipFocused && areControlsVisible -> controlsFocusRequester.requestFocus()
                !skipVisible && skipFocused -> videoFocusRequester.requestFocus()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                surfaceWidth = it.width
                surfaceHeight = it.height
            }
            .then(dpadModifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        // The press has gone, so whatever it was waiting to start is not
                        // starting now.
                        boostPending = false
                        if (isSpeedBoosted) {
                            isSpeedBoosted = false
                            boostResidue = 0f
                            exoPlayer.setPlaybackParameters(exoPlayer.playbackParameters.withSpeed(playbackSpeed))
                        }
                    },
                    onTap = { offset ->
                        val direction = tapZone(offset.x, size.width)
                        when {
                            isLocked -> revealUnlockButton()
                            // With controls hidden, a tap on the same side while seek feedback shows keeps seeking.
                            !areControlsVisible && direction != 0 && seekFeedbackDirection == direction -> seekStep(direction)
                            else -> areControlsVisible = !areControlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        val direction = tapZone(offset.x, size.width)
                        when {
                            isLocked -> revealUnlockButton()
                            // The middle toggles playback; the sides seek.
                            direction == 0 -> {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                playPauseFeedback++
                            }
                            else -> seekStep(direction)
                        }
                    },
                    onLongPress = {
                        // The long press is only the trigger. The rate changes once the hold has
                        // lasted the boost's own window, which is longer than the platform's long
                        // press, so a tap that lingered a moment past it changes nothing. A release
                        // in between calls the whole thing off from onPress above.
                        if (!isLocked && currentGestures.holdToSpeedUp && exoPlayer.isPlaying) {
                            boostPending = true
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        verticalTravel = 0f
                        verticalMode = when {
                            isLocked -> VerticalSwipe.NONE
                            // A held finger is running the video fast; sideways is the rate, and a
                            // wobble must not grab a lane on the way.
                            isSpeedBoosted -> VerticalSwipe.NONE
                            // Inline, any vertical swipe is about the player itself: up for
                            // fullscreen, down to shrink into the mini player.
                            !latestIsFullscreen -> VerticalSwipe.FULLSCREEN
                            // Fullscreen: brightness on the left third, volume on the right, and a
                            // swipe down the middle exits. Either lane can be switched off, and the
                            // swipe it would have taken falls back to the fullscreen pair.
                            offset.x < size.width / 3f && currentGestures.brightnessSwipe ->
                                VerticalSwipe.BRIGHTNESS
                            offset.x > size.width * 2f / 3f && currentGestures.volumeSwipe ->
                                VerticalSwipe.VOLUME
                            else -> VerticalSwipe.FULLSCREEN
                        }
                        // Start from the screen's real brightness so the first swipe doesn't jump.
                        if (verticalMode == VerticalSwipe.BRIGHTNESS) brightness = currentBrightness(activity)
                        if (isLocked) revealUnlockButton()
                    },
                    onDragEnd = {
                        if (verticalMode == VerticalSwipe.FULLSCREEN) {
                            val threshold = size.height * SWIPE_ACTION_FRACTION
                            when {
                                !latestIsFullscreen && verticalTravel < -threshold -> latestFullscreenToggle()
                                !latestIsFullscreen && verticalTravel > threshold -> latestMinimize()
                                latestIsFullscreen && verticalTravel > threshold -> latestFullscreenToggle()
                                latestIsFullscreen && verticalTravel < -threshold && hasEpisodes -> {
                                    settingsInitialPage = PlayerSettingsPage.EPISODES
                                    showSettingsDialog = true
                                    areControlsVisible = false
                                }
                            }
                        }
                        verticalMode = VerticalSwipe.NONE
                        verticalTravel = 0f
                        showBrightnessOverlay = false
                        showVolumeOverlay = false
                    },
                    onDragCancel = {
                        verticalMode = VerticalSwipe.NONE
                        verticalTravel = 0f
                        showBrightnessOverlay = false
                        showVolumeOverlay = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount / size.height // Swipe up to increase
                        when (verticalMode) {
                            VerticalSwipe.NONE -> Unit
                            VerticalSwipe.FULLSCREEN -> verticalTravel += dragAmount
                            VerticalSwipe.BRIGHTNESS -> {
                                brightness = (brightness + delta).coerceIn(0f, 1f)
                                showBrightnessOverlay = true
                                showVolumeOverlay = false
                                activity?.let { act ->
                                    val params = act.window.attributes
                                    params.screenBrightness = brightness
                                    act.window.setAttributes(params)
                                }
                                gestureOverlayTimeout = System.currentTimeMillis() + 2000
                            }
                            VerticalSwipe.VOLUME -> {
                                volume = (volume + delta).coerceIn(0f, 1f)
                                exoPlayer.volume = volume
                                showVolumeOverlay = true
                                showBrightnessOverlay = false
                                gestureOverlayTimeout = System.currentTimeMillis() + 2000
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (isLocked) {
                            revealUnlockButton()
                        } else if (isSpeedBoosted || boostPending) {
                            // A held finger slides the rate, so this drag is not a scrub. A slide
                            // that sets off inside the hold's own window has waited long enough:
                            // it starts the boost here rather than a step later.
                            if (boostPending) startSpeedBoost()
                            boostResidue = 0f
                        } else if (exoPlayer.duration > 0) {
                            scrubStartMs = exoPlayer.currentPosition
                            scrubTargetMs = scrubStartMs
                            isScrubbing = true
                            areControlsVisible = false
                            // A scrub says where the viewer decided to be, so the offer to start
                            // over stops being an offer.
                            showResumeChip = false
                        }
                    },
                    onDragEnd = {
                        if (isScrubbing) {
                            exoPlayer.seekTo(scrubTargetMs)
                            currentTime = scrubTargetMs
                            isScrubbing = false
                        }
                    },
                    onDragCancel = { isScrubbing = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (isSpeedBoosted) {
                            // One step per BOOST_SPEED_STEP_TRAVEL of travel, banked and spent a
                            // step at a time so a wobble cannot throw the rate and the rate comes
                            // back with the finger.
                            boostResidue += dragAmount
                            val stepTravel = BOOST_SPEED_STEP_TRAVEL.toPx()
                            var steps = 0
                            while (boostResidue >= stepTravel) {
                                steps++
                                boostResidue -= stepTravel
                            }
                            while (boostResidue <= -stepTravel) {
                                steps--
                                boostResidue += stepTravel
                            }
                            if (steps != 0) {
                                val stepped = (boostSpeed / BOOST_SPEED_STEP).roundToInt() + steps
                                val next = (stepped * BOOST_SPEED_STEP)
                                    .coerceIn(BOOST_SPEED_MIN, BOOST_SPEED_MAX)
                                // At either end the push outward is dropped, so turning back
                                // answers at once instead of unwinding an overflow nobody can see.
                                if (next <= BOOST_SPEED_MIN || next >= BOOST_SPEED_MAX) {
                                    boostResidue = 0f
                                }
                                if (next != boostSpeed) {
                                    boostSpeed = next
                                    exoPlayer.setPlaybackParameters(
                                        exoPlayer.playbackParameters.withSpeed(next)
                                    )
                                    // An end reads as a stop: the rate has stopped following the
                                    // finger, which is worth a different tap than a step is.
                                    haptics.performHapticFeedback(
                                        if (next <= BOOST_SPEED_MIN || next >= BOOST_SPEED_MAX) {
                                            HapticFeedbackType.LongPress
                                        } else {
                                            HapticFeedbackType.SegmentTick
                                        }
                                    )
                                }
                            }
                        } else if (isScrubbing) {
                            val duration = exoPlayer.duration.coerceAtLeast(0L)
                            // A full-width swipe covers up to three minutes, so short swipes stay precise.
                            val msPerPx = duration.coerceAtMost(SCRUB_FULL_WIDTH_MS).toFloat() / size.width
                            scrubTargetMs = (scrubTargetMs + (dragAmount * msPerPx).toLong()).coerceIn(0L, duration)
                        }
                    }
                )
            }
            // Declared last so it sees events first: once a second finger lands it consumes the
            // gesture, which cancels the tap and brightness/volume detectors above.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f
                    var pinching = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            pinching = true
                            zoom *= event.calculateZoom()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinching && !isLocked) {
                        when {
                            zoom > 1.1f && videoScale != VideoScale.ZOOM -> {
                                // A fill is refused when it would crop more than the picture can
                                // spare: a portrait clip in a landscape window keeps its letterbox.
                                // A shape nobody has reported yet is taken on trust here, as the
                                // HUD takes it; the revocation gives the picture back if the fill
                                // turns out to be one this clip cannot afford.
                                val video = videoAspect
                                if (video == null || isZoomToFillAvailable(
                                        video,
                                        size.width.toFloat() / size.height
                                    )
                                ) {
                                    changeVideoScale(VideoScale.ZOOM)
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                            zoom < 0.9f && videoScale != VideoScale.FIT -> changeVideoScale(VideoScale.FIT)
                        }
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    smoothMotion?.let { bindSmoothMotion(it) }
                    layoutParams = FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    subtitleView?.visibility = android.view.View.GONE
                }
            },
            update = { view ->
                view.resizeMode = videoScale.resizeMode
                if (smoothMotion != null) {
                    view.bindSmoothMotion(smoothMotion)
                } else {
                    view.unbindSmoothMotion()
                }
            },
            // Hand the video surface back so the mini player can take it over.
            onRelease = { view ->
                view.unbindSmoothMotion()
                view.player = null
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = swipeOffset
                    val shrink = 1f - (kotlin.math.abs(swipeOffset) / 1200f)
                    scaleX = shrink
                    scaleY = shrink
                }
        )

        // Resumed position: an offer to skip it, gone as soon as it is taken or scrubbed past.
        AnimatedVisibility(
            visible = showResumeChip && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            // TV: top left, since the TV controls fill the lower third. Up from the controls reaches it.
            modifier = if (dpadControls) {
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 56.dp, top = 32.dp)
            } else {
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = if (isFullscreen) 96.dp else 72.dp)
            }
        ) {
            // One small pill: the whole thing is the button. The resumed time is in the spoken label
            // rather than on screen, where the seek bar already shows it.
            val resumedLabel = stringResource(R.string.vp_resumed_from, formatTime(resumedFromMs ?: 0L))
            val playFromStart = stringResource(R.string.vp_play_from_start)
            Surface(
                onClick = {
                    showResumeChip = false
                    onPlayFromStart()
                },
                shape = ExpressiveShapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "$resumedLabel. $playFromStart" }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(playFromStart, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Gesture Overlays
        androidx.compose.animation.AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GestureIndicator(
                icon = Icons.Default.BrightnessLow,
                value = (brightness * 100).toInt(),
                label = stringResource(R.string.sheet_brightness)
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GestureIndicator(
                icon = Icons.Default.VolumeUp,
                value = (volume * 100).toInt(),
                label = stringResource(R.string.sheet_volume)
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showScaleFeedback,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = ExpressiveShapes.extraLarge,
                color = Color.Black.copy(alpha = 0.62f),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(videoScale.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(videoScale.labelRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Auto-hide gesture overlays
        LaunchedEffect(gestureOverlayTimeout) {
            if (gestureOverlayTimeout > 0) {
                delay(2000)
                showBrightnessOverlay = false
                showVolumeOverlay = false
                gestureOverlayTimeout = 0
            }
        }

        // Compose-rendered subtitles -- always on top of video, below controls
        val displaySubtitleText = remember(currentTime, currentSubtitleText, manualCues, subtitleOffsetMs) {
            if (manualCues.isNotEmpty()) {
                val cueTime = currentTime - subtitleOffsetMs
                manualCues.find { cueTime in it.startMs..it.endMs }?.text ?: ""
            } else {
                currentSubtitleText
            }
        }

        if (displaySubtitleText.isNotEmpty()) {
            // The PiP window is only a couple hundred dp tall: full-size captions with the
            // controls' clearance would sit mid-frame, so hug the bottom edge and shrink them.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = when {
                            isInPictureInPicture -> 4.dp
                            isFullscreen -> 64.dp
                            else -> 40.dp
                        },
                        start = if (isInPictureInPicture) 4.dp else 12.dp,
                        end = if (isInPictureInPicture) 4.dp else 12.dp
                    )
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displaySubtitleText,
                    style = TextStyle(
                        color = Color(captionSettings.textColor.argb),
                        fontSize = if (isInPictureInPicture) {
                            (captionSettings.textSizeSp * PIP_CAPTION_SCALE).coerceAtLeast(9f).sp
                        } else {
                            captionSettings.textSizeSp.sp
                        },
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        shadow = Shadow(
                            color = Color.Black,
                            blurRadius = 4f
                        )
                    ),
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = captionSettings.backgroundOpacity),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        )
                        .padding(
                            horizontal = if (isInPictureInPicture) 4.dp else 12.dp,
                            vertical = if (isInPictureInPicture) 1.dp else 6.dp
                        )
                )
            }
        }

        if (dpadControls) TvPlayerControls(
            isVisible = areControlsVisible && !isInPictureInPicture && !upNextVisible,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            title = title,
            subtitle = subtitle,
            sourceLabel = sourceLabel,
            qualityLabel = qualityDisplayLabel(selectedQuality, activeVideoHeight),
            hasSubtitles = subtitleOptions.isNotEmpty(),
            subtitlesEnabled = selectedSubtitle?.isDisabled == false,
            currentTime = currentTime,
            totalTime = totalTime,
            seekStepSeconds = seekStepSeconds,
            videoScale = videoScale,
            onPauseToggle = {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
            onForward = { seekStep(1) },
            onRewind = { seekStep(-1) },
            onNextClick = onNextClick,
            onSubtitlesClick = {
                settingsInitialPage = PlayerSettingsPage.SUBTITLES
                showSettingsDialog = true
                areControlsVisible = false
            },
            onSourcesClick = {
                settingsInitialPage = PlayerSettingsPage.SOURCES
                showSettingsDialog = true
                areControlsVisible = false
            },
            onQualityClick = {
                settingsInitialPage = PlayerSettingsPage.QUALITY
                showSettingsDialog = true
                areControlsVisible = false
            },
            onSettingsClick = {
                settingsInitialPage = PlayerSettingsPage.MAIN
                showSettingsDialog = true
                areControlsVisible = false
            },
            onVideoScaleClick = {
                // Same rule as the phone: a fill is only entered when the picture can spare it.
                val next = videoScale.next()
                if (next != VideoScale.ZOOM || videoAspect == null || canZoomToFill()) {
                    changeVideoScale(next)
                } else {
                    changeVideoScale(next.next())
                }
            },
            onInteraction = { controlsInteraction++ },
            playFocusRequester = controlsFocusRequester
        ) else PlayerControls(
            isVisible = areControlsVisible && !isInPictureInPicture && !isLocked,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            title = title,
            subtitle = subtitle,
            sourceLabel = sourceLabel,
            qualityLabel = qualityDisplayLabel(selectedQuality, activeVideoHeight),
            hasSubtitles = subtitleOptions.isNotEmpty(),
            subtitlesEnabled = selectedSubtitle?.isDisabled == false,
            currentTime = currentTime,
            totalTime = totalTime,            onPauseToggle = {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.play()
                }
                areControlsVisible = true
            },
            onSeek = { position ->
                exoPlayer.seekTo(position)
                currentTime = position
                areControlsVisible = true
            },
            onForward = {
                seekStep(1)
                areControlsVisible = true
            },
            onRewind = {
                seekStep(-1)
                areControlsVisible = true
            },
            onNextClick = onNextClick,
            onSettingsClick = {
                settingsInitialPage = PlayerSettingsPage.MAIN
                showSettingsDialog = true
                areControlsVisible = false
            },
            onSourcesClick = {
                settingsInitialPage = PlayerSettingsPage.SOURCES
                showSettingsDialog = true
                areControlsVisible = false
            },
            onQualityClick = {
                settingsInitialPage = PlayerSettingsPage.QUALITY
                showSettingsDialog = true
                areControlsVisible = false
            },
            onSubtitlesClick = {
                settingsInitialPage = PlayerSettingsPage.SUBTITLES
                showSettingsDialog = true
                areControlsVisible = false
            },
            isFullscreen = isFullscreen,
            onFullscreenToggle = {
                onFullscreenToggle()
                areControlsVisible = true
            },
            onLockClick = {
                isLocked = true
                areControlsVisible = false
                showSettingsDialog = false
                revealUnlockButton()
                // The lock hides its own button, so this pill is the only thing that says the tap
                // did anything.
                showLockedPill = true
                lockedPillSequence++
            },
            isRotationLocked = isRotationLocked,
            onRotationLockToggle = {
                onRotationLockToggle()
                areControlsVisible = true
            },
            seekStepSeconds = seekStepSeconds,
            videoScale = videoScale,
            onVideoScaleClick = {
                // The cycle has a stop the source cannot take: a fill is only entered when the
                // picture can spare it, or when nothing has said what shape it is yet - the effect
                // above decides that case, and gives the fill back if the answer is no.
                val next = videoScale.next()
                if (next != VideoScale.ZOOM || videoAspect == null || canZoomToFill()) {
                    changeVideoScale(next)
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                areControlsVisible = true
            },
            onCastClick = onCastClick,
            onPictureInPictureClick = onPictureInPictureClick?.let { enter ->
                {
                    areControlsVisible = false
                    enter()
                }
            },
            controlsFocusRequester = controlsFocusRequester.takeIf { dpadControls },
            onBackClick = onBackClick
        )

        AnimatedVisibility(
            visible = seekFeedbackDirection != 0,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(
                    if (seekFeedbackDirection < 0) Alignment.CenterStart
                    else Alignment.CenterEnd
                )
                .padding(horizontal = if (isFullscreen) 72.dp else 28.dp)
        ) {
            Surface(
                shape = ExpressiveShapes.extraLarge,
                color = Color.Black.copy(alpha = 0.62f),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (seekFeedbackDirection < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        if (seekFeedbackDirection < 0) {
                            stringResource(R.string.sheet_back_seconds, seekStackSeconds)
                        } else {
                            stringResource(R.string.sheet_forward_seconds, seekStackSeconds)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isSpeedBoosted,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Surface(
                shape = ExpressiveShapes.extraLarge,
                color = Color.Black.copy(alpha = 0.62f),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        // The rate the hold is actually running at, which a slide changes.
                        text = formatBoostRate(boostSpeed),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isLocked && showUnlockButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            // Keyed on the reveal: a fresh reveal is a fresh offer, with its own timer. A reveal
            // while the badge is up does not change the key, so a hold that is running survives it.
            key(unlockButtonSequence) {
                ScreenUnlockBadge(
                    onUnlock = { unlockScreen() },
                    onExpire = { showUnlockButton = false }
                )
            }
        }

        // A screen reader cannot reveal the badge: a tap activates the focused node rather than the
        // surface, so an offer that is not on screen is an offer it cannot take. While the badge is
        // away, the same unlock action rides on a node the eye never meets - one point of it, no
        // looks and nothing to brush - so the way out the tap gives everyone else is there for
        // TalkBack at any moment.
        if (isLocked && !showUnlockButton) {
            val unlockLabel = stringResource(R.string.vp_unlock_screen)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .size(1.dp)
                    .semantics {
                        contentDescription = unlockLabel
                        customActions = listOf(
                            CustomAccessibilityAction(unlockLabel) {
                                // The ring's confirmation is not skipped with the ring.
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                unlockScreen()
                                true
                            }
                        )
                    }
            )
        }

        AnimatedVisibility(
            visible = showLockedPill,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.vp_screen_locked),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = showPlayPauseFeedback,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White) {
                Icon(
                    if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(18.dp)
                        .size(40.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            ScrubPreview(
                targetMs = scrubTargetMs,
                deltaMs = scrubTargetMs - scrubStartMs,
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            )
        }

        val skipLabel = activeSegment?.let { stringResource(it.type.labelRes) } ?: stringResource(R.string.player_skip_seconds, MANUAL_SKIP_MS / 1_000)
        val onSkip = {
            val target = activeSegment?.endMs ?: (exoPlayer.currentPosition + MANUAL_SKIP_MS)
            exoPlayer.seekTo(target)
            currentTime = target
            if (activeSegment == null) manualSkipUsed = true
        }
        // On a TV it sits clear of the control bar, which fills the bottom of the screen.
        val tvSkipBottom by animateDpAsState(if (areControlsVisible) 244.dp else 48.dp, label = "tvSkipBottom")
        AnimatedVisibility(
            visible = skipVisible,
            enter = slideInHorizontally { it / 2 } + fadeIn(),
            exit = slideOutHorizontally { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = if (dpadControls) 56.dp else if (isFullscreen) 24.dp else 12.dp,
                    bottom = if (dpadControls) tvSkipBottom else if (isFullscreen) 88.dp else 56.dp
                )
        ) {
            if (dpadControls) {
                TvSkipButton(
                    label = skipLabel,
                    onClick = onSkip,
                    modifier = Modifier
                        .focusRequester(skipFocusRequester)
                        .onFocusChanged { skipFocused = it.isFocused }
                        // With the chrome hidden this is the only stop, so the other keys do what
                        // they do on the bare video: seek, or bring the controls up.
                        .onPreviewKeyEvent { event ->
                            if (areControlsVisible || event.isConfirmKey()) false else handleDpadKey(event)
                        }
                )
            } else {
                Button(
                    onClick = onSkip,
                    shape = ExpressiveShapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(skipLabel, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Buffering indicator overlay -- drawn AFTER controls so it renders on top
        AnimatedVisibility(
            visible = isBuffering && !areControlsVisible && !isInPictureInPicture,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = ExpressiveShapes.large,
                    color = Color.Black.copy(alpha = 0.72f),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadingIndicator(modifier = Modifier.size(32.dp))
                        Column {
                            Text(
                                stringResource(R.string.player_connecting),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            sourceLabel?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        PlayerSettingsHost(
            visible = showSettingsDialog,
            isFullscreen = isFullscreen,
            // TV: the D-pad variant renders the settings/side panel even before the
            // stream goes fullscreen, so no ModalBottomSheet is ever reachable there.
            tvControls = dpadControls,
            initialPage = settingsInitialPage,
            onDismiss = { showSettingsDialog = false },
            model = PlayerSettingsModel(
                sourceLabel = sourceLabel,
                sourceSummary = sourceSummary,
                canChangeSource = canChangeSource,
                serversState = serversState,
                qualityOptions = qualityOptions,
                selectedQuality = selectedQuality,
                activeVideoHeight = activeVideoHeight,
                currentSpeed = playbackSpeed,
                subtitleOptions = subtitleOptions,
                selectedSubtitle = selectedSubtitle,
                subtitleLoadingState = subtitleLoadingState,
                captionSettings = captionSettings,
                audioOptions = audioOptions,
                originalLanguage = originalLanguage,
                subtitleOffsetMs = subtitleOffsetMs,
                sleepTimer = sleepTimer,
                episodes = episodes,
                currentEpisode = currentEpisodeNumber,
                episodeProgress = episodeProgress,
                smoothMotion = smoothMotion,
                volumeBoostPercent = volumeBoostPercent,
                videoShaders = videoShaders,
                zoomToFill = zoomToFill,
                zoomToFillAvailable = canZoomToFill()
            ),
            actions = PlayerSettingsActions(
                sources = sourceActions,
                onQualitySelected = { option ->
                selectedQuality = option
                if (option.isAuto) {
                    // Reset to auto quality selection
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .clearVideoSizeConstraints()
                        .setMinVideoSize(0, 0)
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .build()
                } else {
                    // Constrain to selected resolution
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setMaxVideoSize(option.width, option.height)
                        .setMinVideoSize(option.width, option.height)
                        .build()
                }
                },
                onSpeedSelected = { speed ->
                playbackSpeed = speed
                onPlaybackSpeedChanged(speed)
                exoPlayer.setPlaybackParameters(
                    exoPlayer.playbackParameters.withSpeed(speed)
                )
                },
                onSubtitleSelected = { option ->
                    subtitleChoiceMade = true
                    applySubtitle(option)
                    // Remembered for the next title: its language, or that subtitles are off.
                    (if (option == null) SUBTITLES_OFF else option.language)?.let(onSubtitleLanguageChosen)
                    exoPlayer.play()
                },
                onCaptionSettingsChange = onCaptionSettingsChange,
                onSubtitleOffsetChange = { subtitleOffsetMs = it },
                onSleepTimerChange = onSleepTimerChange,
                onEpisodeSelected = onEpisodeSelected,
                onSmoothMotionToggle = { enabled -> smoothMotion?.enabled = enabled },
                onVolumeBoostChange = onVolumeBoostChange,
                onOpenExternalPlayer = onOpenExternalPlayer,
                onPreviewVideoShaders = onPreviewVideoShaders,
                onVideoShadersChange = onVideoShadersChange,
                onZoomToFillChange = { zoomToFill = it },
                onAudioSelected = { option ->
                    val tracks = exoPlayer.currentTracks
                    if (option.groupIndex < tracks.groups.size) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setPreferredAudioLanguage(option.language)
                            .setOverrideForType(
                                TrackSelectionOverride(
                                    tracks.groups[option.groupIndex].mediaTrackGroup,
                                    listOf(option.trackIndex)
                                )
                            )
                            .build()
                    }
                    onAudioLanguageChosen(option.language)
                }
            )
        )
    }
}

@Composable
private fun GestureIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    label: String
) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = ExpressiveShapes.extraLarge,
        modifier = Modifier
            .size(140.dp)
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$value%",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/** Where a hold-to-speed-up starts and stops, and how finely a slide moves it. */
private const val BOOST_SPEED_MIN = 2f
private const val BOOST_SPEED_MAX = 4f
private const val BOOST_SPEED_STEP = 0.1f
/** How far a held finger slides for one rate step. */
private val BOOST_SPEED_STEP_TRAVEL = 20.dp
/**
 * How long a press is held before the rate changes. Longer than the platform's long press, which
 * only triggers it: a tap that lingers a moment too long is not an instruction to hurry.
 */
private const val SPEED_BOOST_HOLD_MS = 650L

/** How long the unlock badge is held to open the lock. A tap is what a pocket does. */
private const val SCREEN_LOCK_HOLD_MS = 1_500L
/** How long "Screen locked" stays up. */
private const val SCREEN_LOCK_PILL_MS = 2_000L
/** How long the unlock badge waits, untouched, before it takes itself away. */
private const val UNLOCK_BUTTON_VISIBLE_MS = 2_500L
/** How long the resumed-from chip stays up before it takes itself away. */
private const val RESUME_CHIP_VISIBLE_MS = 5_000L

/**
 * How much of the picture filling the screen may crop away before the fill is refused.
 *
 * A 16:9 video on a tall phone loses a slim band top and bottom, which is the feature; a portrait
 * clip in a landscape window would lose most of the picture, so it keeps its letterbox.
 */
private const val MAX_ACCEPTABLE_CROP = 0.25f

/**
 * Fraction of the frame zoom-to-fill would crop away to fill a container.
 *
 * Filling scales by the larger factor, so the loss is what the two aspects differ by. Both are
 * width over height and must be positive: a 16:9 video in a 19.5:9 window loses about 0.18.
 */
internal fun zoomFillCropFraction(videoAspect: Float, containerAspect: Float): Float {
    require(videoAspect > 0f && containerAspect > 0f) {
        "Aspects must be positive, got video=$videoAspect container=$containerAspect"
    }
    return 1f - (minOf(videoAspect, containerAspect) / maxOf(videoAspect, containerAspect))
}

/** Whether a source of [videoAspect] may fill a window of [containerAspect] at all. */
internal fun isZoomToFillAvailable(videoAspect: Float, containerAspect: Float): Boolean =
    zoomFillCropFraction(videoAspect, containerAspect) <= MAX_ACCEPTABLE_CROP

/** "2x", "2.1x": whole rates read as whole numbers, and the rest keep one decimal. */
private fun formatBoostRate(rate: Float): String {
    val whole = rate.toInt()
    val tenth = ((rate - whole) * 10f).roundToInt()
    return if (tenth == 0) "${whole}x" else "$whole.${tenth}x"
}

/**
 * The unlock badge the lock leaves behind: a hold-to-confirm control rather than a button, because
 * a tap is what a pocket does.
 *
 * The ring sweeps as it is held and springs back if it is let go, so the gesture reads as a
 * commitment being made rather than a delayed click. TalkBack is offered an explicit unlock action
 * instead, since a press-and-hold is not a gesture a screen reader performs.
 */
@Composable
private fun ScreenUnlockBadge(
    onUnlock: () -> Unit,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier
) {
    var holding by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val ring by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = if (holding) {
            tween(durationMillis = SCREEN_LOCK_HOLD_MS.toInt(), easing = LinearEasing)
        } else {
            // Let go early and the ring empties with the same spring a touch gesture would settle
            // on, rather than snapping to nothing.
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "unlockHold"
    )

    // One clock, two jobs. A hold that is running owns it: a hold let go a frame early unlocks
    // nothing, and the offer is never taken away mid-ring, which is what would make the lock feel
    // stuck. The rest of the time it is the offer's own patience, and a release starts that wait
    // over. The caller keys this composable on the reveal that brings the badge back, so that
    // reveal starts it again.
    LaunchedEffect(holding) {
        if (holding) {
            delay(SCREEN_LOCK_HOLD_MS)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onUnlock()
        } else {
            delay(UNLOCK_BUTTON_VISIBLE_MS)
            onExpire()
        }
    }

    val unlockLabel = stringResource(R.string.vp_unlock_screen)
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = ExpressiveShapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = contentColor,
        modifier = modifier.semantics(mergeDescendants = true) {
            // A hold is not something a screen reader performs, so the label says what the control
            // does and the action does it.
            contentDescription = unlockLabel
            customActions = listOf(
                CustomAccessibilityAction(unlockLabel) {
                    // The ring's confirmation is not skipped with the ring.
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onUnlock()
                    true
                }
            )
        }
    ) {
        Row(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                // A cancelled press is a press that has gone: run this on the
                                // way out either way.
                                holding = false
                            }
                        }
                    )
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .drawBehind {
                        val stroke = 2.5.dp.toPx()
                        val inset = stroke / 2f
                        val ringSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(
                            color = contentColor.copy(alpha = 0.25f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = ringSize,
                            style = Stroke(width = stroke)
                        )
                        if (ring > 0f) {
                            drawArc(
                                color = contentColor,
                                startAngle = -90f,
                                sweepAngle = 360f * ring,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = ringSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = stringResource(R.string.vp_unlock_hint),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** A typical opening's length, for titles AniSkip has no times for. */
/** How far the manual skip (and PiP "Skip intro" without AniSkip times) jumps. */
const val MANUAL_SKIP_MS = 85_000L
/** The manual skip is only offered this early in a video. */
private const val MANUAL_SKIP_WINDOW_MS = 10 * 60_000L

private enum class VerticalSwipe { NONE, BRIGHTNESS, VOLUME, FULLSCREEN }

/** How far (as a share of the player's height) a swipe must travel to go fullscreen, exit or minimize. */
private const val SWIPE_ACTION_FRACTION = 0.12f

/** -1 for the left 35% of the player, 1 for the right 35%, 0 for the middle. */
private fun tapZone(x: Float, width: Int): Int = when {
    x < width * 0.35f -> -1
    x > width * 0.65f -> 1
    else -> 0
}

/** A full-width horizontal swipe scrubs at most this far. */
private const val SCRUB_FULL_WIDTH_MS = 180_000L

@Composable
private fun ScrubPreview(targetMs: Long, deltaMs: Long, durationMs: Long) {
    Surface(
        shape = ExpressiveShapes.extraLarge,
        color = Color.Black.copy(alpha = 0.7f),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .width(200.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${formatTime(targetMs)} / ${formatTime(durationMs)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = (if (deltaMs < 0) "−" else "+") + formatTime(kotlin.math.abs(deltaMs)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (durationMs > 0) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (targetMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/** How the video fills the player: letterboxed, cropped to fill the screen, or stretched. */
enum class VideoScale(val resizeMode: Int, val label: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit", R.string.scale_fit, Icons.Default.FitScreen),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom to fill", R.string.player_zoom_fill, Icons.Default.ZoomOutMap),
    STRETCH(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Stretch", R.string.scale_stretch, Icons.Default.AspectRatio);

    fun next(): VideoScale = entries[(ordinal + 1) % entries.size]
}

/** The window's brightness override, or the system brightness when the window has none. */
private fun currentBrightness(activity: Activity?): Float {
    val window = activity?.window ?: return 0.5f
    val override = window.attributes.screenBrightness
    if (override >= 0f) return override
    val system = runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull() ?: return 0.5f
    return (system / 255f).coerceIn(0f, 1f)
}

/** English name for a real ISO 639 code (`ja`, `jpn`, `pt-BR`), or null for junk and `und`. */
private fun displayLanguageOrNull(code: String): String? {
    val normalized = code.trim().replace('_', '-')
    if (!Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$").matches(normalized)) return null
    if (normalized.equals("und", ignoreCase = true)) return null
    val name = java.util.Locale.forLanguageTag(normalized).getDisplayLanguage(java.util.Locale.getDefault())
    return name.takeIf { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) }
}

/** Files the player can't read itself (compressed, or behind a download token); loaded on demand. */
private fun SubtitleDto.isSideloadOnly(): Boolean =
    source == OpenSubtitlesRepository.SOURCE_NAME || source == SubSourceRepository.SOURCE_NAME ||
        source == SavedSubtitleRepository.SOURCE_NAME ||
        url.substringBefore('?').substringAfterLast('.').lowercase() in setOf("gz", "zip")

/** Caption size in picture-in-picture relative to the user's chosen size. */
private const val PIP_CAPTION_SCALE = 0.55f

/** A remote's playback keys, which reach the player wherever the focus is. */
private fun KeyEvent.isConfirmKey(): Boolean = when (nativeKeyEvent.keyCode) {
    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
    android.view.KeyEvent.KEYCODE_ENTER,
    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> true
    else -> false
}

private fun KeyEvent.isMediaKey(): Boolean = when (nativeKeyEvent.keyCode) {
    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
    android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> true
    else -> false
}
