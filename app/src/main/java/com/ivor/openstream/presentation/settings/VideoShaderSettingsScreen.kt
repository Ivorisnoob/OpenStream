package com.ivor.openstream.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.R
import com.ivor.openstream.data.playback.VideoShaderValidator
import com.ivor.openstream.data.settings.VideoShaderCustomization
import com.ivor.openstream.data.settings.VideoShaderPreset
import com.ivor.openstream.presentation.components.CenteredListBox
import com.ivor.openstream.presentation.components.ExpressiveBackButton
import com.ivor.openstream.presentation.player.components.ShaderPictureSliders
import com.ivor.openstream.presentation.player.components.icon
import com.ivor.openstream.ui.theme.ExpressiveShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings > Video shaders: the switch, the looks to choose from, and the editor for a shader of
 * your own.
 *
 * The player's own page draws the same controls over the picture, which is the better place to
 * judge a look; this page is for setting one up without a video open. The player reads the stored
 * value, so a change made here reaches a video already playing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoShaderSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val appSettings by viewModel.appSettings.collectAsState()
    val shaders = appSettings.videoShaders
    val save: (VideoShaderCustomization) -> Unit = viewModel::setVideoShaders
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.sp_video_shaders)) },
                navigationIcon = {
                    ExpressiveBackButton(onClick = onBackClick, modifier = Modifier.padding(start = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        CenteredListBox(Modifier.fillMaxSize(), minGutter = 16.dp, maxContentWidth = 720.dp) { gutter ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = gutter,
                    end = gutter,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item(key = "master") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchRow(
                            index = 0, count = 1,
                            icon = Icons.Default.AutoAwesome,
                            title = stringResource(R.string.sp_video_shaders),
                            supporting = stringResource(
                                if (shaders.masterEnabled) {
                                    R.string.sp_video_shaders_sub_on
                                } else {
                                    R.string.sp_video_shaders_sub_off
                                }
                            ),
                            checked = shaders.masterEnabled,
                            onCheckedChange = { save(shaders.copy(masterEnabled = it)) }
                        )
                        if (shaders.masterEnabled && !viewModel.shadersApplyNow) {
                            Text(
                                text = stringResource(R.string.sp_video_shaders_restart),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.sp_video_shaders_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (shaders.masterEnabled) {
                    item(key = "presets") {
                        SettingsGroup(title = stringResource(R.string.sp_video_shaders_presets)) {
                            // Two to a row, and a row as tall as its taller tile, so a description
                            // that wraps at a large font is never cut off.
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                VideoShaderPreset.entries.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        pair.forEach { preset ->
                                            PresetTile(
                                                preset = preset,
                                                selected = shaders.activePreset == preset,
                                                onClick = {
                                                    if (shaders.activePreset != preset) {
                                                        save(shaders.copy(activePreset = preset))
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            )
                                        }
                                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    item(key = "picture") {
                        SettingsGroup(title = stringResource(R.string.sp_video_shaders_picture)) {
                            Surface(
                                shape = ExpressiveShapes.large,
                                color = MaterialTheme.colorScheme.surfaceBright,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 6.dp)) {
                                    // Nothing here draws a drag, so only the release is stored and
                                    // the player picks the value up.
                                    ShaderPictureSliders(
                                        settings = shaders,
                                        onPreview = {},
                                        onChange = save
                                    )
                                    TextButton(
                                        onClick = { save(shaders.withNeutralPicture()) },
                                        enabled = !shaders.isPictureNeutral,
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.vs_reset_picture),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "custom") {
                        SettingsGroup(title = stringResource(R.string.sp_video_shaders_custom)) {
                            CustomShaderCard(shaders = shaders, onSave = save)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A look on the Video shaders page: its icon, its name and what it does.
 *
 * The chosen one fills with the accent and squares its corners, and the badge under its icon does
 * the same, so which one is in use reads at a glance and at a distance.
 */
@Composable
private fun PresetTile(
    preset: VideoShaderPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = if (selected) ExpressiveShapes.medium else ExpressiveShapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceBright
        },
        contentColor = content,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = if (selected) ExpressiveShapes.small else CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = preset.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = stringResource(preset.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(preset.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.75f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** What the last Apply did, kept apart from whether the line is still on screen. */
private sealed interface ShaderCheck {
    data object Built : ShaderCheck
    data class Failed(val log: String) : ShaderCheck
}

/**
 * The editor for a shader of your own.
 *
 * Apply is the only thing that stores anything: a template fills the field and nothing else, so an
 * example cannot replace a stored shader until it is compiled and applied. Apply always stores the
 * text - a shader that will not compile is worth keeping to fix - but only promotes Custom to the
 * preset when it builds, since a preset that draws nothing is worse than the one already chosen.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomShaderCard(
    shaders: VideoShaderCustomization,
    onSave: (VideoShaderCustomization) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val stored by rememberUpdatedState(shaders)
    var draft by rememberSaveable(shaders.customShaderSource) {
        mutableStateOf(shaders.customShaderSource)
    }
    var checking by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<ShaderCheck>(ShaderCheck.Built) }
    var checkShown by remember { mutableStateOf(false) }
    val canApply = draft.isNotBlank() && !checking &&
        (draft != shaders.customShaderSource || shaders.activePreset != VideoShaderPreset.CUSTOM)

    Surface(
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.sp_video_shaders_custom_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.sp_video_shaders_custom_template),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                // Wrapping rather than scrolling sideways, so no example sits off the edge.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoShaderCustomization.STARTER_TEMPLATES.forEach { template ->
                        AssistChip(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                draft = template.source
                                checkShown = false
                            },
                            label = {
                                Text(
                                    text = stringResource(template.titleRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    checkShown = false
                },
                label = { Text(stringResource(R.string.sp_video_shaders_custom_field)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 340.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                // Code: a keyboard that capitalises or corrects it breaks it.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                ),
                shape = ExpressiveShapes.large
            )

            Text(
                text = stringResource(R.string.sp_video_shaders_custom_guide),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = checkShown) {
                when (val result = check) {
                    is ShaderCheck.Failed -> Surface(
                        shape = ExpressiveShapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.sp_video_shaders_custom_error),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            if (result.log.isNotBlank()) {
                                Text(
                                    text = result.log,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    ShaderCheck.Built -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.sp_video_shaders_custom_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val source = draft
                    checking = true
                    scope.launch {
                        // Compiles on a throwaway context the way playback would build it, which
                        // blocks, so it is done off the main thread.
                        val problem = withContext(Dispatchers.Default) {
                            VideoShaderValidator.check(source)
                        }
                        checking = false
                        check = if (problem == null) ShaderCheck.Built else ShaderCheck.Failed(problem)
                        checkShown = true
                        haptics.performHapticFeedback(
                            if (problem == null) HapticFeedbackType.Confirm else HapticFeedbackType.LongPress
                        )
                        onSave(
                            stored.copy(
                                customShaderSource = source,
                                activePreset = if (problem == null) {
                                    VideoShaderPreset.CUSTOM
                                } else {
                                    stored.activePreset
                                }
                            )
                        )
                    }
                },
                enabled = canApply,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (checking) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sp_video_shaders_custom_compile),
                    maxLines = 1
                )
            }
        }
    }
}
