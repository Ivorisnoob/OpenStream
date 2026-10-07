package com.ivor.openstream.presentation.marketplace

import com.ivor.openstream.presentation.components.CenteredListBox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ivor.openstream.presentation.components.ConnectedChoiceGroup
import com.ivor.openstream.data.settings.SourceSearchMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.ivor.openstream.R
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.model.MarketplaceSort
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.ui.theme.ExpressiveShapes

private enum class MarketplaceTab(val label: String, @StringRes val labelRes: Int) {
    BROWSE("Browse", R.string.mk_tab_browse),
    INSTALLED("Installed", R.string.mk_tab_installed),
    REPOSITORIES("Repositories", R.string.mk_tab_repositories)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    onBackClick: () -> Unit,
    viewModel: MarketplaceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(MarketplaceTab.BROWSE) }
    var detailsFor by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.mk_title),
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                navigationIcon = {
                    ExpressiveBackButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                actions = {
                    RefreshAction(isSyncing = state.catalog.isSyncing, onClick = viewModel::refresh)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                MarketplaceTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = when (tab) {
                                    MarketplaceTab.INSTALLED ->
                                        "${stringResource(tab.labelRes)} (${state.installed.size})"
                                    else -> stringResource(tab.labelRes)
                                },
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            AnimatedVisibility(visible = state.catalog.isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                MarketplaceTab.BROWSE -> BrowseTab(
                    state = state,
                    viewModel = viewModel,
                    onOpenDetails = { detailsFor = it.key }
                )

                MarketplaceTab.INSTALLED -> InstalledTab(
                    state = state,
                    viewModel = viewModel,
                    onOpenDetails = { detailsFor = it.key },
                    onBrowse = { selectedTab = MarketplaceTab.BROWSE }
                )

                MarketplaceTab.REPOSITORIES -> RepositoriesTab(
                    state = state,
                    viewModel = viewModel
                )
            }
        }
    }

    val details = detailsFor?.let { key -> state.catalog.extensions.firstOrNull { it.key == key } }
    if (details != null) {
        ExtensionDetailsSheet(
            extension = details,
            repoName = state.catalog.repos
                .firstOrNull { it.id == details.manifest.repoId }?.name
                .orEmpty()
                .ifEmpty { stringResource(R.string.st_unknown_repo) },
            onDismiss = { detailsFor = null },
            onInstall = { viewModel.install(details) },
            onUninstall = {
                viewModel.uninstall(details)
                detailsFor = null
            },
            onUpdate = { viewModel.update(details) },
            onEnabledChange = { viewModel.setEnabled(details, it) }
        )
    }
}

@Composable
private fun BrowseTab(
    state: MarketplaceUiState,
    viewModel: MarketplaceViewModel,
    onOpenDetails: (MarketplaceExtension) -> Unit
) {
    CenteredListBox(Modifier.fillMaxSize(), minGutter = 16.dp) { gutter ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.st_search_sources_tags)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }

            if (!state.isFiltered && state.charts.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.home_top_charts),
                        subtitle = stringResource(R.string.sheet_most_popular)
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(state.charts, key = { _, item -> item.key }) { index, extension ->
                            ChartTile(
                                rank = index + 1,
                                extension = extension,
                                onClick = { onOpenDetails(extension) },
                                onInstall = { viewModel.install(extension) }
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarketplaceSort.entries.forEach { sort ->
                        FilterChip(
                            selected = state.sort == sort,
                            onClick = { viewModel.setSort(sort) },
                            label = { Text(stringResource(sort.labelRes)) }
                        )
                    }
                }
            }

            if (state.tags.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.tag == null,
                            onClick = viewModel::clearTag,
                            label = { Text(stringResource(R.string.search_filter_all)) }
                        )
                        state.tags.forEach { tag ->
                            FilterChip(
                                selected = state.tag == tag,
                                onClick = { viewModel.toggleTag(tag) },
                                label = { Text(tag.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            if (state.results.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Storefront,
                        title = if (state.catalog.extensions.isEmpty()) {
                            stringResource(R.string.st_no_catalog)
                        } else {
                            stringResource(R.string.er_nothing_matches)
                        },
                        body = if (state.catalog.extensions.isEmpty()) {
                            stringResource(R.string.st_pull_repo_hint)
                        } else {
                            stringResource(R.string.search_try_other_term)
                        }
                    )
                }
            } else {
                items(state.results, key = { it.key }) { extension ->
                    ExtensionRow(
                        extension = extension,
                        onClick = { onOpenDetails(extension) },
                        onInstall = { viewModel.install(extension) },
                        onUninstall = { viewModel.uninstall(extension) },
                        onUpdate = { viewModel.update(extension) },
                        onEnabledChange = { viewModel.setEnabled(extension, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledTab(
    state: MarketplaceUiState,
    viewModel: MarketplaceViewModel,
    onOpenDetails: (MarketplaceExtension) -> Unit,
    onBrowse: () -> Unit
) {
    val searchMode by viewModel.searchMode.collectAsState()
    CenteredListBox(Modifier.fillMaxSize(), minGutter = 16.dp) { gutter ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LineupSummary(
                    installed = state.installed.size,
                    enabled = state.catalog.enabled.size
                )
            }

            if (state.installed.size > 1) {
                item(key = "search-order") {
                    SearchOrderCard(mode = searchMode, onModeChange = viewModel::setSearchMode)
                }
            }

            if (state.updatable.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Update, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.st_updates_available, state.updatable.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = state.updatable.joinToString { it.manifest.name },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(onClick = viewModel::updateAll, shape = ExpressiveShapes.small) {
                                Text(stringResource(R.string.action_update_all))
                            }
                        }
                    }
                }
            }

            if (state.installed.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Extension,
                        title = stringResource(R.string.mk_no_sources),
                        body = stringResource(R.string.st_source_install_note)
                    )
                }
                item {
                    Button(onClick = onBrowse, shape = ExpressiveShapes.small) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_browse_marketplace))
                    }
                }
            } else {
                itemsIndexed(state.installed, key = { _, extension -> extension.key }) { index, extension ->
                    ExtensionRow(
                        extension = extension,
                        onClick = { onOpenDetails(extension) },
                        onInstall = { viewModel.install(extension) },
                        onUninstall = { viewModel.uninstall(extension) },
                        onUpdate = { viewModel.update(extension) },
                        onEnabledChange = { viewModel.setEnabled(extension, it) },
                        rank = index + 1,
                        onMoveUp = { viewModel.moveSource(extension, -1) }.takeIf { index > 0 },
                        onMoveDown = { viewModel.moveSource(extension, 1) }.takeIf { index < state.installed.lastIndex },
                        showReorder = state.installed.size > 1
                    )
                }
            }
        }
    }
}

/** How the player searches: everything at once, or one source at a time down the list below. */
@Composable
private fun SearchOrderCard(mode: SourceSearchMode, onModeChange: (SourceSearchMode) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.sheet_search_order),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(10.dp))
            val searchModeLabels = SourceSearchMode.entries.associateWith { stringResource(it.labelRes) }
            ConnectedChoiceGroup(
                options = SourceSearchMode.entries,
                selected = mode,
                label = { searchModeLabels.getValue(it) },
                onSelect = onModeChange
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = when (mode) {
                    SourceSearchMode.ALL_AT_ONCE ->
                        stringResource(R.string.player_search_all_at_once)
                    SourceSearchMode.IN_ORDER ->
                        stringResource(R.string.player_search_in_order)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.sheet_use_arrows),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun RepositoriesTab(
    state: MarketplaceUiState,
    viewModel: MarketplaceViewModel
) {
    var repoUrl by rememberSaveable { mutableStateOf("") }

    CenteredListBox(Modifier.fillMaxSize(), minGutter = 16.dp) { gutter ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.st_add_repo_hint),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.mk_addon_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = repoUrl,
                            onValueChange = { repoUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.st_repo_example)) },
                            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                            singleLine = true,
                            shape = ExpressiveShapes.medium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (repoUrl.isNotBlank()) {
                                        viewModel.addRepo(repoUrl)
                                        repoUrl = ""
                                    }
                                }
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.addRepo(repoUrl)
                                repoUrl = ""
                            },
                            enabled = repoUrl.isNotBlank(),
                            shape = ExpressiveShapes.small
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.st_add_repository))
                        }
                    }
                }
            }

            items(state.catalog.repos, key = { it.id }) { repo ->
                RepoCard(
                    repo = repo,
                    extensionCount = state.catalog.extensions.count { it.manifest.repoId == repo.id },
                    onRemove = { viewModel.removeRepo(repo.id) }
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Shield, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.st_repo_data_note),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.st_repo_data_body),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LineupSummary(installed: Int, enabled: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.sheet_your_lineup),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = stringResource(R.string.st_race_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat(label = stringResource(R.string.st_installed), value = installed.toString())
                SummaryStat(label = stringResource(R.string.st_enabled), value = enabled.toString())
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Surface(
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RefreshAction(isSyncing: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "MarketplaceRefresh")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "MarketplaceRefreshRotation"
    )
    IconButton(onClick = onClick, enabled = !isSyncing) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.action_refresh_repositories),
            modifier = Modifier
                .size(24.dp)
                .rotate(if (isSyncing) rotation else 0f)
        )
    }
}
