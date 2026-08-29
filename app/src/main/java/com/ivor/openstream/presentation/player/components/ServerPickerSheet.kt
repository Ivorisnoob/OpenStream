package com.ivor.openstream.presentation.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.presentation.player.ServersState
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
    val servers = when (state) {
        is ServersState.Resolving -> state.servers
        is ServersState.Ready -> state.servers
        else -> emptyList()
    }
    val activeId = when (state) {
        is ServersState.Resolving -> state.activeId
        is ServersState.Ready -> state.activeId
        else -> null
    }
    val failedProviders = when (state) {
        is ServersState.Resolving -> state.failedProviders
        is ServersState.Ready -> state.failedProviders
        is ServersState.Empty -> state.failedProviders
        ServersState.Idle -> emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Servers",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${servers.size} sources",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = ExpressiveShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Auto · Best available", fontWeight = FontWeight.Bold)
                        Text(
                            "Quality first, then audio and provider health",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state is ServersState.Resolving) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Searching sources… ${state.completedProviders} of ${state.totalProviders} checked",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (servers.isEmpty() && state is ServersState.Empty) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "No servers responded",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "The available sources may be offline. Try the race again.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onRetry, shape = ExpressiveShapes.medium) {
                        Text("Retry sources")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(servers, key = VideoServer::id) { server ->
                        val selected = server.id == activeId
                        ListItem(
                            headlineContent = {
                                Text(
                                    "${server.providerName} · ${server.name}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QualityBadge(server.quality)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        listOf(server.audio.label, server.streamType)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                    )
                                }
                            },
                            leadingContent = {
                                Surface(
                                    shape = if (selected) ExpressiveShapes.extraLarge else ExpressiveShapes.small,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (server.isDownloadable) {
                                        IconButton(onClick = { onDownload(server) }) {
                                            Icon(Icons.Default.Download, contentDescription = "Download from ${server.name}")
                                        }
                                    }
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Active server",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                                } else {
                                    Color.Transparent
                                }
                            ),
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .clip(ExpressiveShapes.medium)
                                .clickable { onSelect(server.id) }
                        )
                    }
                    if (failedProviders.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(16.dp))
                            Text(
                                text = "Unavailable: ${failedProviders.distinct().joinToString()}",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun QualityBadge(quality: StreamQuality) {
    val featured = quality.rank >= StreamQuality.Q1080.rank
    Surface(
        shape = ExpressiveShapes.extraSmall,
        color = if (featured) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Text(
            text = quality.label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
