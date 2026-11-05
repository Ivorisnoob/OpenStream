package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import com.ivor.openstream.R

/** What the TV player shows while it looks for a stream. */
sealed interface TvPlayerLoadingStatus {
    /** Still searching: [found] streams so far, [checked] of [total] sources answered. */
    data class Searching(val found: Int, val checked: Int, val total: Int) : TvPlayerLoadingStatus
    data object Preparing : TvPlayerLoadingStatus
    data object OpeningOffline : TvPlayerLoadingStatus
    /** Streams were found but none started on its own. */
    data object ChooseSource : TvPlayerLoadingStatus
    data object NothingFound : TvPlayerLoadingStatus
}

/**
 * The TV player before the video starts: the episode's picture full screen, what is about to play
 * in the lower left like Details, and the search progress under it. When the search needs the
 * viewer (nothing found, or a source to pick) the one button that helps takes focus.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvPlayerLoading(
    showName: String?,
    title: String,
    meta: String,
    overview: String?,
    imageUrl: String?,
    status: TvPlayerLoadingStatus,
    onChooseSource: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TvBackdrop(url = imageUrl)
        TvBackdropScrims()

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = TvLayout.PageStart, bottom = 48.dp)
                .width(560.dp)
        ) {
            if (!showName.isNullOrBlank()) {
                Text(
                    text = showName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!overview.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(28.dp))
            StatusBlock(status = status, onChooseSource = onChooseSource, onRetry = onRetry)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusBlock(
    status: TvPlayerLoadingStatus,
    onChooseSource: () -> Unit,
    onRetry: () -> Unit
) {
    val message = when (status) {
        is TvPlayerLoadingStatus.Searching ->
            stringResource(R.string.src_progress, status.found, status.checked, status.total)
        TvPlayerLoadingStatus.Preparing -> stringResource(R.string.player_preparing_sources)
        TvPlayerLoadingStatus.OpeningOffline -> stringResource(R.string.player_opening_offline)
        TvPlayerLoadingStatus.ChooseSource -> stringResource(R.string.player_choose_server)
        TvPlayerLoadingStatus.NothingFound -> stringResource(R.string.player_no_servers)
    }
    val needsViewer = status == TvPlayerLoadingStatus.ChooseSource || status == TvPlayerLoadingStatus.NothingFound

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!needsViewer) {
            LoadingIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    if (status is TvPlayerLoadingStatus.Searching && status.total > 0) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { status.checked.toFloat() / status.total },
            modifier = Modifier
                .width(320.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
            drawStopIndicator = {}
        )
    }
    if (needsViewer) {
        val requester = remember { FocusRequester() }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = if (status == TvPlayerLoadingStatus.NothingFound) onRetry else onChooseSource,
            modifier = Modifier.focusRequester(requester),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(
                if (status == TvPlayerLoadingStatus.NothingFound) Icons.Default.Refresh else Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            androidx.tv.material3.Text(
                stringResource(
                    if (status == TvPlayerLoadingStatus.NothingFound) R.string.action_retry_sources
                    else R.string.player_choose_source
                )
            )
        }
        LaunchedEffect(status) { runCatching { requester.requestFocus() } }
    }
}
