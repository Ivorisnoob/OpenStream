package com.ivor.openstream.presentation.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Shared TV focus treatment (M3 Expressive for the 10-foot screen):
 * focused items grow with a spring and gain a primary glow ring + elevation,
 * so the remote's position is unmistakable from the couch.
 */
@Composable
fun rememberTvAnimated(): Boolean {
    val context = LocalContext.current
    return remember { TvDetect.animationsEnabled(context) }
}

fun Modifier.tvFocusable(
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onBlurred: () -> Unit = {}
): Modifier {
    var modifier: Modifier = this
    if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)
    return modifier.onFocusChanged {
        if (it.isFocused) onFocused() else onBlurred()
    }
}

fun Modifier.tvFocusHighlight(
    shape: Shape = CircleShape,
    borderWidth: Dp = TvDimens.of(960, 540).focusRingWidth,
    focusedBorderColor: Color? = null,
    scaleWhenFocused: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onBlurred: (() -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val animated = rememberTvAnimated()
    val scale = if (scaleWhenFocused) tvFocusScale(isFocused, animated) else 1f
    val borderColor = focusedBorderColor ?: MaterialTheme.colorScheme.primary

    var mod = this
    if (focusRequester != null) mod = mod.focusRequester(focusRequester)
    mod
        .then(if (scaleWhenFocused) Modifier.scale(scale) else Modifier)
        .onFocusChanged { state ->
            val focused = state.isFocused || state.hasFocus
            if (focused != isFocused) {
                isFocused = focused
                if (focused) onFocused?.invoke() else onBlurred?.invoke()
            }
        }
        .then(
            if (isFocused) Modifier.border(borderWidth, borderColor, shape) else Modifier
        )
}

@Composable
fun tvFocusScale(focused: Boolean, animated: Boolean): Float {
    if (!animated) return 1f
    val dimens = LocalTvDimens.current
    val target = if (focused) dimens.focusScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tvFocusScale"
    )
    return scale
}

@Composable
fun TvCard(
    focused: Boolean,
    animated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onBlurred: () -> Unit = {},
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable () -> Unit
) {
    val dimens = LocalTvDimens.current
    val scale = tvFocusScale(focused, animated)
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (focused) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (focused) dimens.cardElevation else 0.dp,
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .scale(scale)
            .tvFocusable(focusRequester = focusRequester, onFocused = onFocused, onBlurred = onBlurred)
    ) {
        content()
    }
}

@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    focusRequester: FocusRequester? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    ),
    focusedColors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val animated = rememberTvAnimated()
    val dimens = LocalTvDimens.current
    val scale = tvFocusScale(isFocused, animated)
    val highlightBorder = if (isFocused) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.primary) else null

    var buttonModifier = modifier
    if (focusRequester != null) buttonModifier = buttonModifier.focusRequester(focusRequester)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = highlightBorder,
        elevation = if (isFocused) ButtonDefaults.buttonElevation(defaultElevation = dimens.cardElevation) else null,
        colors = if (isFocused) focusedColors else colors,
        contentPadding = contentPadding,
        modifier = buttonModifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        content()
    }
}

@Composable
fun TvOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    focusRequester: FocusRequester? = null,
    border: BorderStroke? = null,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    focusedColors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val animated = rememberTvAnimated()
    val dimens = LocalTvDimens.current
    val scale = tvFocusScale(isFocused, animated)
    val highlightBorder = if (isFocused) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.primary) else border

    var buttonModifier = modifier
    if (focusRequester != null) buttonModifier = buttonModifier.focusRequester(focusRequester)

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = highlightBorder,
        colors = if (isFocused) focusedColors else colors,
        contentPadding = contentPadding,
        modifier = buttonModifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        content()
    }
}

@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val animated = rememberTvAnimated()
    val dimens = LocalTvDimens.current
    val scale = tvFocusScale(isFocused, animated)

    var iconModifier = modifier
    if (focusRequester != null) iconModifier = iconModifier.focusRequester(focusRequester)

    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = iconModifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) Modifier.border(dimens.focusRingWidth, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier
            )
    ) {
        content()
    }
}

/** A focusable row (one focus target) used by TV lists (downloads, sources, settings). */
@Composable
fun TvListRow(
    onClick: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    supporting: String? = null,
    leading: @Composable (() -> Unit)? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    trailingText: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onBlurred: () -> Unit = {}
) {
    val dimens = LocalTvDimens.current
    var isFocused by remember { mutableStateOf(false) }
    val animated = rememberTvAnimated()
    val scale = tvFocusScale(isFocused, animated)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isFocused) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.primary)
        else if (selected) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.secondary)
        else null,
        shadowElevation = if (isFocused) dimens.cardElevation else 0.dp,
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .scale(scale)
            .height(dimens.listRowHeight)
            .tvFocusable(focusRequester = focusRequester, onFocused = {
                isFocused = true
                onFocused()
            }, onBlurred = {
                isFocused = false
                onBlurred()
            })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.paddingMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.paddingMedium)
        ) {
            val subtitleText = subtitle ?: supporting
            if (leading != null) leading() else leadingIcon?.let { icon ->
                Icon(icon, contentDescription = null, modifier = Modifier.size(dimens.minTarget * 0.6f))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitleText.isNullOrBlank()) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) trailing() else trailingText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A focusable option chip used for filters / seasons / languages. */
@Composable
fun TvChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val dimens = LocalTvDimens.current
    var isFocused by remember { mutableStateOf(false) }
    val animated = rememberTvAnimated()
    val scale = tvFocusScale(isFocused, animated)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isFocused) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.primary)
        else if (selected) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.secondary)
        else null,
        shadowElevation = if (isFocused) dimens.cardElevation else 0.dp,
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .scale(scale)
            .height(dimens.chipHeight)
            .tvFocusable(focusRequester = focusRequester, onFocused = { isFocused = true }, onBlurred = { isFocused = false })
    ) {
        Box(
            modifier = Modifier.padding(horizontal = dimens.paddingMedium),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.paddingSmall)
            ) {
                leadingIcon?.let { icon ->
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(dimens.minTarget * 0.45f)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Local focus holder so rails/rows can react when the remote lands on a child. */
@Composable
fun rememberTvFocused(initial: Boolean = false): MutableState<Boolean> {
    return remember { mutableStateOf(initial) }
}
