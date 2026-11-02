package com.ivor.openstream.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.R
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.presentation.components.isCompactWidth

// region Model

/**
 * One setting: what search knows it by ([title], [summary], [value] and [terms], such as the
 * labels of its choices) and how it draws itself as a row.
 */
internal class SettingEntry(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val summary: String?,
    /** The current state in words ("On", "Dark", "1080p"), shown beside a search result. */
    val value: String? = null,
    val terms: List<String> = emptyList(),
    val row: @Composable (index: Int, count: Int, modifier: Modifier, highlight: Float) -> Unit
)

internal class SettingsGroupSpec(
    val key: String,
    val title: String? = null,
    val footer: String? = null,
    val entries: List<SettingEntry>
)

/**
 * A category of the parent page, such as Personalization, opened on its own page. [cluster] puts
 * related categories together in one block on the parent page.
 */
internal class SettingsCategory(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val cluster: Int,
    val groups: List<SettingsGroupSpec>,
    val busy: Boolean = false
) {
    val entries: List<SettingEntry> get() = groups.flatMap { it.entries }
}

/** A category page as list items, so it can scroll straight to any one setting. */
private sealed interface CategoryItem {
    val key: String

    class GroupLabel(val title: String, groupKey: String) : CategoryItem {
        override val key = "group:$groupKey"
    }

    class Row(val entry: SettingEntry, val index: Int, val count: Int) : CategoryItem {
        override val key = "row:${entry.key}"
    }

    class Footer(val text: String, groupKey: String) : CategoryItem {
        override val key = "footer:$groupKey"
    }
}

private fun SettingsCategory.items(): List<CategoryItem> = buildList {
    groups.filter { it.entries.isNotEmpty() }.forEach { group ->
        group.title?.let { add(CategoryItem.GroupLabel(it, group.key)) }
        group.entries.forEachIndexed { index, entry -> add(CategoryItem.Row(entry, index, group.entries.size)) }
        group.footer?.let { add(CategoryItem.Footer(it, group.key)) }
    }
}

// endregion

/**
 * Settings as a parent page of categories and a page for each. On a phone in portrait a category
 * replaces the list and Back returns to it; on wider windows (landscape, tablets) the list stays
 * on the left and the chosen category fills the right.
 *
 * Search sits on the parent page and covers every setting. Picking a result opens its category,
 * scrolls to the setting, pulses it and moves the focus to it.
 */
@Composable
internal fun SettingsLayout(
    categories: List<SettingsCategory>,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit
) {
    val twoPane = !isCompactWidth
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // A setting picked from the search, waiting to be scrolled to once its category is showing.
    var pendingJump by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // A category that disappears (none should, but options come and go) falls back to the list.
    val selected = categories.firstOrNull { it.key == selectedKey }
    // Side by side there is always a category on the right: the first until one is picked.
    val shown = selected ?: categories.firstOrNull()?.takeIf { twoPane }

    // Back clears a search first, then (on a phone) leaves a category for the list.
    BackHandler(enabled = query.isNotEmpty()) { query = "" }
    BackHandler(enabled = query.isEmpty() && !twoPane && selected != null) { selectedKey = null }

    val openCategory: (String) -> Unit = { key -> selectedKey = key }
    val pickResult: (String) -> Unit = { entryKey ->
        focusManager.clearFocus()
        categories.firstOrNull { category -> category.entries.any { it.key == entryKey } }?.let {
            selectedKey = it.key
            pendingJump = entryKey
        }
        query = ""
    }

    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            CategoryListPane(
                categories = categories,
                selectedKey = shown?.key,
                query = query,
                onQueryChange = { query = it },
                listState = listState,
                snackbarHostState = null,
                onBackClick = onBackClick,
                onOpen = openCategory,
                onPickResult = pickResult,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight()
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                shown?.let { category ->
                    // A page of its own per category, so each keeps its scroll position.
                    androidx.compose.runtime.key(category.key) {
                        CategoryPane(
                            category = category,
                            showBack = false,
                            snackbarHostState = snackbarHostState,
                            pendingJump = pendingJump,
                            onJumpHandled = { pendingJump = null },
                            onBackClick = {}
                        )
                    }
                }
            }
        }
    } else {
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        AnimatedContent(
            targetState = selected?.key,
            transitionSpec = {
                // Into a category the new page comes in from the end side; back, it leaves that way.
                val forward = targetState != null
                val sign = if (forward != rtl) 1 else -1
                (slideInHorizontally(tween(300)) { sign * it / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -sign * it / 3 } + fadeOut(tween(200)))
            },
            label = "settings-pages"
        ) { key ->
            val category = categories.firstOrNull { it.key == key }
            if (category == null) {
                CategoryListPane(
                    categories = categories,
                    selectedKey = null,
                    query = query,
                    onQueryChange = { query = it },
                    listState = listState,
                    snackbarHostState = snackbarHostState,
                    onBackClick = onBackClick,
                    onOpen = openCategory,
                    onPickResult = pickResult,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CategoryPane(
                    category = category,
                    showBack = true,
                    snackbarHostState = snackbarHostState,
                    pendingJump = pendingJump,
                    onJumpHandled = { pendingJump = null },
                    onBackClick = { selectedKey = null }
                )
            }
        }
    }
}

// region Parent page

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryListPane(
    categories: List<SettingsCategory>,
    selectedKey: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState?,
    onBackClick: () -> Unit,
    onOpen: (String) -> Unit,
    onPickResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val focusManager = LocalFocusManager.current
    val title = stringResource(R.string.st_title)
    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = { Text(stringResource(R.string.up_version_format, BuildConfig.VERSION_NAME)) },
                navigationIcon = {
                    ExpressiveBackButton(onClick = onBackClick, modifier = Modifier.padding(start = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        val padding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + 32.dp
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SettingsSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { focusManager.clearFocus() },
                modifier = Modifier.widthIn(max = 720.dp)
            )
            val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = padding.calculateBottomPadding())
            if (query.isNotBlank()) {
                SearchResults(
                    query = query,
                    categories = categories,
                    contentPadding = listPadding,
                    onPick = onPickResult
                )
            } else {
                CategoryList(
                    categories = categories,
                    selectedKey = selectedKey,
                    state = listState,
                    contentPadding = listPadding,
                    onOpen = onOpen
                )
            }
        }
    }
}

/** The categories in their clusters, each a block of connected rows. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryList(
    categories: List<SettingsCategory>,
    selectedKey: String?,
    state: LazyListState,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit
) {
    val clusters = categories.groupBy { it.cluster }.toSortedMap().values.toList()
    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp),
        contentPadding = contentPadding
    ) {
        clusters.forEachIndexed { clusterIndex, cluster ->
            itemsIndexed(cluster, key = { _, category -> category.key }) { index, category ->
                val top = when {
                    index > 0 -> ListItemDefaults.SegmentedGap
                    clusterIndex > 0 -> 20.dp
                    else -> 0.dp
                }
                Box(Modifier.padding(top = top)) {
                    CategoryRow(
                        category = category,
                        index = index,
                        count = cluster.size,
                        selected = category.key == selectedKey,
                        onClick = { onOpen(category.key) }
                    )
                }
            }
        }
        item(key = "version") {
            Text(
                text = stringResource(R.string.up_version_build, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
            )
        }
    }
}

/**
 * A category on the parent page: its name and the first few settings inside, so what it holds is
 * clear before it is opened. Side by side, the open one is shown selected.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryRow(
    category: SettingsCategory,
    index: Int,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val preview = category.entries.map { it.title }.distinct().take(3).joinToString(", ")
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        leadingContent = { CategoryIcon(category.icon, selected) },
        supportingContent = { Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryIcon(icon: ImageVector, selected: Boolean) {
    androidx.compose.material3.Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text(stringResource(R.string.st_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResults(
    query: String,
    categories: List<SettingsCategory>,
    contentPadding: PaddingValues,
    onPick: (String) -> Unit
) {
    val byKey = remember(categories) {
        categories.flatMap { category -> category.entries.map { it to category } }.associateBy { it.first.key }
    }
    val results = remember(query, categories) {
        val candidates = byKey.values.map { (entry, category) ->
            SettingsSearch.Candidate(
                key = entry.key,
                title = entry.title,
                summary = entry.summary,
                section = category.title,
                terms = entry.terms + listOfNotNull(entry.value)
            )
        }
        SettingsSearch.search(candidates, query).mapNotNull { byKey[it.key] }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        item(key = "count") {
            // Read out as the results change, so a screen reader hears how many there are.
            Text(
                text = if (results.isEmpty()) {
                    stringResource(R.string.st_search_none, query.trim())
                } else {
                    pluralStringResource(R.plurals.st_search_results, results.size, results.size)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        if (results.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(R.string.st_search_none_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        itemsIndexed(results, key = { _, (entry, _) -> entry.key }) { index, (entry, category) ->
            val where = listOfNotNull(category.title, entry.summary).joinToString(" · ")
            SegmentedListItem(
                onClick = { onPick(entry.key) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = results.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                leadingContent = { RowIcon(entry.icon) },
                supportingContent = { Text(highlighted(where, query), maxLines = 3) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        entry.value?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            ) {
                Text(highlighted(entry.title, query), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// endregion

// region Category page

/** One category's settings, in their groups. Scrolls to and pulses [pendingJump] when it is here. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryPane(
    category: SettingsCategory,
    showBack: Boolean,
    snackbarHostState: SnackbarHostState,
    pendingJump: String?,
    onJumpHandled: () -> Unit,
    onBackClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val items = category.items()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var pulsingKey by remember { mutableStateOf<String?>(null) }
    val pulse = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(pendingJump) {
        val key = pendingJump ?: return@LaunchedEffect
        val index = items.indexOfFirst { it is CategoryItem.Row && it.entry.key == key }
        if (index < 0) return@LaunchedEffect
        try {
            // Let the page lay out (it may have just slid in) before scrolling it.
            withFrameNanos { }
            // Leave the row a little below the top so the group it belongs to is still in view.
            listState.animateScrollToItem(index, scrollOffset = -with(density) { 72.dp.roundToPx() })
            withFrameNanos { }
            runCatching { focusRequesters[key]?.requestFocus() }
            pulsingKey = key
            pulse.snapTo(0f)
            repeat(2) {
                pulse.animateTo(1f, tween(durationMillis = 260))
                pulse.animateTo(0f, tween(durationMillis = 520))
            }
        } finally {
            pulsingKey = null
            // Only now: clearing it earlier would restart this effect and cut the scroll short.
            onJumpHandled()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .semantics { paneTitle = category.title },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(category.title) },
                navigationIcon = {
                    if (showBack) {
                        ExpressiveBackButton(onClick = onBackClick, modifier = Modifier.padding(start = 8.dp))
                    }
                },
                actions = {
                    if (category.busy) {
                        androidx.compose.material3.LoadingIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 32.dp
                )
            ) {
                items.forEachIndexed { position, item ->
                    item(key = item.key, contentType = item::class) {
                        when (item) {
                            is CategoryItem.GroupLabel -> SettingsGroupLabel(
                                title = item.title,
                                modifier = Modifier.padding(top = if (position == 0) 0.dp else 24.dp)
                            )
                            is CategoryItem.Row -> {
                                val requester = focusRequesters.getOrPut(item.entry.key) { FocusRequester() }
                                val top = when {
                                    item.index > 0 -> ListItemDefaults.SegmentedGap
                                    position == 0 -> 0.dp
                                    items[position - 1] is CategoryItem.GroupLabel -> 0.dp
                                    else -> 24.dp
                                }
                                Box(Modifier.padding(top = top)) {
                                    item.entry.row(
                                        item.index,
                                        item.count,
                                        Modifier.focusRequester(requester),
                                        if (pulsingKey == item.entry.key) pulse.value else 0f
                                    )
                                }
                            }
                            is CategoryItem.Footer -> SettingsFooter(item.text)
                        }
                    }
                }
            }
        }
    }
}

// endregion
