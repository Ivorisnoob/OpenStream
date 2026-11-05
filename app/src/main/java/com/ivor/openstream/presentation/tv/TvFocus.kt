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
