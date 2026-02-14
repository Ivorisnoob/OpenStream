package com.ivor.openanime.presentation.player.components

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.ivor.openanime.data.remote.model.SubtitleDto
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExoPlayerView(
    videoUrl: String,
    title: String,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    remoteSubtitles: List<SubtitleDto> = emptyList(),
    onNextClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Player State
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var totalTime by remember { mutableLongStateOf(0L) }
    var bufferPercentage by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // UI State
    var areControlsVisible by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Settings State
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var qualityOptions by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<QualityOption?>(null) }
    var subtitleOptions by remember { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    var selectedSubtitle by remember { mutableStateOf<SubtitleOption?>(null) }

    // Subtitle rendering state -- rendered in Compose, not PlayerView
    var currentSubtitleText by remember { mutableStateOf("") }
    var manualCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleLoadingState by remember { mutableStateOf<SubtitleLoadingState>(SubtitleLoadingState.IDLE) }

    // Reset subtitle state when switching videos
    LaunchedEffect(videoUrl) {
        selectedSubtitle = null
        manualCues = emptyList()
        currentSubtitleText = ""
        subtitleLoadingState = SubtitleLoadingState.IDLE
    }

    LaunchedEffect(selectedSubtitle) {
        val urlStr = selectedSubtitle?.url
        if (urlStr != null) {
            subtitleLoadingState = SubtitleLoadingState.LOADING
            withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL(urlStr)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    connection.setRequestProperty("Referer", "https://www.vidking.net/")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    
                    if (connection.responseCode != 200) {
                        throw Exception("Server returned code ${connection.responseCode} (Subtitles might be restricted)")
                    }

                    val raw = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("PlayerSubtitles", "Downloaded raw data: ${raw.take(100)}...")
                    
                    if (raw.trim().isEmpty()) {
                        throw Exception("Subtitle file is empty")
                    }

                    manualCues = parseSubtitles(raw)
                    subtitleLoadingState = if (manualCues.isNotEmpty()) SubtitleLoadingState.SUCCESS else SubtitleLoadingState.ERROR
                    
                    Log.i("PlayerSubtitles", "Parsed ${manualCues.size} cues for manual sync")
                } catch (e: Exception) {
                    Log.e("PlayerSubtitles", "Failed to parse sideloaded subtitles: ${e.message}")
                    manualCues = emptyList()
                    subtitleLoadingState = SubtitleLoadingState.ERROR
                }
            }
        } else {
            manualCues = emptyList()
            subtitleLoadingState = SubtitleLoadingState.IDLE
        }
    }

    val trackSelector = remember { 
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }
    }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.vidking.net/"))

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = true
            }
    }

    // Helper: parse available tracks from ExoPlayer
    fun parseTracksFromPlayer(tracks: Tracks) {
        val qualities = mutableListOf<QualityOption>()
        val subtitles = mutableListOf<SubtitleOption>()

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
                                        height = format.height
                                    )
                                )
                            }
                        }
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
                                subLabel = remoteMatch?.let { "${it.release ?: ""} (${it.source ?: ""})".trim() }.takeIf { it?.isNotEmpty() == true }
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
                        subLabel = "${remote.release ?: ""} (${remote.source ?: "External"})".trim()
                    )
                )
            }
        }

        // Sort qualities by height descending (Auto stays first)
        qualityOptions = listOf(qualities.first()) + qualities.drop(1).sortedByDescending { it.height }
        subtitleOptions = subtitles

        // If no quality was explicitly selected, stay on Auto
        if (selectedQuality == null) {
            selectedQuality = qualityOptions.firstOrNull()
        }

        // Auto-select extracted subtitle if none selected
        if (selectedSubtitle == null || selectedSubtitle?.isDisabled == true) {
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

    LaunchedEffect(videoUrl, remoteSubtitles) {
        val currentMediaItem = exoPlayer.currentMediaItem
        val currentUri = currentMediaItem?.localConfiguration?.uri
        val newUri = android.net.Uri.parse(videoUrl)
        
        fun buildSubtitleConfigs(subs: List<SubtitleDto>): List<MediaItem.SubtitleConfiguration> {
            return subs.map { sub ->
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
            val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)
            val configs = buildSubtitleConfigs(remoteSubtitles)
            if (configs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(configs)
            }

            val mediaItem = mediaItemBuilder.build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            isBuffering = true
        } 
        // CASE 2: Subtitles arrived later (API finish) -> Hot Update
        else if (remoteSubtitles.isNotEmpty() && 
                 currentMediaItem?.localConfiguration?.subtitleConfigurations?.size != remoteSubtitles.size) {
            
            val currentPosition = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.isPlaying
            
            val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)
            val configs = buildSubtitleConfigs(remoteSubtitles)
            mediaItemBuilder.setSubtitleConfigurations(configs)
            
            // Replace media item without resetting position if possible
            exoPlayer.setMediaItem(mediaItemBuilder.build(), false)
            exoPlayer.prepare() // Need to re-prepare to discover new text tracks
            if (wasPlaying) exoPlayer.play()
            Log.i("PlayerSubtitles", "Sideloaded ${configs.size} subtitles successfully")
        }
    }

    // Polling for position updates
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentTime = exoPlayer.currentPosition
            totalTime = exoPlayer.duration.coerceAtLeast(0L)
            bufferPercentage = exoPlayer.bufferedPercentage
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

    DisposableEffect(Unit) {
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
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        isBuffering = false
                        areControlsVisible = true
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlayerError", "ExoPlayer Error: ${error.message}", error)
                isBuffering = false
                // Attempt to recover playback if it was just a subtitle load error
                exoPlayer.prepare()
                exoPlayer.play()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                // If player starts producing output, it is not buffering
                if (playing) {
                    isBuffering = false
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                parseTracksFromPlayer(tracks)
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
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    // Disable PlayerView's own subtitle rendering since we render in Compose
                    subtitleView?.visibility = android.view.View.GONE
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    areControlsVisible = !areControlsVisible
                }
        )

        // Compose-rendered subtitles -- always on top of video, below controls
        val displaySubtitleText = remember(currentTime, currentSubtitleText, manualCues) {
            if (manualCues.isNotEmpty()) {
                manualCues.find { currentTime in it.startMs..it.endMs }?.text ?: ""
            } else {
                currentSubtitleText
            }
        }

        if (displaySubtitleText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isFullscreen) 64.dp else 40.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displaySubtitleText,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = if (isFullscreen) 18.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        shadow = Shadow(
                            color = Color.Black,
                            blurRadius = 4f
                        )
                    ),
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        PlayerControls(
            isVisible = areControlsVisible,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            title = title,
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
                exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
                currentTime = exoPlayer.currentPosition
                areControlsVisible = true
            },
            onRewind = {
                exoPlayer.seekTo(exoPlayer.currentPosition - 10000)
                currentTime = exoPlayer.currentPosition
                areControlsVisible = true
            },
            onNextClick = onNextClick,
            onSettingsClick = {
                showSettingsSheet = true
                areControlsVisible = false
            },
            isFullscreen = isFullscreen,
            onFullscreenToggle = {
                onFullscreenToggle()
                areControlsVisible = true
            },
            onBackClick = onBackClick
        )

        // Buffering indicator overlay -- drawn AFTER controls so it renders on top
        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        PlayerSettingsSheet(
            onDismiss = { showSettingsSheet = false },
            qualityOptions = qualityOptions,
            selectedQuality = selectedQuality,
            onQualitySelected = { option ->
                selectedQuality = option
                if (option.isAuto) {
                    // Reset to auto quality selection
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .clearVideoSizeConstraints()
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
            currentSpeed = playbackSpeed,
            onSpeedSelected = { speed ->
                playbackSpeed = speed
                exoPlayer.setPlaybackParameters(
                    exoPlayer.playbackParameters.withSpeed(speed)
                )
            },
            subtitleOptions = subtitleOptions,
            selectedSubtitle = selectedSubtitle,
            onSubtitleSelected = { option ->
                selectedSubtitle = option
                if (option == null) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                    currentSubtitleText = ""
                } else {
                    if (option.trackIndex != -1) {
                        // Enable internal track
                        val tracks = exoPlayer.currentTracks
                        if (option.groupIndex < tracks.groups.size) {
                            val group = tracks.groups[option.groupIndex]
                            val override = TrackSelectionOverride(
                                group.mediaTrackGroup,
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
                exoPlayer.play()
            },
            subtitleLoadingState = subtitleLoadingState
        )
    }
}

enum class SubtitleLoadingState {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

private fun parseSubtitles(content: String): List<SubtitleCue> {
    val cues = mutableListOf<SubtitleCue>()
    val cleanContent = content.replace("\ufeff", "").replace("\r\n", "\n").replace("\r", "\n")

    if (cleanContent.contains("[Events]")) {
        Log.i("PlayerSubtitles", "Detected ASS/SSA format")
        val lines = cleanContent.lines()
        val eventsIndex = lines.indexOfFirst { it.trim().contains("[Events]", ignoreCase = true) }
        if (eventsIndex != -1) {
            val dialogueLines = lines.drop(eventsIndex + 1).filter { it.trim().startsWith("Dialogue:", ignoreCase = true) }
            Log.d("PlayerSubtitles", "Found ${dialogueLines.size} Dialogue lines")
            for (line in dialogueLines) {
                try {
                    // Dialogue: 0,0:00:28.57,0:00:30.40,Default,,0,0,0,,Text
                    // Limit is 10 because the text part can contain commas
                    val parts = line.split(",", limit = 10)
                    if (parts.size >= 10) {
                        val start = parseAssTimestamp(parts[1])
                        val end = parseAssTimestamp(parts[2])
                        // Strip ASS override tags like {\fn...} and handle \N (newline)
                        val text = parts[9].replace(Regex("\\{[^}]*\\}"), "")
                                       .replace("\\N", "\n")
                                       .replace("\\n", "\n")
                                       .replace("\\h", " ")
                                       .trim()
                        if (text.isNotEmpty()) {
                            cues.add(SubtitleCue(start, end, text))
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
        }
    } else {
        Log.i("PlayerSubtitles", "Detected SRT/VTT format")
        val timestampRegex = Regex("(\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})")
        val blocks = cleanContent.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            val match = timestampRegex.find(block)
            
            if (match != null) {
                val start = parseTimestamp(match.groupValues[1])
                val end = parseTimestamp(match.groupValues[2])
                
                val textLines = lines.dropWhile { !it.contains("-->") }.drop(1)
                val textRaw = textLines.joinToString("\n").trim()
                
                if (textRaw.isNotEmpty()) {
                    val cleanedText = textRaw.replace(Regex("<[^>]*>"), "").trim()
                    if (cleanedText.isNotEmpty()) {
                        cues.add(SubtitleCue(start, end, cleanedText))
                    }
                }
            }
        }
    }
    
    if (cues.isNotEmpty()) {
        Log.i("PlayerSubtitles", "Successfully parsed ${cues.size} cues. First: ${cues.first().text}")
    } else {
        Log.w("PlayerSubtitles", "Parsed 0 cues from content length: ${cleanContent.length}")
    }
    return cues
}

private fun parseAssTimestamp(ts: String): Long {
    try {
        val parts = ts.trim().split(':')
        if (parts.size < 3) return 0L
        val h = parts[0].toLongOrNull() ?: 0L
        val m = parts[1].toLongOrNull() ?: 0L
        val sParts = parts[2].split('.')
        val s = sParts[0].toLongOrNull() ?: 0L
        val ms = if (sParts.size > 1) {
            // ASS usually has 2 decimals, e.g. .57 -> 570ms
            val msStr = sParts[1].padEnd(3, '0').take(3)
            msStr.toLongOrNull() ?: 0L
        } else 0L
        return (h * 3600 + m * 60 + s) * 1000 + ms
    } catch (e: Exception) {
        return 0L
    }
}

private fun parseTimestamp(ts: String): Long {
    val clean = ts.replace(',', '.')
    val parts = clean.split(':')
    if (parts.size < 3) return 0L
    
    val secondsParts = parts[2].split('.')
    
    val h = parts[0].toLongOrNull() ?: 0L
    val m = parts[1].toLongOrNull() ?: 0L
    val s = secondsParts[0].toLongOrNull() ?: 0L
    val ms = if (secondsParts.size > 1) {
        secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    } else 0L
    
    return (h * 3600 + m * 60 + s) * 1000 + ms
}
