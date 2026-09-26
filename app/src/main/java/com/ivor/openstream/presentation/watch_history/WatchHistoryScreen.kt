package com.ivor.openstream.presentation.watch_history

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.presentation.components.ChoiceChips
import com.ivor.openstream.presentation.components.LibraryEmptyState
import com.ivor.openstream.presentation.components.LibraryHeader
import com.ivor.openstream.presentation.components.LocalSearchField
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun WatchHistoryScreen(
    onBackClick: () -> Unit,
    onResume: (WatchProgress) -> Unit,
    onOpenDetails: (mediaType: String, id: Int) -> Unit,
    viewModel: WatchHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    val remove: (WatchProgress) -> Unit = { entry ->
        viewModel.remove(entry)
        scope.launch {
            val result = snackbar.showSnackbar("Removed from history", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 200.dp)
        ) {
            item(key = "header") {
                LibraryHeader(
                    title = "History",
                    subtitle = if (state.watchedThisWeekMs > 0) {
                        "${formatDuration(state.watchedThisWeekMs)} watched this week"
                    } else if (state.totalCount > 0) {
                        "${state.totalCount} watched"
                    } else {
                        null
                    },
                    onBackClick = onBackClick,
                    trailing = {
                        if (state.totalCount > 0) {
                            IconButton(onClick = { confirmClear = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
                            }
                        }
                    }
                )
            }

            if (state.totalCount > 0) {
                item(key = "search") {
                    LocalSearchField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = "Search your history"
                    )
                }
                item(key = "filters") {
                    ChoiceChips(
                        options = HistoryFilter.entries,
                        selected = state.filter,
                        label = { it.label },
                        onSelect = viewModel::onFilterChange,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                state.totalCount == 0 -> item(key = "empty") {
                    LibraryEmptyState(
                        icon = Icons.Default.History,
                        title = "Nothing watched yet",
                        body = "Episodes and movies you watch show up here, so you can jump back in any time."
                    )
                }
                !state.hasResults -> item(key = "no-matches") {
                    LibraryEmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No matches",
                        body = "Nothing in your history matches this search and filter.",
                        action = {
                            TextButton(onClick = {
                                viewModel.onQueryChange("")
                                viewModel.onFilterChange(HistoryFilter.ALL)
                            }) { Text("Clear search and filters") }
                        }
                    )
                }
            }

            state.groups.forEach { group ->
                stickyHeader(key = "group:${group.label}") {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                            .semantics { heading() }
                    )
                }
                itemsIndexed(group.items, key = { _, entry -> "entry:${entry.mediaType}:${entry.tmdbId}:${entry.season}:${entry.episode}" }) { index, entry ->
                    HistoryRow(
                        entry = entry,
                        index = index,
                        count = group.items.size,
                        onResume = { onResume(entry) },
                        onOpenDetails = { onOpenDetails(entry.mediaType, entry.tmdbId) },
                        onRemove = { remove(entry) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 112.dp)
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("This also resets Continue watching and the watched marks on episodes. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearAll()
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryRow(
    entry: WatchProgress,
    index: Int,
    count: Int,
    onResume: () -> Unit,
    onOpenDetails: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.updatedAt))
    val status = when {
        entry.completed -> "Watched"
        entry.durationMs > 0 -> "${((entry.durationMs - entry.positionMs) / 60_000L).coerceAtLeast(1)}m left"
        else -> null
    }

    Box(modifier = modifier.padding(horizontal = 16.dp)) {
        SegmentedListItem(
            onClick = onResume,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            },
            onLongClickLabel = "More options",
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            colors = ListItemDefaults.segmentedColors(),
            modifier = Modifier.padding(vertical = 1.dp),
            leadingContent = { Thumbnail(entry) },
            supportingContent = {
                Text(
                    text = listOfNotNull(
                        if (entry.isMovie) "Movie" else "S${entry.season} E${entry.episode}" + (entry.episodeTitle?.let { " · $it" } ?: ""),
                        status,
                        time
                    ).joinToString("  ·  "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        ) {
            Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(if (entry.completed) "Watch again" else "Resume") }, onClick = { menuOpen = false; onResume() })
            DropdownMenuItem(text = { Text("Go to details") }, onClick = { menuOpen = false; onOpenDetails() })
            DropdownMenuItem(text = { Text("Remove from history") }, onClick = { menuOpen = false; onRemove() })
        }
    }
}

@Composable
private fun Thumbnail(entry: WatchProgress) {
    Box(
        modifier = Modifier
            .width(112.dp)
            .aspectRatio(16f / 9f)
            .clip(ExpressiveShapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AsyncImage(
            model = (entry.stillPath ?: entry.backdropPath ?: entry.posterPath)?.let { "https://image.tmdb.org/t/p/w300$it" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (entry.completed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        } else if (entry.fraction > 0f) {
            LinearProgressIndicator(
                progress = { entry.fraction },
                gapSize = 0.dp,
                drawStopIndicator = {},
                trackColor = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}
