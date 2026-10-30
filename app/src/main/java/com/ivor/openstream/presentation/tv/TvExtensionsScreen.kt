package com.ivor.openstream.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.ivor.openstream.R
import com.ivor.openstream.data.settings.SourceSearchMode
import com.ivor.openstream.domain.model.ExtensionRepo
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.presentation.marketplace.MarketplaceViewModel
import com.ivor.openstream.presentation.marketplace.extensionSubtitle

/**
 * Sources as a list and a detail pane. The list on the left switches between what is installed
 * (in search order, with the search mode above it) and the whole catalog; focusing a source shows
 * it on the right with its actions. Removing asks first in a centred panel. Repositories are a third
 * list; adding one opens a panel so the on-screen keyboard only comes up when it was asked for.
 * Searching needs typed text and stays on the phone.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvExtensionsScreen(
    modifier: Modifier = Modifier,
    viewModel: MarketplaceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    var tab by remember { mutableStateOf(TvSourcesTab.INSTALLED) }
    val showInstalled = tab == TvSourcesTab.INSTALLED
    val showRepos = tab == TvSourcesTab.REPOSITORIES
    var selectedRepoId by remember { mutableStateOf<String?>(null) }
    var pendingRepoRemove by remember { mutableStateOf<ExtensionRepo?>(null) }
    var addingRepo by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<MarketplaceExtension?>(null) }
    var detailHasFocus by remember { mutableStateOf(false) }

    val visible = when (tab) {
        TvSourcesTab.INSTALLED -> state.installed
        TvSourcesTab.BROWSE -> state.catalog.extensions
        TvSourcesTab.REPOSITORIES -> emptyList()
    }
    val repos = state.catalog.repos
    val selectedRepo = repos.firstOrNull { it.id == selectedRepoId } ?: repos.firstOrNull()
    val addRepoRow = remember { FocusRequester() }
    val selected = visible.firstOrNull { it.key == selectedKey } ?: visible.firstOrNull()
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun rowRequester(key: String) = rowRequesters.getOrPut(key) { FocusRequester() }
    val firstPill = remember { FocusRequester() }

    LaunchedEffect(tab, visible.isEmpty()) {
        if (showRepos) return@LaunchedEffect
        val first = visible.firstOrNull()
        runCatching { if (first != null) rowRequester(first.key).requestFocus() else firstPill.requestFocus() }
    }
    // The panels and the removed row take the focus with them, so it comes back to the list.
    LaunchedEffect(showRepos, addingRepo, pendingRepoRemove, repos.size) {
        if (showRepos && !addingRepo && pendingRepoRemove == null) runCatching { addRepoRow.requestFocus() }
    }
    BackHandler(enabled = detailHasFocus && pendingRemove == null && selected != null) {
        runCatching { rowRequester(selected!!.key).requestFocus() }
    }
    BackHandler(enabled = detailHasFocus && showRepos && !addingRepo && pendingRepoRemove == null) {
        runCatching { addRepoRow.requestFocus() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            val counts = "${stringResource(R.string.st_installed)}: ${state.installed.size}  ·  " +
                "${stringResource(R.string.st_enabled)}: ${state.catalog.enabled.size}"
            TvPageHeader(
                title = stringResource(R.string.st_group_sources),
                supporting = state.message?.let { "$counts  ·  $it" } ?: counts,
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = viewModel::refresh,
                            enabled = !state.catalog.isSyncing,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.action_refresh_repositories))
                        }
                        if (state.updatable.isNotEmpty()) {
                            Button(
                                onClick = viewModel::updateAll,
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.action_update_all))
                            }
                        }
                    }
                }
            )

            Row(Modifier.fillMaxSize()) {
                // The list.
                Column(
                    Modifier
                        .padding(start = TvLayout.ContentStart, end = 16.dp)
                        .width(340.dp)
                        .fillMaxHeight()
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            TvPill(
                                label = stringResource(R.string.mk_tab_installed) + " (${state.installed.size})",
                                selected = showInstalled,
                                onClick = { tab = TvSourcesTab.INSTALLED },
                                modifier = Modifier.focusRequester(firstPill)
                            )
                        }
                        item {
                            TvPill(
                                label = stringResource(R.string.mk_tab_browse) + " (${state.catalog.extensions.size})",
                                selected = !showInstalled,
                                onClick = { tab = TvSourcesTab.BROWSE }
                            )
                        }
                        item {
                            TvPill(
                                label = stringResource(R.string.mk_tab_repositories) + " (${repos.size})",
                                selected = showRepos,
                                onClick = { tab = TvSourcesTab.REPOSITORIES }
                            )
                        }
                    }
                    if (showInstalled && state.installed.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            SourceSearchMode.entries.forEach { mode ->
                                item {
                                    TvPill(
                                        label = stringResource(mode.labelRes),
                                        selected = searchMode == mode,
                                        onClick = { viewModel.setSearchMode(mode) }
                                    )
                                }
                            }
                        }
                    }
                    if (showRepos) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
                        ) {
                            item(key = "add") {
                                ListItem(
                                    selected = false,
                                    onClick = { addingRepo = true },
                                    headlineContent = { Text(stringResource(R.string.st_add_repository)) },
                                    leadingContent = {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    modifier = Modifier.focusRequester(addRepoRow)
                                )
                            }
                            items(repos, key = { it.id }) { repo ->
                                ListItem(
                                    selected = repo.id == selectedRepo?.id,
                                    onClick = { },
                                    headlineContent = {
                                        Text(repo.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = {
                                        Text(repo.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    modifier = Modifier.onFocusChanged { if (it.isFocused) selectedRepoId = repo.id }
                                )
                            }
                        }
                    } else if (visible.isEmpty()) {
                        androidx.compose.material3.Text(
                            text = stringResource(if (showInstalled) R.string.mk_no_sources else R.string.st_pull_repo_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.focusRestorer(selected?.let { rowRequester(it.key) } ?: FocusRequester.Default),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
                        ) {
                            itemsIndexed(visible, key = { _, it -> it.key }) { index, extension ->
                                ListItem(
                                    selected = extension.key == selected?.key,
                                    onClick = { },
                                    headlineContent = {
                                        Text(extension.manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = {
                                        Text(statusLabel(extension), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    leadingContent = {
                                        if (showInstalled) {
                                            Text("${index + 1}", style = MaterialTheme.typography.titleMedium)
                                        } else {
                                            Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(20.dp))
                                        }
                                    },
                                    modifier = Modifier
                                        .focusRequester(rowRequester(extension.key))
                                        .onFocusChanged { if (it.isFocused) selectedKey = extension.key }
                                )
                            }
                        }
                    }
                }

                // The detail pane.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(topStart = 28.dp)
                        )
                        .onFocusChanged { detailHasFocus = it.hasFocus }
                        .padding(start = 32.dp, end = TvLayout.ContentEnd, top = 28.dp)
                ) {
                    if (showRepos) {
                        selectedRepo?.let { repo ->
                            RepoDetail(repo = repo, onRemove = { pendingRepoRemove = repo })
                        }
                    } else if (selected != null) {
                        val position = state.installed.indexOfFirst { it.key == selected.key }
                        ExtensionDetail(
                            extension = selected,
                            canMoveUp = showInstalled && position > 0,
                            canMoveDown = showInstalled && position in 0 until state.installed.lastIndex,
                            onInstall = { viewModel.install(selected) },
                            onUpdate = { viewModel.update(selected) },
                            onEnabledChange = { viewModel.setEnabled(selected, it) },
                            onMove = { offset -> viewModel.moveSource(selected, offset) },
                            onRemove = { pendingRemove = selected }
                        )
                    }
                }
            }
        }

        pendingRemove?.let { extension ->
            TvConfirmPanel(
                title = stringResource(R.string.action_remove) + "?",
                message = extension.manifest.name,
                confirmLabel = stringResource(R.string.action_remove),
                onConfirm = {
                    viewModel.uninstall(extension)
                    pendingRemove = null
                },
                onCancel = { pendingRemove = null }
            )
        }

        pendingRepoRemove?.let { repo ->
            TvConfirmPanel(
                title = stringResource(R.string.action_remove) + "?",
                message = repo.name,
                confirmLabel = stringResource(R.string.action_remove),
                onConfirm = {
                    viewModel.removeRepo(repo.id)
                    pendingRepoRemove = null
                },
                onCancel = { pendingRepoRemove = null }
            )
        }

        if (addingRepo) {
            AddRepoPanel(
                onAdd = { url ->
                    viewModel.addRepo(url)
                    addingRepo = false
                },
                onCancel = { addingRepo = false }
            )
        }
    }
}

private enum class TvSourcesTab { INSTALLED, BROWSE, REPOSITORIES }

@Composable
private fun RepoDetail(repo: ExtensionRepo, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.material3.Text(
            text = repo.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            text = repo.url,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (repo.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Text(
                text = repo.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
        repo.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(20.dp))
        if (repo.isBuiltIn) {
            androidx.compose.material3.Text(
                text = stringResource(R.string.st_official_cannot_remove),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            OutlinedButton(
                onClick = onRemove,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.action_remove))
            }
        }
    }
}

/** Takes a link typed on the TV's keyboard; the field gets the focus, which brings the keyboard up. */
@Composable
private fun AddRepoPanel(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val field = remember { FocusRequester() }
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    fun submit() {
        if (url.isNotBlank()) onAdd(url.trim())
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(Modifier.padding(32.dp)) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.st_add_repo_hint),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { androidx.compose.material3.Text(stringResource(R.string.st_repo_example)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(field)
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = ::submit, enabled = url.isNotBlank()) {
                        Text(stringResource(R.string.st_add_repository))
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(extension: MarketplaceExtension): String = when {
    !extension.manifest.isSupported -> stringResource(R.string.mk_unsupported)
    extension.hasUpdate -> stringResource(R.string.action_update)
    extension.isEnabled -> stringResource(R.string.er_active_player)
    extension.isInstalled -> stringResource(R.string.mk_paused)
    else -> extension.manifest.language
}

@Composable
private fun ExtensionDetail(
    extension: MarketplaceExtension,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val manifest = extension.manifest
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.material3.Text(
            text = manifest.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            text = extensionSubtitle(extension) + "  ·  " + manifest.author,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (manifest.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Text(
                text = manifest.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                !extension.isInstalled -> Button(
                    onClick = onInstall,
                    enabled = manifest.isSupported,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_install))
                }
                extension.hasUpdate -> Button(
                    onClick = onUpdate,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_update))
                }
            }
            if (extension.isInstalled) {
                if (canMoveUp) {
                    IconButton(onClick = { onMove(-1) }) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.cd_move_up, manifest.name))
                    }
                }
                if (canMoveDown) {
                    IconButton(onClick = { onMove(1) }) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.cd_move_down, manifest.name))
                    }
                }
                OutlinedButton(
                    onClick = onRemove,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_remove))
                }
            }
        }

        if (extension.isInstalled && manifest.isSupported) {
            Spacer(Modifier.height(16.dp))
            ListItem(
                selected = false,
                onClick = { onEnabledChange(!extension.isEnabled) },
                headlineContent = { Text(stringResource(R.string.er_active_player)) },
                trailingContent = { Switch(checked = extension.isEnabled, onCheckedChange = null) },
                modifier = Modifier.width(420.dp)
            )
        }
    }
}
