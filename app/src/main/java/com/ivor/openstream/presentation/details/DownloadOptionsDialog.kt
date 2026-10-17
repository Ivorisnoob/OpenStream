package com.ivor.openstream.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.R
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.presentation.player.sourceSummary
import com.ivor.openstream.ui.theme.ExpressiveShapes

/**
 * Long-press download picker: every resolved source with its provider and quality, so the user
 * picks exactly where an episode/movie downloads from. Season downloads prefer the tapped row's
 * provider and quality for each episode.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadOptionsSheet(
    state: DownloadOptionsUiState,
    onDismiss: () -> Unit,
    onDownload: (VideoServer) -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val episodeKey = state.episodes.firstOrNull()?.id ?: 0
    var qualityFilter by rememberSaveable(episodeKey) { mutableStateOf<String?>(null) }
    val qualities = state.servers.map { it.quality }.distinct()
        .sortedWith(compareByDescending<StreamQuality> { it.rank }.thenBy { it.label })
    if (qualityFilter != null && qualities.none { it.name == qualityFilter }) {
        qualityFilter = null
    }
    val visible = state.servers.filter {
        qualityFilter == null || it.quality.name == qualityFilter
    }
    val subtitle = when {
        state.isBatch -> pluralStringResource(
            R.plurals.dl_batch_count,
            state.episodes.size,
            state.episodes.size
        ).let { "${state.title} · $it" }
        state.episodeLabel != null -> "${state.title} · ${state.episodeLabel}"
        else -> state.title
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dl_pick_source_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
            }

            item(key = "hint") {
                Text(
                    text = stringResource(
                        if (state.isBatch) R.string.dl_pick_batch_hint else R.string.dl_pick_source_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.isLoading) {
                item(key = "loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = if (state.totalProviders > 0) {
                                stringResource(
                                    R.string.src_progress,
                                    state.servers.size,
                                    state.completedProviders,
                                    state.totalProviders
                                )
                            } else {
                                stringResource(R.string.player_checking_sources)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (qualities.size > 1) {
                item(key = "filters") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = qualityFilter == null,
                                onClick = { qualityFilter = null },
                                label = { Text(stringResource(R.string.search_filter_all)) },
                                shape = ExpressiveShapes.small
                            )
                        }
                        items(qualities, key = { it.name }) { quality ->
                            FilterChip(
                                selected = qualityFilter == quality.name,
                                onClick = { qualityFilter = quality.name },
                                label = {
                                    Text(
                                        if (quality == StreamQuality.UNKNOWN) {
                                            stringResource(R.string.src_adaptive)
                                        } else {
                                            quality.label
                                        }
                                    )
                                },
                                shape = ExpressiveShapes.small
                            )
                        }
                    }
                }
            }

            if (visible.isNotEmpty()) {
                itemsIndexed(visible, key = { _, server -> server.id }) { index, server ->
                    SegmentedListItem(
                        onClick = { onDownload(server) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = visible.size),
                        colors = ListItemDefaults.segmentedColors(),
                        supportingContent = {
                            Text(
                                "${server.providerName} · ${server.sourceSummary(context)}",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null
                            )
                        }
                    ) {
                        Text(
                            server.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (state.servers.isNotEmpty()) {
                item(key = "no-quality-match") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dl_no_quality_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { qualityFilter = null }) {
                            Text(stringResource(R.string.action_show_all))
                        }
                    }
                }
            } else if (state.isComplete) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dl_no_source),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.action_search_again))
                        }
                    }
                }
            }
        }
    }
}
