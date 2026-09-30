package com.ivor.openstream.presentation.profiles

import com.ivor.openstream.presentation.components.CenteredListBox
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.domain.model.Profile
import com.ivor.openstream.presentation.components.LibraryHeader
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Who's watching?": big avatar tiles, one per profile, plus a way to manage them. */
@Composable
fun WhoIsWatchingScreen(
    profiles: List<Profile>,
    activeId: Long?,
    onSelect: (Profile) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = "Who's watching?",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileTile(profile = profile, selected = profile.id == activeId, onClick = { onSelect(profile) })
                }
            }
            OutlinedButton(onClick = onManage, shape = ExpressiveShapes.medium, modifier = Modifier.padding(bottom = 24.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage profiles")
            }
        }
    }
}

@Composable
private fun ProfileTile(profile: Profile, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.92f, tween(300), label = "profileTileScale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(ExpressiveShapes.large)
            .clickable(role = Role.Button, onClickLabel = "Watch as ${profile.name}", onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp)
    ) {
        ProfileAvatarBadge(profile.avatar, size = 120.dp * scale)
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (profile.isKids) KidsLabel()
    }
}

@Composable
private fun KidsLabel() {
    Surface(shape = ExpressiveShapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Icon(Icons.Default.ChildCare, contentDescription = null, modifier = Modifier.size(14.dp))
            Text("Kids", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/**
 * The active profile's avatar in the Home top bar. Tap switches profile; on a kids profile it
 * takes a [KIDS_HOLD_MS] hold instead (a ring fills while holding), so children stay in.
 */
@Composable
fun ProfileSwitchButton(
    profile: Profile?,
    onSwitch: () -> Unit,
    onKidsTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(if (holding) KIDS_HOLD_MS.toInt() else 150),
        label = "kidsHold"
    )
    val isKids = profile?.isKids == true
    val label = when {
        profile == null -> "Profiles"
        isKids -> "Kids profile ${profile.name}. Hold to exit child protection."
        else -> "Profile ${profile.name}. Switch profile."
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(ExpressiveShapes.extraLarge)
            .semantics {
                contentDescription = label
                if (isKids) onLongClick("Exit kids profile") { onSwitch(); true }
            }
            .then(
                if (isKids) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            holding = true
                            var job: Job? = null
                            job = scope.launch {
                                delay(KIDS_HOLD_MS)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                holding = false
                                onSwitch()
                            }
                            val up = waitForUpOrCancellation()
                            if (job.isActive) {
                                job.cancel()
                                holding = false
                                if (up != null) onKidsTap()
                            }
                        }
                    }
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onSwitch)
                }
            )
    ) {
        if (progress > 0f) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
        }
        ProfileAvatarBadge(profile?.avatar ?: ProfileAvatar.FOX.key, size = SmallAvatar)
    }
}

/** Settings → Profiles: add, rename, change avatar, mark as kids, delete (confirmed). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManageProfilesScreen(
    viewModel: ProfilesViewModel,
    onBackClick: () -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.activeProfile.collectAsState()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenteredListBox(Modifier.fillMaxSize(), minGutter = 0.dp, maxContentWidth = 720.dp) { gutter ->
            LazyColumn(contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 120.dp)) {
                item(key = "header") {
                    LibraryHeader(
                        title = "Profiles",
                        subtitle = "Each profile has its own Watch Later, lists, history and Continue Watching. Downloads are shared.",
                        onBackClick = onBackClick
                    )
                }
                items(profiles, key = { it.id }) { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    "Watching now".takeIf { profile.id == active?.id },
                                    "Kids: rated G, PG, TV-Y to TV-PG only".takeIf { profile.isKids }
                                ).joinToString(" · ").ifEmpty { "Tap to edit" }
                            )
                        },
                        leadingContent = { ProfileAvatarBadge(profile.avatar, size = 48.dp) },
                        trailingContent = {
                            if (profiles.size > 1) {
                                IconButton(onClick = { deleting = profile }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ${profile.name}")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable(onClickLabel = "Edit ${profile.name}") { editing = profile }
                    )
                }
                item(key = "add") {
                    Button(
                        onClick = { adding = true },
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add profile")
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }

    if (adding) {
        ProfileEditorDialog(
            title = "New profile",
            initial = null,
            onSave = { name, avatar, kids ->
                adding = false
                viewModel.create(name, avatar, kids)
            },
            onDismiss = { adding = false }
        )
    }
    editing?.let { profile ->
        ProfileEditorDialog(
            title = "Edit profile",
            initial = profile,
            onSave = { name, avatar, kids ->
                editing = null
                viewModel.update(profile.id, name, avatar, kids)
            },
            onDismiss = { editing = null }
        )
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("Its Watch Later, lists, history and Continue Watching are deleted too. Downloads stay.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.delete(profile)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileEditorDialog(
    title: String,
    initial: Profile?,
    onSave: (name: String, avatar: String, isKids: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var avatar by remember { mutableStateOf(initial?.avatar ?: ProfileAvatar.entries.random().key) }
    var isKids by remember { mutableStateOf(initial?.isKids ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Avatar",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                ProfileAvatar.entries.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { option ->
                            val selected = option.key == avatar
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(ExpressiveShapes.medium)
                                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent)
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = { avatar = option.key })
                                    .semantics { contentDescription = "Avatar ${option.key}" }
                            ) {
                                ProfileAvatarBadge(option.key, size = if (selected) 42.dp else 36.dp)
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(ExpressiveShapes.small)
                        .selectable(selected = isKids, role = Role.Switch, onClick = { isKids = !isKids })
                        .padding(vertical = 8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Kids profile", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Only titles rated for children. Hold the avatar to leave.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isKids, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), avatar, isKids) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** How long a kids profile's avatar must be held to leave it. */
const val KIDS_HOLD_MS = 1_500L
