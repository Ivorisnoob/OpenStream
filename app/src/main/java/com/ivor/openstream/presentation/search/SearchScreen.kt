package com.ivor.openstream.presentation.search

import com.ivor.openstream.presentation.components.bottomContentPadding
import com.ivor.openstream.presentation.components.isCompactWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import com.ivor.openstream.presentation.components.byWidth
import com.ivor.openstream.presentation.components.SkeletonBox
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.domain.model.BrowseGenre
import com.ivor.openstream.presentation.components.ChoiceChips
import com.ivor.openstream.presentation.components.ConnectedChoiceGroup
import com.ivor.openstream.presentation.components.LibraryEmptyState
import com.ivor.openstream.ui.theme.ExpressiveShapes
import java.util.Locale

private val FullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onAnimeClick: (Int, String) -> Unit,
    focusTrigger: Long = 0L,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val open: (AnimeDto) -> Unit = { onAnimeClick(it.id, if (it.isMovie) "movie" else "tv") }
    val results = state.visibleResults
    var showFilters by rememberSaveable { mutableStateOf(false) }

    if (showFilters) {
        FiltersSheet(
            filters = state.filters,
            matchCount = results.size,
            onChange = viewModel::onFiltersChange,
            onReset = viewModel::resetFilters,
            onDismiss = { showFilters = false }
        )
    }

    // Tapping the Search tab while already on it focuses the field.
    LaunchedEffect(focusTrigger) {
        if (focusTrigger > 0L) focusRequester.requestFocus()
    }

    // Infinite scroll: fetch the next page as the grid nears its end.
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, results.size) {
        if (nearEnd && results.isNotEmpty()) viewModel.loadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = byWidth(compact = 112.dp, medium = 128.dp, expanded = 140.dp)),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomContentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "header", span = FullWidth) {
            Column(modifier = Modifier.statusBarsPadding().padding(top = 20.dp)) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .padding(start = 4.dp, bottom = 16.dp)
                        .semantics { heading() }
                )
                TextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Movies, shows and anime") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty() || state.genre != null) {
                            IconButton(onClick = viewModel::clear) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    shape = ExpressiveShapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.submit()
                        focusManager.clearFocus()
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        // A phone-like field on tablets instead of a bar across the whole window.
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .focusRequester(focusRequester)
                )
            }
        }

        if (!state.isBrowsing) {
            item(key = "controls", span = FullWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.genre?.let { genre ->
                        InputChip(
                            selected = true,
                            onClick = viewModel::clear,
                            label = { Text(genre.label) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Stop browsing ${genre.label}", modifier = Modifier.size(18.dp)) },
                            shape = ExpressiveShapes.small
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChoiceChips(
                            options = if (state.genre?.isAnime == true) {
                                listOf(SearchFilter.ALL, SearchFilter.MOVIES, SearchFilter.SERIES)
                            } else {
                                SearchFilter.entries
                            },
                            selected = state.filter,
                            label = { it.label },
                            onSelect = viewModel::onFilterSelected,
                            modifier = Modifier.weight(1f)
                        )
                        SortMenu(state.sort, onSelect = viewModel::onSortSelected)
                    }
                    ActiveFiltersRow(
                        filters = state.filters,
                        onOpen = { showFilters = true },
                        onChange = viewModel::onFiltersChange
                    )
                }
            }
        }

        when {
            state.isBrowsing -> browseContent(
                state = state,
                onRecent = { query ->
                    viewModel.submit(query)
                    focusManager.clearFocus()
                },
                onRemoveRecent = viewModel::removeRecent,
                onClearRecent = viewModel::clearRecent,
                onOpen = open,
                onGenre = { genre ->
                    viewModel.selectGenre(genre)
                    focusManager.clearFocus()
                }
            )

            state.isLoading -> items(9, key = { "skeleton:$it" }) {
                Column {
                    SkeletonBox(modifier = Modifier.fillMaxWidth().aspectRatio(0.68f))
                    SkeletonBox(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(0.8f)
                            .heightIn(min = 12.dp),
                        shape = ExpressiveShapes.small
                    )
                }
            }

            state.error != null -> item(key = "error", span = FullWidth) {
                LibraryEmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "Search didn't go through",
                    body = "Check your connection and try again.",
                    action = { Button(onClick = viewModel::retry, shape = ExpressiveShapes.medium) { Text("Try again") } }
                )
            }

            results.isEmpty() && state.filters.activeCount > 0 && !state.isLoadingMore -> item(key = "no-filtered-results", span = FullWidth) {
                LibraryEmptyState(
                    icon = Icons.Default.FilterAltOff,
                    title = "Nothing matches these filters",
                    body = "Loosen the year, rating or language, or clear them to see everything.",
                    action = { Button(onClick = viewModel::resetFilters, shape = ExpressiveShapes.medium) { Text("Clear filters") } }
                )
            }

            results.isEmpty() && state.query.trim().length >= 2 -> item(key = "no-results", span = FullWidth) {
                LibraryEmptyState(
                    icon = Icons.Default.TravelExplore,
                    title = "No results for \"${state.query.trim()}\"",
                    body = if (state.filter != SearchFilter.ALL) {
                        "Try another filter, a different spelling, or browse by genre."
                    } else {
                        "Try a different spelling or the original title, or browse by genre."
                    }
                )
            }

            else -> {
                val showTopResult = state.genre == null && results.firstOrNull()?.backdropPath != null
                if (showTopResult) {
                    item(key = "top-result", span = FullWidth) { TopResultCard(results.first(), onClick = { open(results.first()) }) }
                }
                val grid = if (showTopResult) results.drop(1) else results
                itemsIndexed(grid, key = { _, item -> "result:${item.mediaType}:${item.id}" }) { _, item ->
                    ResultCard(item, onClick = { open(item) })
                }
                if (state.isLoadingMore) {
                    item(key = "loading-more", span = FullWidth) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.browseContent(
    state: SearchUiState,
    onRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onOpen: (AnimeDto) -> Unit,
    onGenre: (BrowseGenre) -> Unit
) {
    if (state.recent.isNotEmpty()) {
        item(key = "recent", span = FullWidth) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Recent searches", Modifier.weight(1f))
                    TextButton(onClick = onClearRecent) { Text("Clear") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recent.forEach { query ->
                        InputChip(
                            selected = false,
                            onClick = { onRecent(query) },
                            label = { Text(query) },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove $query",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onRemoveRecent(query) }
                                )
                            },
                            shape = ExpressiveShapes.small
                        )
                    }
                }
            }
        }
    }

    if (state.trending.isNotEmpty()) {
        item(key = "trending", span = FullWidth) {
            Column {
                SectionLabel("Trending now")
                // Tablets split the chart into two columns instead of one long, stretched list.
                val columns = byWidth(compact = 1, medium = 2)
                val perColumn = (state.trending.size + columns - 1) / columns
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.trending.chunked(perColumn).forEachIndexed { column, chunk ->
                        TrendingList(
                            items = chunk,
                            firstRank = column * perColumn + 1,
                            onOpen = onOpen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    item(key = "genres", span = FullWidth) {
        Column {
            SectionLabel("Browse by genre")
            val palette = listOf(
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            )
            val perRow = byWidth(compact = 2, medium = 3, expanded = 4)
            BrowseGenre.entries.chunked(perRow).forEachIndexed { row, pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    pair.forEachIndexed { column, genre ->
                        val (container, content) = palette[(row * perRow + column) % palette.size]
                        Surface(
                            onClick = { onGenre(genre) },
                            // Alternating corner sizes keep the grid from reading as a spreadsheet.
                            shape = if ((row + column) % 2 == 0) ExpressiveShapes.large else ExpressiveShapes.medium,
                            color = container,
                            contentColor = content,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 72.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(genre.icon(), contentDescription = null)
                                Text(
                                    text = genre.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                    repeat(perRow - pair.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrendingList(
    items: List<AnimeDto>,
    firstRank: Int,
    onOpen: (AnimeDto) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        items.forEachIndexed { index, item ->
            SegmentedListItem(
                onClick = { onOpen(item) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size),
                colors = ListItemDefaults.segmentedColors(),
                leadingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${firstRank + index}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(32.dp)
                        )
                        AsyncImage(
                            model = "https://image.tmdb.org/t/p/w154${item.posterPath}",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 40.dp, height = 58.dp)
                                .clip(ExpressiveShapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                },
                supportingContent = { Text(metaLine(item)) }
            ) {
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .padding(start = 4.dp, top = 12.dp, bottom = 10.dp)
            .semantics { heading() }
    )
}

/** "Filters" chip plus one removable chip per active filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFiltersRow(
    filters: ResultFilters,
    onOpen: () -> Unit,
    onChange: (ResultFilters) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "open") {
            FilterChip(
                selected = filters.activeCount > 0,
                onClick = onOpen,
                label = { Text(if (filters.activeCount > 0) "Filters · ${filters.activeCount}" else "Filters") },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = ExpressiveShapes.small
            )
        }
        if (filters.year != YearRange.ANY) {
            item(key = "year") {
                RemovableChip(filters.year.label, "year") { onChange(filters.copy(year = YearRange.ANY)) }
            }
        }
        if (filters.minRating != MinRating.ANY) {
            item(key = "rating") {
                RemovableChip("Rated ${filters.minRating.label}", "rating") { onChange(filters.copy(minRating = MinRating.ANY)) }
            }
        }
        filters.language?.let { code ->
            item(key = "language") {
                RemovableChip(languageName(code), "language") { onChange(filters.copy(language = null)) }
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, what: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove $what filter", modifier = Modifier.size(18.dp)) },
        shape = ExpressiveShapes.small
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(
    filters: ResultFilters,
    matchCount: Int,
    onChange: (ResultFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            FilterSection("Release year") {
                YearRange.entries.forEach { range ->
                    SheetChip(range.label, selected = filters.year == range) { onChange(filters.copy(year = range)) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Minimum rating",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() }
                )
                ConnectedChoiceGroup(
                    options = MinRating.entries,
                    selected = filters.minRating,
                    label = { if (it == MinRating.ANY) "Any" else "${it.label} ★" },
                    onSelect = { onChange(filters.copy(minRating = it)) }
                )
            }
            FilterSection("Original language") {
                SheetChip("Any", selected = filters.language == null) { onChange(filters.copy(language = null)) }
                FILTER_LANGUAGES.forEach { (code, name) ->
                    SheetChip(name, selected = filters.language == code) { onChange(filters.copy(language = code)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onReset, enabled = filters.activeCount > 0) { Text("Reset") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss, shape = ExpressiveShapes.medium) {
                    Text(if (matchCount == 1) "Show 1 result" else "Show $matchCount results")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        shape = ExpressiveShapes.small
    )
}

private fun languageName(code: String): String =
    FILTER_LANGUAGES.firstOrNull { it.first == code }?.second
        ?: Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code.uppercase() }

@Composable
private fun SortMenu(selected: SortOption, onSelect: (SortOption) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(selected.label) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) },
            shape = ExpressiveShapes.small,
            modifier = Modifier.padding(start = 8.dp)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun TopResultCard(item: AnimeDto, onClick: () -> Unit) {
    val compact = isCompactWidth
    Surface(
        onClick = onClick,
        shape = ExpressiveShapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            // At 16:9 a tablet-wide card would fill the screen and push the results below it.
            .then(if (compact) Modifier.aspectRatio(16f / 9f) else Modifier.height(280.dp))
    ) {
        Box {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/${if (compact) "w780" else "w1280"}${item.backdropPath}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (compact) {
                            Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                        } else {
                            // The text sits on the left of a wide banner, so the scrim runs sideways.
                            Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.85f), 0.6f to Color.Transparent)
                        }
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (compact) 18.dp else 28.dp)
                    .widthIn(max = 520.dp)
            ) {
                Surface(shape = ExpressiveShapes.small, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Text(
                        "TOP RESULT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(metaLine(item), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                if (!compact && !item.overview.isNullOrBlank()) {
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(item: AnimeDto, onClick: () -> Unit) {
    // Clipped to the artwork's shape so the press ripple follows the card; the text is inset from its corners.
    Column(
        modifier = Modifier
            .clip(ExpressiveShapes.medium)
            .clickable(onClickLabel = "Open ${item.name}", onClick = onClick)
            .padding(bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(ExpressiveShapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = item.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.isMovie) {
                Surface(
                    shape = ExpressiveShapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text("MOVIE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            item.date.take(4).takeIf { it.length == 4 }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.voteAverage?.takeIf { it > 0 }?.let { rating ->
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.padding(start = 6.dp).size(12.dp))
                Text(String.format(Locale.US, " %.1f", rating), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun metaLine(item: AnimeDto): String = listOfNotNull(
    if (item.isMovie) "Movie" else "Series",
    item.date.take(4).takeIf { it.length == 4 },
    item.voteAverage?.takeIf { it > 0 }?.let { String.format(Locale.US, "★ %.1f", it) }
).joinToString("  ·  ")

private fun BrowseGenre.icon(): ImageVector = when (this) {
    BrowseGenre.ANIME -> Icons.Default.AutoAwesome
    BrowseGenre.ACTION -> Icons.Default.LocalFireDepartment
    BrowseGenre.COMEDY -> Icons.Default.SentimentVerySatisfied
    BrowseGenre.DRAMA -> Icons.Default.TheaterComedy
    BrowseGenre.SCI_FI -> Icons.Default.RocketLaunch
    BrowseGenre.ROMANCE -> Icons.Default.Favorite
    BrowseGenre.HORROR -> Icons.Default.Nightlight
    BrowseGenre.MYSTERY -> Icons.Default.Search
    BrowseGenre.CRIME -> Icons.Default.Gavel
    BrowseGenre.THRILLER -> Icons.Default.Bolt
    BrowseGenre.ANIMATION -> Icons.Default.Animation
    BrowseGenre.FAMILY -> Icons.Default.FamilyRestroom
    BrowseGenre.DOCUMENTARY -> Icons.Default.Videocam
}
