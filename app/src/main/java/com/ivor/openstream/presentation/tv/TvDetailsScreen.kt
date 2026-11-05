package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import com.ivor.openstream.R
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.presentation.details.DetailsUiState
import com.ivor.openstream.presentation.details.DetailsViewModel

/**
 * TV Details: the backdrop fills the screen, the title, facts, overview and actions sit on its
 * left, and below them the season chips, the season's episodes as thumbnails, and similar titles.
 * Focus starts on Play (Resume when there is progress).
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun TvDetailsScreen(
    mediaType: String,
    animeId: Int,
    onPlay: (season: Int, episode: Int) -> Unit,
    onOpenTitle: (mediaType: String, id: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isWatchLater.collectAsState()
    val resumeTarget by viewModel.resumeTarget.collectAsState()
    val episodeProgress by viewModel.episodeProgress.collectAsState()
    val latestEpisode by viewModel.latestEpisode.collectAsState()
    val episodeJump by viewModel.episodeJump.collectAsState()

    when (val state = uiState) {
        DetailsUiState.Loading -> TvLoading()
        is DetailsUiState.Error -> TvErrorState(onRetry = viewModel::loadDetails)
        is DetailsUiState.Success -> {
            val details = state.details
            val isMovie = mediaType == "movie"
            val playRequester = remember { FocusRequester() }
            LaunchedEffect(details.id) { runCatching { playRequester.requestFocus() } }

            val seasons = remember(details) { details.seasons.orEmpty().filter { it.seasonNumber > 0 } }
            val episodes = state.selectedSeasonDetails?.episodes.orEmpty()
            val selectedSeason = state.selectedSeasonDetails?.seasonNumber ?: resumeTarget?.season ?: details.firstPlayableSeason()
            val recommendations = details.recommendations?.results.orEmpty().filter { it.posterPath != null }

            BoxWithConstraints(modifier.fillMaxSize()) {
                TvBackdrop(
                    url = (details.backdropPath ?: details.posterPath)?.let { "$TV_BACKDROP_BASE$it" },
                    modifier = Modifier.fillMaxSize()
                )
                TvBackdropScrims()

                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val headerSpace = with(LocalDensity.current) { 48.dp.toPx() }
                val rowPivot = remember(headerSpace) { tvRowPivot(headerSpace) }
                CompositionLocalProvider(LocalBringIntoViewSpec provides rowPivot) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "header") {
                        // The first screen is the title on its own, centred, with the next row
                        // peeking below; anything in it taking focus brings the whole of it back.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Leaves the season tabs and the top of the episode row in view.
                                .height(maxHeight - 176.dp)
                                .padding(start = TvLayout.PageStart, end = TvLayout.ContentEnd)
                                .onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Header(
                                details = details,
                                isMovie = isMovie,
                                isSaved = isSaved,
                                resumeTarget = resumeTarget,
                                playRequester = playRequester,
                                onPlay = {
                                    val resume = resumeTarget
                                    if (resume != null) onPlay(resume.season, resume.episode)
                                    else onPlay(details.firstPlayableSeason(), 1)
                                },
                                onToggleSaved = viewModel::toggleWatchLater
                            )
                        }
                    }

                    val latest = latestEpisode
                    val showLatest = latest != null && (seasons.size > 1 || latest.episodeNumber > 12)
                    if (!isMovie && (seasons.size > 1 || showLatest)) {
                        item(key = "seasons") {
                            val seasonRowState = rememberLazyListState()
                            // Start on the season shown, not on Season 1 of twenty.
                            LaunchedEffect(selectedSeason) {
                                val index = seasons.indexOfFirst { it.seasonNumber == selectedSeason }
                                if (index >= 0) seasonRowState.scrollToItem(index + if (showLatest) 1 else 0)
                            }
                            // Tabs pick their season on focus, after a pause, so moving across
                            // twenty seasons loads only the one the user stops on.
                            var pendingSeason by remember { mutableStateOf<Int?>(null) }
                            LaunchedEffect(pendingSeason) {
                                val target = pendingSeason ?: return@LaunchedEffect
                                if (target == selectedSeason) return@LaunchedEffect
                                delay(450)
                                viewModel.loadSeason(target)
                            }
                            LazyRow(
                                state = seasonRowState,
                                modifier = Modifier.focusRestorer(),
                                contentPadding = PaddingValues(
                                    start = TvLayout.PageStart - 12.dp,
                                    end = TvLayout.ContentEnd,
                                    top = 4.dp,
                                    bottom = 4.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (showLatest && latest != null) {
                                    item(key = "latest") {
                                        SeasonTab(
                                            label = stringResource(R.string.details_latest) + "  " +
                                                stringResource(R.string.li_episode_code, latest.seasonNumber, latest.episodeNumber),
                                            selected = false,
                                            icon = Icons.Default.NewReleases,
                                            onFocused = {},
                                            onClick = viewModel::jumpToLatest
                                        )
                                    }
                                }
                                items(seasons, key = { "season:${it.seasonNumber}" }) { season ->
                                    SeasonTab(
                                        label = stringResource(R.string.details_season, season.seasonNumber),
                                        selected = season.seasonNumber == selectedSeason,
                                        onFocused = { pendingSeason = season.seasonNumber },
                                        onClick = { viewModel.loadSeason(season.seasonNumber) }
                                    )
                                }
                            }
                        }
                    }

                    if (!isMovie && episodes.isNotEmpty()) {
                        item(key = "episodes:$selectedSeason") {
                            val episodeRowState = rememberLazyListState()
                            // Open on the episode asked for (Latest), else where the user is up to.
                            LaunchedEffect(episodes, episodeJump) {
                                val jump = episodeJump
                                val target = when {
                                    jump != null && jump.first == selectedSeason -> jump.second
                                    resumeTarget?.season == selectedSeason -> resumeTarget?.episode
                                    else -> null
                                }
                                val index = episodes.indexOfFirst { it.episodeNumber == target }
                                if (index > 0) episodeRowState.scrollToItem(index)
                                if (jump != null && jump.first == selectedSeason) viewModel.consumeEpisodeJump()
                            }
                            TvRail(
                                title = stringResource(R.string.details_episodes),
                                start = TvLayout.PageStart,
                                state = episodeRowState
                            ) {
                                items(episodes, key = { "ep:${it.seasonNumber}:${it.episodeNumber}" }) { episode ->
                                    EpisodeCard(
                                        episode = episode,
                                        progress = episodeProgress[episode.seasonNumber to episode.episodeNumber],
                                        onClick = { onPlay(episode.seasonNumber, episode.episodeNumber) }
                                    )
                                }
                            }
                        }
                    }

                    if (recommendations.isNotEmpty()) {
                        item(key = "more") {
                            TvRail(title = stringResource(R.string.details_more_like_this), start = TvLayout.PageStart) {
                                items(recommendations, key = { "rec:${it.id}" }) { item ->
                                    TvPosterCard(
                                        title = item.name,
                                        posterPath = item.posterPath,
                                        onClick = { onOpenTitle(item.mediaType ?: mediaType, item.id) }
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
}

@Composable
private fun Header(
    details: AnimeDetailsDto,
    isMovie: Boolean,
    isSaved: Boolean,
    resumeTarget: WatchProgress?,
    playRequester: FocusRequester,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit
) {
    Column(Modifier.width(500.dp)) {
        Text(
            text = details.name,
            style = MaterialTheme.typography.displayMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        val runtime = details.runtime ?: details.episodeRunTime?.firstOrNull()
        Text(
            text = listOfNotNull(
                details.date.take(4).takeIf { it.isNotBlank() },
                details.voteAverage.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                if (isMovie) runtime?.let { "${it / 60}h ${it % 60}m".removePrefix("0h ") }
                else details.numberOfSeasons?.let {
                    if (it == 1) stringResource(R.string.season_count_one) else stringResource(R.string.season_count_other, it)
                },
                details.genres.orEmpty().take(3).joinToString(", ") { it.name }.takeIf { it.isNotBlank() }
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (details.overview.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = details.overview,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPlay,
                modifier = Modifier.focusRequester(playRequester),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                androidx.tv.material3.Text(
                    when {
                        resumeTarget == null -> stringResource(R.string.details_play)
                        isMovie -> stringResource(R.string.cd_resume)
                        else -> stringResource(R.string.cd_resume) + "  " +
                            stringResource(R.string.li_episode_code, resumeTarget.season, resumeTarget.episode)
                    }
                )
            }
            OutlinedButton(
                onClick = onToggleSaved,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(
                    if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                androidx.tv.material3.Text(stringResource(if (isSaved) R.string.cd_saved else R.string.action_save))
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: EpisodeDto, progress: WatchProgress?, onClick: () -> Unit) {
    Box {
        TvLandscapeCard(
            title = stringResource(R.string.li_episode_short, episode.episodeNumber) + " · " + episode.name,
            subtitle = episode.runtime?.let { stringResource(R.string.misc_runtime_min, it) },
            imageUrl = episode.stillPath?.let { "$TV_STILL_BASE$it" },
            progress = progress?.takeIf { !it.completed }?.fraction?.takeIf { it > 0f },
            onClick = onClick
        )
        if (progress?.completed == true) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.cd_watched),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}

private fun AnimeDetailsDto.firstPlayableSeason(): Int =
    seasons?.filter { it.seasonNumber > 0 && it.episodeCount > 0 }?.minByOrNull { it.seasonNumber }?.seasonNumber
        ?: seasons?.firstOrNull()?.seasonNumber
        ?: 1

/** A season as a text tab: underlined when shown, a solid pill while focused. */
@Composable
private fun SeasonTab(
    label: String,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        // The underline is laid over the bottom edge so it never pushes the label off centre.
        Box {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                androidx.tv.material3.Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .width(24.dp)
                    .height(3.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
