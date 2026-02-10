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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import com.ivor.openanime.presentation.player.components.ExoPlayerView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    tmdbId: Int,
    season: Int,
    episode: Int,
    onBackClick: () -> Unit
) {
    // Updated URL based on Vidking documentation
    val embedUrl = "https://www.vidking.net/embed/tv/$tmdbId/$season/$episode?autoPlay=true&color=663399" 
    val videoUrl = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    
    val title = "Season $season - Episode $episode"
    
    // Placeholder data for "Next Episodes"
    val nextEpisodes = remember {
        (episode + 1..episode + 10).map { "Episode $it" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Video Player Area (Fixed Aspect Ratio 16:9)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            if (videoUrl.value != null) {
                // Video extracted! Play with Custom ExoPlayer
                ExoPlayerView(
                    videoUrl = videoUrl.value!!,
                    title = title,
                    onBackClick = onBackClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Loading / Extraction Phase
                
                // Invisible WebView for extraction
                // Confined to the player box
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            
                            // User Agent is CRITICAL for some embed sites to serve content
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val url = request?.url?.toString()
                                    if (url != null) {
                                        if (url.contains(".m3u8") || url.contains(".mp4") || url.contains(".m4s") || url.contains("/manifest")) {
                                            // Found a potential video URL!
                                            if (!url.contains("googleads") && !url.contains("doubleclick") && !url.contains("telemetry")) {
                                                view?.post {
                                                    if (videoUrl.value == null) {
                                                        Log.i("PlayerSniffer", "Video URL Found: $url")
                                                        videoUrl.value = url
                                                        isLoading.value = false
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
                        .alpha(0f) // Keep it invisible
                )
                
                // Loading Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
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
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading Stream...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // 2. Details and Next Episodes (Scrollable)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f) // Fill remaining space
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

            // Next Episodes List
            items(nextEpisodes) { episodeTitle ->
                ListItem(
                    headlineContent = { 
                        Text(
                            text = episodeTitle, 
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        ) 
                    },
                    supportingContent = { 
                        Text(
                            text = "24m", 
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
                             Icon(
                                 imageVector = Icons.Default.PlayArrow,
                                 contentDescription = null,
                                 tint = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

