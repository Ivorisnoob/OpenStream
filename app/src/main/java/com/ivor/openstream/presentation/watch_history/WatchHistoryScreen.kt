package com.ivor.openstream.presentation.watch_history

import com.ivor.openstream.presentation.components.isCompactWidth
import com.ivor.openstream.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import com.ivor.openstream.presentation.components.bottomContentPadding
import com.ivor.openstream.presentation.components.CenteredListBox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
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

private val FullRow: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

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
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)
    val removedMessage = stringResource(R.string.dl_removed_history)
    var confirmClear by remember { mutableStateOf(false) }

    val remove: (WatchProgress) -> Unit = { entry ->
        viewModel.remove(entry)
        scope.launch {
            val result = snackbar.showSnackbar(removedMessage, actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tablets show history as a grid of landscape cards, like a video app's watch history;
        // phones keep the compact list.
        val cards = !isCompactWidth
        CenteredListBox(
            Modifier.fillMaxSize(),
            minGutter = if (cards) 8.dp else 0.dp,
            maxContentWidth = if (cards) 1240.dp else 840.dp
        ) { gutter ->
            LazyVerticalGrid(
                columns = if (cards) GridCells.Adaptive(minSize = 280.dp) else GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = gutter, end = gutter, bottom = bottomContentPadding)
            ) {
                item(key = "header", span = FullRow) {
                    LibraryHeader(
                        title = stringResource(R.string.nav_history),
                        subtitle = if (state.watchedThisWeekMs > 0) {
                            stringResource(R.string.hi_watched_week, formatDuration(state.watchedThisWeekMs))
                        } else if (state.totalCount > 0) {
                            stringResource(R.string.hi_watched_count, state.totalCount)
                        } else {
                            null
                        },
                        onBackClick = onBackClick,
                        trailing = {
                            if (state.totalCount > 0) {
                                IconButton(onClick = { confirmClear = true }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.cd_clear_history))
                                }
                            }
                        }
                    )
                }

                if (state.totalCount > 0) {
                    item(key = "search", span = FullRow) {
                        LocalSearchField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            placeholder = stringResource(R.string.hi_search)
                        )
                    }
                    item(key = "filters", span = FullRow) {
                        ChoiceChips(
                            options = HistoryFilter.entries,
                            selected = state.filter,
                            label = { stringResource(it.labelRes) },
                            onSelect = viewModel::onFilterChange,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                }

                when {
                    state.isLoading -> item(key = "loading", span = FullRow) {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                    state.totalCount == 0 -> item(key = "empty", span = FullRow) {
                        LibraryEmptyState(
                            icon = Icons.Default.History,
                            title = stringResource(R.string.hi_nothing_watched),
                            body = stringResource(R.string.hi_nothing_hint)
                        )
                    }
                    !state.hasResults -> item(key = "no-matches", span = FullRow) {
                        LibraryEmptyState(
                            icon = Icons.Default.SearchOff,
                            title = stringResource(R.string.er_no_results),
                            body = stringResource(R.string.hi_no_match),
                            action = {
                                TextButton(onClick = {
                                    viewModel.onQueryChange("")
                                    viewModel.onFilterChange(HistoryFilter.ALL)
                                }) { Text(stringResource(R.string.search_clear_search_filters)) }
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
                        if (cards) {
                            HistoryCard(
                                entry = entry,
                                onResume = { onResume(entry) },
                                onOpenDetails = { onOpenDetails(entry.mediaType, entry.tmdbId) },
                                onRemove = { remove(entry) },
                                modifier = Modifier.animateItem()
                            )
                        } else {
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
            title = { Text(stringResource(R.string.hi_clear_all)) },
            text = { Text(stringResource(R.string.li_this_reset)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearAll()
                }) { Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } }
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
        entry.completed -> stringResource(R.string.details_watched)
        entry.durationMs > 0 -> stringResource(R.string.misc_minutes_short, ((entry.durationMs - entry.positionMs) / 60_000L).coerceAtLeast(1))
        else -> null
    }

    Box(modifier = modifier.padding(horizontal = 16.dp)) {
        SegmentedListItem(
            onClick = onResume,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            },
            onLongClickLabel = stringResource(R.string.misc_more_options),
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            colors = ListItemDefaults.segmentedColors(),
            modifier = Modifier.padding(vertical = 1.dp),
            leadingContent = { Thumbnail(entry) },
            supportingContent = {
                Text(
                    text = listOfNotNull(
                        if (entry.isMovie) stringResource(R.string.details_movie) else "S${entry.season} E${entry.episode}" + (entry.episodeTitle?.let { " · $it" } ?: ""),
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
            DropdownMenuItem(text = { Text(if (entry.completed) stringResource(R.string.action_watch_again) else stringResource(R.string.cd_resume)) }, onClick = { menuOpen = false; onResume() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_go_to_details)) }, onClick = { menuOpen = false; onOpenDetails() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_remove_from_history)) }, onClick = { menuOpen = false; onRemove() })
        }
    }
}

/** A history entry as a landscape card: artwork with its progress, then title and episode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    entry: WatchProgress,
    onResume: () -> Unit,
    onOpenDetails: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.updatedAt))
    val minutesLeft = ((entry.durationMs - entry.positionMs) / 60_000L).coerceAtLeast(1)

    Box(modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier
                .clip(ExpressiveShapes.large)
                .combinedClickable(
                    onClick = onResume,
                    onClickLabel = if (entry.completed) stringResource(R.string.action_watch_again) else stringResource(R.string.cd_resume),
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onLongClickLabel = stringResource(R.string.misc_more_options)
                )
                .padding(bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(ExpressiveShapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = (entry.stillPath ?: entry.backdropPath ?: entry.posterPath)?.let { "https://image.tmdb.org/t/p/w500$it" },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)))
                )
                // Status chip: done, or how much is left.
                Surface(
                    shape = ExpressiveShapes.small,
                    color = if (entry.completed) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                    contentColor = if (entry.completed) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (entry.completed) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(stringResource(R.string.details_watched), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        } else if (entry.durationMs > 0) {
                            Text(stringResource(R.string.misc_minutes_short, minutesLeft), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        } else {
                            Text(time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!entry.completed && entry.fraction > 0f) {
                    LinearProgressIndicator(
                        progress = { entry.fraction },
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp)
            )
            Text(
                text = listOfNotNull(
                    if (entry.isMovie) stringResource(R.string.details_movie) else "S${entry.season} E${entry.episode}" + (entry.episodeTitle?.let { " · $it" } ?: ""),
                    time
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(if (entry.completed) stringResource(R.string.action_watch_again) else stringResource(R.string.cd_resume)) }, onClick = { menuOpen = false; onResume() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_go_to_details)) }, onClick = { menuOpen = false; onOpenDetails() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_remove_from_history)) }, onClick = { menuOpen = false; onRemove() })
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

@Composable
private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) stringResource(R.string.misc_duration_hm, minutes / 60, minutes % 60) else stringResource(R.string.misc_duration_m, minutes)
}
