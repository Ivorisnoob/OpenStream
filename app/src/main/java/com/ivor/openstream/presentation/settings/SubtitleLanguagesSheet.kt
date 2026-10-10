package com.ivor.openstream.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import com.ivor.openstream.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository

/** "English, Spanish", or "Off" when no language is picked. */
@Composable
internal fun subtitleLanguagesSummary(languages: List<String>): String =
    if (languages.isEmpty()) stringResource(R.string.misc_off)
    else languages.joinToString(", ") { SavedSubtitleRepository.languageName(it) }

/**
 * Which subtitle languages every new download saves for offline use. Changes apply as they are
 * made; downloads already finished keep what they have (the Downloads sheet adds more).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubtitleLanguagesSheet(
    selected: List<String>,
    fromSites: Boolean,
    onLanguagesChange: (List<String>) -> Unit,
    onFromSitesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // Picked languages first, in the order they were when the sheet opened, so rows don't jump.
    val ordered = remember {
        val all = SavedSubtitleRepository.COMMON_LANGUAGES + selected.filter { it !in SavedSubtitleRepository.COMMON_LANGUAGES }
        all.distinct().sortedWith(
            compareBy<String> { it !in selected }.thenBy { SavedSubtitleRepository.languageName(it) }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            item(key = "header") {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
                    Text(
                        text = stringResource(R.string.dl_subtitles_with),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = if (selected.isEmpty()) {
                            stringResource(R.string.dl_no_language)
                        } else {
                            stringResource(R.string.dl_subtitles_hint)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item(key = "sites") {
                SegmentedListItem(
                    checked = fromSites,
                    onCheckedChange = onFromSitesChange,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    colors = ListItemDefaults.segmentedColors(),
                    leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                    supportingContent = {
                        Text(
                            if (fromSites) {
                                stringResource(R.string.dl_video_source_short)
                            } else {
                                stringResource(R.string.dl_only_source)
                            }
                        )
                    },
                    trailingContent = { Switch(checked = fromSites, onCheckedChange = null) }
                ) {
                    Text(stringResource(R.string.dl_also_from_sites), fontWeight = FontWeight.SemiBold)
                }
            }

            item(key = "languages-title") {
                Text(
                    text = stringResource(R.string.st_languages),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
                        .semantics { heading() }
                )
            }
            itemsIndexed(ordered, key = { _, code -> code }) { index, code ->
                val checked = code in selected
                SegmentedListItem(
                    checked = checked,
                    onCheckedChange = { on ->
                        onLanguagesChange(if (on) selected + code else selected - code)
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = ordered.size),
                    colors = ListItemDefaults.segmentedColors(),
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) }
                ) {
                    Text(
                        SavedSubtitleRepository.languageName(code),
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
