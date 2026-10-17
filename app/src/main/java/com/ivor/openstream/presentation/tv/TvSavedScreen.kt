package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.presentation.watch_later.SavedFilter
import com.ivor.openstream.presentation.watch_later.SavedSort
import com.ivor.openstream.presentation.watch_later.WatchLaterViewModel

private const val POSTER_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * The 10-foot Saved screen.
 *
 * Filter and sort are chips under the title (a remote has neither a search field
 * nor a dropdown), and every title is a [TvListRow] — OK opens its details. The
 * phone's long-press-to-remove has no remote equivalent, so removal lives where
 * the rest of a TV user's decisions live: the bookmark button on the details
 * screen un-saves the same title.
 *
 * No sheets, no dialogs, no touch gestures: only focus stops with rings.
 */
@Composable
fun TvSavedScreen(
    onOpenTitle: (id: Int, mediaType: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchLaterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val dimens = LocalTvDimens.current
    val firstChipRequester = remember { FocusRequester() }

    // Initial focus, mandatory. Wrapped because an empty library has no chips
    // with content worth focusing and a request before attachment throws.
    LaunchedEffect(Unit) { runCatching { firstChipRequester.requestFocus() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TvTopBar(
            title = stringResource(R.string.nav_saved),
            onBack = null,
            actions = {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        if (state.isLoading) {
            TvLoading()
            return@Column
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
        ) {
            items(SavedFilter.entries, key = { it.name }) { filter ->
                TvChoiceChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.onFilterChange(filter) },
                    label = stringResource(filter.labelRes),
                    focusRequester = if (filter == SavedFilter.entries.first()) {
                        firstChipRequester
                    } else {
                        null
                    }
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.paddingSmall, start = dimens.gutter, end = dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
        ) {
            items(SavedSort.entries, key = { it.name }) { sort ->
                TvChoiceChip(
                    selected = state.sort == sort,
                    onClick = { viewModel.onSortChange(sort) },
                    label = stringResource(sort.labelRes)
                )
            }
        }

        if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(R.string.li_nothing_saved), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(
                            if (state.totalCount == 0) R.string.li_tap_list_hint else R.string.li_nothing_saved_filter
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.gutter,
                end = dimens.gutter,
                top = dimens.rowGap,
                bottom = dimens.gutter
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.listRowGap)
        ) {
            items(state.items, key = { "${it.entry.id}:${it.entry.mediaType}" }) { item ->
                TvSavedRow(
                    entry = item.entry,
                    progressLine = if (item.latest != null) {
                        stringResource(R.string.li_episode_code, item.latest.season, item.latest.episode)
                    } else {
                        null
                    },
                    focusRequester = null,
                    onClick = { onOpenTitle(item.entry.id, item.entry.mediaType) }
                )
            }
        }
    }
}

/** One saved title: a poster thumbnail, the name, and where you left off. */
@Composable
private fun TvSavedRow(
    entry: WatchLaterEntity,
    progressLine: String?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val dimens = LocalTvDimens.current
    val thumbHeight = dimens.listRowHeight - dimens.paddingSmall * 2

    TvListRow(
        onClick = onClick,
        focusRequester = focusRequester,
        title = entry.title,
        subtitle = when {
            entry.mediaType == "movie" -> stringResource(R.string.li_saved_movies)
            else -> stringResource(R.string.li_saved_series)
        }.let { type -> if (progressLine != null) "$type · $progressLine" else type },
        leading = {
            AsyncImage(
                model = entry.posterPath?.let { "$POSTER_BASE$it" },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(thumbHeight * 2f / 3f)
                    .height(thumbHeight)
                    .clip(MaterialTheme.shapes.small)
            )
        },
        trailing = if (entry.voteAverage > 0.0) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dimens.minTarget * 0.5f)
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", entry.voteAverage),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    )
}
