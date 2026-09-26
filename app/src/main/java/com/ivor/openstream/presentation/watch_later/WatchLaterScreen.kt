package com.ivor.openstream.presentation.watch_later

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.presentation.components.ChoiceChips
import com.ivor.openstream.presentation.components.LibraryEmptyState
import com.ivor.openstream.presentation.components.LibraryHeader
import com.ivor.openstream.presentation.components.LocalSearchField
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WatchLaterScreen(
    onBackClick: () -> Unit,
    onAnimeClick: (Int, String) -> Unit,
    viewModel: WatchLaterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sortMenuOpen by remember { mutableStateOf(false) }
    val fullWidth: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    val remove: (WatchLaterEntity) -> Unit = { entry ->
        viewModel.remove(entry)
        scope.launch {
            val result = snackbar.showSnackbar("Removed ${entry.title}", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "header", span = fullWidth) {
                LibraryHeader(
                    modifier = Modifier.bleed(16.dp),
                    title = "Saved",
                    subtitle = when (state.totalCount) {
                        0 -> null
                        1 -> "1 title"
                        else -> "${state.totalCount} titles"
                    },
                    onBackClick = onBackClick,
                    trailing = {
                        if (state.totalCount > 1) {
                            Box {
                                IconButton(onClick = { sortMenuOpen = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort: ${state.sort.label}")
                                }
                                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                    SavedSort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            trailingIcon = if (option == state.sort) {
                                                { Icon(Icons.Default.Check, contentDescription = "Selected") }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                sortMenuOpen = false
                                                viewModel.onSortChange(option)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            if (state.totalCount > 0) {
                item(key = "search", span = fullWidth) {
                    Column(modifier = Modifier.bleed(16.dp)) {
                        LocalSearchField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            placeholder = "Search saved titles"
                        )
                        ChoiceChips(
                            options = SavedFilter.entries,
                            selected = state.filter,
                            label = { it.label },
                            onSelect = viewModel::onFilterChange,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            when {
                state.isLoading -> item(key = "loading", span = fullWidth) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                state.totalCount == 0 -> item(key = "empty", span = fullWidth) {
                    LibraryEmptyState(
                        icon = Icons.Default.BookmarkBorder,
                        title = "Nothing saved yet",
                        body = "Tap My list on any title to keep it here for later."
                    )
                }
                state.items.isEmpty() -> item(key = "no-matches", span = fullWidth) {
                    LibraryEmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No matches",
                        body = "Nothing you saved matches this search and filter.",
                        action = {
                            TextButton(onClick = {
                                viewModel.onQueryChange("")
                                viewModel.onFilterChange(SavedFilter.ALL)
                            }) { Text("Clear search and filters") }
                        }
                    )
                }
            }

            items(state.items, key = { "saved:${it.entry.mediaType}:${it.entry.id}" }) { item ->
                SavedCard(
                    item = item,
                    onOpen = { onAnimeClick(item.entry.id, item.entry.mediaType) },
                    onRemove = { remove(item.entry) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 112.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedCard(
    item: SavedItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val entry = item.entry
    val latest = item.latest

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.combinedClickable(
                onClick = onOpen,
                onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            },
                onClickLabel = "Open ${entry.title}",
                onLongClickLabel = "More options"
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(ExpressiveShapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = entry.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (entry.mediaType == "movie") Badge("MOVIE")
                    if (entry.voteAverage > 0) {
                        Surface(shape = ExpressiveShapes.extraSmall, color = Color.Black.copy(alpha = 0.65f), contentColor = Color.White) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                                Text(String.format(Locale.US, " %.1f", entry.voteAverage), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (latest != null && !latest.completed && latest.fraction > 0f) {
                    LinearProgressIndicator(
                        progress = { latest.fraction },
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        trackColor = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            latest?.let {
                Text(
                    text = when {
                        it.isMovie && it.completed -> "Watched"
                        it.isMovie -> "Continue watching"
                        else -> "S${it.season} E${it.episode}" + if (it.completed) " · watched" else ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen() })
            DropdownMenuItem(text = { Text("Remove from saved") }, onClick = { menuOpen = false; onRemove() })
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = ExpressiveShapes.extraSmall, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Lets a full-width grid row reach the screen edges past the grid's side padding. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-horizontal.roundToPx(), 0) }
}
