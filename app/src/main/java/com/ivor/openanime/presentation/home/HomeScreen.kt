package com.ivor.openanime.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openanime.data.remote.model.AnimeDto
import com.ivor.openanime.presentation.components.AnimeCard
import kotlin.math.absoluteValue
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.ivor.openanime.ui.theme.ExpressiveShapes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.height

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onAnimeClick: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val isTablet = LocalConfiguration.current.screenWidthDp > 600
            if (isTablet) {
                TopAppBar(
                    title = { 
                        Text(
                            "OpenStream",
                            style = MaterialTheme.typography.displaySmall
                        ) 
                    },
                    scrollBehavior = scrollBehavior
                )
            } else {
                LargeTopAppBar(
                    title = { 
                        Text(
                            "OpenStream",
                            style = MaterialTheme.typography.displaySmall
                        ) 
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                is HomeUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Error loading content", style = MaterialTheme.typography.titleMedium)
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                is HomeUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        if (state.trending.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Trending Now")
                                TrendingHeroCarousel(
                                    animeList = state.trending.take(10), // Limit carousel to top 10
                                    onAnimeClick = onAnimeClick
                                )
                            }
                        }

                        if (state.topRated.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Top Rated")
                                HorizontalAnimeList(
                                    animeList = state.topRated,
                                    onAnimeClick = onAnimeClick
                                )
                            }
                        }

                        if (state.airingToday.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Airing Today")
                                HorizontalAnimeList(
                                    animeList = state.airingToday,
                                    onAnimeClick = onAnimeClick
                                )
                            }
                        }

                        if (state.popular.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Popular")
                                HorizontalAnimeList(
                                    animeList = state.popular,
                                    onAnimeClick = onAnimeClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrendingHeroCarousel(
    animeList: List<AnimeDto>,
    onAnimeClick: (Int) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { animeList.size })
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) { page ->
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState
                    .currentPageOffsetFraction
            ).absoluteValue

            val graphicsModifier = Modifier.graphicsLayer {
                // Expressive Scale Effect: Center item is larger
                val scale = lerp(
                    start = 0.85f,
                    stop = 1f,
                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                )
                scaleX = scale
                scaleY = scale
                
                // Fade out side items
                alpha = lerp(
                    start = 0.5f,
                    stop = 1f,
                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                )
            }

            if (isTablet) {
                val carouselHeight = (configuration.screenHeightDp * 0.7f).dp
                HeroAnimeCard(
                    anime = animeList[page],
                    onClick = { onAnimeClick(animeList[page].id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(carouselHeight)
                        .then(graphicsModifier)
                )
            } else {
                AnimeCard(
                    anime = animeList[page],
                    onClick = { onAnimeClick(animeList[page].id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .then(graphicsModifier)
                )
            }
        }
    }
}

@Composable
fun HeroAnimeCard(
    anime: AnimeDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ExpressiveShapes.large)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val imageUrl = if (anime.backdropPath != null) {
            "https://image.tmdb.org/t/p/w780${anime.backdropPath}"
        } else {
            "https://image.tmdb.org/t/p/w500${anime.posterPath}"
        }
        
        AsyncImage(
            model = imageUrl,
            contentDescription = anime.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Dark gradient overlay from bottom to top
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )
        
        // Text info overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            val typeText = if (anime.mediaType == "movie") "Movie" else "TV Show"
            val ratingText = if (anime.voteAverage != null && anime.voteAverage > 0) "・ ★ ${String.format("%.1f", anime.voteAverage)}" else ""
            Text(
                text = "${typeText} $ratingText",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Text(
                text = anime.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall, // Larger/bolder header
        color = MaterialTheme.colorScheme.primary, // Use primary color for emphasis
        modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 16.dp) // More breathing room
    )
}

@Composable
fun HorizontalAnimeList(
    animeList: List<AnimeDto>,
    onAnimeClick: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(animeList, key = { it.id }) { anime ->
            Box(modifier = Modifier.width(140.dp)) {
                AnimeCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
