package com.ivor.openstream.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.R
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.presentation.details.DetailsUiState
import com.ivor.openstream.presentation.details.DetailsViewModel
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.launch

private const val STILL_BASE = "https://image.tmdb.org/t/p/w300"
private const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

/**
 * Leanback details: backdrop hero, oversized Play, season chips and an
 * episode grid — every step reachable with up/down/left/right + OK.
 */
@Composable
fun TvDetailsScreen(
    mediaType: String,
    animeId: Int,
    onBackClick: () -> Unit,
    onPlay: (season: Int, episode: Int) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isWatchLater.collectAsState()
    val resumeTarget by viewModel.resumeTarget.collectAsState()
    val episodeProgress by viewModel.episodeProgress.collectAsState()
    val animated = rememberTvAnimated()

    BackHandler(onBack = onBackClick)

    when (val state = uiState) {
        DetailsUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is DetailsUiState.Error -> TvErrorState(onRetry = viewModel::loadDetails)

        is DetailsUiState.Success -> {
            val details = state.details
            val playRequester = remember { FocusRequester() }
            LaunchedEffect(details.id) { playRequester.requestFocus() }
            val startSeason = resumeTarget?.season
                ?: details.firstPlayableSeason()
            val startEpisode = resumeTarget?.episode ?: 1
            val seasons = remember(details) {
                details.seasons.orEmpty().filter { it.seasonNumber > 0 }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                item(key = "hero") {
                    TvDetailsHero(
                        details = details,
                        isMovie = mediaType == "movie",
                        isSaved = isSaved,
                        resumeLabel = resumeTarget != null,
                        animated = animated,
                        playRequester = playRequester,
                        onPlay = { onPlay(startSeason, startEpisode) },
                        onToggleSaved = viewModel::toggleWatchLater
                    )
                }
                if (mediaType != "movie") {
                    if (seasons.isNotEmpty()) {
                        item(key = "seasons") {
                            TvSeasonRow(
                                seasons = seasons.map { it.seasonNumber to (it.name.ifBlank { "Season ${it.seasonNumber}" }) },
                                selected = state.selectedSeasonDetails?.seasonNumber
                                    ?: startSeason,
                                animated = animated,
                                onSelect = viewModel::loadSeason
                            )
                        }
                    }
                    val episodes = state.selectedSeasonDetails?.episodes.orEmpty()
                    if (episodes.isNotEmpty()) {
                        item(key = "episodes") {
                            Text(
                                text = stringResource(R.string.details_episodes),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 56.dp)
                                    .padding(bottom = 12.dp, top = 8.dp)
                                    .semantics { heading() }
                            )
                        }
                        item(key = "episode-grid") {
                            TvEpisodeGrid(
                                episodes = episodes,
                                animated = animated,
                                watched = episodeProgress,
                                onPlayEpisode = { onPlay(it.seasonNumber, it.episodeNumber) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun AnimeDetailsDto.firstPlayableSeason(): Int =
    seasons?.filter { it.seasonNumber > 0 && it.episodeCount > 0 }?.minByOrNull { it.seasonNumber }?.seasonNumber
        ?: seasons?.firstOrNull()?.seasonNumber
        ?: 1

@Composable
private fun TvDetailsHero(
    details: AnimeDetailsDto,
    isMovie: Boolean,
    isSaved: Boolean,
    resumeLabel: Boolean,
    animated: Boolean,
    playRequester: FocusRequester,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp)
    ) {
        AsyncImage(
            model = details.backdropPath?.let { "$BACKDROP_BASE$it" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                        0.55f to Color.Transparent
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.6f)
                .padding(horizontal = 56.dp, vertical = 24.dp)
        ) {
            Text(
                text = details.name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                details.date.take(4).takeIf { it.length == 4 },
                details.voteAverage.takeIf { it > 0 }?.let {
                    String.format(java.util.Locale.US, "★ %.1f", it)
                },
                details.numberOfSeasons?.takeIf { !isMovie }?.let {
                    if (it == 1) stringResource(R.string.season_count_one)
                    else stringResource(R.string.season_count_other, it)
                }
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (details.overview.isNotBlank()) {
                Text(
                    text = details.overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(modifier = Modifier.padding(top = 20.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .height(64.dp)
                        .focusRequester(playRequester)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.width(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (resumeLabel) R.string.cd_resume else R.string.details_play),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Spacer(Modifier.width(16.dp))
                OutlinedButton(
                    onClick = onToggleSaved,
                    modifier = Modifier.height(64.dp)
                ) {
                    Icon(
                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (isSaved) R.string.cd_saved else R.string.action_save),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSeasonRow(
    seasons: List<Pair<Int, String>>,
    selected: Int,
    animated: Boolean,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        items(seasons, key = { "season:${it.first}" }) { (number, name) ->
            val focused = rememberTvFocused()
            FilterChip(
                selected = number == selected,
                onClick = { onSelect(number) },
                label = { Text(name, style = MaterialTheme.typography.titleMedium) },
                modifier = Modifier
                    .scale(tvFocusScale(focused.value, animated))
                    .tvFocusable()
                    .onFocusChanged { focused.value = it.isFocused }
            )
        }
    }
}

@Composable
private fun TvEpisodeGrid(
    episodes: List<EpisodeDto>,
    animated: Boolean,
    watched: Map<Pair<Int, Int>, com.ivor.openstream.domain.model.WatchProgress>,
    onPlayEpisode: (EpisodeDto) -> Unit
) {
    // Fixed columns: predictable D-pad traversal beats adaptive reflowing rows.
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height((episodes.size.coerceAtLeast(1) * 190).coerceAtMost(900).dp)
    ) {
        items(episodes, key = { "ep:${it.seasonNumber}:${it.episodeNumber}" }) { episode ->
            val focused = rememberTvFocused()
            val bringIntoViewRequester = remember { BringIntoViewRequester() }
            val scope = rememberCoroutineScope()
            TvCard(
                focused = focused.value,
                animated = animated,
                onClick = { onPlayEpisode(episode) },
                onFocused = {
                    focused.value = true
                    scope.launch {
                        runCatching { bringIntoViewRequester.bringIntoView() }
                    }
                },
                onBlurred = { focused.value = false },
                modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                        if (episode.stillPath != null) {
                            AsyncImage(
                                model = "$STILL_BASE${episode.stillPath}",
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (watched[episode.seasonNumber to episode.episodeNumber]?.completed == true) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(ExpressiveShapes.small)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.details_watched),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Text(
                        text = "${episode.episodeNumber}. ${episode.name}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
