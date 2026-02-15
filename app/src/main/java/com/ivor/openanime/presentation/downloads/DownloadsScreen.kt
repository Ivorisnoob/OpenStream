package com.ivor.openanime.presentation.downloads

import android.app.DownloadManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownloadOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openanime.data.local.entity.DownloadEntity
import com.ivor.openanime.presentation.components.ExpressiveBackButton
import com.ivor.openanime.ui.theme.ExpressiveShapes

// Expressive Motion Tokens
private val ExpressiveDefaultSpatial = CubicBezierEasing(0.38f, 1.21f, 0.22f, 1.00f)
private val ExpressiveDefaultEffects = CubicBezierEasing(0.34f, 0.80f, 0.34f, 1.00f)

private const val DurationSpatialDefault = 500
private const val DurationEffectsDefault = 200

private fun materialSharedAxisYIn(): ContentTransform {
    return (slideInVertically(
                animationSpec = tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)
            ) { height -> height / 2 } + 
            fadeIn(
                animationSpec = tween(DurationEffectsDefault, delayMillis = 50, easing = ExpressiveDefaultEffects)
            ))
        .togetherWith(
            slideOutVertically(
                animationSpec = tween(DurationSpatialDefault, easing = ExpressiveDefaultSpatial)
            ) { height -> -height / 2 } + 
            fadeOut(
                animationSpec = tween(DurationEffectsDefault, easing = ExpressiveDefaultEffects)
            )
        )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: (DownloadEntity) -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Expressive Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveBackButton(onClick = onBackClick)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Downloads",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = downloads.isEmpty(),
                transitionSpec = { materialSharedAxisYIn() },
                modifier = Modifier.fillMaxSize(),
                label = "DownloadsContent"
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyDownloadsView()
                } else {
                    DownloadsListView(
                        downloads = downloads,
                        onDeleteClick = { viewModel.removeDownload(it) },
                        onItemClick = onDownloadClick
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDownloadsView() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FileDownloadOff,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No downloads yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Downloaded episodes will appear here",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DownloadsListView(
    downloads: List<DownloadEntity>,
    onDeleteClick: (String) -> Unit,
    onItemClick: (DownloadEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Active & Completed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(downloads, key = { it.downloadId }) { item ->
            DownloadItem(
                item = item,
                onDeleteClick = { onDeleteClick(item.downloadId) },
                onItemClick = { onItemClick(item) }
            )
        }
    }
}

@Composable
fun DownloadItem(
    item: DownloadEntity,
    onDeleteClick: () -> Unit,
    onItemClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onItemClick() },
        headlineContent = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                val statusText = when (item.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Downloaded"
                    DownloadManager.STATUS_FAILED -> "Download Failed"
                    DownloadManager.STATUS_RUNNING -> "Downloading... (${item.progress}%)"
                    DownloadManager.STATUS_PENDING -> "Waiting to start..."
                    DownloadManager.STATUS_PAUSED -> "Paused"
                    else -> "Processing..."
                }
                
                val sizeText = when {
                    item.totalBytes > 0 -> "${formatFileSize(item.downloadedBytes)} / ${formatFileSize(item.totalBytes)}"
                    item.downloadedBytes > 0 -> "${formatFileSize(item.downloadedBytes)} (Total unknown)"
                    else -> "Waiting..."
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.status == DownloadManager.STATUS_FAILED) 
                            MaterialTheme.colorScheme.error 
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (item.status != DownloadManager.STATUS_SUCCESSFUL && item.status != DownloadManager.STATUS_FAILED) {
                    val progressValue = item.progress / 100f
                    if (item.progress >= 0 && item.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { progressValue },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(6.dp)
                                .clip(MaterialTheme.shapes.extraSmall),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    } else {
                        // Indeterminate progress for HLS with unknown size
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(6.dp)
                                .clip(MaterialTheme.shapes.extraSmall),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }
        },
        leadingContent = {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w200${item.posterPath}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(80.dp)
                    .width(56.dp)
                    .clip(ExpressiveShapes.small)
            )
        },
        trailingContent = {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return java.text.DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

// Helper function removed as androidx.compose.foundation.layout.width is available
