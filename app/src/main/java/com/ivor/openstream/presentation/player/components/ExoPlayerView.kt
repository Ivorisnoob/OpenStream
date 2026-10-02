package com.ivor.openstream.presentation.player.components

import android.app.Activity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
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
    onPlaybackError: () -> Unit = {},
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
    mimeType: String? = null
) {
    val context = LocalContext.current
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

    fun changeVideoScale(scale: VideoScale) {
        videoScale = scale
        showScaleFeedback = true
        scaleFeedbackSequence++
    }

    LaunchedEffect(scaleFeedbackSequence) {
        if (scaleFeedbackSequence > 0) {
            delay(900)
            showScaleFeedback = false
        }
    }

    // Read through State so the gesture handlers, created once, see a changed setting.
    val currentSeekStep by rememberUpdatedState(seekStepSeconds)

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

    fun revealUnlockButton() {
        showUnlockButton = true
        unlockButtonSequence++
    }

    LaunchedEffect(unlockButtonSequence) {
        if (unlockButtonSequence > 0) {
            delay(2500)
            showUnlockButton = false
        }
    }

    // While locked, back does nothing except show the unlock button.
    BackHandler(enabled = isLocked) { revealUnlockButton() }

    LaunchedEffect(seekFeedbackSequence) {
        if (seekFeedbackSequence > 0) {
            // Long enough for a follow-up tap to land after the double-tap timeout.
            delay(1_000)
            seekFeedbackDirection = 0
        }
    }

    // Reset subtitle state when switching videos
    LaunchedEffect(videoUrl) {
        activeVideoHeight = 0
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

    // Helper: parse available tracks from ExoPlayer
    fun parseTracksFromPlayer(tracks: Tracks) {
        val qualities = mutableListOf<QualityOption>()
        val subtitles = mutableListOf<SubtitleOption>()
        val audios = mutableListOf<AudioOption>()

        // Always add Auto as the first quality option
        qualities.add(QualityOption(label = "Auto", width = 0, height = 0, isAuto = true))

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            val trackType = group.type

            when (trackType) {
                C.TRACK_TYPE_VIDEO -> {
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        if (format.height > 0) {
                            val label = "${format.height}p"
                            // Avoid duplicates
                            if (qualities.none { it.label == label }) {
                                qualities.add(
                                    QualityOption(
                                        label = label,
                                        width = format.width,
                                        height = format.height,
                                        bitrate = format.bitrate
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
                                ?: if (group.length == 1 && audios.isEmpty()) "Default" else "Track ${audios.size + 1}",
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
                            remoteMatch != null -> remoteMatch.display ?: remoteMatch.language?.uppercase() ?: "English"
                            format.label == "English (Extracted)" || trackId == "extracted" -> "English (Extracted)"
                            format.label != null -> format.label!!
                            format.language != null -> {
                                val lang = format.language!!
                                val locale = if (lang.length <= 3) java.util.Locale(lang) 
                                             else try { java.util.Locale.forLanguageTag(lang.replace("_", "-")) } catch(e:Exception) { java.util.Locale.ENGLISH }
                                
                                val display = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
                                if (display.isNotEmpty() && !display.equals(lang, ignoreCase = true)) display else lang.uppercase()
                            }
                            else -> "Track ${subtitles.size + 1}"
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
                        label = remote.display ?: remote.language?.uppercase() ?: "English",
                        trackIndex = -1, // No internal track
                        groupIndex = -1,
                        url = remote.url,
                        subLabel = "${remote.release ?: ""} (${remote.source ?: "External"})".trim(),
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
                    .setLabel(sub.display ?: "English")
                    .setId(sub.id)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                    .build()
            }
        }

        // CASE 1: Video URL changed (Episode switch) -> Full Reset
        if (currentUri != newUri) {
            applyRequestHeaders(requestHeaders)
            val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl).setMimeType(mimeType)
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
            
            val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl).setMimeType(mimeType)
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

    // Auto-hide controls
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying) {
            delay(3000)
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

    // Pause when the app leaves the foreground so audio never keeps playing behind the launcher.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
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
                latestPlaybackError()
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        if (isSpeedBoosted) {
                            isSpeedBoosted = false
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
                        if (!isLocked && exoPlayer.isPlaying) {
                            isSpeedBoosted = true
                            areControlsVisible = false
                            exoPlayer.setPlaybackParameters(exoPlayer.playbackParameters.withSpeed(BOOST_SPEED))
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
                            // Inline, any vertical swipe is about the player itself: up for
                            // fullscreen, down to shrink into the mini player.
                            !latestIsFullscreen -> VerticalSwipe.FULLSCREEN
                            // Fullscreen: brightness on the left third, volume on the right, and a
                            // swipe down the middle exits.
                            offset.x < size.width / 3f -> VerticalSwipe.BRIGHTNESS
                            offset.x > size.width * 2f / 3f -> VerticalSwipe.VOLUME
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
                        } else if (!isSpeedBoosted && exoPlayer.duration > 0) {
                            scrubStartMs = exoPlayer.currentPosition
                            scrubTargetMs = scrubStartMs
                            isScrubbing = true
                            areControlsVisible = false
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
                        if (isScrubbing) {
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
                            zoom > 1.1f && videoScale != VideoScale.ZOOM -> changeVideoScale(VideoScale.ZOOM)
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
                    layoutParams = FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    subtitleView?.visibility = android.view.View.GONE
                }
            },
            update = { view -> view.resizeMode = videoScale.resizeMode },
            // Hand the video surface back so the mini player can take it over.
            onRelease = { view -> view.player = null },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = swipeOffset
                    val shrink = 1f - (kotlin.math.abs(swipeOffset) / 1200f)
                    scaleX = shrink
                    scaleY = shrink
                }
        )

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
                label = "Brightness"
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
                label = "Volume"
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
                        videoScale.label,
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
                        color = Color.White,
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

        PlayerControls(
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
            totalTime = totalTime,
            onPauseToggle = {
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
            },
            isRotationLocked = isRotationLocked,
            onRotationLockToggle = {
                onRotationLockToggle()
                areControlsVisible = true
            },
            seekStepSeconds = seekStepSeconds,
            videoScale = videoScale,
            onVideoScaleClick = {
                changeVideoScale(videoScale.next())
                areControlsVisible = true
            },
            onCastClick = onCastClick,
            onPictureInPictureClick = onPictureInPictureClick?.let { enter ->
                {
                    areControlsVisible = false
                    enter()
                }
            },
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
                        if (seekFeedbackDirection < 0) "$seekStackSeconds sec back" else "$seekStackSeconds sec ahead",
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
                    Text("2× speed", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
            Surface(
                onClick = {
                    isLocked = false
                    showUnlockButton = false
                    areControlsVisible = true
                },
                shape = ExpressiveShapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Text("Tap to unlock", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
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

        // Skip intro / recap / credits. AniSkip segments show whenever playback is inside one; without
        // them, a manual jump is offered early in the video while the controls are up.
        var manualSkipUsed by remember(videoUrl) { mutableStateOf(false) }
        val fittingSegments = remember(skipSegments, totalTime / 10_000) { skipSegments.forDuration(totalTime) }
        val activeSegment = fittingSegments.firstOrNull { currentTime >= it.startMs && currentTime < it.endMs - 1_000 }
        val offerManualSkip = skipSegments.isEmpty() && !manualSkipUsed && areControlsVisible &&
            totalTime > MANUAL_SKIP_MS * 4 && currentTime in 5_000L..MANUAL_SKIP_WINDOW_MS
        val skipLabel = activeSegment?.type?.label ?: "Skip ${MANUAL_SKIP_MS / 1_000}s"
        AnimatedVisibility(
            visible = (activeSegment != null || offerManualSkip) && !isLocked && !isInPictureInPicture && !showSettingsDialog,
            enter = slideInHorizontally { it / 2 } + fadeIn(),
            exit = slideOutHorizontally { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = if (isFullscreen) 24.dp else 12.dp, bottom = if (isFullscreen) 88.dp else 56.dp)
        ) {
            Button(
                onClick = {
                    val target = activeSegment?.endMs ?: (exoPlayer.currentPosition + MANUAL_SKIP_MS)
                    exoPlayer.seekTo(target)
                    currentTime = target
                    if (activeSegment == null) manualSkipUsed = true
                },
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
                                "Connecting to stream",
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
                episodeProgress = episodeProgress
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

private const val BOOST_SPEED = 2f
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
enum class VideoScale(val resizeMode: Int, val label: String, val icon: ImageVector) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit", Icons.Default.FitScreen),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom to fill", Icons.Default.ZoomOutMap),
    STRETCH(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Stretch", Icons.Default.AspectRatio);

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
    val name = java.util.Locale.forLanguageTag(normalized).getDisplayLanguage(java.util.Locale.ENGLISH)
    return name.takeIf { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) }
}

/** Files the player can't read itself (compressed, or behind a download token); loaded on demand. */
private fun SubtitleDto.isSideloadOnly(): Boolean =
    source == OpenSubtitlesRepository.SOURCE_NAME || source == SubSourceRepository.SOURCE_NAME ||
        source == SavedSubtitleRepository.SOURCE_NAME ||
        url.substringBefore('?').substringAfterLast('.').lowercase() in setOf("gz", "zip")

/** Caption size in picture-in-picture relative to the user's chosen size. */
private const val PIP_CAPTION_SCALE = 0.55f
