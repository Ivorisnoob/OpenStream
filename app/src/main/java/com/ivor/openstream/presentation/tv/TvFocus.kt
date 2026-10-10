package com.ivor.openstream.presentation.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

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
    return modifier
        .focusable()
        .onFocusChanged {
            if (it.isFocused) onFocused() else onBlurred()
        }
}

/** Scale applied to a focused TV element (near-1x when unfocused or motion is off). */
@Composable
fun tvFocusScale(focused: Boolean, animated: Boolean): Float {
    if (!animated) return if (focused) 1.04f else 1f
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

/** Local focus holder so rails/rows can react when the remote lands on a child. */
@Composable
fun rememberTvFocused(initial: Boolean = false): MutableState<Boolean> {
    return remember { mutableStateOf(initial) }
}
