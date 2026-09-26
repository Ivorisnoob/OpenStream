@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.ivor.openstream.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.presentation.player.ServersState
import com.ivor.openstream.presentation.player.sourceSummary
import com.ivor.openstream.ui.theme.ExpressiveShapes

/** What the Sources page needs: the live resolution state and what the user can do with it. */
class SourcesPageActions(
    val onSelect: (VideoServer) -> Unit,
    val onRetry: () -> Unit,
    val onDownload: (VideoServer) -> Unit
)

/**
 * Sources opened on their own, for when no stream is playing yet (or every server failed) and the
 * player's settings panel therefore does not exist. It shares the panel, header and rows with the
 * Sources page inside settings, so both look identical.
 */
@Composable
fun SourcesPanel(
    visible: Boolean,
    isFullscreen: Boolean,
    state: ServersState,
    actions: SourcesPageActions,
    onDismiss: () -> Unit
) {
    PlayerPanelHost(visible = visible, isFullscreen = isFullscreen, onDismiss = onDismiss) { listModifier, onClose ->
        val filter = rememberSourceFilter()
        PanelHeader(
            title = "Sources",
            subtitle = sourcesStatus(state),
            onClose = onClose,
            actions = { SourcesRefreshAction(state, actions.onRetry) }
        )
        LazyColumn(
            modifier = listModifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            sourcesPage(state, filter, actions)
        }
    }
}

@Composable
fun rememberSourceFilter(): MutableState<String?> = rememberSaveable { mutableStateOf(null) }

@Composable
fun SourcesRefreshAction(state: ServersState, onRetry: () -> Unit) {
    if (state !is ServersState.Idle) {
        IconButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = "Search sources again")
        }
    }
}

fun sourcesStatus(state: ServersState): String {
    val servers = state.availableServers
    val failed = state.unavailableProviders.distinct()
    return when (state) {
        is ServersState.Resolving ->
            "${servers.size} ready · checked ${state.completedProviders} of ${state.totalProviders}"
        is ServersState.Ready ->
            "${servers.size} available" + if (failed.isNotEmpty()) " · ${failed.size} not responding" else ""
        is ServersState.Empty -> "No source responded"
        ServersState.Idle -> "Waiting to search"
    }
}

/** The list itself, shared by the settings page and the standalone panel. */
fun LazyListScope.sourcesPage(
    state: ServersState,
    filter: MutableState<String?>,
    actions: SourcesPageActions
) {
    val servers = state.availableServers
    val activeId = state.selectedServerId
    val qualityFilters = servers
        .map { it.quality.filterLabel() }
        .distinct()
        .sortedWith(compareByDescending<String> { qualityRank(it) }.thenBy { it })
    val selectedFilter = filter.value?.takeIf { it in qualityFilters }
    val visibleServers = selectedFilter?.let { f -> servers.filter { it.quality.filterLabel() == f } } ?: servers

    if (state is ServersState.Resolving) {
        item(key = "resolving") {
            PanelNotice(
                title = "Checking sources",
                body = "Results appear as each source responds.",
                busy = true,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }

    if (servers.isEmpty() && state is ServersState.Empty) {
        item(key = "empty") {
            PanelNotice(
                title = "No source is ready",
                body = "Streaming hosts change often. Search again for fresh links.",
                actionLabel = "Search again",
                onAction = actions.onRetry
            )
        }
    }

    if (qualityFilters.size > 1) {
        item(key = "filters") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { filter.value = null },
                        label = { Text("All") },
                        shape = ExpressiveShapes.small
                    )
                }
                items(qualityFilters, key = { it }) { quality ->
                    FilterChip(
                        selected = selectedFilter == quality,
                        onClick = { filter.value = quality },
                        label = { Text(quality) },
                        shape = ExpressiveShapes.small
                    )
                }
            }
        }
    }

    itemsIndexed(visibleServers, key = { _, server -> server.id }) { index, server ->
        SourceRow(
            server = server,
            selected = server.id == activeId,
            index = index,
            count = visibleServers.size,
            actions = actions
        )
    }

    val failed = state.unavailableProviders.distinct()
    if (failed.isNotEmpty() && servers.isNotEmpty()) {
        item(key = "failed") {
            Text(
                text = "Not responding: ${failed.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SourceRow(
    server: VideoServer,
    selected: Boolean,
    index: Int,
    count: Int,
    actions: SourcesPageActions
) {
    SegmentedListItem(
        selected = selected,
        onClick = { actions.onSelect(server) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(),
        leadingContent = {
            PanelIcon(
                icon = if (selected) Icons.Default.CheckCircle else Icons.Default.PlayCircle,
                highlighted = selected
            )
        },
        supportingContent = {
            Text(
                "${server.providerName} · ${server.sourceSummary()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            if (server.isDownloadable) {
                IconButton(onClick = { actions.onDownload(server) }) {
                    Icon(Icons.Default.Download, contentDescription = "Download from ${server.name}")
                }
            }
        }
    ) {
        Text(
            server.name,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

val ServersState.availableServers: List<VideoServer>
    get() = when (this) {
        is ServersState.Resolving -> servers
        is ServersState.Ready -> servers
        else -> emptyList()
    }

private val ServersState.selectedServerId: String?
    get() = when (this) {
        is ServersState.Resolving -> activeId
        is ServersState.Ready -> activeId
        else -> null
    }

private val ServersState.unavailableProviders: List<String>
    get() = when (this) {
        is ServersState.Resolving -> failedProviders
        is ServersState.Ready -> failedProviders
        is ServersState.Empty -> failedProviders
        ServersState.Idle -> emptyList()
    }

private fun StreamQuality.filterLabel(): String = when (this) {
    StreamQuality.UNKNOWN -> "Adaptive"
    else -> label
}

private fun qualityRank(label: String): Int = when (label) {
    "4K" -> 6
    "1440p" -> 5
    "1080p" -> 4
    "HD" -> 3
    "720p" -> 2
    "480p" -> 1
    else -> 0
}
