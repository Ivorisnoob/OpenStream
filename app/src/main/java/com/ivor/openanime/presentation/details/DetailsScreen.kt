package com.ivor.openanime.presentation.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openanime.data.remote.model.EpisodeDto
import com.ivor.openanime.presentation.components.ExpressiveBackButton
import com.ivor.openanime.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    mediaType: String,
    onBackClick: () -> Unit,
    onPlayClick: (Int, Int) -> Unit, // season, episode
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header Item
                        item {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp)) {
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
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                                                    MaterialTheme.colorScheme.background
                                                ),
                                                startY = 0f,
                                                endY = Float.POSITIVE_INFINITY
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
                                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (!details.tagline.isNullOrEmpty()) {
                                        Text(
                                            text = "\"${details.tagline}\"",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Rating: ${String.format("%.1f", details.voteAverage)} • ${details.date.take(4)} • ${details.status ?: "Unknown"}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Genres
                                    if (!details.genres.isNullOrEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            details.genres.take(4).forEach { genre ->
                                                AssistChip(
                                                    onClick = { /* No-op */ },
                                                    label = { Text(genre.name) },
                                                    shape = ExpressiveShapes.small
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Actions & Overview
                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Button(
                                    onClick = {
                                        val seasonNum = seasonDetails?.seasonNumber 
                                            ?: details.seasons?.firstOrNull()?.seasonNumber ?: 1
                                        val episodeNum = 1
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

                                // Additional Metadata (Studios, etc.)
                                if (!details.productionCompanies.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Studios", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        details.productionCompanies.forEach { company ->
                                            Text(
                                                text = company.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                                        ExpressiveShapes.extraSmall
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

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
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(
                                        items = details.seasons,
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
