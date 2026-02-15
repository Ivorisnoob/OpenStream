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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.unit.sp
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

    Scaffold(
        floatingActionButton = {
            if (uiState is DetailsUiState.Success) {
                val state = uiState as DetailsUiState.Success
                val details = state.details
                val seasonDetails = state.selectedSeasonDetails
                
                ExtendedFloatingActionButton(
                    onClick = {
                        val seasonNum = seasonDetails?.seasonNumber 
                            ?: details.seasons?.firstOrNull()?.seasonNumber ?: 1
                        val episodeNum = 1
                        onPlayClick(seasonNum, episodeNum)
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = { Text("Play Now") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    expanded = true
                )
            }
        }
    ) { innerPadding ->
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
                    ) {
                        // Header Item
                        item {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .height(500.dp)) { // Taller, more immersive header
                                AsyncImage(
                                    model = "https://image.tmdb.org/t/p/w1280${details.backdropPath ?: details.posterPath}",
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Gradient Scrim
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
                                                    MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
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
                                        .padding(24.dp) // More padding
                                ) {
                                    // Expressive Chips (Vibrant)
                                    if (!details.genres.isNullOrEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        ) {
                                            details.genres.take(3).forEach { genre ->
                                                SuggestionChip(
                                                    onClick = { /* No-op */ },
                                                    label = { 
                                                        Text(
                                                            genre.name, 
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                        ) 
                                                    },
                                                    shape = ExpressiveShapes.small,
                                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                    ),
                                                    border = null
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = details.name,
                                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold), // Bolder
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (!details.tagline.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = details.tagline,
                                            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "★ ${String.format("%.1f", details.voteAverage)}  •  ${details.date.take(4)}  •  ${details.status ?: "Unknown"}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Overview & Studios
                        item {
                            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                                Text(
                                    text = details.overview,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Studios as small expressive tags
                                if (!details.productionCompanies.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        details.productionCompanies.forEach { company ->
                                            Text(
                                                text = company.name.uppercase(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
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

                        // Season Selector (Scrollable Chips)
                        if (!details.seasons.isNullOrEmpty()) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
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
                                            shape = ExpressiveShapes.small,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Episodes List
                        if (isLoadingEpisodes) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
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
        headlineContent = { 
            Text(
                episode.name, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            ) 
        },
        supportingContent = { 
            Text(
                "Episode ${episode.episodeNumber} • ${String.format("%.1f", episode.voteAverage)}", 
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        leadingContent = {
            Card(
                shape = ExpressiveShapes.small,
                modifier = Modifier.size(width = 100.dp, height = 56.dp)
            ) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w300${episode.stillPath}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent // Integrate with background
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp) // Indent items slightly
    )
}
