package com.ivor.openstream.presentation.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import com.ivor.openstream.R
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.presentation.home.HomeRail
import com.ivor.openstream.presentation.home.HomeUiState
import com.ivor.openstream.presentation.home.HomeViewModel
import kotlinx.coroutines.flow.debounce

/** What the info panel above the rows describes: the featured title, or the card in focus. */
private sealed interface HomeFocus {
    data class Title(val item: AnimeDto) : HomeFocus
    data class Resume(val progress: WatchProgress) : HomeFocus
}

private fun AnimeDto.tvMediaType(): String = mediaType ?: if (isMovie) "movie" else "tv"

/**
 * TV Home, laid out like the platform's own launchers: the backdrop and the info panel at the top
 * follow the card in focus, the featured title's Play and Details buttons sit in that panel, and
 * the rows below scroll so the row in focus always lands in the same place.
 */
@OptIn(ExperimentalFoundationApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun TvHomeScreen(
    onOpenTitle: (mediaType: String, id: Int) -> Unit,
    onPlay: (mediaType: String, id: Int, season: Int, episode: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()

    when (val state = uiState) {
        HomeUiState.Loading -> TvLoading()
        is HomeUiState.Error -> TvErrorState(onRetry = viewModel::refresh)
        is HomeUiState.Success -> {
            val featured = remember(state.hero) { state.hero.firstOrNull { it.backdropPath != null } }
            var focus by remember { mutableStateOf<HomeFocus?>(null) }
            // Moving quickly along a row should not flash every backdrop on the way.
            val shown by produceState<HomeFocus?>(featured?.let { HomeFocus.Title(it) }, featured) {
                snapshotFlow { focus ?: featured?.let { HomeFocus.Title(it) } }
                    .debounce(180)
                    .collect { value = it }
            }
            val featuredProgress = featured?.let { hero -> continueWatching.firstOrNull { it.tmdbId == hero.id } }

            val playRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { playRequester.requestFocus() } }

            BoxWithConstraints(modifier.fillMaxSize()) {
                val panelHeight = maxHeight * 0.5f
                TvBackdrop(
                    url = shown.backdropUrl(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.78f)
                        .align(Alignment.TopEnd)
                )
                TvBackdropScrims()

                Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(panelHeight)
                            .padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd, bottom = 12.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            InfoPanel(shown)
                            if (featured != null) {
                                // Hidden while a card has focus but still focusable, so Up from the
                                // first row lands here and brings them back.
                                val buttonsAlpha by animateFloatAsState(if (focus == null) 1f else 0f, label = "buttons")
                                Spacer(Modifier.height(16.dp))
                                FeaturedButtons(
                                    progress = featuredProgress,
                                    playRequester = playRequester,
                                    modifier = Modifier
                                        .alpha(buttonsAlpha)
                                        .onFocusChanged { if (it.hasFocus) focus = null },
                                    onPlay = {
                                        val progress = featuredProgress
                                        if (progress != null) {
                                            onPlay(progress.mediaType, progress.tmdbId, progress.season, progress.episode)
                                        } else {
                                            onPlay(featured.tvMediaType(), featured.id, 1, 1)
                                        }
                                    },
                                    onDetails = { onOpenTitle(featured.tvMediaType(), featured.id) }
                                )
                            }
                        }
                    }

                    val headerSpace = with(LocalDensity.current) { 40.dp.toPx() }
                    val rowPivot = remember(headerSpace) { tvRowPivot(headerSpace) }
                    val defaultSpec = LocalBringIntoViewSpec.current
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowPivot) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (continueWatching.isNotEmpty()) {
                                item(key = "continue") {
                                    // Rows scroll sideways the usual way; only the column pivots.
                                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                        TvRail(title = stringResource(R.string.home_continue_watching)) {
                                            items(continueWatching, key = { "cw:${it.mediaType}:${it.tmdbId}" }) { progress ->
                                                ContinueCard(
                                                    progress = progress,
                                                    onClick = {
                                                        onPlay(progress.mediaType, progress.tmdbId, progress.season, progress.episode)
                                                    },
                                                    onFocused = { focus = HomeFocus.Resume(progress) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            items(state.rails.filter { it.items.isNotEmpty() }, key = { "rail:${it.key}" }) { rail ->
                                CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                    TitleRail(
                                        rail = rail,
                                        onOpen = { onOpenTitle(it.tvMediaType(), it.id) },
                                        onFocused = { focus = HomeFocus.Title(it) }
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

private fun HomeFocus?.backdropUrl(): String? = when (this) {
    is HomeFocus.Title -> (item.backdropPath ?: item.posterPath)?.let { "$TV_BACKDROP_BASE$it" }
    is HomeFocus.Resume -> (progress.backdropPath ?: progress.stillPath)?.let { "$TV_BACKDROP_BASE$it" }
    null -> null
}

@Composable
private fun InfoPanel(shown: HomeFocus?) {
    AnimatedContent(
        targetState = shown,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = {
            when (it) {
                is HomeFocus.Title -> "t${it.item.id}"
                is HomeFocus.Resume -> "r${it.progress.tmdbId}"
                null -> "none"
            }
        },
        label = "info"
    ) { target ->
        Column(Modifier.width(460.dp)) {
            when (target) {
                is HomeFocus.Title -> {
                    val item = target.item
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    MetaLine(
                        listOfNotNull(
                            item.date.take(4).takeIf { it.isNotBlank() },
                            item.voteAverage?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                            stringResource(if (item.isMovie) R.string.details_movie else R.string.search_filter_series)
                        )
                    )
                    item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                is HomeFocus.Resume -> {
                    val progress = target.progress
                    Text(
                        text = progress.title,
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    MetaLine(
                        listOfNotNull(
                            if (progress.isMovie) null else stringResource(R.string.li_episode_code, progress.season, progress.episode),
                            progress.episodeTitle?.takeIf { it.isNotBlank() },
                            stringResource(R.string.home_continue_watching)
                        )
                    )
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun MetaLine(parts: List<String>) {
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun FeaturedButtons(
    progress: WatchProgress?,
    playRequester: FocusRequester,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onPlay,
            modifier = Modifier.focusRequester(playRequester),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            androidx.tv.material3.Text(
                if (progress != null && !progress.isMovie) {
                    stringResource(R.string.cd_resume) + "  " +
                        stringResource(R.string.li_episode_code, progress.season, progress.episode)
                } else {
                    stringResource(if (progress != null) R.string.cd_resume else R.string.details_play)
                }
            )
        }
        OutlinedButton(
            onClick = onDetails,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            androidx.tv.material3.Text(stringResource(R.string.st_details))
        }
    }
}

@Composable
private fun ContinueCard(progress: WatchProgress, onClick: () -> Unit, onFocused: () -> Unit) {
    TvLandscapeCard(
        title = progress.title,
        subtitle = if (progress.isMovie) null else {
            stringResource(R.string.li_episode_code, progress.season, progress.episode) +
                (progress.episodeTitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        },
        imageUrl = (progress.stillPath ?: progress.backdropPath)?.let { "$TV_STILL_BASE$it" },
        progress = progress.fraction.takeIf { it > 0f },
        onClick = onClick,
        onFocused = onFocused
    )
}

@Composable
private fun TitleRail(rail: HomeRail, onOpen: (AnimeDto) -> Unit, onFocused: (AnimeDto) -> Unit) {
    TvRail(title = rail.title) {
        items(rail.items, key = { "${rail.key}:${it.id}" }) { item ->
            TvPosterCard(
                title = item.name,
                posterPath = item.posterPath,
                onClick = { onOpen(item) },
                onFocused = { onFocused(item) }
            )
        }
    }
}
