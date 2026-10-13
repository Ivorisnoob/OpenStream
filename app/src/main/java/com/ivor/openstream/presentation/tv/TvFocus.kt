package com.ivor.openstream.presentation.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/**
 * Observes focus on an already-focusable (clickable) element. Never adds another focusable
 * node: a second target makes D-pad OK land on something with no click action.
 */
fun Modifier.tvFocusable(
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onBlurred: () -> Unit = {}
): Modifier {
    var modifier: Modifier = this
    if (focusRequester != null) modifier = modifier.focusRequester(focusRequester)
    return modifier
        .onFocusChanged {
            if (it.isFocused) onFocused() else onBlurred()
        }
}

/** Scale applied to a focused TV element (1x when unfocused or motion is off: the ring and
 * elevation carry focus alone under reduced motion). */

/**
 * Universal TV focus highlight modifier for remote navigation.
 * Scales the element smoothly, adds a prominent primary focus border, and tracks focus.
 */
fun Modifier.tvFocusHighlight(
    shape: Shape = CircleShape,
    borderWidth: Dp = 3.dp,
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
    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }
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
            if (isFocused) {
                Modifier.border(borderWidth, borderColor, shape)
            } else {
                Modifier
            }
        )
}

/** Scale applied to a focused TV element: animated spring when motion is on. */
@Composable
fun tvFocusScale(focused: Boolean, animated: Boolean): Float {
    if (!animated) return 1f
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tvFocusScale"
    )
    return scale
}

/** A TV card: surface that lifts, glows and scales when the remote lands on it. */
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
    val scale = tvFocusScale(focused, animated)
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (focused) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else null,
        shadowElevation = if (focused) 16.dp else 0.dp,
        modifier = modifier
            .scale(scale)
            .tvFocusable(focusRequester = focusRequester, onFocused = onFocused, onBlurred = onBlurred)
    ) {
        content()
    }
}

/**
 * TV button with unmistakable remote focus: expands with a spring, displays a vibrant
 * primary border, elevated shadow, and high-contrast color scheme when selected.
 */
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
    val scale = tvFocusScale(isFocused, animated)
    val highlightBorder = if (isFocused) {
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    } else null

    var buttonModifier = modifier
    if (focusRequester != null) {
        buttonModifier = buttonModifier.focusRequester(focusRequester)
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = highlightBorder,
        elevation = if (isFocused) ButtonDefaults.buttonElevation(defaultElevation = 8.dp) else null,
        colors = if (isFocused) focusedColors else colors,
        contentPadding = contentPadding,
        modifier = buttonModifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        content()
    }
}

/**
 * TV outlined button with high-contrast remote focus ring and container fill on focus.
 */
@Composable
fun TvOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    focusRequester: FocusRequester? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
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
    val scale = tvFocusScale(isFocused, animated)
    val highlightBorder = if (isFocused) {
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    } else border

    var buttonModifier = modifier
    if (focusRequester != null) {
        buttonModifier = buttonModifier.focusRequester(focusRequester)
    }

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

/**
 * TV icon button with scale and primary ring when remote focuses on it.
 */
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
    val scale = tvFocusScale(isFocused, animated)

    var iconModifier = modifier
    if (focusRequester != null) {
        iconModifier = iconModifier.focusRequester(focusRequester)
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = iconModifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else Modifier
            )
    ) {
        content()
    }
}

/** Local focus holder so rails/rows can react when the remote lands on a child. */
@Composable
fun rememberTvFocused(initial: Boolean = false): MutableState<Boolean> {
    return remember { mutableStateOf(initial) }
}
