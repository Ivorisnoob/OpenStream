package com.ivor.openanime.presentation.watch_history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openanime.presentation.components.AnimeCard
import com.ivor.openanime.presentation.components.ExpressiveBackButton

// Expressive Motion Tokens
private val ExpressiveDefaultSpatial = CubicBezierEasing(0.38f, 1.21f, 0.22f, 1.00f) // 500ms
private val ExpressiveDefaultEffects = CubicBezierEasing(0.34f, 0.80f, 0.34f, 1.00f) // 200ms

private const val DurationSpatialDefault = 500
private const val DurationEffectsDefault = 200

private fun materialSharedAxisYIn(): ContentTransform {
    return (slideInVertically(
                animationSpec = tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)
            ) { height -> height / 2 } + 
            fadeIn(
                animationSpec = tween(DurationEffectsDefault, delayMillis = 50, easing = ExpressiveDefaultEffects)
            ))
        .togetherWith(
            slideOutVertically(
                animationSpec = tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)
            ) { height -> -height / 2 } + 
            fadeOut(
                animationSpec = tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)
            )
        )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WatchHistoryScreen(
    onBackClick: () -> Unit,
    onAnimeClick: (Int, String) -> Unit,
    viewModel: WatchHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Fixed Header matching SearchScreen pattern
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ExpressiveBackButton(onClick = onBackClick)
                
                // Expressive Clear Button
                if (uiState.history.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Watch History",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            val contentState = when {
                uiState.isLoading -> WatchHistoryContentState.Loading
                uiState.history.isEmpty() -> WatchHistoryContentState.Empty
                else -> WatchHistoryContentState.Content
            }

            AnimatedContent(
                targetState = contentState,
                transitionSpec = { materialSharedAxisYIn() },
                modifier = Modifier.fillMaxSize(),
                label = "WatchHistoryContent"
            ) { state ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (state) {
                        WatchHistoryContentState.Loading -> {
                            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        WatchHistoryContentState.Empty -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp), // Larger illustrative icon
                                    tint = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "No history yet",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Titles you watch will appear here",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        WatchHistoryContentState.Content -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Recent Header
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = "Recently Watched",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                items(uiState.history, key = { it.id }) { anime ->
                                    AnimeCard(
                                        anime = anime,
                                        onClick = { onAnimeClick(anime.id, anime.mediaType ?: "tv") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class WatchHistoryContentState {
    Loading, Empty, Content
}
