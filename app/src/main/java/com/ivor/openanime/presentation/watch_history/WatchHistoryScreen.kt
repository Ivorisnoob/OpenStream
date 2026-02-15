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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openanime.presentation.components.AnimeCard
import com.ivor.openanime.presentation.components.ExpressiveBackButton

// Expressive Motion Tokens (Spring approximations from M3 specs)
// Source: https://m3.material.io/styles/motion/overview/specs

// Spatial (Large movements)
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        "Watch History",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                subtitle = {
                    val count = uiState.history.size
                    if (count > 0) {
                        Text(
                            "$count titles",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    ExpressiveBackButton(onClick = onBackClick)
                },
                actions = {
                    if (uiState.history.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear History"
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        
        val contentState = when {
            uiState.isLoading -> WatchHistoryContentState.Loading
            uiState.history.isEmpty() -> WatchHistoryContentState.Empty
            else -> WatchHistoryContentState.Content
        }

        AnimatedContent(
            targetState = contentState,
            transitionSpec = { materialSharedAxisYIn() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No history yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Titles you watch will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    WatchHistoryContentState.Content -> {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalItemSpacing = 16.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
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

private enum class WatchHistoryContentState {
    Loading, Empty, Content
}
