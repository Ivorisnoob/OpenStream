package com.ivor.openanime.presentation.player

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.ivor.openanime.presentation.player.components.ExoPlayerView

@SuppressLint("SetJavaScriptEnabled")
@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    tmdbId: Int,
    season: Int,
    episode: Int,
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    // Collect specific state updates
    val nextEpisodes by viewModel.nextEpisodes.collectAsState()
    val isLoadingEpisodes by viewModel.isLoadingEpisodes.collectAsState()

    // Trigger data fetch
    LaunchedEffect(tmdbId, season, episode) {
        viewModel.loadSeasonDetails(tmdbId, season, episode)
    }

    // Embed URL for extraction
    val embedUrl = "https://www.vidking.net/embed/tv/$tmdbId/$season/$episode?autoPlay=true&color=663399" 
    val videoUrl = remember { mutableStateOf<String?>(null) }
    
    val currentTitle = "Season $season - Episode $episode"

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Add top padding for status bar (immersive feel but content below status bar)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Video Player Area 
        // Increased height for better visibility (approx 16:9 but slightly taller container for controls)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f) 
                .background(Color.Black)
        ) {
            if (videoUrl.value != null) {
                ExoPlayerView(
                    videoUrl = videoUrl.value!!,
                    title = currentTitle,
                    onBackClick = onBackClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
                                                    if (videoUrl.value == null) {
                                                        Log.i("PlayerSniffer", "Video URL Found: $url")
                                                        videoUrl.value = url
                                                        view.stopLoading()
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
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Extracting Stream...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // 2. Details and Next Episodes
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Title and Description
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Season $season Episode $episode",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Release Date: 2024 • Rating: 4.8", 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // Next Episodes Header
            item {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
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

            items(nextEpisodes) { episodeItem ->
                ListItem(
                    headlineContent = { 
                        Text(
                            text = episodeItem.name, 
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    supportingContent = { 
                        Text(
                            text = "Episode ${episodeItem.episodeNumber} • ${episodeItem.runtime ?: "?"}m", 
                            style = MaterialTheme.typography.bodySmall
                        ) 
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (episodeItem.stillPath != null) {
                                AsyncImage(
                                    model = "https://image.tmdb.org/t/p/w500${episodeItem.stillPath}",
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


