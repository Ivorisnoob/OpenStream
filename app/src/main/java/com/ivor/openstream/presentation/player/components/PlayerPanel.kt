@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.ivor.openstream.presentation.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.ui.theme.ExpressiveShapes

/**
 * The shared surface for player sheets (settings, sources). Inline, it is a bottom sheet below the
 * video. In fullscreen it is a side panel drawn inside the player: a sheet or dialog opens its own
 * window, which would bring the hidden system bars back and break immersive mode.
 *
 * [content] receives the modifier its scrolling list should use (fill the panel, or cap the sheet)
 * and a close action, which is null for the sheet since it already has a drag handle.
 */
@Composable
fun PlayerPanelHost(
    visible: Boolean,
    isFullscreen: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(listModifier: Modifier, onClose: (() -> Unit)?) -> Unit
) {
    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(400.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
                            )
                            .padding(top = 12.dp)
                    ) {
                        content(Modifier.weight(1f), onDismiss)
                    }
                }
            }
            BackHandler(enabled = visible, onBack = onDismiss)
        }
    } else if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                content(Modifier.heightIn(max = 480.dp), null)
            }
        }
    }
}

@Composable
fun PanelHeader(
    title: String,
    onClose: (() -> Unit)?,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack == null) 24.dp else 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        onBack?.let {
            IconButton(onClick = it) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
        onClose?.let {
            IconButton(onClick = it) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }
}

/** Leading icon tile used by panel rows. */
@Composable
fun PanelIcon(
    icon: ImageVector,
    busy: Boolean = false,
    highlighted: Boolean = false
) {
    Surface(
        shape = ExpressiveShapes.medium,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun PanelNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Surface(
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (busy) {
                LoadingIndicator(modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            actionLabel?.let {
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = onAction) { Text(it) }
            }
        }
    }
}
