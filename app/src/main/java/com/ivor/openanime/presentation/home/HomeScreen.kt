package com.ivor.openanime.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openanime.data.remote.model.AnimeDto
import com.ivor.openanime.presentation.components.AnimeCard

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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("OpenStream") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onSearchClick) {
                        // Using a standard search icon for now
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_search),
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_recent_history),
                            contentDescription = "History"
                        )
                    }
                }
            )
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
                                HorizontalAnimeList(
                                    animeList = state.trending,
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
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
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
                    onClick = { onAnimeClick(anime.id) }
                )
            }
        }
    }
}
