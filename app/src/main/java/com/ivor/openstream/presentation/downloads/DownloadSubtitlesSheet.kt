package com.ivor.openstream.presentation.downloads

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.data.subtitles.SavedSubtitle
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.ui.theme.ExpressiveShapes

/**
 * Subtitles for one finished download: what is saved on the device (these play offline), results
 * from the subtitle sites to tick and save, and importing a file the user already has.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadSubtitlesSheet(
    download: DownloadEntity,
    viewModel: DownloadSubtitlesViewModel,
    onDismiss: () -> Unit
) {
    LaunchedEffect(download.downloadId) { viewModel.open(download) }
    val state by viewModel.uiState.collectAsState()
    var pickingLanguage by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFile)
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.close()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            item(key = "header") { SheetHeader(download) }

            // On this device
            item(key = "saved-title") {
                SectionHeader(
                    title = "On this device",
                    detail = if (state.saved.isEmpty()) null else "Works offline",
                    action = {
                        FilledTonalButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.isImporting
                        ) {
                            if (state.isImporting) {
                                LoadingIndicator(Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Import file")
                        }
                    }
                )
            }
            if (state.saved.isEmpty()) {
                item(key = "saved-empty") {
                    HintCard(
                        icon = { Icon(Icons.Default.ClosedCaption, contentDescription = null) },
                        text = "Nothing saved yet. Pick subtitles below, or import an SRT, VTT or ASS file you already have."
                    )
                }
            }
            itemsIndexed(state.saved, key = { _, item -> "saved:${item.id}" }) { index, saved ->
                SavedRow(
                    saved = saved,
                    index = index,
                    count = state.saved.size,
                    onDelete = { viewModel.delete(saved) },
                    modifier = Modifier.animateItem()
                )
            }

            state.message?.let { message ->
                item(key = "message") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }

            // Online
            item(key = "online-title") {
                SectionHeader(
                    title = "Find online",
                    detail = "OpenSubtitles and SubSource",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            item(key = "filters") {
                FilterRow(
                    languages = state.languages,
                    languageFilter = state.languageFilter,
                    hideHearingImpaired = state.hideHearingImpaired,
                    onLanguage = viewModel::setLanguageFilter,
                    onHideHearingImpaired = viewModel::setHideHearingImpaired,
                    onOtherLanguage = { pickingLanguage = true }
                )
            }

            val groups = state.groupedOnline
            val searching = state.isSearching ||
                (state.languageFilter != null && state.languageFilter in state.searchingLanguages)
            when {
                searching && groups.isEmpty() -> item(key = "searching") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                groups.isEmpty() -> item(key = "online-empty") {
                    val language = state.languageFilter
                    HintCard(
                        icon = { Icon(if (state.online.isEmpty()) Icons.Default.WifiOff else Icons.Default.Search, contentDescription = null) },
                        text = when {
                            language != null -> "No ${SavedSubtitleRepository.languageName(language)} subtitles found for this one."
                            state.online.isEmpty() -> "Nothing found. Searching needs a connection; saved subtitles above still work offline."
                            else -> "Everything here is hidden by the filters."
                        },
                        action = {
                            TextButton(onClick = {
                                if (language != null) viewModel.searchLanguage(language) else viewModel.retrySearch()
                            }) { Text("Search again") }
                        }
                    )
                }
            }
            groups.forEach { (language, subtitles) ->
                item(key = "group:$language") {
                    LanguageHeader(
                        name = language?.let { SavedSubtitleRepository.languageName(it) } ?: "Other",
                        count = subtitles.size,
                        isSearching = language != null && language in state.searchingLanguages,
                        onSearchMore = language?.let { code -> { viewModel.searchLanguage(code) } }
                    )
                }
                itemsIndexed(subtitles, key = { _, item -> "online:${item.id}:${item.url}" }) { index, subtitle ->
                    OnlineRow(
                        subtitle = subtitle,
                        index = index,
                        count = subtitles.size,
                        isSaved = subtitle.id in state.savedOriginIds,
                        isSaving = subtitle.id in state.saving,
                        isSelected = subtitle.id in state.selected,
                        onToggle = { viewModel.toggle(subtitle) }
                    )
                }
            }
            if (searching && groups.isNotEmpty()) {
                item(key = "searching-more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(40.dp))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.selected.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SelectionBar(
                count = state.selected.size,
                onClear = viewModel::clearSelection,
                onSave = viewModel::saveSelected
            )
        }
        Spacer(Modifier.navigationBarsPadding())
    }

    if (pickingLanguage) {
        LanguagePickerDialog(
            onPick = { language ->
                pickingLanguage = false
                viewModel.searchLanguage(language)
            },
            onDismiss = { pickingLanguage = false }
        )
    }
}

@Composable
private fun SheetHeader(download: DownloadEntity) {
    Row(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = ExpressiveShapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.padding(12.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = download.sheetTitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

@Composable
private fun HintCard(
    icon: @Composable () -> Unit,
    text: String,
    action: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { it() }
            }
        }
    }
}

@Composable
private fun FilterRow(
    languages: List<String>,
    languageFilter: String?,
    hideHearingImpaired: Boolean,
    onLanguage: (String?) -> Unit,
    onHideHearingImpaired: (Boolean) -> Unit,
    onOtherLanguage: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        item(key = "all") {
            FilterChip(
                selected = languageFilter == null,
                onClick = { onLanguage(null) },
                label = { Text("All languages") }
            )
        }
        // A language searched by name stays in the row even before results come back.
        val shown = if (languageFilter != null && languageFilter !in languages) listOf(languageFilter) + languages else languages
        items(shown, key = { it }) { language ->
            FilterChip(
                selected = languageFilter == language,
                onClick = { onLanguage(if (languageFilter == language) null else language) },
                label = { Text(SavedSubtitleRepository.languageName(language)) }
            )
        }
        item(key = "other") {
            AssistChip(
                onClick = onOtherLanguage,
                label = { Text("Another language") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        item(key = "sdh") {
            FilterChip(
                selected = hideHearingImpaired,
                onClick = { onHideHearingImpaired(!hideHearingImpaired) },
                label = { Text("Hide SDH") },
                leadingIcon = {
                    Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageHeader(name: String, count: Int, isSearching: Boolean, onSearchMore: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "  $count",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        when {
            isSearching -> LoadingIndicator(Modifier.size(32.dp))
            onSearchMore != null -> TextButton(onClick = onSearchMore) { Text("More releases") }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SavedRow(
    saved: SavedSubtitle,
    index: Int,
    count: Int,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(),
        modifier = modifier,
        leadingContent = {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        supportingContent = {
            val detail = listOfNotNull(
                saved.release,
                saved.origin,
                "SDH".takeIf { saved.isHearingImpaired && "(SDH)" !in saved.label }
            ).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${saved.label} subtitles")
            }
        }
    ) {
        Text(saved.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlineRow(
    subtitle: SubtitleDto,
    index: Int,
    count: Int,
    isSaved: Boolean,
    isSaving: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val label = subtitle.display ?: subtitle.language?.let { SavedSubtitleRepository.languageName(it) } ?: "Subtitles"
    SegmentedListItem(
        checked = isSelected,
        onCheckedChange = { onToggle() },
        enabled = !isSaved && !isSaving,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(),
        leadingContent = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                when {
                    isSaved -> Icon(Icons.Default.CheckCircle, contentDescription = "Saved", tint = MaterialTheme.colorScheme.primary)
                    isSaving -> LoadingIndicator(Modifier.size(32.dp))
                    // The row itself toggles; the box only shows the state.
                    else -> Checkbox(checked = isSelected, onCheckedChange = null)
                }
            }
        },
        supportingContent = {
            Text(
                text = when {
                    isSaved -> "Saved · " + (subtitle.release ?: subtitle.source.orEmpty())
                    isSaving -> "Saving…"
                    else -> listOfNotNull(subtitle.release, subtitle.source).joinToString(" · ")
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onSave: () -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (count == 1) "1 selected" else "$count selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) { Text("Clear") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (count == 1) "Save" else "Save $count")
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val languages = remember {
        PICKER_LANGUAGES.map { it to SavedSubtitleRepository.languageName(it) }.sortedBy { it.second }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search a language") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(languages, key = { it.first }) { (code, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) { onPick(code) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun DownloadEntity.sheetTitle(): String =
    if (mediaType == "movie") {
        displayTitle
    } else {
        "$displayTitle · S$season E$episode" + (episodeTitle?.let { " · $it" } ?: "")
    }

/** ISO 639-1, plus OpenSubtitles' "pb" for Brazilian Portuguese. */
private val PICKER_LANGUAGES = listOf(
    "en", "es", "fr", "de", "it", "pt", "pb", "nl", "sv", "no", "da", "fi", "is", "pl", "cs", "sk",
    "sl", "hr", "sr", "bs", "bg", "mk", "ro", "hu", "el", "tr", "ru", "uk", "et", "lv", "lt", "sq",
    "ar", "he", "fa", "ur", "hi", "bn", "ta", "te", "ml", "kn", "mr", "si", "ne", "zh", "ja", "ko",
    "th", "vi", "id", "ms", "tl", "my", "km", "ka", "hy", "eu", "ca", "gl"
)
