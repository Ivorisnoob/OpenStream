package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.R
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.presentation.marketplace.MarketplaceViewModel
import com.ivor.openstream.presentation.marketplace.extensionSubtitle

/** Installed sources first, everything else on the second chip. */
private enum class TvExtensionsSection { INSTALLED, ALL }

/**
 * The extension marketplace at 10 feet.
 *
 * Built for a remote, not a touchscreen:
 *
 * - **A row is a decision, actions are below it.** OK on a row opens an inline
 *   action strip under that row — Install / Update / Enable / Uninstall as
 *   focusable chips. The phone opens a bottom sheet; a sheet has no place to
 *   put focus on a TV, so the actions live in the layout where the D-pad can
 *   reach them and the row stays where the viewer left it.
 * - **Uninstall confirms in place.** The chip becomes "Remove?" and OK commits
 *   beside it, with Cancel next to that. No [androidx.compose.material
 *   .AlertDialog].
 * - **No search field, no Add-repository-by-URL.** Both need a keyboard. The
 *   phone's `setQuery` and `addRepo` are simply not wired here; a viewer uses
 *   the Installed/All chips and Update all instead. (Same reason the downloads
 *   screen has no SAF import.)
 * - **Every stop rings.** Rows and actions are `TvListRow`/`TvChoiceChip`, so
 *   they carry the focus ring from [LocalTvDimens] rather than a ripple that a
 *   D-pad never triggers.
 */
@Composable
fun TvExtensionsScreen(
    modifier: Modifier = Modifier,
    viewModel: MarketplaceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dimens = LocalTvDimens.current

    var section by remember { mutableStateOf(TvExtensionsSection.INSTALLED) }

    /** Key of the row whose action strip / confirm is open. */
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var pendingRemovalKey by remember { mutableStateOf<String?>(null) }

    val sectionRequester = remember { FocusRequester() }
    val firstRowRequester = remember { FocusRequester() }

    val visible = when (section) {
        TvExtensionsSection.INSTALLED -> state.installed
        TvExtensionsSection.ALL -> state.catalog.extensions
    }

    // Initial focus. Wrapped in runCatching because an empty Installed list has
    // no row to focus, and requesting focus before a node is attached throws —
    // the chip is the reliable landing spot.
    LaunchedEffect(section, state.catalog.isSyncing) {
        runCatching { firstRowRequester.requestFocus() }
            .recoverCatching { sectionRequester.requestFocus() }
    }

    // A repo change (install, uninstall, refresh) can empty the list the remote
    // was standing on; closing the strip keeps a stale key from pointing at a
    // row that no longer exists.
    LaunchedEffect(visible.map { it.key }) {
        if (expandedKey != null && visible.none { it.key == expandedKey }) {
            expandedKey = null
            pendingRemovalKey = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TvTopBar(
            title = stringResource(R.string.mk_title),
            onBack = null,
            actions = {
                TvOutlinedButton(onClick = viewModel::refresh, enabled = !state.catalog.isSyncing) {
                    Text(
                        text = stringResource(R.string.action_refresh_repositories),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                TvButton(
                    onClick = viewModel::updateAll,
                    enabled = state.updatable.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.SystemUpdateAlt,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.action_update_all),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        )

        TvExtensionsSummary(
            installedCount = state.installed.size,
            enabledCount = state.catalog.enabled.size,
            updatableCount = state.updatable.size,
            message = state.message
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.listRowGap)
        ) {
            TvChoiceChip(
                selected = section == TvExtensionsSection.INSTALLED,
                onClick = { section = TvExtensionsSection.INSTALLED },
                label = stringResource(R.string.mk_tab_installed) + " (${state.installed.size})",
                focusRequester = if (section == TvExtensionsSection.INSTALLED) sectionRequester else null,
                leading = Icons.Default.CheckCircle
            )
            TvChoiceChip(
                selected = section == TvExtensionsSection.ALL,
                onClick = { section = TvExtensionsSection.ALL },
                label = stringResource(R.string.mk_tab_browse) + " (${state.catalog.extensions.size})",
                focusRequester = if (section == TvExtensionsSection.ALL) sectionRequester else null,
                leading = Icons.Default.Extension
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.gutter,
                end = dimens.gutter,
                top = dimens.rowGap,
                bottom = dimens.paddingLarge
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.listRowGap)
        ) {
            if (visible.isEmpty()) {
                item(key = "extensions_empty") {
                    TvEmptyMessage(
                        if (section == TvExtensionsSection.INSTALLED) {
                            stringResource(R.string.mk_no_sources)
                        } else {
                            stringResource(R.string.st_pull_repo_hint)
                        }
                    )
                }
            }

            items(visible, key = { it.key }) { extension ->
                TvExtensionItem(
                    extension = extension,
                    expanded = expandedKey == extension.key,
                    confirmingRemoval = pendingRemovalKey == extension.key,
                    focusRequester = if (extension.key == visible.first().key) {
                        firstRowRequester
                    } else {
                        null
                    },
                    onToggleExpanded = {
                        if (expandedKey == extension.key) {
                            expandedKey = null
                            pendingRemovalKey = null
                        } else {
                            expandedKey = extension.key
                            pendingRemovalKey = null
                        }
                    },
                    onArmRemoval = { pendingRemovalKey = extension.key },
                    onDisarmRemoval = { pendingRemovalKey = null },
                    onInstall = {
                        viewModel.install(extension)
                        expandedKey = null
                    },
                    onUpdate = {
                        viewModel.update(extension)
                        expandedKey = null
                    },
                    onEnabledChange = { enabled -> viewModel.setEnabled(extension, enabled) },
                    onUninstall = {
                        viewModel.uninstall(extension)
                        pendingRemovalKey = null
                        expandedKey = null
                    }
                )
            }
        }
    }
}

/** Counts, plus whatever the ViewModel last said (installed, removed, updated). */
@Composable
private fun TvExtensionsSummary(
    installedCount: Int,
    enabledCount: Int,
    updatableCount: Int,
    message: String?
) {
    val dimens = LocalTvDimens.current
    val counts = "${stringResource(R.string.st_installed)}: $installedCount · " +
        "${stringResource(R.string.st_enabled)}: $enabledCount"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter)
    ) {
        Text(
            text = if (message != null) "$counts · $message" else counts,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (message == null && updatableCount > 0) {
            Text(
                text = stringResource(R.string.st_updates_available, updatableCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * An extension, plus the action strip it opens.
 *
 * Only one row is expanded at a time, so the action strip never grows into a
 * wall of buttons: at 10 feet one open strip is already a full line of decisions.
 */
@Composable
private fun TvExtensionItem(
    extension: MarketplaceExtension,
    expanded: Boolean,
    confirmingRemoval: Boolean,
    focusRequester: FocusRequester?,
    onToggleExpanded: () -> Unit,
    onArmRemoval: () -> Unit,
    onDisarmRemoval: () -> Unit,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onUninstall: () -> Unit
) {
    val dimens = LocalTvDimens.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.paddingSmall / 2)) {
        TvListRow(
            onClick = onToggleExpanded,
            focusRequester = focusRequester,
            title = extension.manifest.name,
            supporting = extension.tvSubtitle(),
            leading = Icons.Default.Extension,
            selected = expanded,
            trailingText = when {
                !extension.manifest.isSupported -> stringResource(R.string.mk_unsupported)
                extension.hasUpdate -> stringResource(R.string.st_updated_to, extension.manifest.versionName)
                extension.isEnabled -> stringResource(R.string.er_active_player)
                extension.isInstalled -> stringResource(R.string.mk_paused)
                else -> null
            }
        )

        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.listRowGap),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                when {
                    !extension.isInstalled -> {
                        TvChoiceChip(
                            selected = false,
                            onClick = onInstall,
                            label = stringResource(R.string.action_install),
                            leading = Icons.Default.Add
                        )
                    }
                    extension.hasUpdate -> {
                        TvChoiceChip(
                            selected = true,
                            onClick = onUpdate,
                            label = stringResource(R.string.action_update)
                        )
                    }
                }

                if (extension.isInstalled) {
                    // The chip is filled when the source is live, and its label
                    // is the action OK performs: a viewer should never have to
                    // work out whether "Enabled" means on or "press to enable".
                    TvChoiceChip(
                        selected = extension.isEnabled,
                        onClick = { onEnabledChange(!extension.isEnabled) },
                        label = if (extension.isEnabled) {
                            stringResource(R.string.mk_paused)
                        } else {
                            stringResource(R.string.st_enabled)
                        }
                    )
                }

                if (confirmingRemoval) {
                    TvChoiceChip(
                        selected = true,
                        onClick = onUninstall,
                        label = stringResource(R.string.action_remove) + "?",
                        leading = Icons.Outlined.DeleteOutline
                    )
                    TvChoiceChip(
                        selected = false,
                        onClick = onDisarmRemoval,
                        label = stringResource(R.string.action_cancel)
                    )
                } else if (extension.isInstalled) {
                    TvChoiceChip(
                        selected = false,
                        onClick = onArmRemoval,
                        label = stringResource(R.string.action_remove),
                        leading = Icons.Outlined.DeleteOutline
                    )
                }
            }
        }
    }
}

/** The phone's `extensionSubtitle` plus what is actually installed right now. */
@Composable
private fun MarketplaceExtension.tvSubtitle(): String {
    val base = extensionSubtitle(this)
    return when {
        !isInstalled -> base
        hasUpdate -> "$base · ${stringResource(R.string.action_update)}"
        else -> "$base · ${stringResource(R.string.st_installed)}"
    }
}