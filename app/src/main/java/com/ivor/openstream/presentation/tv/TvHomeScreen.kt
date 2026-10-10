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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.R
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.presentation.home.HomeUiState
import com.ivor.openstream.presentation.home.HomeViewModel
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.launch

private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w780"
private const val HERO_IMAGE_BASE = "https://image.tmdb.org/t/p/w1280"

/** Overscan-safe frame for the 10-foot screen: nothing focusable near the edges. */
internal val TvFramePadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp)

/**
 * Leanback home: menu row, cinematic hero and landscape rails, all remote driven.
 * One hero moment (the featured backdrop); everything else stays calm and legible.
 */
@Composable
fun TvHomeScreen(
    onOpenTitle: (mediaType: String, id: Int) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val animated = rememberTvAnimated()

    fun AnimeDto.tvMediaType(): String = mediaType ?: if (isMovie) "movie" else "tv"

    val heroItem: AnimeDto? = (uiState as? HomeUiState.Success)
        ?.hero?.firstOrNull { it.backdropPath != null }
    val heroProgress: WatchProgress? = heroItem?.let { hero ->
        continueWatching.firstOrNull { it.tmdbId == hero.id && !it.isMovie }
    }
    val playRequester = remember { FocusRequester() }
    val detailsRequester = remember { FocusRequester() }
    LaunchedEffect(heroItem?.id) {
        // Land the remote on Play when there is something to resume, else Details.
        (if (heroProgress != null) playRequester else detailsRequester).requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Menu row: brand + search + settings, all D-pad reachable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TvFramePadding)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            TvMenuChip(
                icon = Icons.Default.Search,
                label = stringResource(R.string.nav_search),
                onClick = onSearch
            )
            Spacer(Modifier.width(16.dp))
            TvMenuChip(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.st_title),
                onClick = onSettings
            )
        }

        when (val state = uiState) {
            HomeUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is HomeUiState.Error -> TvErrorState(
                onRetry = { viewModel.refresh() }
            )

            is HomeUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    if (heroItem != null) {
                        item(key = "hero") {
                            TvHero(
                                item = heroItem,
                                progress = heroProgress,
                                animated = animated,
                                playRequester = playRequester,
                                detailsRequester = detailsRequester,
                                onResume = { heroProgress?.let(onResume) },
                                onOpenDetails = { onOpenTitle(heroItem.tvMediaType(), heroItem.id) }
                            )
                        }
                    }
                    if (continueWatching.isNotEmpty()) {
                        item(key = "continue") {
                            TvContinueRail(
                                items = continueWatching,
                                animated = animated,
                                onResume = onResume,
                                onOpenDetails = { onOpenTitle(it.mediaType, it.tmdbId) }
                            )
                        }
                    }
                    items(
                        items = state.rails,
                        key = { "rail:${it.key}" }
                    ) { rail ->
                        if (rail.items.isNotEmpty()) {
                            TvRail(
                                title = rail.title,
                                animated = animated,
                                onOpen = { onOpenTitle(it.tvMediaType(), it.id) },
                                items = rail.items
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMenuChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val focused = rememberTvFocused()
    val scale = tvFocusScale(focused.value, rememberTvAnimated())
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (focused.value) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .then(Modifier.scale(scale))
            .focusable()
            .onFocusChanged { focused.value = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TvHero(
    item: AnimeDto,
    progress: WatchProgress?,
    animated: Boolean,
    playRequester: FocusRequester,
    detailsRequester: FocusRequester,
    onResume: () -> Unit,
    onOpenDetails: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .padding(TvFramePadding)
    ) {
        AsyncImage(
            model = "$HERO_IMAGE_BASE${item.backdropPath}",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(ExpressiveShapes.extraLarge)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ExpressiveShapes.extraLarge)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.52f)
                .padding(36.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.voteAverage?.takeIf { it > 0 }?.let { rating ->
                Text(
                    text = String.format(java.util.Locale.US, "★ %.1f", rating),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(modifier = Modifier.padding(top = 20.dp)) {
                if (progress != null) {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .height(60.dp)
                            .focusRequester(playRequester)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.cd_resume),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                }
                OutlinedButton(
                    onClick = onOpenDetails,
                    modifier = Modifier
                        .height(60.dp)
                        .focusRequester(detailsRequester)
                ) {
                    Text(
                        text = stringResource(R.string.st_details),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TvContinueRail(
    items: List<WatchProgress>,
    animated: Boolean,
    onResume: (WatchProgress) -> Unit,
    onOpenDetails: (WatchProgress) -> Unit
) {
    TvRailHeader(title = stringResource(R.string.home_continue_watching))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(items, key = { "cw:${it.mediaType}:${it.tmdbId}:${it.season}:${it.episode}" }) { item ->
            TvLandscapeCard(
                imageUrl = item.backdropPath?.let { "$IMAGE_BASE$it" },
                title = item.title,
                subtitle = if (item.isMovie) null else "S${item.season} · E${item.episode}",
                progress = item.fraction.takeIf { it > 0f },
                animated = animated,
                onClick = { onResume(item) }
            )
        }
    }
}

@Composable
internal fun TvRail(
    title: String,
    items: List<AnimeDto>,
    animated: Boolean,
    onOpen: (AnimeDto) -> Unit
) {
    TvRailHeader(title = title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(items, key = { "rail:${it.id}" }) { item ->
            TvLandscapeCard(
                imageUrl = item.backdropPath?.let { "$IMAGE_BASE$it" }
                    ?: item.posterPath?.let { "$IMAGE_BASE$it" },
                title = item.name,
                subtitle = item.date.take(4).takeIf { it.length == 4 },
                progress = null,
                animated = animated,
                onClick = { onOpen(item) }
            )
        }
    }
}

@Composable
private fun TvRailHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(horizontal = 56.dp)
            .padding(bottom = 12.dp)
            .semantics { heading() }
    )
}

@Composable
private fun TvLandscapeCard(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    progress: Float?,
    animated: Boolean,
    onClick: () -> Unit
) {
    val focused = rememberTvFocused()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .width(264.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        TvCard(
            focused = focused.value,
            animated = animated,
            onClick = onClick,
            onFocused = {
                focused.value = true
                scope.launch {
                    runCatching { bringIntoViewRequester.bringIntoView() }
                }
            },
            onBlurred = { focused.value = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
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
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
internal fun TvErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.st_could_not_catalog),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_check_connection),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, modifier = Modifier.height(56.dp)) {
            Text(
                text = stringResource(R.string.action_try_again),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
