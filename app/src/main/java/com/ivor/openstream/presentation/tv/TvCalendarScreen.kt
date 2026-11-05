package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.R
import com.ivor.openstream.data.images.TmdbImages
import com.ivor.openstream.data.repository.UpcomingItem
import com.ivor.openstream.data.repository.UpcomingKind
import com.ivor.openstream.presentation.calendar.CalendarFilter
import com.ivor.openstream.presentation.calendar.CalendarFormat
import com.ivor.openstream.presentation.calendar.CalendarViewModel

/**
 * The calendar for a remote: a row per day under the filter pills. OK plays what is already out and
 * opens the title's page for what is not.
 */
@Composable
fun TvCalendarScreen(
    onOpenDetails: (id: Int, mediaType: String) -> Unit,
    onPlay: (UpcomingItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sections = state.sections
    val firstCard = remember { FocusRequester() }
    val firstPill = remember { FocusRequester() }

    LaunchedEffect(state.isLoading, state.filter) {
        if (state.isLoading) return@LaunchedEffect
        runCatching { firstCard.requestFocus() }.recoverCatching { firstPill.requestFocus() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            TvPageHeader(
                title = stringResource(R.string.calendar_title),
                supporting = state.nextUp?.let {
                    stringResource(R.string.calendar_next_up) + "  ·  " + it.title + "  ·  " +
                        CalendarFormat.relative(it.airDate, state.today)
                }
            )
            LazyRow(
                contentPadding = PaddingValues(start = TvLayout.ContentStart, end = TvLayout.ContentEnd, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(CalendarFilter.entries, key = { it.name }) { filter ->
                    TvPill(
                        label = stringResource(filter.labelRes),
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        modifier = if (filter == CalendarFilter.entries.first()) Modifier.focusRequester(firstPill) else Modifier
                    )
                }
            }
            when {
                state.isLoading -> TvLoading()
                sections.isEmpty() -> TvEmptyPage(
                    stringResource(
                        when {
                            state.loadFailed -> R.string.calendar_error
                            state.followedCount == 0 -> R.string.calendar_empty_body
                            state.items.isNotEmpty() -> R.string.calendar_nothing_filter
                            else -> R.string.calendar_nothing_scheduled
                        }
                    )
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 48.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    sections.forEachIndexed { sectionIndex, section ->
                        item(key = "day:${section.day}") {
                            TvRail(
                                title = section.day?.let { CalendarFormat.dayHeading(it, state.today) }
                                    ?: stringResource(R.string.calendar_out_now)
                            ) {
                                items(section.items, key = { it.key }) { item ->
                                    TvLandscapeCard(
                                        title = item.title,
                                        imageUrl = TmdbImages.url("w500", item.stillPath ?: item.backdropPath ?: item.posterPath),
                                        subtitle = item.season?.let { season ->
                                            stringResource(R.string.li_episode_code, season, item.episode ?: 0)
                                        },
                                        onClick = {
                                            val playable = item.kind != UpcomingKind.MOVIE && !item.airDate.isAfter(state.today)
                                            if (playable) onPlay(item) else onOpenDetails(item.tmdbId, item.mediaType)
                                        },
                                        modifier = if (sectionIndex == 0 && item == section.items.first()) {
                                            Modifier.focusRequester(firstCard)
                                        } else {
                                            Modifier
                                        }
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
