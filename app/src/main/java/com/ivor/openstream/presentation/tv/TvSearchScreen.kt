package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.R
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.domain.model.BrowseGenre
import com.ivor.openstream.presentation.home.RailStyle
import com.ivor.openstream.presentation.search.SearchViewModel
import kotlinx.coroutines.launch

private const val TV_SEARCH_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

/** Wide keys: a remote thumb lands on them, a phone-sized one would not. */
private const val TV_KEYBOARD_COLUMNS = 7

/** Sentinel key: not a character, a delete. */
private const val TV_KEY_BACKSPACE = '\b'

/** A-Z, space, backspace. 28 keys, laid out as [TV_KEYBOARD_COLUMNS]-wide rows. */
private val TV_KEYBOARD_ROWS: List<List<Char>> =
    ("ABCDEFGHIJKLMNOPQRSTUVWXYZ " + TV_KEY_BACKSPACE)
        .toList()
        .chunked(TV_KEYBOARD_COLUMNS)

/**
 * TV search, driven entirely by the D-pad.
 *
 * **There is no text field here, and there must not be one.** Android TV has no
 * IME: a `TextField` on this screen can only be focused, never typed into, which
 * is the same "the highlight does not go anywhere" failure as a phone card with
 * no ring — a focus stop that looks interactive and does nothing. So the query
 * is composed from an on-screen keyboard ([TvKeyboard]) whose every key is a
 * real, ringed, pressable focus target.
 *
 * Two modes, swapped by `SearchUiState.isBrowsing`:
 *
 * - **Browsing** (no submitted query, no genre): the keyboard plus recent
 *   searches, genre chips and trending. The remote starts on the first letter,
 *   so the first thing the viewer can do is type.
 * - **Searching** (a query was submitted or a genre picked): the keyboard steps
 *   aside and the results grid takes the whole screen, because results are what
 *   the viewer came for and a keyboard covering half of them is how a search
 *   screen ends up feeling broken.
 *
 * Every size comes from [LocalTvDimens]; there is not one dp literal in this
 * file, because the phone scale is what made the old TV UI look enormous.
 */
@Composable
fun TvSearchScreen(
    onTitleClick: (animeId: Int, mediaType: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val dimens = LocalTvDimens.current
    val animated = rememberTvAnimated()

    // The composed query lives here, not in the view model: nothing is searched
    // until the viewer commits with the Search button, which is what makes the
    // keyboard feel deliberate instead of firing a request per letter.
    var draft by rememberSaveable { mutableStateOf("") }

    val results = state.visibleResults
    val gridState = rememberLazyGridState()

    val firstKeyRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val searchRequester = remember { FocusRequester() }
    // Set when a search starts, cleared once the grid actually has something to
    // land on: the request often runs before the first page arrives.
    var awaitingResultsFocus by remember { mutableStateOf(false) }

    fun AnimeDto.tvMediaType(): String = mediaType ?: if (isMovie) "movie" else "tv"

    val open: (AnimeDto) -> Unit = { onTitleClick(it.id, it.tvMediaType()) }

    fun submitDraft() {
        val query = draft.trim()
        if (query.isEmpty()) return
        draft = query
        viewModel.submit(query)
    }

    // The remote must be somewhere the instant the screen opens, and must follow
    // the mode change: keyboard while composing, the first result once there is
    // one. runCatching because requesting focus before the node is attached throws.
    LaunchedEffect(state.isBrowsing) {
        if (state.isBrowsing) {
            awaitingResultsFocus = false
            runCatching { firstKeyRequester.requestFocus() }
        } else {
            awaitingResultsFocus = true
            runCatching { firstResultRequester.requestFocus() }
                .recoverCatching { searchRequester.requestFocus() }
        }
    }
    LaunchedEffect(results) {
        if (awaitingResultsFocus && results.isNotEmpty()) {
            awaitingResultsFocus = false
            runCatching { firstResultRequester.requestFocus() }
        }
    }

    // Infinite scroll, derived off layoutInfo exactly like the phone grid does.
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, results.size) {
        if (nearEnd && results.isNotEmpty()) viewModel.loadMore()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = dimens.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimens.rowGap)
    ) {
        TvQueryBar(
            query = if (state.isBrowsing) draft else state.query,
            genreLabel = state.genre?.let { stringResource(it.labelRes) },
            canBackspace = state.isBrowsing && draft.isNotEmpty(),
            canClear = !state.isBrowsing || draft.isNotEmpty(),
            canSearch = state.isBrowsing && draft.isNotBlank(),
            onBackspace = { draft = draft.dropLast(1) },
            onClear = { viewModel.clear() },
            onSearch = ::submitDraft,
            searchFocusRequester = searchRequester,
            modifier = Modifier.padding(horizontal = dimens.gutter)
        )

        if (state.isBrowsing) {
            TvKeyboard(
                onKey = { key ->
                    draft = when (key) {
                        TV_KEY_BACKSPACE -> draft.dropLast(1)
                        else -> draft + key
                    }
                },
                firstKeyRequester = firstKeyRequester,
                modifier = Modifier.padding(horizontal = dimens.gutter)
            )
            TvBrowseContent(
                recent = state.recent,
                trending = state.trending,
                selectedGenre = state.genre,
                animated = animated,
                onSearchRecent = { query ->
                    draft = query
                    viewModel.submit(query)
                },
                onRemoveRecent = viewModel::removeRecent,
                onGenre = { genre ->
                    draft = ""
                    viewModel.selectGenre(genre)
                },
                onOpen = open,
                modifier = Modifier.weight(1f)
            )
        } else {
            TvResultsContent(
                results = results,
                query = state.query,
                isLoading = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                error = state.error,
                animated = animated,
                gridState = gridState,
                firstResultRequester = firstResultRequester,
                onOpen = open,
                onRetry = viewModel::retry,
                onClear = viewModel::clear,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * The query line: what has been composed, what will be searched, and the three
 * things a viewer can do to it.
 *
 * The text itself is *not* a focus target. A focusable display with no action is
 * the dead-end stop this file exists to avoid; only the buttons are stops.
 */
@Composable
private fun TvQueryBar(
    query: String,
    genreLabel: String?,
    canBackspace: Boolean,
    canClear: Boolean,
    canSearch: Boolean,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    searchFocusRequester: FocusRequester? = null
) {
    val dimens = LocalTvDimens.current
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.listRowHeight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.paddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.height(dimens.listRowHeight * 0.34f)
            )
            Spacer(Modifier.width(dimens.paddingMedium))
            Column(modifier = Modifier.weight(1f)) {
                genreLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = query.ifBlank { stringResource(R.string.search_hint) },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (query.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TvIconButton(onClick = onBackspace, enabled = canBackspace) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.pin_backspace)
                )
            }
            TvIconButton(onClick = onClear, enabled = canClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_clear_search)
                )
            }
            Spacer(Modifier.width(dimens.paddingSmall))
            TvButton(
                onClick = onSearch,
                enabled = canSearch,
                shape = MaterialTheme.shapes.extraLarge,
                focusRequester = searchFocusRequester
            ) {
                Text(
                    text = stringResource(R.string.nav_search),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * The on-screen keyboard: the whole reason this screen works.
 *
 * A fixed grid of wide [TvButton]s, so D-pad traversal is obvious — right walks
 * along a row, down drops to the row below at the same column, and OK presses
 * the highlighted key. Nothing here is a bare `clickable`, so the remote always
 * has a visible ring to follow.
 */
@Composable
private fun TvKeyboard(
    onKey: (Char) -> Unit,
    firstKeyRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val dimens = LocalTvDimens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
    ) {
        TV_KEYBOARD_ROWS.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)) {
                row.forEachIndexed { columnIndex, key ->
                    val requester = if (rowIndex == 0 && columnIndex == 0) firstKeyRequester else null
                    if (key == TV_KEY_BACKSPACE) {
                        TvButton(
                            onClick = { onKey(TV_KEY_BACKSPACE) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            focusRequester = requester
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = stringResource(R.string.pin_backspace),
                                modifier = Modifier.height(dimens.buttonHeight * 0.4f)
                            )
                        }
                    } else {
                        TvButton(
                            onClick = { onKey(key) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            focusRequester = requester
                        ) {
                            if (key == ' ') {
                                Icon(
                                    Icons.Default.SpaceBar,
                                    contentDescription = null,
                                    modifier = Modifier.height(dimens.buttonHeight * 0.4f)
                                )
                            } else {
                                Text(
                                    text = key.toString(),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * What there is to look at before a query exists: recent searches, genres and
 * trending. All of it optional, so a first-run screen is not three empty rails.
 */
@Composable
private fun TvBrowseContent(
    recent: List<String>,
    trending: List<AnimeDto>,
    selectedGenre: BrowseGenre?,
    animated: Boolean,
    onSearchRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onGenre: (BrowseGenre) -> Unit,
    onOpen: (AnimeDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalTvDimens.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimens.paddingLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.rowGap)
    ) {
        if (recent.isNotEmpty()) {
            item(key = "recent") {
                Column {
                    TvRailHeader(stringResource(R.string.search_recent))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = dimens.gutter),
                        horizontalArrangement = Arrangement.spacedBy(dimens.listRowGap)
                    ) {
                        items(recent, key = { "recent:$it" }) { query ->
                            TvRecentSearch(
                                query = query,
                                onSearch = { onSearchRecent(query) },
                                onRemove = { onRemoveRecent(query) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "genres") {
            Column {
                TvRailHeader(stringResource(R.string.search_browse_genre))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = dimens.gutter),
                    horizontalArrangement = Arrangement.spacedBy(dimens.railGap)
                ) {
                    items(BrowseGenre.entries.toList(), key = { "genre:${it.name}" }) { genre ->
                        TvChoiceChip(
                            selected = selectedGenre == genre,
                            onClick = { onGenre(genre) },
                            label = stringResource(genre.labelRes)
                        )
                    }
                }
            }
        }

        if (trending.isNotEmpty()) {
            item(key = "trending") {
                TvAnimeRail(
                    title = stringResource(R.string.home_trending_now),
                    style = RailStyle.POSTER,
                    items = trending,
                    animated = animated,
                    onOpen = onOpen
                )
            }
        }
    }
}

/**
 * One recent search: run it, or forget it.
 *
 * Two sibling focus targets rather than one row with a trailing button, because
 * a control with two focusable nodes makes D-pad OK land on the decoration and
 * do nothing. Left and right walk between the row and its remove button.
 */
@Composable
private fun TvRecentSearch(
    query: String,
    onSearch: () -> Unit,
    onRemove: () -> Unit
) {
    val dimens = LocalTvDimens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvListRow(
            onClick = onSearch,
            title = query,
            modifier = Modifier.width(dimens.landscapeCardWidth),
            leadingIcon = Icons.Default.History
        )
        Spacer(Modifier.width(dimens.listRowGap))
        TvIconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_remove_query, query)
            )
        }
    }
}

/**
 * The results grid, plus its loading, empty and error states.
 *
 * The grid owns the whole content area: a search that shows its results behind
 * a keyboard is a search whose results nobody can see.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TvResultsContent(
    results: List<AnimeDto>,
    query: String,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    animated: Boolean,
    gridState: LazyGridState,
    firstResultRequester: FocusRequester,
    onOpen: (AnimeDto) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalTvDimens.current
    Box(modifier = modifier.fillMaxSize()) {
        when {
            error != null && results.isEmpty() -> TvErrorState(onRetry = onRetry)
            isLoading && results.isEmpty() -> TvLoading()
            results.isEmpty() -> TvSearchEmpty(query = query, onBack = onClear)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = dimens.posterCardWidth),
                state = gridState,
                contentPadding = PaddingValues(
                    start = dimens.gutter,
                    end = dimens.gutter,
                    top = dimens.paddingSmall,
                    bottom = dimens.paddingLarge
                ),
                horizontalArrangement = Arrangement.spacedBy(dimens.railGap),
                verticalArrangement = Arrangement.spacedBy(dimens.rowGap),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = results,
                    key = { _, item -> "result:${item.mediaType}:${item.id}" }
                ) { index, item ->
                    TvSearchPosterCard(
                        item = item,
                        animated = animated,
                        focusRequester = if (index == 0) firstResultRequester else null,
                        onClick = { onOpen(item) }
                    )
                }
                if (isLoadingMore) {
                    item(key = "loading-more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimens.listRowHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nothing came back.
 *
 * The one action offered is the way out: back to the keyboard, because the only
 * thing the viewer can act on is how they spelled it.
 */
@Composable
private fun TvSearchEmpty(query: String, onBack: () -> Unit) {
    val dimens = LocalTvDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = query,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(dimens.paddingSmall))
        Text(
            text = stringResource(R.string.search_no_results_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(dimens.paddingLarge))
        TvButton(onClick = onBack) {
            Text(
                text = stringResource(R.string.action_search_again),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * One result: poster, title, year.
 *
 * A single focus target for the whole card, so D-pad OK always opens the title
 * the ring is drawn around. It scrolls itself into view on focus, which is what
 * keeps the highlight from being left behind on a grid the viewer has scrolled.
 */
@Composable
private fun TvSearchPosterCard(
    item: AnimeDto,
    animated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val dimens = LocalTvDimens.current
    val focused = rememberTvFocused()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    TvCard(
        focused = focused.value,
        animated = animated,
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        onFocused = {
            focused.value = true
            scope.launch { runCatching { bringIntoViewRequester.bringIntoView() } }
        },
        onBlurred = { focused.value = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                val imageUrl = item.posterPath?.let { "$TV_SEARCH_IMAGE_BASE$it" }
                    ?: item.backdropPath?.let { "$TV_SEARCH_IMAGE_BASE$it" }
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = stringResource(R.string.cd_poster, item.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = dimens.paddingSmall / 2)
            )
            item.date.take(4).takeIf { it.length == 4 }?.let { year ->
                Text(
                    text = year,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}