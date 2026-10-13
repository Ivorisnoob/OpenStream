package com.ivor.openstream.presentation.tv

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.presentation.downloads.DownloadGroup
import com.ivor.openstream.presentation.downloads.DownloadViewModel
import com.ivor.openstream.presentation.downloads.formatBytes

/** The two sections the remote can be in. A TV gets tabs, not a long scroll. */
private enum class TvDownloadsSection { IN_PROGRESS, LIBRARY }

/** Sentinel for the top bar's arm-then-confirm Delete all. */
private const val ALL_DOWNLOADS = "*all*"

/**
 * Downloads at 10 feet.
 *
 * Three things this screen refuses to be, because each one was a real failure on
 * a remote:
 *
 * - **No dialogs.** Deleting a download or a whole show arms an *inline*
 *   confirm: the row's trailing text becomes "Delete download?" and the OK
 *   button next to it commits, with Cancel beside it. An [androidx.compose
 *   .material3.AlertDialog] on TV is a modal the D-pad cannot reason about —
 *   focus lands nowhere visible and Back looks broken.
 * - **No Local files tab and no import.** `DownloadsUiState` carries imported
 *   files because a phone can hand the ViewModel SAF URIs from
 *   `rememberLauncherForActivityResult`. A TV has no document picker and no way
 *   to type a path, so those rows and that action are omitted entirely rather
 *   than shipped as a button that always fails.
 * - **No phone metrics.** Every size comes from [LocalTvDimens]; the phone
 *   build's 56 dp gutters and 72 dp rows are what made the old TV version feel
 *   like a giant phone screen with overlapping text.
 *
 * Focus: the remote lands on the first row of the current section, falling back
 * to the section chip when that section is empty, so *something* is always
 * highlighted the moment the screen opens.
 */
@Composable
fun TvDownloadsScreen(
    onPlay: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val dimens = LocalTvDimens.current

    // Local to the screen on purpose: the ViewModel's DownloadTab models the
    // phone's Downloads/Local-files switch, and Local files is not reachable
    // from a remote (no document picker). Keeping the section here means the
    // ViewModel's SAF half is simply never exercised on TV.
    var section by remember { mutableStateOf(TvDownloadsSection.IN_PROGRESS) }

    /** downloadId (or "group:<key>") of the row whose confirm is showing. */
    var pendingConfirm by remember { mutableStateOf<String?>(null) }

    val sectionRequester = remember { FocusRequester() }
    val firstRowRequester = remember { FocusRequester() }

    // Initial focus, mandatory. Requested after the state settles so the row
    // actually exists, and every request is wrapped: asking a FocusRequester to
    // focus before it is attached throws, and an empty section has no row at
    // all, so the fallback chip is the real landing spot.
    LaunchedEffect(section, state.isLoading) {
        if (state.isLoading) return@LaunchedEffect
        runCatching { firstRowRequester.requestFocus() }
            .recoverCatching { sectionRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TvTopBar(
            title = stringResource(R.string.dl_title),
            // Back is the nav rail and the hardware key: one rule, in
            // TvAppScaffold, rather than a second one that only some screens
            // implement.
            onBack = null,
            actions = {
                // Delete-all is the most destructive thing on the screen, so it
                // arms before it fires: one press asks "Delete all?", OK
                // commits, Cancel disarms. No dialog, no mis-click from a
                // remote held down on OK.
                if (pendingConfirm == ALL_DOWNLOADS) {
                    TvButton(onClick = {
                        viewModel.removeAll()
                        pendingConfirm = null
                    }) {
                        Text(
                            text = stringResource(R.string.action_ok),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    TvOutlinedButton(onClick = { pendingConfirm = null }) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    TvOutlinedButton(
                        onClick = { pendingConfirm = ALL_DOWNLOADS },
                        enabled = state.inProgress.isNotEmpty() || state.library.isNotEmpty()
                    ) {
                        Text(
                            text = stringResource(R.string.action_delete_all),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        )

        TvStorageSummary(
            storedBytes = state.storedBytes,
            freeBytes = state.freeBytes,
            completedCount = state.completedCount
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.listRowGap)
        ) {
            TvChoiceChip(
                selected = section == TvDownloadsSection.IN_PROGRESS,
                onClick = { section = TvDownloadsSection.IN_PROGRESS },
                label = stringResource(R.string.hist_filter_in_progress) +
                    " (${state.inProgress.size})",
                focusRequester = if (section == TvDownloadsSection.IN_PROGRESS) sectionRequester else null,
                leading = Icons.Default.Download
            )
            TvChoiceChip(
                selected = section == TvDownloadsSection.LIBRARY,
                onClick = { section = TvDownloadsSection.LIBRARY },
                label = stringResource(R.string.st_library) + " (${state.library.sumOf { it.items.size }})",
                focusRequester = if (section == TvDownloadsSection.LIBRARY) sectionRequester else null,
                leading = Icons.Default.CheckCircle
            )
        }

        if (state.isLoading) {
            TvLoading()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.gutter,
                end = dimens.gutter,
                top = dimens.rowGap,
                bottom = dimens.paddingLarge
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.listRowGap)
        ) {
            when (section) {
                TvDownloadsSection.IN_PROGRESS -> {
                    if (state.inProgress.isEmpty()) {
                        item(key = "in_progress_empty") {
                            TvEmptyMessage(stringResource(R.string.dl_nothing_yet))
                        }
                    }
                    items(state.inProgress, key = { "progress:${it.downloadId}" }) { download ->
                        TvInProgressItem(
                            download = download,
                            confirming = pendingConfirm == download.downloadId,
                            focusRequester = if (download.downloadId == state.inProgress.first().downloadId) {
                                firstRowRequester
                            } else {
                                null
                            },
                            onArmConfirm = { pendingConfirm = download.downloadId },
                            onDisarmConfirm = { pendingConfirm = null },
                            onCommitConfirm = {
                                viewModel.remove(download)
                                pendingConfirm = null
                            },
                            onPause = { viewModel.pause(download) },
                            onResume = { viewModel.resume(download) },
                            onRetry = { viewModel.retry(download) }
                        )
                    }
                }

                TvDownloadsSection.LIBRARY -> {
                    if (state.library.isEmpty()) {
                        item(key = "library_empty") {
                            TvEmptyMessage(stringResource(R.string.dl_nothing_finished))
                        }
                    }
                    // Groups are flat rows in one lazy list: a nested scrolling
                    // column per show would trap the D-pad.
                    state.library.forEachIndexed { groupIndex, group ->
                        val confirmKey = "group:${group.key}"
                        item(key = confirmKey) {
                            TvDownloadGroupHeader(
                                group = group,
                                confirming = pendingConfirm == confirmKey,
                                focusRequester = if (groupIndex == 0) firstRowRequester else null,
                                onArmConfirm = { pendingConfirm = confirmKey },
                                onDisarmConfirm = { pendingConfirm = null },
                                onCommitConfirm = {
                                    viewModel.removeGroup(group)
                                    pendingConfirm = null
                                }
                            )
                        }
                        items(group.items, key = { "${group.key}:${it.downloadId}" }) { item ->
                            TvLibraryItem(
                                download = item,
                                isMovie = group.isMovie,
                                onPlay = { onPlay(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One line telling the viewer what the downloads are costing them.
 *
 * Free space is the number people actually want on this screen; it is the reason
 * a download fails, so it sits under the title rather than three taps away in
 * settings.
 */
@Composable
private fun TvStorageSummary(
    storedBytes: Long,
    freeBytes: Long,
    completedCount: Int
) {
    val dimens = LocalTvDimens.current
    val storageLine = stringResource(R.string.dl_storage_free, formatBytes(freeBytes))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter)
    ) {
        Text(
            text = "${formatBytes(storedBytes)} $storageLine · $completedCount " +
                stringResource(R.string.dl_episodes_other, completedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(dimens.paddingSmall))
    }
}

/**
 * An active download: the row, a real progress bar, and pause/resume/cancel.
 *
 * The cancel path is the confirm pattern, not a dialog. Once armed the row's
 * trailing text asks the question out loud and two buttons appear: OK commits,
 * Cancel steps back. Nothing overlays anything, so focus is never in doubt.
 */
@Composable
private fun TvInProgressItem(
    download: DownloadEntity,
    confirming: Boolean,
    focusRequester: FocusRequester?,
    onArmConfirm: () -> Unit,
    onDisarmConfirm: () -> Unit,
    onCommitConfirm: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit
) {
    val dimens = LocalTvDimens.current
    val failed = download.status == DownloadStatus.FAILED
    val paused = download.status == DownloadStatus.PAUSED

    Column(verticalArrangement = Arrangement.spacedBy(dimens.paddingSmall / 2)) {
        TvListRow(
            // OK on the row does the contextual thing: retry a broken transfer,
            // otherwise toggle pause. Cancel is always explicit on the right.
            onClick = when {
                confirming -> onCommitConfirm
                failed -> onRetry
                paused -> onResume
                else -> onPause
            },
            focusRequester = focusRequester,
            title = download.displayTitle,
            supporting = download.supportingLine(),
            leading = when {
                failed -> Icons.Default.Refresh
                paused -> Icons.Default.Pause
                else -> Icons.Default.Download
            },
            trailingText = if (confirming) stringResource(R.string.dl_delete_confirm) else null,
            trailing = {
                if (confirming) {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)) {
                        TvButton(onClick = onCommitConfirm) {
                            Text(
                                text = stringResource(R.string.action_ok),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        TvOutlinedButton(onClick = onDisarmConfirm) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)) {
                        TvOutlinedButton(onClick = if (failed) onRetry else if (paused) onResume else onPause) {
                            Text(
                                text = stringResource(
                                    when {
                                        failed -> R.string.action_retry
                                        paused -> R.string.cd_resume
                                        else -> R.string.cd_pause
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        TvIconButton(onClick = onArmConfirm) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete_download)
                            )
                        }
                    }
                }
            }
        )

        // The bar is decoration on the row, not a second focus target: a
        // separate stop here would swallow the right-arrow that is meant to
        // reach Pause and Cancel.
        LinearProgressIndicator(
            progress = { (download.progress / 100f).coerceIn(0f, 1f) },
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.paddingSmall / 2)
        )
    }
}

/** A show or movie in the library: its header, then its episodes. */
@Composable
private fun TvDownloadGroupHeader(
    group: DownloadGroup,
    confirming: Boolean,
    focusRequester: FocusRequester?,
    onArmConfirm: () -> Unit,
    onDisarmConfirm: () -> Unit,
    onCommitConfirm: () -> Unit
) {
    val dimens = LocalTvDimens.current
    val episodes = if (group.items.size == 1) {
        stringResource(R.string.dl_episode_one)
    } else {
        stringResource(R.string.dl_episodes_other, group.items.size)
    }

    TvListRow(
        onClick = if (confirming) onCommitConfirm else onArmConfirm,
        focusRequester = focusRequester,
        title = group.title,
        supporting = stringResource(
            R.string.dl_episodes_size,
            episodes,
            formatBytes(group.totalBytes)
        ),
        leading = if (group.isMovie) Icons.Default.CheckCircle else Icons.Default.Download,
        trailingText = if (confirming) stringResource(R.string.action_delete_all) else null,
        trailing = {
            if (confirming) {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)) {
                    TvButton(onClick = onCommitConfirm) {
                        Text(
                            text = stringResource(R.string.action_ok),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    TvOutlinedButton(onClick = onDisarmConfirm) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else {
                TvOutlinedButton(onClick = onArmConfirm) {
                    Text(
                        text = stringResource(R.string.action_remove),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    )
}

/** One downloadable episode or movie. OK plays it; it is already on the device. */
@Composable
private fun TvLibraryItem(
    download: DownloadEntity,
    isMovie: Boolean,
    onPlay: () -> Unit
) {
    TvListRow(
        onClick = onPlay,
        title = download.tvEpisodeLabel(),
        supporting = download.supportingLine(),
        leading = Icons.Default.PlayArrow,
        trailingText = stringResource(R.string.cd_downloaded)
    )
}

/** "S2 E4 · The One" / "The Movie · 1.4 GB · stored on this device". */
private fun DownloadEntity.tvEpisodeLabel(): String {
    val base = if (mediaType == "movie") title else "S$season E$episode"
    val detail = episodeTitle?.takeIf { it.isNotBlank() && it != base }
    return if (detail != null) "$base · $detail" else base
}

@Composable
private fun DownloadEntity.supportingLine(): String {
    val size = sizeBytes.takeIf { it > 0 }?.let { " · ${formatBytes(it)}" }.orEmpty()
    val status = when (status) {
        DownloadStatus.RESOLVING -> stringResource(R.string.dl_finding_source)
        DownloadStatus.QUEUED -> stringResource(R.string.dl_queued)
        DownloadStatus.RUNNING -> if (totalBytes > 0) {
            stringResource(
                R.string.dl_progress_full,
                progress,
                formatBytes(downloadedBytes),
                formatBytes(totalBytes)
            )
        } else {
            stringResource(R.string.dl_progress_short, progress, formatBytes(downloadedBytes))
        }
        DownloadStatus.PAUSED -> stringResource(R.string.dl_paused_percent, progress)
        DownloadStatus.FAILED -> errorMessage?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.dl_failed_retry)
        else -> stringResource(R.string.dl_stored_device)
    }
    return "$status$size"
}

/** Empty state: a sentence and a way out, both readable from the couch. */
@Composable
internal fun TvEmptyMessage(text: String, modifier: Modifier = Modifier) {
    val dimens = LocalTvDimens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.paddingLarge),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}