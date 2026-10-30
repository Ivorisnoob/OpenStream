package com.ivor.openstream.presentation.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.R
import com.ivor.openstream.data.images.TmdbImages
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.repository.UpcomingItem
import com.ivor.openstream.data.repository.UpcomingKind
import com.ivor.openstream.presentation.components.CenteredListBox
import com.ivor.openstream.presentation.components.ConnectedChoiceGroup
import com.ivor.openstream.presentation.components.ExpressiveMotion
import com.ivor.openstream.presentation.components.LibraryHeader
import com.ivor.openstream.presentation.components.bottomContentPadding
import com.ivor.openstream.ui.theme.ExpressiveShapes
import com.ivor.openstream.ui.theme.onScrim
import java.time.LocalDate

/**
 * What is coming from the titles the user follows: the next release as a banner, two weeks as a
 * strip of days, then the agenda. Anything out and unwatched sits on top and plays straight away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit,
    onOpenDetails: (id: Int, mediaType: String) -> Unit,
    onPlay: (UpcomingItem) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sections = state.sections
    val listState = rememberLazyListState()
    // Header, filters, strip, then the banner and the partial-failure note when they show.
    val nextUp = state.nextUp
    val showPartialFailure = state.failedCount > 0 && !state.loadFailed
    val leadingItems = 3 + (if (nextUp != null) 1 else 0) + (if (showPartialFailure) 1 else 0)

    LaunchedEffect(state.selectedDay) {
        val day = state.selectedDay ?: return@LaunchedEffect
        var index = leadingItems
        for (section in sections) {
            if (section.day == day) {
                listState.animateScrollToItem(index)
                break
            }
            index += section.items.size + 1
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            CenteredListBox(modifier = Modifier.fillMaxSize()) { gutter ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = gutter, end = gutter, bottom = bottomContentPadding)
                ) {
                    item(key = "header") {
                        LibraryHeader(
                            title = stringResource(R.string.calendar_title),
                            subtitle = null,
                            onBackClick = onBackClick
                        )
                    }
                    item(key = "filters") {
                        ConnectedChoiceGroup(
                            options = CalendarFilter.entries,
                            selected = state.filter,
                            label = { stringResource(it.labelRes) },
                            onSelect = viewModel::setFilter,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    if (nextUp != null) {
                        item(key = "next") {
                            NextUpBanner(
                                item = nextUp,
                                today = state.today,
                                onClick = { onOpenDetails(nextUp.tmdbId, nextUp.mediaType) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    item(key = "strip") {
                        if (state.items.isNotEmpty()) {
                            DayStrip(
                                days = state.stripDays,
                                today = state.today,
                                selected = state.selectedDay,
                                counts = state.countsByDay,
                                onSelect = { viewModel.selectDay(if (it == state.selectedDay) null else it) }
                            )
                        }
                    }

                    if (showPartialFailure) {
                        item(key = "partial") {
                            Text(
                                text = stringResource(R.string.calendar_some_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    sections.forEach { section ->
                        item(key = "day:${section.day}") {
                            SectionHeading(
                                text = section.day?.let { CalendarFormat.dayHeading(it, state.today) }
                                    ?: stringResource(R.string.calendar_out_now)
                            )
                        }
                        items(section.items, key = { it.key }) { item ->
                            AgendaRow(
                                item = item,
                                today = state.today,
                                reminded = item.tmdbId in state.remindedMovies,
                                onOpen = { onOpenDetails(item.tmdbId, item.mediaType) },
                                onPlay = { onPlay(item) },
                                onToggleReminder = { viewModel.toggleMovieReminder(item) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (!state.isLoading && sections.isEmpty()) {
                        item(key = "empty") {
                            EmptyCalendar(
                                state = state,
                                onRetry = viewModel::retry,
                                onOpenSuggestion = { onOpenDetails(it.id, "tv") }
                            )
                        }
                    }
                }
            }
        }
        if (state.isLoading) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
            .semantics { heading() }
    )
}

@Composable
private fun episodeLine(item: UpcomingItem): String? {
    val code = item.season?.let { season -> stringResource(R.string.li_episode_code, season, item.episode ?: 0) }
    return listOfNotNull(code, item.episodeName).joinToString("  ·  ").ifEmpty { null }
}

@Composable
private fun NextUpBanner(item: UpcomingItem, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val line = episodeLine(item)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .aspectRatio(16f / 9f)
            .clip(ExpressiveShapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = TmdbImages.url("w780", item.backdropPath ?: item.stillPath ?: item.posterPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.3f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f)
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = ExpressiveShapes.small,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = stringResource(R.string.calendar_next_up) + "  ·  " + CalendarFormat.relative(item.airDate, today),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onScrim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (line != null) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onScrim.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayStrip(
    days: List<LocalDate>,
    today: LocalDate,
    selected: LocalDate?,
    counts: Map<LocalDate, Int>,
    onSelect: (LocalDate) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(days, key = { it.toString() }) { day ->
            val isSelected = day == selected
            val count = counts[day] ?: 0
            val colors = MaterialTheme.colorScheme
            val background by animateColorAsState(
                when {
                    isSelected -> colors.primary
                    day == today -> colors.secondaryContainer
                    else -> Color.Transparent
                },
                label = "dayBackground"
            )
            val pop by animateFloatAsState(
                if (isSelected) 1f else 0.9f,
                ExpressiveMotion.finiteEffectsSpec(),
                label = "dayPop"
            )
            val foreground = when {
                isSelected -> colors.onPrimary
                day == today -> colors.onSecondaryContainer
                else -> colors.onSurface
            }
            val description = stringResource(R.string.calendar_day_cd, CalendarFormat.fullDate(day), count)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(52.dp)
                    .clip(ExpressiveShapes.medium)
                    .clickable(enabled = count > 0) { onSelect(day) }
                    .semantics {
                        contentDescription = description
                        this.selected = isSelected
                    }
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = CalendarFormat.weekday(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .scale(pop)
                            .background(
                                background,
                                if (isSelected) MaterialShapes.Cookie9Sided.toShape() else CircleShape
                            )
                    )
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = foreground
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Up to three dots: enough to tell a busy day from a quiet one.
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(5.dp)) {
                    repeat(count.coerceAtMost(3)) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .background(colors.primary, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(
    item: UpcomingItem,
    today: LocalDate,
    reminded: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onToggleReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val released = !item.airDate.isAfter(today)
    val isNew = released && item.airDate.isAfter(today.minusDays(NEW_BADGE_DAYS))
    val line = episodeLine(item)
    val isMovie = item.kind == UpcomingKind.MOVIE
    Surface(
        onClick = onOpen,
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = TmdbImages.url("w300", item.stillPath ?: item.backdropPath ?: item.posterPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 112.dp, height = 64.dp)
                    .clip(ExpressiveShapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (line != null) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (isNew) stringResource(R.string.calendar_new_badge) else CalendarFormat.relative(item.airDate, today),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal,
                    color = if (isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                released && !isMovie -> FilledIconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.calendar_watch_now_cd, item.title)
                    )
                }
                isMovie && !released -> FilledTonalIconToggleButton(
                    checked = reminded,
                    onCheckedChange = { onToggleReminder() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (reminded) Icons.Default.Notifications else Icons.Outlined.NotificationsNone,
                        contentDescription = stringResource(if (reminded) R.string.remind_on else R.string.remind_me)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCalendar(state: CalendarUiState, onRetry: () -> Unit, onOpenSuggestion: (AnimeDto) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(
                when {
                    state.loadFailed -> R.string.calendar_error
                    state.followedCount == 0 -> R.string.calendar_empty_title
                    state.items.isNotEmpty() -> R.string.calendar_nothing_filter
                    else -> R.string.calendar_nothing_scheduled
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        if (state.followedCount == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.calendar_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
        if (state.loadFailed) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
        if (state.suggestions.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth()) {
                SectionHeading(stringResource(R.string.calendar_suggestions))
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                items(state.suggestions, key = { it.id }) { show ->
                    AsyncImage(
                        model = TmdbImages.url("w342", show.posterPath),
                        contentDescription = show.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(112.dp)
                            .aspectRatio(0.68f)
                            .clip(ExpressiveShapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onOpenSuggestion(show) }
                    )
                }
            }
        }
    }
}

private const val NEW_BADGE_DAYS = 3L
