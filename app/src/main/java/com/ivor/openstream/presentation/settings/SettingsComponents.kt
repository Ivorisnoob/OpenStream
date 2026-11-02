package com.ivor.openstream.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ivor.openstream.presentation.components.ConnectedChoiceGroup
import com.ivor.openstream.ui.theme.ExpressiveShapes

// Building blocks shared by the Settings page, its search results and the video shader screen.

/** A titled group of segmented rows inside a section, with an optional busy spinner. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsGroupLabel(title: String, busy: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (busy) LoadingIndicator(modifier = Modifier.size(20.dp))
    }
}

/** The explanatory note under a group of rows. */
@Composable
internal fun SettingsFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
    )
}

/** A titled group of segmented rows with an optional explanatory footer, laid out as one block. */
@Composable
internal fun SettingsGroup(
    title: String,
    footer: String? = null,
    busy: Boolean = false,
    rows: @Composable ColumnScope.() -> Unit
) {
    Column {
        SettingsGroupLabel(title, busy)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = rows)
        footer?.let { SettingsFooter(it) }
    }
}

/**
 * A row's background. [highlight] (0 to 1) blends it toward the primary container: the pulse that
 * shows where a search result landed.
 */
@Composable
private fun rowContainerColor(highlight: Float): Color =
    if (highlight > 0f) {
        lerp(MaterialTheme.colorScheme.surfaceBright, MaterialTheme.colorScheme.primaryContainer, highlight)
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

/**
 * Row colours. A row on, or chosen, keeps the same background as the others: its switch or radio
 * button already says so, and a filled row would swallow its icon tile and read as a different
 * kind of thing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun settingsRowColors(highlight: Float = 0f) = rowContainerColor(highlight).let { container ->
    ListItemDefaults.segmentedColors(containerColor = container, selectedContainerColor = container)
}

@Composable
internal fun RowIcon(icon: ImageVector) {
    Surface(
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
    }
}

/** A row that opens something: another screen, a picker, or an action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NavigationRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: String? = null,
    highlight: Float = 0f
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        enabled = enabled,
        colors = settingsRowColors(highlight),
        modifier = modifier,
        leadingContent = { RowIcon(icon) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badge?.let { Badge { Text(it) } }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

/** A row that turns something on or off; the whole row toggles, the switch only shows it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    highlight: Float = 0f
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(highlight),
        modifier = modifier,
        leadingContent = { RowIcon(icon) },
        supportingContent = { Text(supporting) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) }
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

/** One of several rows of which exactly one is chosen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RadioRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Float = 0f
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(highlight),
        modifier = modifier,
        leadingContent = { RowIcon(icon) },
        supportingContent = supporting?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        trailingContent = { RadioButton(selected = selected, onClick = null) }
    ) {
        Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

/**
 * A row whose choices sit in a button group under its title. The group spans the whole row rather
 * than the text column, so every choice gets as much room as the row has.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> ChoiceRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    highlight: Float = 0f
) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = rowContainerColor(highlight),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RowIcon(icon)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    supporting?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            AdaptiveChoices(
                options = options,
                selected = selected,
                label = label,
                onSelect = onSelect,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/**
 * A connected button group while every choice fits its share of the width. With large text or many
 * choices the buttons wrap onto more lines instead, so no label is cut short.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> AdaptiveChoices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier) {
        val share = maxWidth / options.size.coerceAtLeast(1)
        val longest = options.maxOfOrNull { label(it).length } ?: 0
        // About 8dp of width per character of a label at the default text size, plus padding.
        val needed = (longest * 8 + 24).dp * fontScale
        if (share >= needed) {
            ConnectedChoiceGroup(options = options, selected = selected, label = label, onSelect = onSelect)
        } else {
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val checked = option == selected
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { onSelect(option) },
                        modifier = Modifier.semantics { role = Role.RadioButton }
                    ) {
                        if (checked) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(18.dp)
                            )
                        }
                        Text(label(option), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** [text] with every occurrence of [query]'s words picked out, for search results. */
@Composable
internal fun highlighted(text: String, query: String): AnnotatedString {
    val ranges = SettingsSearch.highlights(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    val style = SpanStyle(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        fontWeight = FontWeight.Bold
    )
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            append(text.substring(cursor, range.first))
            withStyle(style) { append(text.substring(range.first, range.last + 1)) }
            cursor = range.last + 1
        }
        append(text.substring(cursor))
    }
}
