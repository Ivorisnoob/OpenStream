package com.ivor.openstream.presentation.watch_later

import com.ivor.openstream.presentation.components.bottomContentPadding
import com.ivor.openstream.R
import com.ivor.openstream.presentation.components.byWidth
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
import com.ivor.openstream.presentation.lists.ListCard
import com.ivor.openstream.presentation.lists.ListNameDialog
import com.ivor.openstream.presentation.lists.NewListCard
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WatchLaterScreen(
    onBackClick: () -> Unit,
    onAnimeClick: (Int, String) -> Unit,
    onOpenList: (listId: Long) -> Unit = {},
    viewModel: WatchLaterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val lists by viewModel.lists.collectAsState()
    var creatingList by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)
    var sortMenuOpen by remember { mutableStateOf(false) }
    val fullWidth: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    val remove: (WatchLaterEntity) -> Unit = { entry ->
        viewModel.remove(entry)
        scope.launch {
            val result = snackbar.showSnackbar(context.getString(R.string.li_removed_title, entry.title), actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = byWidth(compact = 120.dp, medium = 128.dp, expanded = 140.dp)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "header", span = fullWidth) {
                LibraryHeader(
                    modifier = Modifier.bleed(16.dp),
                    title = stringResource(R.string.nav_saved),
                    subtitle = when (state.totalCount) {
                        0 -> null
                        1 -> stringResource(R.string.count_title_one)
                        else -> stringResource(R.string.count_titles, state.totalCount)
                    },
                    onBackClick = onBackClick,
                    trailing = {
                        if (state.totalCount > 1) {
                            Box {
                                IconButton(onClick = { sortMenuOpen = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.cd_sort_by, stringResource(state.sort.labelRes)))
                                }
                                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                    SavedSort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(option.labelRes)) },
                                            trailingIcon = if (option == state.sort) {
                                                { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_selected)) }
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

            // The user's own lists sit above Watch Later; the row ends with "New list".
            item(key = "lists", span = fullWidth) {
                Column(modifier = Modifier.bleed(16.dp)) {
                    Text(
                        text = stringResource(R.string.li_your_lists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .semantics { heading() }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        lazyItems(lists, key = { "list:${it.id}" }) { list ->
                            ListCard(list = list, onClick = { onOpenList(list.id) })
                        }
                        item(key = "new-list") { NewListCard(onClick = { creatingList = true }) }
                    }
                    Text(
                        text = stringResource(R.string.action_watch_later),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
                            .semantics { heading() }
                    )
                }
            }

            if (state.totalCount > 0) {
                item(key = "search", span = fullWidth) {
                    Column(modifier = Modifier.bleed(16.dp)) {
                        LocalSearchField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            placeholder = stringResource(R.string.li_search_saved)
                        )
                        ChoiceChips(
                            options = SavedFilter.entries,
                            selected = state.filter,
                            label = { stringResource(it.labelRes) },
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
                        title = stringResource(R.string.li_nothing_saved),
                        body = stringResource(R.string.li_tap_list_hint)
                    )
                }
                state.items.isEmpty() -> item(key = "no-matches", span = fullWidth) {
                    LibraryEmptyState(
                        icon = Icons.Default.SearchOff,
                        title = stringResource(R.string.er_no_results),
                        body = stringResource(R.string.li_nothing_saved_filter),
                        action = {
                            TextButton(onClick = {
                                viewModel.onQueryChange("")
                                viewModel.onFilterChange(SavedFilter.ALL)
                            }) { Text(stringResource(R.string.search_clear_search_filters)) }
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

    if (creatingList) {
        ListNameDialog(
            title = stringResource(R.string.li_new_list),
            confirmLabel = stringResource(R.string.create_label),
            onConfirm = { name ->
                creatingList = false
                viewModel.createList(name, onOpenList)
            },
            onDismiss = { creatingList = false }
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
        // Clipped to the artwork's shape so the press ripple follows the card; the text is inset from its corners.
        Column(
            modifier = Modifier
                .clip(ExpressiveShapes.medium)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onClickLabel = stringResource(R.string.misc_open_title, entry.title),
                    onLongClickLabel = stringResource(R.string.misc_more_options)
                )
                .padding(bottom = 10.dp)
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
                modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp)
            )
            latest?.let {
                Text(
                    text = when {
                        it.isMovie && it.completed -> stringResource(R.string.details_watched)
                        it.isMovie -> stringResource(R.string.home_continue_watching)
                        it.completed -> stringResource(R.string.misc_code_watched, "S${it.season} E${it.episode}")
                        else -> "S${it.season} E${it.episode}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_open)) }, onClick = { menuOpen = false; onOpen() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_remove_from_saved)) }, onClick = { menuOpen = false; onRemove() })
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
