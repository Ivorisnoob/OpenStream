package com.ivor.openanime.presentation.player

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import com.ivor.openanime.presentation.player.components.ExoPlayerView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (videoUrl.value != null) {
                // Video extracted! Play with ExoPlayer
                ExoPlayerView(
                    videoUrl = videoUrl.value!!,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Loading / Extraction Phase
                // Invisible WebView Sniffer
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
                                        Log.d("PlayerSniffer", "Intercepting: $url")
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
                        // Keep it effectively invisible but technically "visible" for rendering
                        .run { if (videoUrl.value == null) this else this.alpha(0f) } 
                )
                
                // Loading Overlay while extracting
                if (isLoading.value) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
