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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.presentation.home.HomeRail
import com.ivor.openstream.presentation.home.HomeUiState
import com.ivor.openstream.presentation.home.HomeViewModel
import com.ivor.openstream.presentation.home.RailStyle
import kotlinx.coroutines.launch
import java.util.Locale

private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
private const val HERO_IMAGE_BASE = "https://image.tmdb.org/t/p/w1280"

/**
 * Leanback home: a compact cinematic hero above horizontal rails.
 *
 * Sized entirely from [LocalTvDimens] rather than dp literals, because the
 * phone scale is what made the old build feel enormous at 10 feet. The hero is
 * deliberately short (a fraction of the window, not 420 dp) so at least one
 * full rail is visible on screen at all times; the viewer should never have to
 * scroll to find out what the app has.
 */
@Composable
fun TvHomeScreen(
    onOpenTitle: (mediaType: String, id: Int) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onProviders: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val animated = rememberTvAnimated()
    val dimens = LocalTvDimens.current

    fun AnimeDto.tvMediaType(): String = mediaType ?: if (isMovie) "movie" else "tv"

    val heroItem: AnimeDto? = (uiState as? HomeUiState.Success)
        ?.hero?.firstOrNull { it.backdropPath != null }
    val heroProgress: WatchProgress? = heroItem?.let { hero ->
        continueWatching.firstOrNull { it.tmdbId == hero.id && !it.isMovie }
    }

    // Land the remote on something specific the moment the screen appears.
    // Without this the D-pad has no focused node and every key press is a no-op.
    val playRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val railRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(heroItem?.id, uiState) {
        runCatching { playRequester.requestFocus() }
            .recoverCatching { railRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = dimens.paddingMedium)
    ) {
        when (val state = uiState) {
            HomeUiState.Loading -> TvLoading()

            is HomeUiState.Error -> TvErrorState(onRetry = viewModel::refresh)

            is HomeUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = dimens.paddingLarge),
                    verticalArrangement = Arrangement.spacedBy(dimens.rowGap)
                ) {
                    if (heroItem != null) {
                        item(key = "hero") {
                            TvHero(
                                item = heroItem,
                                progress = heroProgress,
                                animated = animated,
                                playRequester = playRequester,
                                onResume = { heroProgress?.let(onResume) },
                                onOpenDetails = { onOpenTitle(heroItem.tvMediaType(), heroItem.id) }
                            )
                        }
                    }
                    if (continueWatching.isNotEmpty()) {
                        item(key = "continue") {
                            TvProgressRail(
                                title = stringResource(R.string.home_continue_watching),
                                items = continueWatching,
                                animated = animated,
                                firstItemRequester = railRequester,
                                onOpen = { onResume(it) }
                            )
                        }
                    }
                    items(state.rails, key = { "rail:${it.key}" }) { rail ->
                        if (rail.items.isNotEmpty()) {
                            TvAnimeRail(
                                title = rail.title,
                                style = rail.style,
                                items = rail.items,
                                animated = animated,
                                onOpen = { onOpenTitle(it.tvMediaType(), it.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The hero: a backdrop with the title and actions over a scrim.
 *
 * Height comes from [TvDimens.heroHeight], which is a fraction of the window,
 * so this never eats the screen the way a fixed 420 dp block did on a 536 dp
 * window.
 */
@Composable
private fun TvHero(
    item: AnimeDto,
    progress: WatchProgress?,
    animated: Boolean,
    playRequester: androidx.compose.ui.focus.FocusRequester,
    onResume: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val dimens = LocalTvDimens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.heroHeight)
            .padding(horizontal = dimens.gutter)
            .clip(MaterialTheme.shapes.extraLarge)
    ) {
        AsyncImage(
            model = item.backdropPath?.let { "$HERO_IMAGE_BASE$it" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f),
                        0.5f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.52f)
                .padding(dimens.paddingLarge),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.voteAverage?.takeIf { it > 0 }?.let { rating ->
                Row(
                    modifier = Modifier.padding(top = dimens.paddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.height(dimens.gutterTight / 2)
                    )
                    Text(
                        text = String.format(Locale.US, " %.1f", rating),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
            Row(modifier = Modifier.padding(top = dimens.paddingMedium)) {
                if (progress != null) {
                    TvButton(onClick = onResume, focusRequester = playRequester) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.cd_resume), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(dimens.paddingSmall))
                }
                TvOutlinedButton(onClick = onOpenDetails) {
                    Text(stringResource(R.string.st_details), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Continue watching: landscape cards with a progress bar. */
@Composable
private fun TvProgressRail(
    title: String,
    items: List<WatchProgress>,
    animated: Boolean,
    firstItemRequester: androidx.compose.ui.focus.FocusRequester,
    onOpen: (WatchProgress) -> Unit
) {
    TvRailHeader(title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = LocalTvDimens.current.gutter),
        horizontalArrangement = Arrangement.spacedBy(LocalTvDimens.current.railGap)
    ) {
        itemsIndexed(
            items,
            key = { _, item -> "cw:${item.mediaType}:${item.tmdbId}:${item.season}:${item.episode}" }
        ) { index, item ->
            TvLandscapeCard(
                imageUrl = item.backdropPath?.let { "$IMAGE_BASE$it" },
                title = item.title,
                subtitle = if (item.isMovie) null else "S${item.season} · E${item.episode}",
                progress = item.fraction.takeIf { it > 0f },
                animated = animated,
                focusRequester = if (index == 0) firstItemRequester else null,
                onClick = { onOpen(item) }
            )
        }
    }
}

/** One catalogue rail, poster or landscape depending on its style. */
@Composable
internal fun TvAnimeRail(
    title: String,
    style: RailStyle,
    items: List<AnimeDto>,
    animated: Boolean,
    onOpen: (AnimeDto) -> Unit
) {
    val dimens = LocalTvDimens.current
    TvRailHeader(title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = dimens.gutter),
        horizontalArrangement = Arrangement.spacedBy(dimens.railGap)
    ) {
        items(items, key = { "rail:${it.mediaType}:${it.id}" }) { item ->
            when (style) {
                RailStyle.LANDSCAPE -> TvLandscapeCard(
                    imageUrl = item.backdropPath?.let { "$IMAGE_BASE$it" }
                        ?: item.posterPath?.let { "$IMAGE_BASE$it" },
                    title = item.name,
                    subtitle = item.date.take(4).takeIf { it.length == 4 },
                    progress = null,
                    animated = animated,
                    onClick = { onOpen(item) }
                )

                else -> TvPosterCard(
                    imageUrl = item.posterPath?.let { "$IMAGE_BASE$it" }
                        ?: item.backdropPath?.let { "$IMAGE_BASE$it" },
                    title = item.name,
                    animated = animated,
                    onClick = { onOpen(item) }
                )
            }
        }
    }
}

@Composable
internal fun TvRailHeader(title: String) {
    val dimens = LocalTvDimens.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = dimens.gutter)
    )
}

/**
 * A landscape card.
 *
 * The whole card (image + label) is one focus target, and it scrolls itself
 * into view on focus: the manual bring-into-view is what previously fought the
 * rail's own scroll and left the highlight off screen.
 */
@Composable
private fun TvLandscapeCard(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    progress: Float?,
    animated: Boolean,
    onClick: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val dimens = LocalTvDimens.current
    val focused = rememberTvFocused()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    TvCard(
        focused = focused.value,
        animated = animated,
        onClick = onClick,
        focusRequester = focusRequester,
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
                    .height(dimens.cardImageHeight)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (progress != null) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = dimens.paddingSmall / 2)
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A poster card, for the rails the catalogue marks as poster style. */
@Composable
private fun TvPosterCard(
    imageUrl: String?,
    title: String,
    animated: Boolean,
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
                .width(dimens.cardWidth * 0.66f)
                .bringIntoViewRequester(bringIntoViewRequester)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.cardWidth * 0.66f * 1.5f)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = dimens.paddingSmall / 2)
            )
        }
    }
}

@Composable
internal fun TvErrorState(onRetry: () -> Unit) {
    val dimens = LocalTvDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.st_could_not_catalog),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(dimens.paddingSmall))
        Text(
            text = stringResource(R.string.home_check_connection),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(dimens.paddingLarge))
        TvButton(onClick = onRetry) {
            Text(stringResource(R.string.action_try_again), style = MaterialTheme.typography.titleMedium)
        }
    }
}