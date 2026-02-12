package com.ivor.openanime.presentation.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openanime.ui.theme.ExpressiveShapes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.ivor.openanime.data.remote.model.AnimeDetailsDto
import com.ivor.openanime.data.remote.model.SeasonDto
import com.ivor.openanime.data.remote.model.EpisodeDto

import com.ivor.openanime.presentation.components.ExpressiveBackButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailsScreen(
    mediaType: String,
    onBackClick: () -> Unit,
    onPlayClick: (Int, Int) -> Unit, // season, episode
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        // Remove topBar to let content go under status bar
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Success/Content State... (Details logic follows)
            // Overlay Back Button
            ExpressiveBackButton(
                onClick = onBackClick,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            )
            when (val state = uiState) {
                is DetailsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                is DetailsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is DetailsUiState.Success -> {
                    val details = state.details
                    val seasonDetails = state.selectedSeasonDetails
                    val isLoadingEpisodes = state.isLoadingEpisodes
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Remove contentPadding here or handle it carefully with full-screen header
                        // We want the image to go under the status bar, so no strict top padding here ideally
                        // But innerPadding forces it. Let's ignore innerPadding top for image effect if edge-to-edge
                    ) {
                        // Header Item
                        item {
                            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                                AsyncImage(
                                    model = "https://image.tmdb.org/t/p/w1280${details.backdropPath ?: details.posterPath}",
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Scrim
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                                startY = 0f,
                                                endY = 1000f
                                            )
                                        )
                                )
                                
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = details.name,
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Rating: ${String.format("%.1f", details.voteAverage)} • ${details.date.take(4)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Actions & Overview
                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Button(
                                    onClick = {
                                        // Play logic: First episode of selected season, or S1E1 fallback
                                        val seasonNum = seasonDetails?.seasonNumber 
                                            ?: details.seasons?.firstOrNull()?.seasonNumber ?: 1
                                        val episodeNum = 1 // Default to first episode
                                        onPlayClick(seasonNum, episodeNum)
                                    },
                                    shape = ExpressiveShapes.large,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Play Now")
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Overview", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = details.overview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        // Season Selector
                        if (!details.seasons.isNullOrEmpty()) {
                            item {
                                Text(
                                    text = "Seasons",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                                ) {
                                    items(
                                        items = details.seasons!!,
                                        key = { it.seasonNumber }
                                    ) { season ->
                                        val isSelected = seasonDetails?.seasonNumber == season.seasonNumber
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.loadSeason(season.seasonNumber) },
                                            label = { Text(season.name) },
                                            shape = ExpressiveShapes.small
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Episodes List
                        if (isLoadingEpisodes) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    LoadingIndicator()
                                }
                            }
                        } else {
                            seasonDetails?.episodes?.let { episodes ->
                                items(
                                    items = episodes,
                                    key = { it.id }
                                ) { episode ->
                                    EpisodeItem(
                                        episode = episode,
                                        onClick = { onPlayClick(episode.seasonNumber, episode.episodeNumber) }
                                    )
                                }
                            }
                        }
                        
                        // Bottom Padding for Navigation Bar
                        item {
                            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeItem(
    episode: EpisodeDto,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(episode.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("Episode ${episode.episodeNumber} • ${episode.voteAverage}", maxLines = 1) },
        leadingContent = {
            Card(
                shape = ExpressiveShapes.small,
                modifier = Modifier.size(width = 120.dp, height = 68.dp)
            ) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w300${episode.stillPath}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
