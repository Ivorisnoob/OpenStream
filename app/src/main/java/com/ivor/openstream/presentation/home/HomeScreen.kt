package com.ivor.openstream.presentation.home

import com.ivor.openstream.presentation.components.SkeletonBox
import com.ivor.openstream.presentation.components.bottomContentPadding
import com.ivor.openstream.presentation.components.byWidth
import com.ivor.openstream.presentation.components.isCompactWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.ivor.openstream.domain.model.Profile
import com.ivor.openstream.presentation.profiles.ProfileSwitchButton
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onAnimeClick: (id: Int, mediaType: String) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onOpenDetails: (mediaType: String, id: Int) -> Unit,
    onSettingsClick: () -> Unit,
    onUpdateClick: () -> Unit = {},
    profile: Profile? = null,
    onSwitchProfile: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val open: (AnimeDto) -> Unit = { onAnimeClick(it.id, if (it.isMovie) "movie" else "tv") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hide: (AnimeDto) -> Unit = { anime ->
        viewModel.hideTitle(anime)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "${anime.name} hidden from Home",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.unhideTitle(anime)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            HomeUiState.Loading -> HomeSkeleton()

            is HomeUiState.Error -> HomeError(onRetry = { viewModel.loadData() })

            is HomeUiState.Success -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Clears the floating toolbar and the mini player.
                    contentPadding = PaddingValues(bottom = bottomContentPadding)
                ) {
                    item(key = "hero") {
                        HeroSection(
                            items = state.hero,
                            onOpen = open,
                            onSettingsClick = onSettingsClick,
                            onUpdateClick = onUpdateClick,
                            profile = profile,
                            onSwitchProfile = onSwitchProfile,
                            onKidsTap = {
                                scope.launch { snackbarHostState.showSnackbar("Hold the avatar to leave the kids profile") }
                            }
                        )
                    }

                    if (continueWatching.isNotEmpty()) {
                        item(key = "continue_watching") {
                            SectionHeader(title = "Continue watching")
                            ContinueWatchingRail(
                                items = continueWatching,
                                onResume = onResume,
                                onOpenDetails = { onOpenDetails(it.mediaType, it.tmdbId) },
                                onRemove = viewModel::removeFromContinueWatching
                            )
                        }
                    }

                    state.rails.forEach { rail ->
                        item(key = rail.key) {
                            SectionHeader(title = rail.title)
                            when (rail.style) {
                                RailStyle.RANKED -> RankedRail(rail.items, open, hide)
                                RailStyle.LANDSCAPE -> LandscapeRail(rail.items, open, hide)
                                RailStyle.POSTER -> PosterRail(rail.items, open, hide)
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            // Above the floating toolbar and the mini player.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isCompactWidth) 180.dp else 100.dp)
        )
    }
}

// region Hero

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroSection(
    items: List<AnimeDto>,
    onOpen: (AnimeDto) -> Unit,
    onSettingsClick: () -> Unit,
    onUpdateClick: () -> Unit,
    profile: Profile?,
    onSwitchProfile: () -> Unit,
    onKidsTap: () -> Unit
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OpenStream",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            // A kids profile keeps Settings and updates out of reach; leaving takes a hold on the avatar.
            if (profile?.isKids != true) {
                IconButton(onClick = onUpdateClick) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "Check for updates")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
            ProfileSwitchButton(profile = profile, onSwitch = onSwitchProfile, onKidsTap = onKidsTap)
        }

        if (items.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < 600.dp) {
                    HorizontalCenteredHeroCarousel(
                        state = rememberCarouselState { items.size },
                        itemSpacing = 8.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp)
                    ) { index ->
                        HeroCard(anime = items[index], rank = index + 1, onClick = { onOpen(items[index]) })
                    }
                } else {
                    // Tablets: several phone-sized poster cards side by side, not one huge banner.
                    HorizontalMultiBrowseCarousel(
                        state = rememberCarouselState { items.size },
                        preferredItemWidth = 320.dp,
                        itemSpacing = 8.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                    ) { index ->
                        HeroCard(anime = items[index], rank = index + 1, onClick = { onOpen(items[index]) })
                    }
                }
            }
        }
    }
}

/** 0 for a sliver at the carousel's edge, 1 for the focused card; used to fade text in. */
@OptIn(ExperimentalMaterial3Api::class)
private val CarouselItemScope.focus: Float
    get() {
        val info = carouselItemDrawInfo
        return if (info.maxSize > info.minSize) {
            ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.HeroCard(
    anime: AnimeDto,
    rank: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .maskClip(ExpressiveShapes.extraLarge)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = "https://image.tmdb.org/t/p/w780${anime.posterPath}",
            contentDescription = anime.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )
        // Text only belongs on the focused card; the side cards are slivers of artwork.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
                .graphicsLayer { alpha = focus * focus },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = ExpressiveShapes.small,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = "#$rank this week",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                text = anime.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            MetaLine(anime)
        }
    }
}

@Composable
private fun MetaLine(anime: AnimeDto) {
    val parts = buildList {
        anime.date.take(4).takeIf { it.length == 4 }?.let(::add)
        if (anime.isMovie) add("Movie")
        anime.genreIds.orEmpty().mapNotNull(GENRE_NAMES::get).firstOrNull { it != "Animation" }?.let(::add)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        anime.voteAverage?.takeIf { it > 0 }?.let { rating ->
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFC857),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = String.format(Locale.US, " %.1f", rating) + if (parts.isNotEmpty()) "  ·  " else "",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
        Text(
            text = parts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// endregion

// region Rails

@Composable
private fun RankedRail(items: List<AnimeDto>, onOpen: (AnimeDto) -> Unit, onHide: (AnimeDto) -> Unit) {
    val posterWidth = byWidth(compact = 136.dp, medium = 156.dp, expanded = 168.dp)
    val numeralStyle = TextStyle(
        fontSize = (posterWidth.value * 0.97f).sp,
        fontWeight = FontWeight.Black,
        drawStyle = Stroke(width = 6f)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(items, key = { _, anime -> anime.id }) { index, anime ->
            var menuOpen by remember { mutableStateOf(false) }
            Box(modifier = Modifier.size(width = posterWidth + 48.dp, height = posterWidth / 0.68f + 14.dp)) {
                Text(
                    text = "${index + 1}",
                    style = numeralStyle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = 28.dp)
                )
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w342${anime.posterPath}",
                    contentDescription = anime.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(posterWidth)
                        .aspectRatio(0.68f)
                        .clip(ExpressiveShapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .titleClickable(anime, onOpen) { menuOpen = true }
                )
                NotInterestedMenu(menuOpen, onDismiss = { menuOpen = false }, onHide = { onHide(anime) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandscapeRail(items: List<AnimeDto>, onOpen: (AnimeDto) -> Unit, onHide: (AnimeDto) -> Unit) {
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { items.size },
        preferredItemWidth = byWidth(compact = 300.dp, medium = 380.dp, expanded = 420.dp),
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(byWidth(compact = 188.dp, medium = 220.dp, expanded = 240.dp))
    ) { index ->
        val anime = items[index]
        var menuOpen by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .maskClip(ExpressiveShapes.large)
                .titleClickable(anime, onOpen) { menuOpen = true }
        ) {
            NotInterestedMenu(menuOpen, onDismiss = { menuOpen = false }, onHide = { onHide(anime) })
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w780${anime.backdropPath}",
                contentDescription = anime.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
            )
            Text(
                text = anime.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .graphicsLayer { alpha = focus }
            )
        }
    }
}

@Composable
private fun PosterRail(items: List<AnimeDto>, onOpen: (AnimeDto) -> Unit, onHide: (AnimeDto) -> Unit) {
    val posterWidth = byWidth(compact = 132.dp, medium = 152.dp, expanded = 164.dp)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(items, key = { _, anime -> anime.id }) { _, anime ->
            var menuOpen by remember { mutableStateOf(false) }
            // Clipped to the artwork's shape so the press ripple follows the card; the title is
            // inset from the rounded bottom corners so they never cut into it.
            Column(
                modifier = Modifier
                    .width(posterWidth)
                    .clip(ExpressiveShapes.medium)
                    .titleClickable(anime, onOpen) { menuOpen = true }
                    .padding(bottom = 10.dp)
            ) {
                NotInterestedMenu(menuOpen, onDismiss = { menuOpen = false }, onHide = { onHide(anime) })
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w342${anime.posterPath}",
                    contentDescription = anime.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.68f)
                        .clip(ExpressiveShapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                Text(
                    text = anime.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp)
                )
            }
        }
    }
}

/** Tap opens the title; long-press opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.titleClickable(anime: AnimeDto, onOpen: (AnimeDto) -> Unit, onLongPress: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        onClickLabel = "Open ${anime.name}",
        onLongClickLabel = "More options",
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongPress()
        },
        onClick = { onOpen(anime) }
    )
}

@Composable
private fun NotInterestedMenu(expanded: Boolean, onDismiss: () -> Unit, onHide: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Not interested") },
            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
            onClick = {
                onDismiss()
                onHide()
            }
        )
    }
}

// endregion

@Composable
fun SectionHeader(title: String, topPadding: Dp = 28.dp) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = topPadding, bottom = 14.dp)
    )
}

@Composable
private fun HomeError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Couldn't reach the catalog",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Check your connection and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry, shape = ExpressiveShapes.medium) {
            Text("Try again")
        }
    }
}

/** TMDB genre ids that anime titles use (the TV and movie lists share most of them). */
private val GENRE_NAMES = mapOf(
    16 to "Animation",
    10759 to "Action & Adventure",
    28 to "Action",
    12 to "Adventure",
    35 to "Comedy",
    18 to "Drama",
    10765 to "Sci-Fi & Fantasy",
    14 to "Fantasy",
    878 to "Sci-Fi",
    9648 to "Mystery",
    10749 to "Romance",
    80 to "Crime",
    27 to "Horror",
    53 to "Thriller",
    10751 to "Family",
    10762 to "Kids",
    36 to "History",
    10402 to "Music"
)

/** Mirrors the Home layout (title, hero, two shelves) so nothing jumps when content arrives. */
@Composable
private fun HomeSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        SkeletonBox(
            modifier = Modifier
                .padding(start = 24.dp, top = 16.dp, bottom = 20.dp)
                .size(width = 180.dp, height = 32.dp),
            shape = ExpressiveShapes.small
        )
        SkeletonBox(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(420.dp),
            shape = ExpressiveShapes.extraLarge
        )
        repeat(2) {
            SkeletonBox(
                modifier = Modifier
                    .padding(start = 24.dp, top = 28.dp, bottom = 14.dp)
                    .size(width = 200.dp, height = 24.dp),
                shape = ExpressiveShapes.small
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) {
                    SkeletonBox(modifier = Modifier.size(width = 132.dp, height = 194.dp))
                }
            }
        }
    }
}
