package com.ivor.openanime.presentation.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openanime.presentation.components.AnimeCard
import com.ivor.openanime.presentation.components.ExpressiveBackButton
import com.ivor.openanime.ui.theme.ExpressiveShapes



// Expressive Motion Tokens (Spring approximations from M3 specs)
// Source: https://m3.material.io/styles/motion/overview/specs

// Spatial (Large movements)
val ExpressiveFastSpatial = CubicBezierEasing(0.42f, 1.67f, 0.21f, 0.90f) // 350ms
val ExpressiveDefaultSpatial = CubicBezierEasing(0.38f, 1.21f, 0.22f, 1.00f) // 500ms
val ExpressiveSlowSpatial = CubicBezierEasing(0.39f, 1.29f, 0.35f, 0.98f) // 650ms

// Effects (Small movements like fade, scale)
val ExpressiveFastEffects = CubicBezierEasing(0.31f, 0.94f, 0.34f, 1.00f) // 150ms
val ExpressiveDefaultEffects = CubicBezierEasing(0.34f, 0.80f, 0.34f, 1.00f) // 200ms
val ExpressiveSlowEffects = CubicBezierEasing(0.34f, 0.88f, 0.34f, 1.00f) // 300ms

private const val DurationSpatialDefault = 500
private const val DurationEffectsDefault = 200

private fun materialSharedAxisYIn(): ContentTransform {
    // Shared Axis Y (Expressive)
    // Slide uses Spatial curve (physics-based), opacity uses Effects curve
    return (slideInVertically(
                animationSpec = tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)
            ) { height -> height / 2 } + 
            fadeIn(
                animationSpec = tween(DurationEffectsDefault, delayMillis = 50, easing = ExpressiveDefaultEffects)
            ))
        .togetherWith(
            slideOutVertically(
                animationSpec = tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)
            ) { height -> -height / 2 } + 
            fadeOut(
                animationSpec = tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)
            )
        )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onAnimeClick: (Int, String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.query) {
        if (uiState.query != searchQuery) {
            searchQuery = uiState.query
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Expressive Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveBackButton(onClick = onBackClick)
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Discover",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Custom Expressive Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(ExpressiveShapes.large),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Anime, movies, or genres...") },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.onSearch(searchQuery)
                            keyboardController?.hide()
                        })
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            searchQuery = "" 
                            viewModel.onSearch("") 
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Filters (Connected Button Group)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                SearchFilter.entries.forEachIndexed { index, filter ->
                    ToggleButton(
                        checked = uiState.filter == filter,
                        onCheckedChange = { viewModel.onFilterSelected(filter) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            SearchFilter.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    ) {
                        Text(
                            when (filter) {
                                SearchFilter.ALL -> "All"
                                SearchFilter.MOVIE -> "Movies"
                                SearchFilter.TV -> "TV Shows"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Content Area with Material Motion
            AnimatedContent(
                targetState = when {
                    uiState.isLoading -> SearchContentState.Loading
                    uiState.error != null -> SearchContentState.Error
                    uiState.searchResults.isNotEmpty() -> SearchContentState.Results
                    searchQuery.isNotEmpty() -> SearchContentState.Empty
                    else -> SearchContentState.History
                },
                transitionSpec = { materialSharedAxisYIn() },
                label = "SearchContent"
            ) { targetState ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (targetState) {
                        SearchContentState.Loading -> {
                            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        SearchContentState.Error -> {
                            Text(
                                text = uiState.error ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        SearchContentState.Results -> {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(2),
                                verticalItemSpacing = 16.dp,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(uiState.searchResults, key = { it.id }) { anime ->
                                    AnimeCard(
                                        anime = anime,
                                        onClick = { onAnimeClick(anime.id, anime.mediaType ?: "tv") }
                                    )
                                }
                            }
                        }
                        SearchContentState.History -> {
                            Column {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                LazyColumn {
                                    items(uiState.history) { historyItem ->
                                        ListItem(
                                            headlineContent = { Text(historyItem) },
                                            leadingContent = { 
                                                Icon(
                                                    Icons.Default.History, 
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary 
                                                ) 
                                            },
                                            trailingContent = {
                                                IconButton(onClick = { viewModel.removeHistoryItem(historyItem) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                                }
                                            },
                                            colors = ListItemDefaults.colors(
                                                containerColor = Color.Transparent
                                            ),
                                            modifier = Modifier
                                                .clickable {
                                                    searchQuery = historyItem
                                                    viewModel.onSearch(historyItem)
                                                    keyboardController?.hide()
                                                }
                                        )
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Clear History",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier
                                                .clickable { viewModel.clearHistory() }
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        SearchContentState.Empty -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No matches found",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Try a different keyword",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class SearchContentState {
    Loading, Error, Results, History, Empty
}
