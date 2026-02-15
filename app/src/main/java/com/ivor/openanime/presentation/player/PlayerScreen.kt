package com.ivor.openanime.presentation.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.ivor.openanime.data.remote.model.SubtitleDto
import com.ivor.openanime.presentation.player.components.ExoPlayerView
import com.ivor.openanime.presentation.components.ExpressiveBackButton
import com.ivor.openanime.ui.theme.ExpressiveShapes

// Expressive Motion Tokens
private val ExpressiveDefaultSpatial = CubicBezierEasing(0.38f, 1.21f, 0.22f, 1.00f)
private val ExpressiveDefaultEffects = CubicBezierEasing(0.34f, 0.80f, 0.34f, 1.00f)
private const val DurationSpatialDefault = 500
private const val DurationEffectsDefault = 200

@SuppressLint("SetJavaScriptEnabled")
@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    mediaType: String,
    tmdbId: Int,
    season: Int,
    episode: Int,
    onBackClick: () -> Unit,
    onEpisodeClick: (Int) -> Unit,
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

    var sniffedSubtitles by remember { mutableStateOf<List<SubtitleDto>>(emptyList()) }

    val allSubtitles = remember(remoteSubtitles, sniffedSubtitles) {
        (remoteSubtitles + sniffedSubtitles).distinctBy { it.url }
    }

    // Fullscreen state
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // Trigger data fetch
    LaunchedEffect(tmdbId, season, episode) {
        viewModel.loadSeasonDetails(mediaType, tmdbId, season, episode)
    }

    // Embed URL for extraction
    val embedUrl = if (mediaType == "movie") {
        "https://www.vidking.net/embed/movie/$tmdbId?autoPlay=true&color=663399"
    } else {
        "https://www.vidking.net/embed/tv/$tmdbId/$season/$episode?autoPlay=true&color=663399"
    }
    var videoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Dynamic Title for Player HUD
    val currentTitle = if (mediaType == "movie") {
         mediaDetails?.name ?: "Movie"
    } else {
         val showName = mediaDetails?.name ?: "Show"
         val epName = currentEpisode?.name ?: "Episode $episode"
         "$showName - S$season:E$episode $epName"
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
        }
        isFullscreen = true
    }

    fun exitFullscreen() {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = act.window
            WindowCompat.setDecorFitsSystemWindows(window, true)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        isFullscreen = false
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
            }
        }
    }

    // Determine next episode click
    val nextEpisode = nextEpisodes.firstOrNull { it.episodeNumber > episode }
    val onNextClick: (() -> Unit)? = if (nextEpisode != null) {
        {
            videoUrl = null
            sniffedSubtitles = emptyList()
            onEpisodeClick(nextEpisode.episodeNumber)
        }
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(if (isFullscreen) PaddingValues(0.dp) else WindowInsets.statusBars.asPaddingValues())
    ) {
        // 1. Video Player Area - Always present, size depends on isFullscreen
        val videoModifier = if (isFullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
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
                if (hasUrl) {
                    ExoPlayerView(
                        videoUrl = videoUrl!!,
                        title = currentTitle,
                        isFullscreen = isFullscreen,
                        onFullscreenToggle = {
                            if (isFullscreen) exitFullscreen() else enterFullscreen()
                        },
                        onBackClick = {
                            if (isFullscreen) exitFullscreen() else onBackClick()
                        },
                        modifier = Modifier.fillMaxSize(),
                        remoteSubtitles = allSubtitles,
                        onNextClick = onNextClick
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        // Invisible WebView for extraction
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val url = request?.url?.toString()
                                            if (url != null) {
                                                if (url.contains(".m3u8") || url.contains(".mp4") || url.contains(".m4s") || url.contains("/manifest")) {
                                                    if (!url.contains("googleads") && !url.contains("doubleclick") && !url.contains("telemetry")) {
                                                        view?.post {
                                                            if (videoUrl == null) {
                                                                Log.i("PlayerSniffer", "Video URL Found: $url")
                                                                videoUrl = url
                                                            }
                                                        }
                                                    }
                                                } else if (url.contains("sub.wyzie.ru") || url.contains(".vtt") || url.contains(".srt") || url.contains("subtitle")) {
                                                    if (!url.contains("googleads")) {
                                                        view?.post {
                                                            if (sniffedSubtitles.none { it.url == url }) {
                                                                Log.i("PlayerSniffer", "Subtitle URL Found: $url")
                                                                sniffedSubtitles = sniffedSubtitles + SubtitleDto(
                                                                    id = "sniffed_${url.hashCode()}",
                                                                    url = url,
                                                                    display = "Detected Subtitle ${sniffedSubtitles.size + 1}"
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                    }
                                    loadUrl(embedUrl)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0f)
                        )
                        
                        // Loading Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            // Back button visible during loading
                            Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                                ExpressiveBackButton(
                                    onClick = onBackClick,
                                    containerColor = Color.Black.copy(alpha = 0.5f),
                                    contentColor = Color.White
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LoadingIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Extracting Stream...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Details and Next Episodes - Only visible when NOT in fullscreen
        AnimatedVisibility(
            visible = !isFullscreen,
            enter = fadeIn(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)) + 
                    slideInVertically(tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)) { it / 4 },
            exit = fadeOut(tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)) + 
                   slideOutVertically(tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)) { it / 4 }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Title and Detailed Description
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Show Title (if TV) or Movie Tagline
                        if (mediaType != "movie") {
                            Text(
                                text = mediaDetails?.name ?: "TV Show",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        
                        // Main Title (Episode Name or Movie Name)
                        Text(
                            text = if (mediaType == "movie") mediaDetails?.name ?: "Movie" 
                                   else currentEpisode?.name ?: "Episode $episode",
                            style = MaterialTheme.typography.headlineSmall, // Expressive
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Metadata Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mediaType != "movie") {
                                Text(
                                    text = "S$season • E$episode",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            
                            val rating = if (mediaType == "movie") mediaDetails?.voteAverage else currentEpisode?.voteAverage
                            if (rating != null) {
                                Text(
                                    text = "★ ${String.format("%.1f", rating)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            
                            val date = if (mediaType == "movie") mediaDetails?.date else currentEpisode?.airDate
                            if (!date.isNullOrEmpty()) {
                                Text(
                                    text = date.take(4),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Genres Chips
                        if (mediaDetails?.genres?.isNotEmpty() == true) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                mediaDetails?.genres?.take(3)?.forEach { genre ->
                                    SuggestionChip(
                                        onClick = { /* No-op */ },
                                        label = { Text(genre.name) },
                                        shape = ExpressiveShapes.small,
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        // Overview (Plot)
                        val overview = if (mediaType == "movie") mediaDetails?.overview else currentEpisode?.overview
                        if (!overview.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                // Next Episodes Header
                if (nextEpisodes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                
                if (isLoadingEpisodes) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }

                items(nextEpisodes) { ep ->
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = ep.name, 
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) 
                        },
                        supportingContent = { 
                            Text(
                                text = "Episode ${ep.episodeNumber} • ${ep.runtime ?: "?"}m", 
                                style = MaterialTheme.typography.bodySmall
                            ) 
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(68.dp)
                                    .clip(ExpressiveShapes.small) // Expressive Shape
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                if (ep.stillPath != null) {
                                    AsyncImage(
                                        model = "https://image.tmdb.org/t/p/w500${ep.stillPath}",
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                 
                                // Play icon overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Reset state for new episode
                                videoUrl = null
                                sniffedSubtitles = emptyList()
                                onEpisodeClick(ep.episodeNumber)
                            }
                    )
                }
            }
        }
    }
}
