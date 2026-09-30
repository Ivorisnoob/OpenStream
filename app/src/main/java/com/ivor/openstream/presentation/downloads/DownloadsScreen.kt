package com.ivor.openstream.presentation.downloads

import com.ivor.openstream.presentation.components.bottomContentPadding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.runtime.key
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import com.ivor.openstream.presentation.components.CenteredListBox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: (DownloadEntity) -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Removes every downloaded and queued video from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.removeAll()
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (maxWidth >= WIDE_DOWNLOADS_MIN_WIDTH && !state.isLoading && !state.isEmpty) {
            WideDownloads(
                state = state,
                viewModel = viewModel,
                onBackClick = onBackClick,
                onDownloadClick = onDownloadClick,
                onDeleteAll = { confirmDeleteAll = true }
            )
        } else {
        CenteredListBox(Modifier.fillMaxSize(), minGutter = 16.dp) { gutter ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                // Leaves room for the floating navigation toolbar.
                contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 136.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                item(key = "header") { DownloadsHeader(state.completedCount, onBackClick) }

                if (!state.isLoading && !state.isEmpty) {
                    item(key = "storage") {
                        StorageCard(
                            usedBytes = state.storedBytes,
                            freeBytes = state.freeBytes,
                            onDeleteAll = { confirmDeleteAll = true },
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                when {
                    state.isLoading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                    state.isEmpty -> item(key = "empty") { EmptyDownloads() }
                }

                if (state.inProgress.isNotEmpty()) {
                    item(key = "in-progress-title") { SectionTitle("In progress") }
                    itemsIndexed(state.inProgress, key = { _, item -> "active:${item.downloadId}" }) { index, item ->
                        InProgressRow(
                            download = item,
                            index = index,
                            count = state.inProgress.size,
                            onPause = { viewModel.pause(item) },
                            onResume = { viewModel.resume(item) },
                            onRetry = { viewModel.retry(item) },
                            onCancel = { viewModel.remove(item) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                if (state.library.isNotEmpty()) {
                    item(key = "library-title") { SectionTitle("On this device") }
                    items(state.library, key = { "group:${it.key}" }) { group ->
                        LibraryGroup(
                            group = group,
                            onPlay = onDownloadClick,
                            onDelete = viewModel::remove,
                            onDeleteAll = { viewModel.removeGroup(group) },
                            modifier = Modifier
                                .animateItem()
                                .padding(bottom = 12.dp)
                        )
                    }
                }
            }
        }
        }
    }
}

/** From here Downloads splits into a fixed side column and a library grid. */
private val WIDE_DOWNLOADS_MIN_WIDTH = 900.dp

@Composable
private fun DownloadsHeader(completedCount: Int, onBackClick: () -> Unit) {
    Column(modifier = Modifier.statusBarsPadding().padding(top = 16.dp, bottom = 20.dp)) {
        ExpressiveBackButton(onClick = onBackClick)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.semantics { heading() }
        )
        if (completedCount > 0) {
            Text(
                text = pluralize(completedCount, "video") + " on this device",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Tablets: storage and anything still downloading stay in view on the left while the library
 * fills the rest of the screen as a grid of title cards.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WideDownloads(
    state: DownloadsUiState,
    viewModel: DownloadViewModel,
    onBackClick: () -> Unit,
    onDownloadClick: (DownloadEntity) -> Unit,
    onDeleteAll: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 8.dp, bottom = 32.dp)
        ) {
            DownloadsHeader(state.completedCount, onBackClick)
            StorageCard(
                usedBytes = state.storedBytes,
                freeBytes = state.freeBytes,
                onDeleteAll = onDeleteAll,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (state.inProgress.isNotEmpty()) {
                SectionTitle("In progress")
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    state.inProgress.forEachIndexed { index, item ->
                        key(item.downloadId) {
                            InProgressRow(
                                download = item,
                                index = index,
                                count = state.inProgress.size,
                                onPause = { viewModel.pause(item) },
                                onResume = { viewModel.resume(item) },
                                onRetry = { viewModel.retry(item) },
                                onCancel = { viewModel.remove(item) }
                            )
                        }
                    }
                }
            }
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, bottom = bottomContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp
        ) {
            item(key = "library-title", span = StaggeredGridItemSpan.FullLine) {
                // Lines the title up with "Downloads" in the side column.
                Box(Modifier.statusBarsPadding().padding(top = 96.dp)) {
                    SectionTitle(if (state.library.isEmpty()) "Nothing finished yet" else "On this device")
                }
            }
            items(state.library, key = { "group:${it.key}" }) { group ->
                LibraryGroup(
                    group = group,
                    onPlay = onDownloadClick,
                    onDelete = viewModel::remove,
                    onDeleteAll = { viewModel.removeGroup(group) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun StorageCard(
    usedBytes: Long,
    freeBytes: Long,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = usedBytes + freeBytes
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = ExpressiveShapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatBytes(usedBytes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "used by downloads · ${formatBytes(freeBytes)} free",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { (usedBytes.toFloat() / total).coerceIn(0.01f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
            OutlinedButton(
                onClick = onDeleteAll,
                shape = ExpressiveShapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete all")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InProgressRow(
    download: DownloadEntity,
    index: Int,
    count: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(download.progress / 100f, label = "downloadProgress")
    SegmentedListItem(
        onClick = when (download.status) {
            DownloadStatus.FAILED -> onRetry
            DownloadStatus.PAUSED -> onResume
            DownloadStatus.RUNNING, DownloadStatus.QUEUED -> onPause
            else -> ({})
        },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(),
        modifier = modifier,
        leadingContent = { Thumbnail(download.stillPath ?: download.posterPath) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = statusLine(download),
                    color = if (download.status == DownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (download.status == DownloadStatus.RUNNING || download.status == DownloadStatus.PAUSED) {
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        trailingContent = {
            Row {
                when (download.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    }
                    DownloadStatus.PAUSED -> IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                    DownloadStatus.FAILED -> IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    }
                    else -> LoadingIndicator(modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel download")
                }
            }
        }
    ) {
        Text(
            text = download.headline(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryGroup(
    group: DownloadGroup,
    onPlay: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(group.key) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Surface(
                onClick = { if (group.isMovie) onPlay(group.items.first()) else expanded = !expanded },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = ExpressiveShapes.large
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = group.posterPath?.let { "https://image.tmdb.org/t/p/w185$it" },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 64.dp, height = 92.dp)
                            .clip(ExpressiveShapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (group.isMovie) {
                                "Movie · ${formatBytes(group.totalBytes)}"
                            } else {
                                "${pluralize(group.items.size, "episode")} · ${formatBytes(group.totalBytes)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (group.isMovie) {
                        FilledTonalIconButton(onClick = { onPlay(group.items.first()) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                        IconButton(onClick = onDeleteAll) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete download")
                        }
                    } else {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(chevronRotation)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && !group.isMovie,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    group.items.forEachIndexed { index, item ->
                        SegmentedListItem(
                            onClick = { onPlay(item) },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = group.items.size),
                            colors = ListItemDefaults.segmentedColors(),
                            leadingContent = { Thumbnail(item.stillPath ?: item.posterPath) },
                            supportingContent = { Text(formatBytes(item.totalBytes)) },
                            trailingContent = {
                                IconButton(onClick = { onDelete(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete episode")
                                }
                            }
                        ) {
                            Text(
                                text = "E${item.episode}" + (item.episodeTitle?.let { " · $it" } ?: ""),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = onDeleteAll) {
                            Text("Delete all", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(path: String?) {
    AsyncImage(
        model = path?.let { "https://image.tmdb.org/t/p/w300$it" },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 88.dp, height = 50.dp)
            .clip(ExpressiveShapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

@Composable
private fun EmptyDownloads() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = ExpressiveShapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.DownloadForOffline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Nothing downloaded yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Download episodes from any show's page and watch them without a connection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .width(280.dp)
        )
    }
}

private fun DownloadEntity.headline(): String =
    if (mediaType == "movie") displayTitle else "$displayTitle · S$season E$episode"

private fun statusLine(download: DownloadEntity): String = when (download.status) {
    DownloadStatus.RESOLVING -> "Finding a source…"
    DownloadStatus.QUEUED -> "Waiting to start"
    DownloadStatus.PAUSED -> "Paused · ${download.progress}%"
    DownloadStatus.FAILED -> download.errorMessage?.let { "$it · tap to retry" } ?: "Failed · tap to retry"
    DownloadStatus.RUNNING -> if (download.totalBytes > 0) {
        "${download.progress}% · ${formatBytes(download.downloadedBytes)} of ${formatBytes(download.totalBytes)}"
    } else {
        "${download.progress}% · ${formatBytes(download.downloadedBytes)}"
    }
    else -> download.episodeTitle.orEmpty()
}

private fun pluralize(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024) else String.format(Locale.US, "%.0f MB", mb)
}
