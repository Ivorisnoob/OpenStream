package com.ivor.openstream.presentation.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import coil3.compose.AsyncImage
import com.ivor.openstream.data.local.dao.CustomListSummary
import com.ivor.openstream.ui.theme.ExpressiveShapes

/** Asks for a list name; used to create and to rename lists. */
@Composable
fun ListNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * "Add to list" from a title: Watch Later plus every custom list, each a checkbox row, and a way
 * to start a new list with the title in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    titleName: String,
    lists: List<CustomListSummary>,
    memberOf: Set<Long>,
    isInWatchLater: Boolean,
    onToggleWatchLater: () -> Unit,
    onToggleList: (listId: Long, add: Boolean) -> Unit,
    onCreateList: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = "Save $titleName to…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() }
            )
            LazyColumn {
                item(key = "watch-later") {
                    ListRow(
                        name = "Watch later",
                        detail = null,
                        checked = isInWatchLater,
                        icon = { Icon(if (isInWatchLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null) },
                        onToggle = { onToggleWatchLater() }
                    )
                }
                items(lists, key = { it.id }) { list ->
                    val checked = list.id in memberOf
                    ListRow(
                        name = list.name,
                        detail = itemCountLabel(list.itemCount),
                        checked = checked,
                        icon = { ListCover(list, Modifier.size(40.dp)) },
                        onToggle = { onToggleList(list.id, !checked) }
                    )
                }
                item(key = "new") {
                    ListItem(
                        headlineContent = { Text("New list", fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) { creating = true }
                    )
                }
            }
        }
    }
    if (creating) {
        ListNameDialog(
            title = "New list",
            confirmLabel = "Create",
            onConfirm = { name ->
                creating = false
                onCreateList(name)
            },
            onDismiss = { creating = false }
        )
    }
}

@Composable
private fun ListRow(
    name: String,
    detail: String?,
    checked: Boolean,
    icon: @Composable () -> Unit,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = icon,
        trailingContent = {
            Icon(
                if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
    )
}

/** Up to four posters in a grid; a list icon when the list is empty. */
@Composable
fun ListCover(list: CustomListSummary, modifier: Modifier = Modifier) {
    val posters = list.posters?.split('|')?.filter { it.isNotBlank() }.orEmpty()
    Surface(
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.aspectRatio(1f)
    ) {
        when {
            posters.isEmpty() -> Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            posters.size < 4 -> AsyncImage(
                model = "https://image.tmdb.org/t/p/w185${posters.first()}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else -> Column(Modifier.fillMaxSize()) {
                posters.chunked(2).forEach { row ->
                    Row(Modifier.weight(1f)) {
                        row.forEach { path ->
                            AsyncImage(
                                model = "https://image.tmdb.org/t/p/w154$path",
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A list tile for the Saved tab's row of lists. */
@Composable
fun ListCard(list: CustomListSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(128.dp)
            .clip(ExpressiveShapes.medium)
            .clickable(onClickLabel = "Open ${list.name}", onClick = onClick)
            // Keeps the text clear of the rounded bottom corners the clip above cuts.
            .padding(bottom = 10.dp)
    ) {
        ListCover(list, Modifier.fillMaxWidth())
        Text(
            text = list.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp)
        )
        Text(
            text = itemCountLabel(list.itemCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

/** The "New list" tile at the end of the Saved tab's row of lists. */
@Composable
fun NewListCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(128.dp)
            .clip(ExpressiveShapes.medium)
            .clickable(onClickLabel = "Create a list", onClick = onClick)
            .padding(bottom = 10.dp)
    ) {
        Surface(
            shape = ExpressiveShapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Text(
            text = "New list",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp)
        )
    }
}

fun itemCountLabel(count: Int): String = if (count == 1) "1 title" else "$count titles"

private const val MAX_NAME_LENGTH = 60
