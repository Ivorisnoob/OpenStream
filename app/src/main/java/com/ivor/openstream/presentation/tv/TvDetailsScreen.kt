package com.ivor.openstream.presentation.tv

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.R
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.presentation.details.DetailsUiState
import com.ivor.openstream.presentation.details.DetailsViewModel
import java.util.Locale

private const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"
private const val STILL_BASE = "https://image.tmdb.org/t/p/w300"

@Composable
fun TvDetailsScreen(
    mediaType: String,
    animeId: Int,
    onBackClick: () -> Unit,
    onPlay: (season: Int, episode: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isWatchLater.collectAsState()
    val resumeTarget by viewModel.resumeTarget.collectAsState()
    val episodeProgress by viewModel.episodeProgress.collectAsState()
    val animated = rememberTvAnimated()
    val dimens = LocalTvDimens.current

    when (val state = uiState) {
        DetailsUiState.Loading -> TvLoading()

        is DetailsUiState.Error -> TvErrorState(onRetry = viewModel::loadDetails)

        is DetailsUiState.Success -> {
            val details = state.details
            val playRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(details.id) { runCatching { playRequester.requestFocus() } }

            val startSeason = resumeTarget?.season ?: details.firstPlayableSeason()
            val startEpisode = resumeTarget?.episode ?: 1
            val seasons = remember(details) { details.seasons.orEmpty().filter { it.seasonNumber > 0 } }
            val episodes = state.selectedSeasonDetails?.episodes.orEmpty()
            val selectedSeason = state.selectedSeasonDetails?.seasonNumber ?: startSeason

            Column(modifier = modifier.fillMaxSize()) {
                TvTopBar(title = details.name, onBack = onBackClick)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = dimens.paddingLarge),
                    verticalArrangement = Arrangement.spacedBy(dimens.rowGap)
                ) {
                    item(key = "hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimens.backdropHeroHeight)
                                .padding(horizontal = dimens.gutter)
                                .clip(MaterialTheme.shapes.extraLarge)
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
                                            0.45f to Color.Transparent,
                                            1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f)
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(dimens.paddingLarge),
                                verticalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
                            ) {
                                Text(
                                    text = details.name,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val meta = listOfNotNull(
                                    details.date.take(4).takeIf { it.length == 4 },
                                    details.voteAverage.takeIf { it > 0 }?.let { String.format(Locale.US, "★ %.1f", it) },
                                    details.numberOfSeasons?.takeIf { mediaType != "movie" }?.let {
                                        if (it == 1) stringResource(R.string.season_count_one) else stringResource(R.string.season_count_other, it)
                                    }
                                ).joinToString("  ·  ")
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                                if (details.overview.isNotBlank()) {
                                    Text(
                                        text = details.overview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    item(key = "actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimens.gutter),
                            horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
                        ) {
                            TvButton(onClick = { onPlay(startSeason, startEpisode) }, focusRequester = playRequester) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(dimens.paddingSmall))
                                Text(
                                    text = stringResource(if (resumeTarget != null) R.string.cd_resume else R.string.details_play),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            TvOutlinedButton(onClick = viewModel::toggleWatchLater) {
                                Icon(
                                    if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(dimens.paddingSmall))
                                Text(
                                    text = stringResource(if (isSaved) R.string.cd_saved else R.string.action_save),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    if (mediaType != "movie" && seasons.isNotEmpty()) {
                        item(key = "seasons") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = dimens.gutter),
                                verticalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
                            ) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)) {
                                    items(seasons, key = { "season:${it.seasonNumber}" }) { season ->
                                        TvChoiceChip(
                                            selected = season.seasonNumber == selectedSeason,
                                            onClick = { viewModel.loadSeason(season.seasonNumber) },
                                            label = season.name.ifBlank {
                                                stringResource(R.string.details_season, season.seasonNumber)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (mediaType != "movie" && episodes.isNotEmpty()) {
                        item(key = "episodes-header") {
                            Text(
                                text = stringResource(R.string.details_episodes),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = dimens.gutter)
                            )
                        }
                        items(episodes, key = { "episode:${it.seasonNumber}:${it.episodeNumber}" }) { episode ->
                            val completed = episodeProgress[episode.seasonNumber to episode.episodeNumber]?.completed == true
                            TvListRow(
                                onClick = { onPlay(episode.seasonNumber, episode.episodeNumber) },
                                title = "${episode.episodeNumber}. ${episode.name}",
                                subtitle = if (completed) stringResource(R.string.details_watched) else null,
                                leading = {
                                    Box(
                                        modifier = Modifier
                                            .size(dimens.episodeThumbWidth, dimens.episodeThumbHeight)
                                            .clip(MaterialTheme.shapes.medium)
                                    ) {
                                        episode.stillPath?.let {
                                            AsyncImage(
                                                model = "$STILL_BASE$it",
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                },
                                trailing = {
                                    if (completed) {
                                        Text(
                                            text = stringResource(R.string.details_watched),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
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
