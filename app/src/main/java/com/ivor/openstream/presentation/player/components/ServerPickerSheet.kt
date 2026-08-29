package com.ivor.openstream.presentation.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.presentation.player.ServersState
import com.ivor.openstream.presentation.player.sourceQualityLabel
import com.ivor.openstream.presentation.player.sourceSummary
import com.ivor.openstream.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerPickerSheet(
    state: ServersState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onDownload: (VideoServer) -> Unit
) {
    val servers = state.availableServers
    val activeId = state.selectedServerId
    val activeServer = servers.firstOrNull { it.id == activeId }
    val failedProviders = state.unavailableProviders
    val qualityFilters = remember(servers) {
        servers
            .map { it.quality.filterLabel() }
            .distinct()
            .sortedWith(compareByDescending<String> { qualityRank(it) }.thenBy { it })
    }
    var selectedQuality by rememberSaveable(qualityFilters) { mutableStateOf<String?>(null) }
    val visibleServers = remember(servers, selectedQuality) {
        selectedQuality?.let { filter ->
            servers.filter { it.quality.filterLabel() == filter }
        } ?: servers
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playback source",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = when (state) {
                            is ServersState.Resolving ->
                                "${servers.size} ready · checked ${state.completedProviders} of ${state.totalProviders}"
                            is ServersState.Ready ->
                                "${servers.size} available${failedProviders.takeIf { it.isNotEmpty() }?.let { " · ${it.size} unavailable" }.orEmpty()}"
                            is ServersState.Empty -> "Sources are temporarily unavailable"
                            ServersState.Idle -> "Waiting to search"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state !is ServersState.Idle) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Search sources again")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (activeServer != null) {
                ActiveSourceCard(activeServer)
            } else if (state is ServersState.Resolving) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = ExpressiveShapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Finding the best route", fontWeight = FontWeight.Bold)
                            Text(
                                "Results appear as soon as each source responds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
                            )
                        }
                    }
                }
            }

            if (qualityFilters.size > 1) {
                Text(
                    text = "Filter by source quality",
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedQuality == null,
                            onClick = { selectedQuality = null },
                            label = { Text("All") }
                        )
                    }
                    items(qualityFilters, key = { it }) { quality ->
                        FilterChip(
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = quality },
                            label = { Text(quality) }
                        )
                    }
                }
            }

            when {
                servers.isEmpty() && state is ServersState.Empty -> EmptySources(onRetry)
                servers.isEmpty() -> Spacer(Modifier.height(24.dp))
                else -> {
                    Text(
                        text = selectedQuality?.let { "$it routes" } ?: "Available routes",
                        modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    ) {
                        items(visibleServers, key = VideoServer::id) { server ->
                            SourceCard(
                                server = server,
                                selected = server.id == activeId,
                                onSelect = { onSelect(server.id) },
                                onDownload = { onDownload(server) }
                            )
                        }
                        if (failedProviders.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = ExpressiveShapes.small,
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f)
                                ) {
                                    Text(
                                        text = "Not responding: ${failedProviders.distinct().joinToString()}",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ServersState.availableServers: List<VideoServer>
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

@Composable
private fun ActiveSourceCard(server: VideoServer) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = ExpressiveShapes.medium, color = MaterialTheme.colorScheme.primary) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${server.providerName} · ${server.sourceSummary()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Automatic recovery is on",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)
                )
            }
            Icon(Icons.Default.CheckCircle, contentDescription = "Current source")
        }
    }
}

@Composable
private fun SourceCard(
    server: VideoServer,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.medium)
            .clickable(onClick = onSelect),
        shape = ExpressiveShapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = if (selected) ExpressiveShapes.extraLarge else ExpressiveShapes.small,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    server.providerName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaPill(server.sourceQualityLabel(), featured = server.quality.rank >= StreamQuality.Q1080.rank)
                    if (server.audio.label.isNotBlank()) MetaPill(server.audio.label)
                    MetaPill(server.streamType)
                }
            }
            if (server.isDownloadable) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download from ${server.name}")
                }
            }
        }
    }
}

@Composable
private fun MetaPill(label: String, featured: Boolean = false) {
    Surface(
        shape = ExpressiveShapes.extraSmall,
        color = if (featured) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptySources(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "No source is ready",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            "Streaming hosts change often. Search again to request fresh links.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onRetry, shape = ExpressiveShapes.medium) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Search again")
        }
    }
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
