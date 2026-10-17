package com.ivor.openstream.presentation.downloads

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.R
import com.ivor.openstream.data.downloads.ExportState
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.local.entity.LocalMediaEntity
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.presentation.components.CenteredListBox
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.presentation.components.bottomContentPadding
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: (DownloadEntity) -> Unit,
    onLocalMediaClick: (LocalMediaEntity) -> Unit = {},
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var subtitlesForId by rememberSaveable { mutableStateOf<String?>(null) }
    // The id, not the row: rememberSaveable can only keep saveable values.
    var localMediaToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val localMediaToDelete = localMediaToDeleteId?.let { id -> state.localFiles.firstOrNull { it.id == id } }

    // Android 9 and older need the storage permission before saving to Downloads.
    var pendingExport by remember { mutableStateOf<DownloadEntity?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val download = pendingExport
        pendingExport = null
        if (download != null) {
            if (granted) viewModel.exportToStorage(download) else viewModel.onStoragePermissionDenied()
        }
    }
    val exportToStorage: (DownloadEntity) -> Unit = { download ->
        if (viewModel.storageExporter.needsStoragePermission()) {
            pendingExport = download
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.exportToStorage(download)
        }
    }
    val subtitlesViewModel: DownloadSubtitlesViewModel = hiltViewModel()
    val subtitlesFor = subtitlesForId?.let { id -> state.library.flatMap { it.items }.firstOrNull { it.downloadId == id } }
    val pendingDelete = pendingDeleteId?.let { id ->
        state.library.flatMap { it.items }.firstOrNull { it.downloadId == id }
            ?: state.inProgress.firstOrNull { it.downloadId == id }
    }
    val pendingGroup = pendingGroupKey?.let { key -> state.library.firstOrNull { it.key == key } }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (subtitlesFor != null) {
        DownloadSubtitlesSheet(
            download = subtitlesFor,
            viewModel = subtitlesViewModel,
            onDismiss = { subtitlesForId = null }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.dl_delete_all_confirm)) },
            text = { Text(stringResource(R.string.dl_delete_all_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        viewModel.removeAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (localMediaToDelete != null) {
        val target = localMediaToDelete
        AlertDialog(
            onDismissRequest = { localMediaToDeleteId = null },
            title = { Text(stringResource(R.string.local_delete_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.local_delete_confirm_body))
                    if (target != null) {
                        Text(
                            text = target.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val media = localMediaToDelete
                    localMediaToDeleteId = null
                    if (media != null) viewModel.removeLocalMedia(media.id)
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { localMediaToDeleteId = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (pendingDelete != null) {
        val cancelling = pendingDelete.status.let { it == DownloadStatus.QUEUED || it == DownloadStatus.RUNNING || it == DownloadStatus.RESOLVING || it == DownloadStatus.PAUSED }
        if (cancelling) {
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text(stringResource(R.string.dl_cancel_confirm)) },
                text = { Text(stringResource(R.string.dl_cancel_body, pendingDelete.describe())) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = pendingDelete
                            pendingDeleteId = null
                            if (target != null) viewModel.remove(target)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.cd_cancel_download)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.action_keep)) }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text(stringResource(R.string.dl_delete_confirm)) },
                text = { Text(stringResource(R.string.dl_delete_body, pendingDelete.describe())) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = pendingDelete
                            pendingDeleteId = null
                            if (target != null) viewModel.remove(target)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    }

    if (pendingGroup != null) {
        AlertDialog(
            onDismissRequest = { pendingGroupKey = null },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.dl_delete_episodes,
                        pendingGroup.items.size,
                        pendingGroup.items.size
                    )
                )
            },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.dl_delete_group_body,
                        pendingGroup.items.size,
                        pendingGroup.items.size,
                        pendingGroup.title
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val group = pendingGroup
                        pendingGroupKey = null
                        if (group != null) viewModel.removeGroup(group)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingGroupKey = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.selectedTab == DownloadTab.LOCAL_FILES) {
            LocalFilesView(
                state = state,
                onBackClick = onBackClick,
                onTabSelect = viewModel::setSelectedTab,
                onLocalMediaClick = onLocalMediaClick,
                onDeleteMedia = { localMediaToDeleteId = it.id },
                onImportClick = { openDocumentLauncher.launch(arrayOf("video/*")) },
                onSearchChange = viewModel::setSearchQuery,
                onSortChange = viewModel::setSortOrder
            )
        } else if (maxWidth >= WIDE_DOWNLOADS_MIN_WIDTH && !state.isLoading && !state.isEmpty) {
            WideDownloads(
                state = state,
                viewModel = viewModel,
                onBackClick = onBackClick,
                onTabSelect = viewModel::setSelectedTab,
                onDownloadClick = onDownloadClick,
                onDeleteAll = { confirmDeleteAll = true },
                onSubtitles = { subtitlesForId = it.downloadId },
                onExport = exportToStorage,
                onDelete = { pendingDeleteId = it.downloadId },
                onDeleteGroup = { pendingGroupKey = it.key }
            )
        } else {
            CenteredListBox(Modifier.fillMaxSize(), minGutter = 16.dp) { gutter ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 136.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    item(key = "header") {
                        DownloadsHeader(
                            selectedTab = DownloadTab.DOWNLOADS,
                            onTabSelect = viewModel::setSelectedTab,
                            completedCount = state.completedCount,
                            localCount = state.localFiles.size,
                            onBackClick = onBackClick
                        )
                    }

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
                        item(key = "in-progress-title") { SectionTitle(stringResource(R.string.hist_filter_in_progress)) }
                        itemsIndexed(state.inProgress, key = { _, item -> "active:${item.downloadId}" }) { index, item ->
                            InProgressRow(
                                download = item,
                                index = index,
                                count = state.inProgress.size,
                                onPause = { viewModel.pause(item) },
                                onResume = { viewModel.resume(item) },
                                onRetry = { viewModel.retry(item) },
                                onCancel = { pendingDeleteId = item.downloadId },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (state.library.isNotEmpty()) {
                        item(key = "library-title") { SectionTitle(stringResource(R.string.st_on_device)) }
                        items(state.library, key = { "group:${it.key}" }) { group ->
                            LibraryGroup(
                                group = group,
                                subtitleCount = state::subtitleCount,
                                exportStates = state.exportStates,
                                onPlay = onDownloadClick,
                                onSubtitles = { subtitlesForId = it.downloadId },
                                onExport = exportToStorage,
                                onDelete = { pendingDeleteId = it.downloadId },
                                onDeleteAll = { pendingGroupKey = group.key },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}

/** From here Downloads splits into a fixed side column and a library grid. */
private val WIDE_DOWNLOADS_MIN_WIDTH = 900.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsHeader(
    selectedTab: DownloadTab,
    onTabSelect: (DownloadTab) -> Unit,
    completedCount: Int,
    localCount: Int,
    onBackClick: () -> Unit
) {
    Column(modifier = Modifier.statusBarsPadding().padding(top = 16.dp, bottom = 20.dp)) {
        ExpressiveBackButton(onClick = onBackClick)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.download_channel_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.semantics { heading() }
        )
        val countText = if (selectedTab == DownloadTab.DOWNLOADS) {
            if (completedCount == 1) {
                stringResource(R.string.dl_video_one)
            } else if (completedCount > 1) {
                stringResource(R.string.dl_videos_on_device, completedCount)
            } else null
        } else {
            if (localCount == 1) {
                stringResource(R.string.dl_video_one)
            } else if (localCount > 1) {
                stringResource(R.string.dl_videos_on_device, localCount)
            } else null
        }
        if (countText != null) {
            Text(
                text = countText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == DownloadTab.DOWNLOADS,
                onClick = { onTabSelect(DownloadTab.DOWNLOADS) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(stringResource(R.string.dl_tab_downloads))
            }
            SegmentedButton(
                selected = selectedTab == DownloadTab.LOCAL_FILES,
                onClick = { onTabSelect(DownloadTab.LOCAL_FILES) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(stringResource(R.string.dl_tab_local_files))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LocalFilesView(
    state: DownloadsUiState,
    onBackClick: () -> Unit,
    onTabSelect: (DownloadTab) -> Unit,
    onLocalMediaClick: (LocalMediaEntity) -> Unit,
    onDeleteMedia: (LocalMediaEntity) -> Unit,
    onImportClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (LocalMediaSortOrder) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    CenteredListBox(Modifier.fillMaxSize(), minGutter = 16.dp) { gutter ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 136.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            item(key = "header") {
                DownloadsHeader(
                    selectedTab = DownloadTab.LOCAL_FILES,
                    onTabSelect = onTabSelect,
                    completedCount = state.completedCount,
                    localCount = state.localFiles.size,
                    onBackClick = onBackClick
                )
            }

            item(key = "controls") {
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text(stringResource(R.string.local_search_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = ExpressiveShapes.large,
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            FilledTonalIconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.cd_sort_by, stringResource(state.sortOrder.labelRes)))
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                LocalMediaSortOrder.entries.forEach { sortOrder ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(sortOrder.labelRes)) },
                                        onClick = {
                                            onSortChange(sortOrder)
                                            sortMenuExpanded = false
                                        },
                                        leadingIcon = if (sortOrder == state.sortOrder) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onImportClick,
                            shape = ExpressiveShapes.large
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.local_import_files))
                        }
                    }

                    if (state.isImporting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (state.localFiles.isEmpty() && !state.isImporting) {
                item(key = "empty") {
                    EmptyLocalFiles(onImport = onImportClick)
                }
            } else {
                itemsIndexed(state.localFiles, key = { _, item -> "local:${item.id}" }) { index, item ->
                    LocalMediaRow(
                        media = item,
                        index = index,
                        count = state.localFiles.size,
                        onClick = { onLocalMediaClick(item) },
                        onDelete = { onDeleteMedia(item) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalMediaRow(
    media: LocalMediaEntity,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(),
        modifier = modifier,
        leadingContent = {
            Surface(
                shape = ExpressiveShapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(width = 64.dp, height = 48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        supportingContent = {
            val durationText = formatDuration(media.durationMs)
            val sizeText = formatBytes(media.sizeBytes)
            Text(
                text = "$sizeText · $durationText",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_download))
            }
        }
    ) {
        Text(
            text = media.displayName,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyLocalFiles(onImport: () -> Unit) {
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
                    Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.local_no_files_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.local_no_files_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .width(280.dp)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onImport,
            shape = ExpressiveShapes.large
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.local_import_files))
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
    onTabSelect: (DownloadTab) -> Unit,
    onDownloadClick: (DownloadEntity) -> Unit,
    onDeleteAll: () -> Unit,
    onSubtitles: (DownloadEntity) -> Unit,
    onExport: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onDeleteGroup: (DownloadGroup) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 8.dp, bottom = 32.dp)
        ) {
            DownloadsHeader(
                selectedTab = DownloadTab.DOWNLOADS,
                onTabSelect = onTabSelect,
                completedCount = state.completedCount,
                localCount = state.localFiles.size,
                onBackClick = onBackClick
            )
            StorageCard(
                usedBytes = state.storedBytes,
                freeBytes = state.freeBytes,
                onDeleteAll = onDeleteAll,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (state.inProgress.isNotEmpty()) {
                SectionTitle(stringResource(R.string.hist_filter_in_progress))
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
                                onCancel = { onDelete(item) }
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
                    SectionTitle(if (state.library.isEmpty()) stringResource(R.string.dl_nothing_finished) else stringResource(R.string.st_on_device))
                }
            }
            items(state.library, key = { "group:${it.key}" }) { group ->
                LibraryGroup(
                    group = group,
                    subtitleCount = state::subtitleCount,
                    exportStates = state.exportStates,
                    onPlay = onDownloadClick,
                    onSubtitles = onSubtitles,
                    onExport = onExport,
                    onDelete = onDelete,
                    onDeleteAll = { onDeleteGroup(group) },
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
                        text = stringResource(R.string.dl_storage_free, formatBytes(freeBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (total > 0) {
                LinearWavyProgressIndicator(
                    progress = { (usedBytes.toFloat() / total).coerceIn(0.01f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
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
                Text(stringResource(R.string.action_delete_all))
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
                if (download.isSmart) {
                    Text(
                        text = stringResource(R.string.dl_smart_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
                        Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.cd_pause))
                    }
                    DownloadStatus.PAUSED -> IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_resume))
                    }
                    DownloadStatus.FAILED -> IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_retry))
                    }
                    else -> LoadingIndicator(modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_cancel_download))
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
    subtitleCount: (DownloadEntity) -> Int,
    exportStates: Map<String, ExportState>,
    onPlay: (DownloadEntity) -> Unit,
    onSubtitles: (DownloadEntity) -> Unit,
    onExport: (DownloadEntity) -> Unit,
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
                                stringResource(R.string.details_movie) + " · ${formatBytes(group.totalBytes)}" + subtitleSuffix(subtitleCount(group.items.first()))
                            } else {
                                val episodes = if (group.items.size == 1) {
                                    stringResource(R.string.dl_episode_one)
                                } else {
                                    stringResource(R.string.dl_episodes_other, group.items.size)
                                }
                                stringResource(R.string.dl_episodes_size, episodes, formatBytes(group.totalBytes))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (group.isMovie) {
                        val movie = group.items.first()
                        FilledTonalIconButton(onClick = { onPlay(movie) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.details_play))
                        }
                        ExportButton(
                            state = exportStates[movie.downloadId],
                            onClick = { onExport(movie) }
                        )
                        IconButton(onClick = { onSubtitles(movie) }) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = stringResource(R.string.cd_subtitles_for, group.title))
                        }
                        IconButton(onClick = { onDelete(movie) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_download))
                        }
                    } else {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = if (expanded) stringResource(R.string.dl_collapse) else stringResource(R.string.dl_expand),
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
                            supportingContent = { Text(formatBytes(item.sizeBytes) + subtitleSuffix(subtitleCount(item))) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ExportButton(
                                        state = exportStates[item.downloadId],
                                        onClick = { onExport(item) }
                                    )
                                    IconButton(onClick = { onSubtitles(item) }) {
                                        Icon(Icons.Default.ClosedCaption, contentDescription = stringResource(R.string.cd_subtitles_episode, item.episode))
                                    }
                                    IconButton(onClick = { onDelete(item) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dl_delete_episode))
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.li_episode_short, item.episode) + (item.episodeTitle?.let { " · $it" } ?: ""),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDeleteAll) {
                            Text(stringResource(R.string.action_delete_all), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportButton(
    state: ExportState?,
    onClick: () -> Unit
) {
    when (state) {
        is ExportState.Progress -> {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        is ExportState.Success -> {
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.dl_saved_to_storage),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        else -> {
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = stringResource(R.string.dl_save_to_storage)
                )
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
            text = stringResource(R.string.dl_nothing_yet),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.dl_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .width(280.dp)
        )
    }
}

@Composable
private fun DownloadEntity.headline(): String =
    if (mediaType == "movie") displayTitle else "$displayTitle · ${stringResource(R.string.li_episode_code, season, episode)}"

/** Plain words for confirmation dialogs: "Title" or "Title · Season 1, Episode 5". */
@Composable
private fun DownloadEntity.describe(): String =
    if (mediaType == "movie") displayTitle
    else "$displayTitle · ${stringResource(R.string.season_episode_format, season, episode)}"

@Composable
private fun statusLine(download: DownloadEntity): String = when (download.status) {
    DownloadStatus.RESOLVING -> stringResource(R.string.dl_finding_source)
    DownloadStatus.QUEUED -> stringResource(R.string.player_waiting_start)
    DownloadStatus.PAUSED -> stringResource(R.string.dl_paused_percent, download.progress)
    DownloadStatus.FAILED -> download.errorMessage?.let { stringResource(R.string.dl_error_retry, it) }
        ?: stringResource(R.string.dl_failed_retry)
    DownloadStatus.RUNNING -> if (download.totalBytes > 0) {
        stringResource(
            R.string.dl_progress_full,
            download.progress,
            formatBytes(download.downloadedBytes),
            formatBytes(download.totalBytes)
        )
    } else {
        stringResource(R.string.dl_progress_short, download.progress, formatBytes(download.downloadedBytes))
    }
    else -> download.episodeTitle.orEmpty()
}

@Composable
private fun subtitleSuffix(count: Int) = if (count == 0) {
    ""
} else if (count == 1) {
    stringResource(R.string.dl_subtitle_suffix_one)
} else {
    stringResource(R.string.dl_subtitle_suffix_other, count)
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024) else String.format(Locale.US, "%.0f MB", mb)
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
