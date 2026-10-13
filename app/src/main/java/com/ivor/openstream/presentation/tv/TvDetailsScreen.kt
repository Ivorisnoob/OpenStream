package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch
import java.util.Locale

private const val STILL_BASE = "https://image.tmdb.org/t/p/w300"
private const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

/**
 * Leanback details: a compact backdrop hero, a Play button the remote lands on
 * immediately, season chips and a wide episode grid.
 *
 * Episodes are laid out with [FlowRow] in rows of a fixed count rather than a
 * `LazyVerticalGrid` nested inside a `LazyColumn`. The nested grid needed a
 * hand-computed fixed height (`episodeCount * 190.dp`, clamped) which was the
 * direct cause of rows overlapping on any season long enough to hit the clamp.
 */
@Composable
fun TvDetailsScreen(
    mediaType: String,
    animeId: Int,
    onBack: () -> Unit,
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
            // The remote must land somewhere on entry, so Play is the first stop.
            LaunchedEffect(details.id) { runCatching { playRequester.requestFocus() } }

            val startSeason = resumeTarget?.season ?: details.firstPlayableSeason()
            val startEpisode = resumeTarget?.episode ?: 1
            val seasons = remember(details) {
                details.seasons.orEmpty().filter { it.seasonNumber > 0 }
            }
            val episodes = state.selectedSeasonDetails?.episodes.orEmpty()

            Column(modifier = modifier.fillMaxSize()) {
                // Pushed screens own their header, so back is always on screen.
                TvTopBar(title = details.name, onBack = onBack)
                LazyRow(
                    // weight, not fillMaxSize: inside a Column the latter takes the
                    // parent's full height and draws the rail underneath the top bar.
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = dimens.gutter,
                        end = dimens.gutter,
                        top = dimens.gutterTight,
                        bottom = dimens.gutter
                    )
                ) {
                    item(key = "details") {
                        Column(
                            modifier = Modifier.width(dimens.sidePanelWidth),
                            verticalArrangement = Arrangement.spacedBy(dimens.gutterTight)
                        ) {
                            TvDetailsHero(
                                details = details,
                                isMovie = mediaType == "movie",
                                isSaved = isSaved,
                                resumeLabel = resumeTarget != null,
                                playRequester = playRequester,
                                onPlay = { onPlay(startSeason, startEpisode) },
                                onToggleSaved = viewModel::toggleWatchLater
                            )

                            if (mediaType != "movie" && seasons.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.details_seasons),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                TvSeasonRow(
                                    seasons = seasons.map {
                                        it.seasonNumber to it.name.ifBlank { "Season ${it.seasonNumber}" }
                                    },
                                    selected = state.selectedSeasonDetails?.seasonNumber ?: startSeason,
                                    animated = animated,
                                    onSelect = viewModel::loadSeason
                                )
                            }
                        }
                    }

                    if (mediaType != "movie" && episodes.isNotEmpty()) {
                        item(key = "episodes") {
                            Column(
                                modifier = Modifier.width(dimens.cardWidth * 5.2f),
                                verticalArrangement = Arrangement.spacedBy(dimens.gutterTight)
                            ) {
                                Text(
                                    text = stringResource(R.string.details_episodes),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                TvEpisodeRows(
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
    playRequester: androidx.compose.ui.focus.FocusRequester,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit
) {
    val dimens = LocalTvDimens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterTight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
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
                            0.55f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f)
                        )
                    )
            )
        }

        Text(
            text = details.name,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        val meta = listOfNotNull(
            details.date.take(4).takeIf { it.length == 4 },
            details.voteAverage.takeIf { it > 0 }?.let { String.format(Locale.US, "★ %.1f", it) },
            details.numberOfSeasons?.takeIf { !isMovie }?.let {
                if (it == 1) stringResource(R.string.season_count_one)
                else stringResource(R.string.season_count_other, it)
            }
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (details.overview.isNotBlank()) {
            Text(
                text = details.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)) {
            TvButton(onClick = onPlay, focusRequester = playRequester) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (resumeLabel) R.string.cd_resume else R.string.details_play),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            TvOutlinedButton(onClick = onToggleSaved) {
                Icon(
                    if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (isSaved) R.string.cd_saved else R.string.action_save),
                    style = MaterialTheme.typography.titleMedium
                )
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
    val dimens = LocalTvDimens.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
    ) {
        items(seasons, key = { "season:${it.first}" }) { (number, name) ->
            TvChoiceChip(
                selected = number == selected,
                onClick = { onSelect(number) },
                label = name
            )
        }
    }
}

/**
 * The episode list, in rows of a fixed width.
 *
 * Fixed-count rows give predictable D-pad traversal: right walks along an
 * episode, down drops to the row below at the same column, and there is never
 * a reflow when the season changes length.
 */
@Composable
private fun TvEpisodeRows(
    episodes: List<EpisodeDto>,
    animated: Boolean,
    watched: Map<Pair<Int, Int>, WatchProgress>,
    onPlayEpisode: (EpisodeDto) -> Unit
) {
    val dimens = LocalTvDimens.current
    val columns = 4
    Column(verticalArrangement = Arrangement.spacedBy(dimens.listRowGap)) {
        episodes.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.listRowGap)) {
                row.forEach { episode ->
                    TvEpisodeCell(
                        episode = episode,
                        animated = animated,
                        completed = watched[episode.seasonNumber to episode.episodeNumber]?.completed == true,
                        onClick = { onPlayEpisode(episode) }
                    )
                }
                // Keep the last row's cells the same width as a full one, so the
                // grid does not stretch the leftovers across the panel.
                repeat(columns - row.size) {
                    Spacer(Modifier.width(dimens.cardWidth))
                }
            }
        }
    }
}

@Composable
private fun TvEpisodeCell(
    episode: EpisodeDto,
    animated: Boolean,
    completed: Boolean,
    onClick: () -> Unit
) {
    val dimens = LocalTvDimens.current
    val focused = rememberTvFocused()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    TvCard(
        focused = focused.value,
        animated = animated,
        onClick = onClick,
        onFocused = {
            focused.value = true
            scope.launch { runCatching { bringIntoViewRequester.bringIntoView() } }
        },
        onBlurred = { focused.value = false }
    ) {
        Column(
            modifier = Modifier
                .width(dimens.cardWidth)
                .bringIntoViewRequester(bringIntoViewRequester)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.cardImageHeight * 0.8f)
            ) {
                episode.stillPath?.let {
                    AsyncImage(
                        model = "$STILL_BASE$it",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (completed) {
                    Text(
                        text = stringResource(R.string.details_watched),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(dimens.paddingSmall / 2)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "${episode.episodeNumber}. ${episode.name}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = dimens.paddingSmall / 2)
            )
        }
    }
}