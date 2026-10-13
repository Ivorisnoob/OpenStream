package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.R
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.presentation.settings.SettingsViewModel

/**
 * TV settings.
 *
 * This is the screen that answers "I cannot get to the quality selector with the remote".
 * Two things make the difference:
 *
 * 1. **Real rows, not nested toggle groups.** The phone layout packs up to six
 *    sub-buttons into a row that is itself a focus stop, so on a TV the remote lands
 *    on the *group*, not the quality option, and the sub-buttons are far too small to
 *    hit. Here OK on a group row expands an inline row of full-size `TvChoiceChip`s
 *    directly beneath it.
 * 2. **Initial focus.** The first row takes focus on entry, so the remote always has
 *    somewhere to go.
 */
@Composable
fun TvSettingsScreen(
    onOpenExtensions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val appSettings by viewModel.appSettings.collectAsState()
    val dimens = LocalTvDimens.current
    val firstRowRequester = remember { FocusRequester() }
    var expanded by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { runCatching { firstRowRequester.requestFocus() } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dimens.gutter,
            end = dimens.gutter,
            top = dimens.paddingMedium,
            bottom = dimens.gutter
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.listRowGap)
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.st_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = dimens.paddingSmall)
            )
        }

        item(key = "playback-header") {
            TvSectionLabel(stringResource(R.string.st_playback))
        }

        item(key = "stream-quality") {
            TvChoiceGroupRow(
                title = stringResource(R.string.st_stream_cap),
                supporting = stringResource(R.string.st_stream_cap_hint),
                options = AppSettings.STREAM_CAPS,
                selected = appSettings.streamMaxHeight,
                label = { height ->
                    when (height) {
                        0 -> stringResource(R.string.st_stream_cap_none)
                        2160 -> stringResource(R.string.q_2160p)
                        else -> "${height}p"
                    }
                },
                focusRequester = firstRowRequester,
                expanded = expanded == "stream-quality",
                onToggle = {
                    expanded = if (expanded == "stream-quality") null else "stream-quality"
                },
                onSelect = viewModel::setStreamMaxHeight
            )
        }

        item(key = "autoplay") {
            TvSwitchRow(
                title = stringResource(R.string.sheet_autoplay_next),
                supporting = stringResource(R.string.player_countdown_next),
                checked = appSettings.autoPlayNext,
                onCheckedChange = viewModel::setAutoPlayNext
            )
        }

        item(key = "skip") {
            TvSwitchRow(
                title = stringResource(R.string.sheet_skip_button),
                supporting = stringResource(R.string.player_offer_skip),
                checked = appSettings.showSkipButton,
                onCheckedChange = viewModel::setShowSkipButton
            )
        }

        item(key = "speed") {
            TvFloatChoiceGroupRow(
                title = stringResource(R.string.sheet_default_speed),
                options = AppSettings.DEFAULT_SPEEDS,
                selected = appSettings.defaultSpeed,
                label = { speed ->
                    if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"
                },
                expanded = expanded == "speed",
                onToggle = { expanded = if (expanded == "speed") null else "speed" },
                onSelect = viewModel::setDefaultSpeed
            )
        }

        item(key = "seek") {
            TvChoiceGroupRow(
                title = stringResource(R.string.st_seek_step),
                options = AppSettings.SEEK_STEPS,
                selected = appSettings.seekStepSeconds,
                label = { "$it s" },
                expanded = expanded == "seek",
                onToggle = { expanded = if (expanded == "seek") null else "seek" },
                onSelect = viewModel::setSeekStep
            )
        }

        item(key = "metered-header") {
            TvSectionLabel(stringResource(R.string.st_data_mobile))
        }

        item(key = "metered-cap") {
            TvChoiceGroupRow(
                title = stringResource(R.string.st_data_mobile_cap),
                supporting = stringResource(R.string.st_data_mobile_cap_hint),
                options = AppSettings.METERED_CAPS,
                selected = appSettings.meteredMaxHeight,
                label = { height ->
                    if (height == 0) stringResource(R.string.st_data_mobile_remember_none) else "${height}p"
                },
                expanded = expanded == "metered",
                onToggle = { expanded = if (expanded == "metered") null else "metered" },
                onSelect = viewModel::setMeteredMaxHeight
            )
        }

        item(key = "extensions") {
            TvSwitchRow(
                title = stringResource(R.string.st_data_mobile_warn),
                supporting = stringResource(R.string.st_data_mobile_warn_hint),
                checked = appSettings.warnBeforeMeteredStream,
                onCheckedChange = viewModel::setWarnBeforeMeteredStream
            )
        }

        item(key = "sources-header") {
            TvSectionLabel(stringResource(R.string.st_group_sources))
        }

        item(key = "extensions-nav") {
            TvListRow(
                onClick = onOpenExtensions,
                title = stringResource(R.string.mk_title),
                supporting = stringResource(R.string.st_sources_hint),
                leading = Icons.Default.SkipNext
            )
        }
    }
}

/** A group heading, so a long settings list stays scannable from the couch. */
@Composable
private fun TvSectionLabel(text: String) {
    val dimens = LocalTvDimens.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = dimens.paddingMedium, bottom = dimens.paddingSmall / 2)
    )
}

/**
 * A row that opens an inline strip of options.
 *
 * The row shows the current value on the right, so the answer to "what is it set to" is
 * visible without expanding anything; expanding only changes what OK does.
 */
@Composable
private fun TvChoiceGroupRow(
    title: String,
    options: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    onToggle: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    focusRequester: FocusRequester? = null
) {
    val dimens = LocalTvDimens.current
    TvListRow(
        onClick = onToggle,
        title = title,
        supporting = supporting,
        selected = expanded,
        focusRequester = focusRequester,
        trailingText = label(selected),
        modifier = modifier.fillMaxWidth()
    )
    if (expanded) {
        TvOptionStrip(options = options, selected = selected, label = label, onSelect = onSelect)
    }
}

/** Same shape for float-valued options (playback speeds). */
@Composable
private fun TvFloatChoiceGroupRow(
    title: String,
    options: List<Float>,
    selected: Float,
    label: (Float) -> String,
    onSelect: (Float) -> Unit,
    onToggle: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    TvListRow(
        onClick = onToggle,
        title = title,
        supporting = supporting,
        selected = expanded,
        trailingText = label(selected),
        modifier = modifier.fillMaxWidth()
    )
    if (expanded) {
        val dimens = LocalTvDimens.current
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.paddingSmall),
            horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
        ) {
            items(options) { option ->
                TvChoiceChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = label(option)
                )
            }
        }
    }
}

/** A toggle row; OK flips it, which is the whole interaction on a remote. */
@Composable
private fun TvSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    TvListRow(
        onClick = { onCheckedChange(!checked) },
        title = title,
        supporting = supporting,
        leading = leading,
        modifier = modifier.fillMaxWidth(),
        trailing = { TvTogglePill(checked = checked) }
    )
}

/**
 * The on/off indicator for a TV toggle row.
 *
 * A wide track with a filled state rather than M3's `Switch`: at 10 feet the switch's
 * thumb is the one thing on screen you cannot read the state of, and a toggle you have to
 * squint at is a toggle you get wrong.
 */
@Composable
private fun TvTogglePill(checked: Boolean) {
    val dimens = LocalTvDimens.current
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val thumbColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(
        modifier = Modifier
            .width(dimens.minTarget * 0.72f)
            .height(dimens.chipHeight * 0.52f)
    ) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        // Thumb travel is inset so the thumb never clips the rounded track ends.
        val inset = size.height * 0.18f
        val thumbRadius = size.height / 2f - inset
        val centerX = if (checked) size.width - thumbRadius - inset else thumbRadius + inset
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = androidx.compose.ui.geometry.Offset(centerX, size.height / 2f)
        )
    }
}

@Composable
private fun TvOptionStrip(
    options: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit
) {
    val dimens = LocalTvDimens.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
    ) {
        items(options) { option ->
            TvChoiceChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = label(option)
            )
        }
    }
}