package com.ivor.openstream.presentation.marketplace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ivor.openstream.domain.model.ExtensionRepo
import com.ivor.openstream.domain.model.ExtensionStatus
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun ExtensionAvatar(
    extension: MarketplaceExtension,
    size: Int = 48
) {
    val manifest = extension.manifest
    Surface(
        modifier = Modifier.size(size.dp),
        shape = ExpressiveShapes.medium,
        color = if (extension.isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (extension.isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        if (manifest.iconUrl != null) {
            AsyncImage(
                model = manifest.iconUrl,
                contentDescription = null,
                modifier = Modifier.size(size.dp).clip(ExpressiveShapes.medium)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = manifest.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun StatusDot(status: ExtensionStatus) {
    Surface(
        modifier = Modifier.size(8.dp),
        shape = CircleShape,
        color = statusColor(status)
    ) {}
}

@Composable
fun statusColor(status: ExtensionStatus): Color = when (status) {
    ExtensionStatus.OK -> MaterialTheme.colorScheme.primary
    ExtensionStatus.SLOW -> MaterialTheme.colorScheme.tertiary
    ExtensionStatus.BETA -> MaterialTheme.colorScheme.secondary
    ExtensionStatus.DOWN -> MaterialTheme.colorScheme.error
}

/** A row in the browse and installed lists. */
@Composable
fun ExtensionRow(
    extension: MarketplaceExtension,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    rank: Int? = null
) {
    val manifest = extension.manifest
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.large)
            .clickable(onClick = onClick),
        shape = ExpressiveShapes.large,
        color = if (extension.isInstalled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (extension.hasUpdate) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f))
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rank != null) {
                    Text(
                        text = rank.toString(),
                        modifier = Modifier.width(24.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                ExtensionAvatar(extension)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = manifest.name,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (manifest.tags.contains("official")) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Official extension",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = extensionSubtitle(extension),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                ExtensionAction(
                    extension = extension,
                    onInstall = onInstall,
                    onUpdate = onUpdate
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = manifest.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(
                visible = extension.isInstalled,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                !manifest.isSupported -> "Needs a newer app version"
                                extension.isEnabled -> "Active in player"
                                else -> "Paused"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (extension.isEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Switch(
                            checked = extension.isEnabled,
                            onCheckedChange = onEnabledChange,
                            enabled = manifest.isSupported
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onUninstall) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionAction(
    extension: MarketplaceExtension,
    onInstall: () -> Unit,
    onUpdate: () -> Unit
) {
    when {
        extension.hasUpdate -> FilledTonalButton(onClick = onUpdate, shape = ExpressiveShapes.small) {
            Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Update")
        }

        extension.isInstalled -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Installed",
            tint = MaterialTheme.colorScheme.primary
        )

        !extension.manifest.isSupported -> Text(
            text = "Unsupported",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )

        else -> Button(onClick = onInstall, shape = ExpressiveShapes.small) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Install")
        }
    }
}

/** Compact "top charts" tile used in the browse rail. */
@Composable
fun ChartTile(
    rank: Int,
    extension: MarketplaceExtension,
    onClick: () -> Unit,
    onInstall: () -> Unit
) {
    val manifest = extension.manifest
    Surface(
        modifier = Modifier
            .width(212.dp)
            .clip(ExpressiveShapes.large)
            .clickable(onClick = onClick),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                StatusDot(manifest.status)
            }
            Spacer(Modifier.height(10.dp))
            ExtensionAvatar(extension, size = 40)
            Spacer(Modifier.height(10.dp))
            Text(
                text = manifest.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = manifest.language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (extension.isInstalled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Installed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Button(
                    onClick = onInstall,
                    shape = ExpressiveShapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Install")
                }
            }
        }
    }
}

@Composable
fun RepoCard(
    repo: ExtensionRepo,
    extensionCount: Int,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (repo.isBuiltIn) Icons.Default.Verified else Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = repo.url.substringAfter("://"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!repo.isBuiltIn) {
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("$extensionCount extensions") },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(lastSyncLabel(repo.lastSyncedAt)) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            if (repo.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = repo.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionDetailsSheet(
    extension: MarketplaceExtension,
    repoName: String,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val manifest = extension.manifest
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExtensionAvatar(extension, size = 64)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manifest.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${manifest.author} · v${manifest.versionName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBlock(label = "Status", value = manifest.status.label)
                StatBlock(label = "Language", value = manifest.language)
                StatBlock(
                    label = if (manifest.installs > 0) "Installs" else "Reliability",
                    value = if (manifest.installs > 0) {
                        formatCount(manifest.installs)
                    } else {
                        reliabilityLabel(extension)
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = manifest.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))
            DetailLine(label = "Repository", value = repoName)
            DetailLine(label = "Engine", value = manifest.engine.type.key)
            if (manifest.tags.isNotEmpty()) {
                DetailLine(label = "Tags", value = manifest.tags.joinToString(", "))
            }
            if (manifest.updatedAt > 0L) {
                DetailLine(label = "Updated", value = lastSyncLabel(manifest.updatedAt))
            }
            if (manifest.rating > 0f && manifest.ratingCount > 0) {
                DetailLine(
                    label = "Rating",
                    value = "${manifest.rating} ★ (${formatCount(manifest.ratingCount.toLong())})"
                )
            }

            if (extension.isInstalled) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Use when resolving streams",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = extension.isEnabled,
                        onCheckedChange = onEnabledChange,
                        enabled = manifest.isSupported
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    extension.hasUpdate -> Button(
                        onClick = onUpdate,
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveShapes.small
                    ) {
                        Icon(Icons.Default.Update, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Update to v${manifest.versionName}")
                    }

                    !extension.isInstalled -> Button(
                        onClick = onInstall,
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveShapes.small,
                        enabled = manifest.isSupported
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install")
                    }

                    else -> OutlinedButton(
                        onClick = onUninstall,
                        modifier = Modifier.weight(1f),
                        shape = ExpressiveShapes.small
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Remove")
                    }
                }
                if (manifest.homepage != null) {
                    OutlinedButton(
                        onClick = { uriHandler.openUri(manifest.homepage) },
                        shape = ExpressiveShapes.small
                    ) {
                        Text("Website")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Surface(
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun extensionSubtitle(extension: MarketplaceExtension): String {
    val manifest = extension.manifest
    val parts = mutableListOf(manifest.language, "v${manifest.versionName}")
    if (manifest.installs > 0) parts += "${formatCount(manifest.installs)} installs"
    extension.usage.successRate?.let { parts += "${(it * 100).roundToInt()}% success" }
    if (manifest.isFallback) parts += "fallback"
    return parts.joinToString(" · ")
}

fun reliabilityLabel(extension: MarketplaceExtension): String {
    val rate = extension.usage.successRate ?: return "No data yet"
    return "${(rate * 100).roundToInt()}%"
}

fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> "${value / 100_000 / 10.0}M"
    value >= 1_000 -> "${value / 100 / 10.0}k"
    else -> value.toString()
}

fun lastSyncLabel(timestamp: Long): String {
    if (timestamp <= 0L) return "Never synced"
    val elapsed = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        else -> "${days / 30}mo ago"
    }
}
