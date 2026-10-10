package com.ivor.openstream.presentation.profiles

import com.ivor.openstream.presentation.components.CenteredListBox
import com.ivor.openstream.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.data.settings.ProfilePin
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
    viewModel: ProfilesViewModel,
    onSelect: (Profile) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingProfile by remember { mutableStateOf<Profile?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var lockSeconds by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val wrongText = stringResource(R.string.pin_wrong)
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
                text = stringResource(R.string.pf_who_watching),
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
                    ProfileTile(
                        profile = profile,
                        selected = profile.id == activeId,
                        onClick = {
                            if (profile.hasPin) {
                                pendingProfile = profile
                                pinInput = ""
                                pinError = null
                                lockSeconds = null
                            } else {
                                onSelect(profile)
                            }
                        }
                    )
                }
            }
            OutlinedButton(onClick = onManage, shape = ExpressiveShapes.medium, modifier = Modifier.padding(bottom = 24.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_manage_profiles))
            }
        }
    }

    pendingProfile?.let { pending ->
        PinPadDialog(
            profileName = pending.name,
            lockedSeconds = lockSeconds,
            error = pinError,
            pin = pinInput,
            onDigit = { digit ->
                if (lockSeconds == null && pinInput.length < ProfilePin.MAX_LENGTH && digit.isDigit()) {
                    pinInput += digit
                    pinError = null
                }
            },
            onBackspace = {
                if (pinInput.isNotEmpty()) {
                    pinInput = pinInput.dropLast(1)
                    pinError = null
                }
            },
            onClear = {
                pinInput = ""
                pinError = null
            },
            onSubmit = {
                scope.launch {
                    when (val result = viewModel.checkPin(pending, pinInput)) {
                        ProfilesViewModel.PinCheck.Ok -> {
                            pendingProfile = null
                            onSelect(pending)
                        }
                        ProfilesViewModel.PinCheck.Wrong -> {
                            pinError = wrongText
                            pinInput = ""
                        }
                        is ProfilesViewModel.PinCheck.Locked -> {
                            lockSeconds = result.secondsLeft
                            pinError = null
                        }
                    }
                }
            },
            onDismiss = { pendingProfile = null }
        )
    }
}

@Composable
private fun ProfileTile(profile: Profile, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.92f, tween(300), label = "profileTileScale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(ExpressiveShapes.large)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.pf_watch_as, profile.name), onClick = onClick)
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
        if (profile.isKids || profile.hasPin) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (profile.isKids) KidsLabel()
                if (profile.hasPin) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.pin_on),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KidsLabel() {
    Surface(shape = ExpressiveShapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Icon(Icons.Default.ChildCare, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.pf_kids), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
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
        profile == null -> stringResource(R.string.st_profiles)
        isKids -> stringResource(R.string.pf_kids_tile_desc, profile.name)
        else -> stringResource(R.string.pf_profile_tile_desc, profile.name)
    }
    val exitKidsLabel = stringResource(R.string.pf_exit_kids)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(ExpressiveShapes.extraLarge)
            .semantics {
                contentDescription = label
                if (isKids) onLongClick(exitKidsLabel) { onSwitch(); true }
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
                        title = stringResource(R.string.st_profiles),
                        subtitle = stringResource(R.string.st_profiles_each),
                        onBackClick = onBackClick
                    )
                }
                items(profiles, key = { it.id }) { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    stringResource(R.string.er_watching_now).takeIf { profile.id == active?.id },
                                    stringResource(R.string.pf_kids_rated).takeIf { profile.isKids },
                                    stringResource(R.string.pin_on).takeIf { profile.hasPin }
                                ).joinToString(" · ").ifEmpty { stringResource(R.string.action_tap_to_edit) }
                            )
                        },
                        leadingContent = { ProfileAvatarBadge(profile.avatar, size = 48.dp) },
                        trailingContent = {
                            if (profiles.size > 1) {
                                IconButton(onClick = { deleting = profile }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_profile, profile.name))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.pf_edit_profile, profile.name)) { editing = profile }
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
                        Text(stringResource(R.string.st_add_profile))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }

    if (adding) {
        ProfileEditorDialog(
            title = stringResource(R.string.st_new_profile),
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
            title = stringResource(R.string.st_edit_profile),
            initial = profile,
            onSave = { name, avatar, kids ->
                editing = null
                viewModel.update(profile.id, name, avatar, kids)
            },
            onDismiss = { editing = null },
            onSavePin = { pin -> viewModel.setPin(profile.id, pin) },
            onRemovePin = { viewModel.clearPin(profile.id) }
        )
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.pf_delete_title, profile.name)) },
            text = { Text(stringResource(R.string.pf_delete_profile_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.delete(profile)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun ProfileEditorDialog(
    title: String,
    initial: Profile?,
    onSave: (name: String, avatar: String, isKids: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit = {},
    onRemovePin: () -> Unit = {}
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var avatar by remember { mutableStateOf(initial?.avatar ?: ProfileAvatar.entries.random().key) }
    var isKids by remember { mutableStateOf(initial?.isKids ?: false) }
    var showPinSet by remember { mutableStateOf(false) }
    var hasPinUi by remember { mutableStateOf(initial?.hasPin ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(stringResource(R.string.pf_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.pf_avatar),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                ProfileAvatar.entries.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { option ->
                            val selected = option.key == avatar
                            val avatarDescription = stringResource(R.string.cd_avatar, option.key)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(ExpressiveShapes.medium)
                                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent)
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = { avatar = option.key })
                                    .semantics { contentDescription = avatarDescription }
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
                        Text(stringResource(R.string.pf_kids_profile), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.pf_kids_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isKids, onCheckedChange = null)
                }
                if (initial != null) {
                    if (hasPinUi) {
                        Text(
                            stringResource(R.string.pin_on),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            OutlinedButton(onClick = { showPinSet = true }) { Text(stringResource(R.string.pin_set)) }
                            OutlinedButton(onClick = {
                                hasPinUi = false
                                onRemovePin()
                            }) { Text(stringResource(R.string.pin_remove)) }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showPinSet = true },
                            modifier = Modifier.padding(top = 16.dp)
                        ) { Text(stringResource(R.string.pin_set)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), avatar, isKids) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
    if (showPinSet) {
        PinSetDialog(
            onSave = {
                showPinSet = false
                hasPinUi = true
                onSavePin(it)
            },
            onDismiss = { showPinSet = false }
        )
    }
}

/** Two-field PIN entry: the code is saved only when both fields hold the same 4-8 digits. */
@Composable
private fun PinSetDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var enter by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = enter.isNotEmpty() && confirm.isNotEmpty() && enter != confirm
    val canSave = ProfilePin.isValidPin(enter) && enter == confirm
    val pinKeyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_set)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = enter,
                    onValueChange = { enter = it.filter(Char::isDigit).take(ProfilePin.MAX_LENGTH) },
                    label = { Text(stringResource(R.string.pin_enter_new)) },
                    placeholder = { Text(stringResource(R.string.pin_hint_digits)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = pinKeyboard,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(ProfilePin.MAX_LENGTH) },
                    label = { Text(stringResource(R.string.pin_confirm)) },
                    placeholder = { Text(stringResource(R.string.pin_hint_digits)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = pinKeyboard,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mismatch) {
                    Text(
                        stringResource(R.string.pin_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(enter) }, enabled = canSave) { Text(stringResource(R.string.pin_set)) }
        }
    )
}

/**
 * Number-pad PIN entry for a locked profile. While [lockedSeconds] is non-null the pad is
 * disabled and the lockout message is shown instead of the hint or error.
 */
@Composable
private fun PinPadDialog(
    profileName: String,
    lockedSeconds: Long? = null,
    error: String? = null,
    pin: String = "",
    attemptsLeftHint: String? = null,
    onDigit: (Char) -> Unit = {},
    onBackspace: () -> Unit = {},
    onClear: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val locked = lockedSeconds != null
    val canSubmit = !locked && pin.length in ProfilePin.MIN_LENGTH..ProfilePin.MAX_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.pin_for_name, profileName),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    repeat(ProfilePin.MAX_LENGTH) { i ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < pin.length) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                        )
                    }
                }
                when {
                    lockedSeconds != null -> Text(
                        stringResource(R.string.pin_locked_seconds, lockedSeconds),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    error != null -> Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    attemptsLeftHint != null -> Text(
                        attemptsLeftHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    else -> Text(
                        stringResource(R.string.pin_hint_digits),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    listOf(
                        listOf('1', '2', '3'),
                        listOf('4', '5', '6'),
                        listOf('7', '8', '9')
                    ).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { digit ->
                                PinKey(label = digit.toString(), enabled = !locked, onClick = { onDigit(digit) })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onClear, enabled = !locked && pin.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Clear, contentDescription = null)
                        }
                        PinKey(label = "0", enabled = !locked, onClick = { onDigit('0') })
                        IconButton(onClick = onBackspace, enabled = !locked && pin.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            IconButton(onClick = onSubmit, enabled = canSubmit) {
                Icon(Icons.Filled.Check, contentDescription = null)
            }
        }
    )
}

@Composable
private fun RowScope.PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

/** How long a kids profile's avatar must be held to leave it. */
const val KIDS_HOLD_MS = 1_500L
