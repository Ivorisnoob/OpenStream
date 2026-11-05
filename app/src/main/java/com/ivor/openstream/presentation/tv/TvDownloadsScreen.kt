package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.presentation.downloads.DownloadGroup
import com.ivor.openstream.presentation.downloads.DownloadViewModel
import com.ivor.openstream.presentation.downloads.formatBytes

/** What the confirm panel is about to delete. */
private sealed interface PendingDelete {
    data class One(val download: DownloadEntity) : PendingDelete
    data object Everything : PendingDelete
}

/**
 * Downloads as rows of cards, like Home. What is still downloading comes first (OK pauses,
 * resumes or retries); then movies as posters and one row per show of its episodes (OK plays).
 * Holding OK on any card asks to delete it in a centred panel, never a dialog. Imported local
 * files are a phone feature (they need a document picker) and are left out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvDownloadsScreen(
    onPlay: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var pending by remember { mutableStateOf<PendingDelete?>(null) }
    val firstCard = remember { FocusRequester() }

    val movies = remember(state.library) { state.library.filter { it.isMovie }.flatMap { it.items } }
    val shows = remember(state.library) { state.library.filterNot { it.isMovie } }
    val isEmpty = state.inProgress.isEmpty() && state.library.isEmpty()

    LaunchedEffect(state.isLoading, isEmpty) {
        if (!state.isLoading && !isEmpty) runCatching { firstCard.requestFocus() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            val count = if (state.completedCount == 1) stringResource(R.string.dl_episode_one)
            else stringResource(R.string.dl_episodes_other, state.completedCount)
            TvPageHeader(
                title = stringResource(R.string.dl_title),
                supporting = if (isEmpty) null else listOf(
                    count,
                    stringResource(
                        R.string.dl_episodes_size,
                        formatBytes(state.storedBytes),
                        stringResource(R.string.dl_storage_free, formatBytes(state.freeBytes))
                    ),
                    stringResource(R.string.tv_hold_to_delete)
                ).joinToString("  ·  "),
                actions = {
                    if (!isEmpty) {
                        OutlinedButton(onClick = { pending = PendingDelete.Everything }) {
                            androidx.tv.material3.Text(stringResource(R.string.action_delete_all))
                        }
                    }
                }
            )

            when {
                state.isLoading -> TvLoading()
                isEmpty -> TvEmptyPage(stringResource(R.string.dl_nothing_yet))
                else -> {
                    val headerSpace = with(LocalDensity.current) { 48.dp.toPx() }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides remember(headerSpace) { tvRowPivot(headerSpace) }) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
                        ) {
                            var firstAssigned = false
                            fun takeFirst(): Modifier =
                                if (firstAssigned) Modifier else Modifier.focusRequester(firstCard).also { firstAssigned = true }

                            if (state.inProgress.isNotEmpty()) {
                                val first = takeFirst()
                                item(key = "in_progress") {
                                    TvRail(title = stringResource(R.string.hist_filter_in_progress)) {
                                        items(state.inProgress, key = { it.downloadId }) { download ->
                                            InProgressCard(
                                                download = download,
                                                modifier = if (download == state.inProgress.first()) first else Modifier,
                                                onClick = {
                                                    when (download.status) {
                                                        DownloadStatus.FAILED -> viewModel.retry(download)
                                                        DownloadStatus.PAUSED -> viewModel.resume(download)
                                                        else -> viewModel.pause(download)
                                                    }
                                                },
                                                onLongClick = { pending = PendingDelete.One(download) }
                                            )
                                        }
                                    }
                                }
                            }
                            if (movies.isNotEmpty()) {
                                val first = takeFirst()
                                item(key = "movies") {
                                    TvRail(title = stringResource(R.string.li_saved_movies)) {
                                        items(movies, key = { it.downloadId }) { download ->
                                            TvPosterCard(
                                                title = download.displayTitle,
                                                posterPath = download.posterPath,
                                                modifier = if (download == movies.first()) first else Modifier,
                                                onClick = { onPlay(download) },
                                                onLongClick = { pending = PendingDelete.One(download) }
                                            )
                                        }
                                    }
                                }
                            }
                            shows.forEach { group ->
                                val first = takeFirst()
                                item(key = group.key) {
                                    ShowRail(
                                        group = group,
                                        firstModifier = first,
                                        onPlay = onPlay,
                                        onDelete = { pending = PendingDelete.One(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        when (val target = pending) {
            is PendingDelete.One -> TvConfirmPanel(
                title = stringResource(R.string.dl_delete_confirm),
                message = target.download.label(),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    viewModel.remove(target.download)
                    pending = null
                },
                onCancel = { pending = null }
            )
            PendingDelete.Everything -> TvConfirmPanel(
                title = stringResource(R.string.dl_delete_all_confirm),
                message = null,
                confirmLabel = stringResource(R.string.action_delete_all),
                onConfirm = {
                    viewModel.removeAll()
                    pending = null
                },
                onCancel = { pending = null }
            )
            null -> Unit
        }
    }
}

@Composable
private fun ShowRail(
    group: DownloadGroup,
    firstModifier: Modifier,
    onPlay: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit
) {
    val episodes = if (group.items.size == 1) stringResource(R.string.dl_episode_one)
    else stringResource(R.string.dl_episodes_other, group.items.size)
    TvRail(title = "${group.title}  ·  $episodes  ·  ${formatBytes(group.totalBytes)}") {
        items(group.items, key = { it.downloadId }) { download ->
            TvLandscapeCard(
                title = download.episodeLabel(),
                subtitle = download.sizeBytes.takeIf { it > 0 }?.let { formatBytes(it) },
                imageUrl = (download.stillPath ?: download.posterPath)?.let { "$TV_STILL_BASE$it" },
                modifier = if (download == group.items.first()) firstModifier else Modifier,
                onClick = { onPlay(download) },
                onLongClick = { onDelete(download) }
            )
        }
    }
}

/** A download still on its way: its progress on the card and its state in words. */
@Composable
private fun InProgressCard(
    download: DownloadEntity,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box {
        TvLandscapeCard(
            title = download.label(),
            subtitle = download.statusLine(),
            imageUrl = (download.stillPath ?: download.posterPath)?.let { "$TV_STILL_BASE$it" },
            progress = (download.progress / 100f).coerceIn(0f, 1f),
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick
        )
        val badge = when (download.status) {
            DownloadStatus.FAILED -> Icons.Default.Refresh
            DownloadStatus.PAUSED -> Icons.Default.Pause
            else -> null
        }
        if (badge != null) {
            Icon(
                badge,
                contentDescription = null,
                tint = if (download.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}

/** "Show · S2 E4" for episodes, the title for movies. */
private fun DownloadEntity.label(): String =
    if (mediaType == "movie") displayTitle else "$displayTitle · S$season E$episode"

/** "S2 E4 · The One", or just the code when the episode has no name. */
private fun DownloadEntity.episodeLabel(): String {
    val code = "S$season E$episode"
    val name = episodeTitle?.takeIf { it.isNotBlank() && it != code }
    return if (name != null) "$code · $name" else code
}

@Composable
private fun DownloadEntity.statusLine(): String = when (status) {
    DownloadStatus.RESOLVING -> stringResource(R.string.dl_finding_source)
    DownloadStatus.QUEUED -> stringResource(R.string.dl_queued)
    DownloadStatus.RUNNING -> if (totalBytes > 0) {
        stringResource(R.string.dl_progress_full, progress, formatBytes(downloadedBytes), formatBytes(totalBytes))
    } else {
        stringResource(R.string.dl_progress_short, progress, formatBytes(downloadedBytes))
    }
    DownloadStatus.PAUSED -> stringResource(R.string.dl_paused_percent, progress)
    DownloadStatus.FAILED -> errorMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.dl_failed_retry)
    else -> stringResource(R.string.dl_stored_device)
}
