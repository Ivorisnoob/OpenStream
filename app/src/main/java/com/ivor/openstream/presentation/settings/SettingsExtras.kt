package com.ivor.openstream.presentation.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.openstream.R
import com.ivor.openstream.data.downloads.DownloadStorage
import com.ivor.openstream.data.settings.CaptionStyleSettings
import com.ivor.openstream.data.settings.PlayerPreferences
import com.ivor.openstream.data.settings.ProfilePin
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import java.util.Locale

// Rows and dialogs for the settings that need more than a switch or a choice.

// region Captions

/**
 * What subtitles will look like, over a stand-in for a video frame: the scene behind them is drawn
 * dark like a film still, so the size, plate and colour read as they will in the player.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CaptionPreviewRow(
    style: CaptionStyleSettings,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    highlight: Float = 0f
) {
    val sample = stringResource(R.string.st_caption_preview)
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = highlightedContainer(highlight),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(
                    // A video frame is dark whatever the theme, which is what captions are read over.
                    Brush.verticalGradient(listOf(Color(0xFF2B3A4A), Color(0xFF101418))),
                    RoundedCornerShape(16.dp)
                )
                .clearAndSetSemantics { contentDescription = sample },
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = sample,
                style = TextStyle(
                    color = Color(style.textColor.argb),
                    fontSize = style.textSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(color = Color.Black, blurRadius = 4f)
                ),
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    // The position setting raises captions by a share of the picture's height.
                    .padding(bottom = (style.verticalOffset * 140).dp)
                    .background(Color.Black.copy(alpha = style.backgroundOpacity), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** A row with a slider under its title and the current value at the end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SliderRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    highlight: Float = 0f
) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = highlightedContainer(highlight),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RowIcon(icon)
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = title }
            )
        }
    }
}

// endregion

// region Languages

/** The language's own name in the app's language, such as "Spanish" or "Japanese". */
internal fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayName(Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        .ifBlank { code }

/**
 * Picks the subtitle or audio language every video starts with. Null lets each video decide; for
 * subtitles [allowOff] adds "Off", which keeps them off.
 */
@Composable
internal fun LanguagePickerDialog(
    title: String,
    current: String?,
    allowOff: Boolean,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val auto = stringResource(R.string.st_lang_auto)
    val off = stringResource(R.string.misc_off)
    val codes = remember(current) {
        val common = SavedSubtitleRepository.COMMON_LANGUAGES
        val extra = listOfNotNull(current?.takeIf { it != PlayerPreferences.SUBTITLES_OFF && it !in common })
        common + extra
    }
    val options: List<Pair<String?, String>> = buildList {
        add(null to auto)
        if (allowOff) add(PlayerPreferences.SUBTITLES_OFF to off)
        codes.forEach { add(it to languageName(it)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp).selectableGroup()) {
                itemsIndexed(options, key = { _, option -> option.first ?: "auto" }) { index, (code, label) ->
                    Box(Modifier.padding(top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap)) {
                        RadioRow(
                            index = index,
                            count = options.size,
                            icon = Icons.Default.Translate,
                            title = label,
                            supporting = null,
                            selected = code == current,
                            onClick = { onSelect(code) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// endregion

// region Home rows

/**
 * One Home row: shown or not (the whole left part toggles), and arrows to move it. The arrows
 * carry the row's name, so a screen reader says what moves where.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeRowItem(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    shown: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onShownChange: (Boolean) -> Unit,
    onMove: (delta: Int) -> Unit,
    modifier: Modifier = Modifier,
    highlight: Float = 0f
) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = highlightedContainer(highlight),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .toggleable(value = shown, role = Role.Switch, onValueChange = onShownChange)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            ) {
                RowIcon(icon)
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = shown, onCheckedChange = null)
            }
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.st_move_up, title))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.st_move_down, title))
            }
        }
    }
}

// endregion

// region Storage

/**
 * Where downloads live. Picking another volume asks first, then moves everything with a progress
 * bar; the app restarts when the move is done, and the dialog cannot be closed while it runs.
 */
@Composable
internal fun StorageLocationDialog(
    volumes: List<DownloadStorage.Volume>,
    currentRoot: String,
    downloadsBytes: Long,
    moveProgress: Float?,
    onMove: (DownloadStorage.Volume) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf(volumes.firstOrNull { it.root.absolutePath == currentRoot }) }
    val moving = moveProgress != null
    AlertDialog(
        onDismissRequest = { if (!moving) onDismiss() },
        title = { Text(stringResource(if (moving) R.string.st_storage_moving else R.string.st_storage_location)) },
        text = {
            if (moving) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(progress = { moveProgress ?: 0f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.st_storage_moving_body, (((moveProgress ?: 0f) * 100).toInt())),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Column(Modifier.selectableGroup()) {
                    volumes.forEachIndexed { index, volume ->
                        Box(Modifier.padding(top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap)) {
                            RadioRow(
                                index = index,
                                count = volumes.size,
                                icon = if (volume.removable) Icons.Default.SdCard else Icons.Default.Smartphone,
                                title = stringResource(if (volume.removable) R.string.st_storage_sd else R.string.st_storage_internal),
                                supporting = stringResource(
                                    R.string.st_storage_free,
                                    Formatter.formatShortFileSize(context, volume.freeBytes),
                                    Formatter.formatShortFileSize(context, volume.totalBytes)
                                ),
                                selected = picked == volume,
                                onClick = { picked = volume }
                            )
                        }
                    }
                    if (volumes.none { it.removable }) {
                        Text(
                            stringResource(R.string.st_storage_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    val target = picked
                    if (target != null && target.root.absolutePath != currentRoot) {
                        Text(
                            stringResource(
                                R.string.st_storage_move_body,
                                Formatter.formatShortFileSize(context, downloadsBytes)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            val target = picked
            if (!moving) {
                TextButton(
                    onClick = { target?.let(onMove) },
                    enabled = target != null && target.root.absolutePath != currentRoot
                ) { Text(stringResource(R.string.st_storage_move)) }
            }
        },
        dismissButton = {
            if (!moving) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// endregion

// region Kids lock

/**
 * Sets the parental PIN (typed twice), or, when one is set, changes or removes it. The PIN is
 * asked before a kids profile can switch to a profile without its own PIN, or manage profiles.
 */
@Composable
internal fun KidsLockDialog(
    hasLock: Boolean,
    onSet: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var choosing by rememberSaveable { mutableStateOf(!hasLock) }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

    if (!choosing) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.st_kids_lock)) },
            text = { Text(stringResource(R.string.st_kids_lock_on_body)) },
            confirmButton = {
                TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.st_kids_lock_change)) }
            },
            dismissButton = {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.st_kids_lock_turn_off)) }
            }
        )
        return
    }

    val confirming = first != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.st_kids_lock)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (confirming) R.string.st_kids_lock_confirm else R.string.st_kids_lock_new))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { typed ->
                        pin = typed.filter(Char::isDigit).take(ProfilePin.MAX_LENGTH)
                        error = false
                    },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.st_kids_lock_mismatch)) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text(stringResource(R.string.pin_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ProfilePin.isValidPin(pin),
                onClick = {
                    val chosen = first
                    when {
                        chosen == null -> {
                            first = pin
                            pin = ""
                        }
                        chosen == pin -> onSet(pin)
                        else -> {
                            error = true
                            first = null
                            pin = ""
                        }
                    }
                }
            ) { Text(stringResource(if (confirming) R.string.action_save else R.string.action_next)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// endregion

// region Licences

private data class Library(val name: String, val author: String, val license: String?, val url: String)

private val LIBRARIES = listOf(
    Library("Jetpack Compose and Material 3", "Google", "Apache License 2.0", "https://developer.android.com/jetpack/compose"),
    Library("AndroidX (Activity, Core, Lifecycle, Navigation, Room)", "Google", "Apache License 2.0", "https://github.com/androidx/androidx"),
    Library("Media3 and ExoPlayer", "Google", "Apache License 2.0", "https://github.com/androidx/media"),
    Library("Compose for TV", "Google", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/tv"),
    Library("Material Components for Android", "Google", "Apache License 2.0", "https://github.com/material-components/material-components-android"),
    Library("Dagger and Hilt", "Google", "Apache License 2.0", "https://github.com/google/dagger"),
    Library("Kotlin", "JetBrains", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
    Library("kotlinx.coroutines", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Library("kotlinx.serialization", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    Library("OkHttp", "Square", "Apache License 2.0", "https://github.com/square/okhttp"),
    Library("Retrofit", "Square", "Apache License 2.0", "https://github.com/square/retrofit"),
    Library("Coil", "Coil Contributors", "Apache License 2.0", "https://github.com/coil-kt/coil"),
    Library("Material Motion for Compose", "fornewid", "Apache License 2.0", "https://github.com/fornewid/material-motion-compose"),
    Library("SmoothMotion", "Ivor", null, "https://github.com/Ivorisnoob/smoothmotion-android")
)

/** OpenStream's own licence, the libraries it is built with, and the TMDB notice. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LicensesSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            item(key = "title") {
                Text(
                    stringResource(R.string.st_licenses),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 8.dp, bottom = 8.dp)
                        .semantics { heading() }
                )
                Text(
                    stringResource(R.string.st_license_app),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                )
            }
            itemsIndexed(LIBRARIES, key = { _, library -> library.name }) { index, library ->
                Box(Modifier.padding(top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap)) {
                    SegmentedListItem(
                        onClick = { runCatching { uriHandler.openUri(library.url) } },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = LIBRARIES.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        supportingContent = {
                            Text(listOfNotNull(library.author, library.license ?: stringResource(R.string.st_license_see_project)).joinToString(" · "))
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    ) {
                        Text(library.name, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item(key = "tmdb") {
                Column {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        stringResource(R.string.st_license_tmdb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

// endregion

/** The rows' background, pulsed toward the primary container when a search result lands on it. */
@Composable
private fun highlightedContainer(highlight: Float): Color =
    if (highlight > 0f) {
        androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceBright, MaterialTheme.colorScheme.primaryContainer, highlight)
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }
