package com.ivor.openstream.presentation.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.data.local.entity.CustomListItemEntity
import com.ivor.openstream.presentation.components.LibraryEmptyState
import com.ivor.openstream.presentation.components.LibraryHeader
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.launch

/** One custom list: its titles, and renaming or deleting it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomListScreen(
    onBackClick: () -> Unit,
    onOpenTitle: (id: Int, mediaType: String) -> Unit,
    viewModel: CustomListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    // Deleted (here or from a restore elsewhere): nothing left to show.
    LaunchedEffect(state.isLoading, state.list) {
        if (!state.isLoading && state.list == null) onBackClick()
    }

    val remove: (CustomListItemEntity) -> Unit = { item ->
        viewModel.remove(item)
        scope.launch {
            val result = snackbar.showSnackbar("Removed ${item.title}", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(item)
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
                    title = state.list?.name.orEmpty(),
                    subtitle = if (state.isLoading) null else itemCountLabel(state.items.size),
                    onBackClick = onBackClick,
                    trailing = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "List options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                                DropdownMenuItem(text = { Text("Delete list") }, onClick = { menuOpen = false; confirmDelete = true })
                            }
                        }
                    }
                )
            }
            when {
                state.isLoading -> item(key = "loading", span = fullWidth) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                state.items.isEmpty() -> item(key = "empty", span = fullWidth) {
                    LibraryEmptyState(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        title = "This list is empty",
                        body = "Open any movie or show and tap Add to list."
                    )
                }
            }
            items(state.items, key = { "${it.mediaType}:${it.tmdbId}" }) { item ->
                ListTitleCard(
                    item = item,
                    onOpen = { onOpenTitle(item.tmdbId, item.mediaType) },
                    onRemove = { remove(item) },
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

    if (renaming) {
        ListNameDialog(
            title = "Rename list",
            confirmLabel = "Rename",
            initialName = state.list?.name.orEmpty(),
            onConfirm = { name ->
                renaming = false
                viewModel.rename(name)
            },
            onDismiss = { renaming = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${state.list?.name.orEmpty()}”?") },
            text = { Text("The list goes away. The titles in it stay in the app and in your other lists.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListTitleCard(
    item: CustomListItemEntity,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
                onClickLabel = "Open ${item.title}",
                onLongClickLabel = "More options"
            )
        ) {
            AsyncImage(
                model = item.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(ExpressiveShapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (item.mediaType == "movie") {
                Text("Movie", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen() })
            DropdownMenuItem(text = { Text("Remove from list") }, onClick = { menuOpen = false; onRemove() })
        }
    }
}
