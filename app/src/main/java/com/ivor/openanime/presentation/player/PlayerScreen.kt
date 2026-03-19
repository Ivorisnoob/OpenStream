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
import androidx.compose.foundation.BorderStroke
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
import android.app.DownloadManager
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
import androidx.compose.material3.Surface
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
    downloadId: String? = null,
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

    var videoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var isResolvingLocalUri by remember { mutableStateOf(downloadId != null) }

    var sniffedSubtitles by remember { mutableStateOf<List<SubtitleDto>>(emptyList()) }

    val allSubtitles = remember(remoteSubtitles, sniffedSubtitles) {
        (remoteSubtitles + sniffedSubtitles).distinctBy { it.url }
    }

    // Fullscreen state
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // Trigger data fetch
    LaunchedEffect(tmdbId, season, episode, downloadId) {
        if (downloadId != null) {
            isResolvingLocalUri = true
            val uri = viewModel.getPlaybackUri(downloadId)
            if (uri != null) {
                videoUrl = uri
                Log.d("PlayerScreen", "Playing from local/cache: $uri")
            }
            isResolvingLocalUri = false
        }
        viewModel.loadSeasonDetails(mediaType, tmdbId, season, episode)
    }

    // Embed URL for extraction (only if not playing local)
    val embedUrl = if (videoUrl == null) {
        if (mediaType == "movie") {
            "https://www.vidking.net/embed/movie/$tmdbId?autoPlay=true&color=663399"
        } else {
            "https://www.vidking.net/embed/tv/$tmdbId/$season/$episode?autoPlay=true&color=663399"
        }
    } else null
    
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
            onEpisodeClick(nextEpisode.episodeNumber)
        }
    } else null

    // Metadata Sniffer Bridge
    val webInterface = remember {
        object {
            @android.webkit.JavascriptInterface
            fun onMetadataFound(json: String) {
                Log.i("PlayerSniffer", "Metadata Found: $json")
                // Here we can parse the JSON and look for sources/qualities
            }
        }
    }

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
                if (hasUrl) {
                    ExoPlayerView(
                        videoUrl = videoUrl!!,
                        title = playerTitle,
                        subtitle = playerSubtitle,
                        dataSourceFactory = viewModel.dataSourceFactory,
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
                                    
                                    addJavascriptInterface(webInterface, "AndroidSniffer")

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            // Inject script to hook into XHR and fetch
                                            val injection = """
                                                (function() {
                                                    const originalOpen = XMLHttpRequest.prototype.open;
                                                    XMLHttpRequest.prototype.open = function(method, url) {
                                                        this.addEventListener('load', function() {
                                                            if (url.includes('api') || url.includes('source')) {
                                                                try {
                                                                    const response = JSON.parse(this.responseText);
                                                                    if (response.sources || response.file) {
                                                                        window.AndroidSniffer.onMetadataFound(this.responseText);
                                                                    }
                                                                } catch(e) {}
                                                            }
                                                        });
                                                        originalOpen.apply(this, arguments);
                                                    };

                                                    const originalFetch = window.fetch;
                                                    window.fetch = function() {
                                                        return originalFetch.apply(this, arguments).then(response => {
                                                            const clone = response.clone();
                                                            if (clone.url.includes('api') || clone.url.includes('source')) {
                                                                clone.text().then(text => {
                                                                    try {
                                                                        const json = JSON.parse(text);
                                                                        if (json.sources || json.file) {
                                                                            window.AndroidSniffer.onMetadataFound(text);
                                                                        }
                                                                    } catch(e) {}
                                                                });
                                                            }
                                                            return response;
                                                        });
                                                    };
                                                })();
                                            """.trimIndent()
                                            view?.evaluateJavascript(injection, null)
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val url = request?.url?.toString()
                                            if (url != null) {
                                                val isHls = url.contains(".m3u8") || url.contains("/manifest")
                                                val isVideo = url.contains(".mp4") || url.contains(".m4s")
                                                
                                                if (isHls || isVideo) {
                                                    if (!url.contains("googleads") && !url.contains("doubleclick") && !url.contains("telemetry")) {
                                                        view?.post {
                                                            // Prioritize MP4 over M3U8 for downloads/better playback
                                                            if (videoUrl == null || (videoUrl!!.contains(".m3u8") && isVideo)) {
                                                                Log.i("PlayerSniffer", "${if (isVideo) "Direct Video" else "HLS Stream"} URL Found: $url")
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
                                    if (embedUrl != null) {
                                        loadUrl(embedUrl)
                                    }
                                }
                            },
                            update = { view ->
                                if (embedUrl != null && view.url != embedUrl) {
                                    view.loadUrl(embedUrl)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0f)
                        )
                        
                        // Extraction / Loading Overlay (Cinematic)
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

                                // Centered Loading Content
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(64.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = playerTitle,
                                            color = Color.White,
                                            style = MaterialTheme.typography.displaySmall.copy(
                                                fontWeight = FontWeight.Black
                                            ),
                                            textAlign = TextAlign.Center
                                        )
                                        
                                        if (playerSubtitle.isNotEmpty()) {
                                            Text(
                                                text = playerSubtitle,
                                                color = Color.White.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.headlineSmall,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(32.dp))
                                        
                                        Text(
                                            text = "Step 1: Extracting Stream...",
                                            color = Color.White.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.labelLarge
                                        )
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
                // Editorial Header
                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                            .fillMaxWidth()
                    ) {
                        // Media Type / Series Context
                        if (mediaType != "movie") {
                            Text(
                                text = (mediaDetails?.name ?: "Series").uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Main Title (Display Grade)
                        Text(
                            text = if (mediaType == "movie") playerTitle else currentEpisode?.name ?: "Episode $episode",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 40.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Metadata Chips Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // S:E pill
                            if (mediaType != "movie") {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = ExpressiveShapes.small
                                ) {
                                    Text(
                                        text = "S$season : E$episode",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            // Rating
                            val rating = if (mediaType == "movie") mediaDetails?.voteAverage else currentEpisode?.voteAverage
                            if (rating != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB800),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = String.format("%.1f", rating),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Year
                            val date = if (mediaType == "movie") mediaDetails?.date else currentEpisode?.airDate
                            if (!date.isNullOrEmpty()) {
                                Text(
                                    text = date.take(4),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Genres
                        if (mediaDetails?.genres?.isNotEmpty() == true) {
                            Spacer(modifier = Modifier.height(20.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                mediaDetails?.genres?.take(3)?.forEach { genre ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = ExpressiveShapes.extraSmall,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Text(
                                            text = genre.name,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        // Overview (Editorial Style)
                        val overview = if (mediaType == "movie") mediaDetails?.overview else currentEpisode?.overview
                        if (!overview.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    lineHeight = 28.sp,
                                    letterSpacing = 0.2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }

                        // Download Action (Expressive Button)
                        val currentDownload by viewModel.currentDownload.collectAsState()
                        val downloadState = currentDownload?.status
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (downloadState == DownloadManager.STATUS_SUCCESSFUL) {
                                Button(
                                    onClick = { /* No-op */ },
                                    shape = ExpressiveShapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Available Offline")
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                OutlinedIconButton(
                                    onClick = { 
                                        currentDownload?.let { viewModel.removeDownload(it.downloadId) } 
                                    },
                                    modifier = Modifier.size(56.dp),
                                    shape = ExpressiveShapes.medium
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Download", tint = MaterialTheme.colorScheme.error)
                                }
                            } else if (downloadState == DownloadManager.STATUS_RUNNING || downloadState == DownloadManager.STATUS_PENDING) {
                                OutlinedButton(
                                    onClick = { 
                                        currentDownload?.let { viewModel.removeDownload(it.downloadId) }
                                    },
                                    shape = ExpressiveShapes.medium,
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    if (downloadState == DownloadManager.STATUS_RUNNING) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 3.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("${currentDownload?.progress}%")
                                    } else {
                                        Icon(Icons.Default.Schedule, contentDescription = null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Queued")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (videoUrl != null) {
                                            val fileName = "${playerTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")}_$tmdbId.mp4"
                                            viewModel.downloadVideo(videoUrl!!, playerTitle, fileName, mediaType, tmdbId, season, episode)
                                        }
                                    },
                                    enabled = videoUrl != null,
                                    shape = ExpressiveShapes.medium,
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Download Details")
                                }
                            }
                        }
                    }
                }

                // Next Episodes Header
                if (nextEpisodes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                
                if (isLoadingEpisodes) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }

                items(nextEpisodes) { ep ->
                    Surface(
                        onClick = {
                            videoUrl = null
                            sniffedSubtitles = emptyList()
                            onEpisodeClick(ep.episodeNumber)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        shape = ExpressiveShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // High Quality Thumbnail
                            Box(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(80.dp)
                                    .clip(ExpressiveShapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                AsyncImage(
                                    model = "https://image.tmdb.org/t/p/w500${ep.stillPath}",
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    alpha = 0.9f
                                )
                                
                                // Episode Number Badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                                            ExpressiveShapes.extraSmall
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "EP ${ep.episodeNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Metadata Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ep.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${ep.runtime ?: "?"} minutes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Bottom Padding for FAB or spacing
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

