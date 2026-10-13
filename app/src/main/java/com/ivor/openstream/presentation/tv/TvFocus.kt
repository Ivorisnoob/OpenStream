package com.ivor.openstream.presentation.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 10-foot focus system.
 *
 * Everything here exists because of one rule of D-pad navigation: **the remote
 * must always be somewhere obvious.** Three failures follow from breaking it,
 * and all three were reported against the old code:
 *
 * - *No initial focus.* A screen entered with nothing focused looks identical to
 *   a broken one: the D-pad goes nowhere because there is no focused node to
 *   move. Screens request focus on entry (see `TvFocusTarget`).
 * - *No focus visual.* M3's plain `clickable` only ripples on press. A focused
 *   result card looked exactly like an unfocused one, so the highlight
 *   "did not go anywhere". Every component below draws a ring.
 * - *A second focusable node.* Two focus targets on one control means D-pad OK
 *   lands on the decoration, which has no click action and does nothing. The
 *   helpers below only ever *observe* focus; the host control stays the single
 *   focusable node.
 */

/** True when the system animator scale is on, so focus motion can be skipped. */
@Composable
fun rememberTvAnimated(): Boolean {
    val context = LocalContext.current
    return remember { TvDetect.animationsEnabled(context) }
}

/**
 * Observes focus on an already-focusable element.
 *
 * Never adds another focusable node: a second target makes D-pad OK land on
 * something with no click action, which is exactly the "presses OK and nothing
 * happens" bug.
 */
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

/**
 * The focus ring plus a small spring scale.
 *
 * Scale alone is not enough (6% on a dark card is nearly invisible) and a
 * border alone is not enough (it reads as part of the artwork), so the ring is
 * the primary signal and the scale reinforces it.
 */
@Composable
private fun Modifier.tvFocusTreatment(shape: Shape): Modifier {
    val dimens = LocalTvDimens.current
    val animated = rememberTvAnimated()
    val scale by animateFloatAsState(
        targetValue = if (animated) dimens.focusScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tvFocusScale"
    )
    return this
        .scale(scale)
        .border(dimens.focusRingWidth, MaterialTheme.colorScheme.primary, shape)
}

/** Spring scale for a focused element; 1x when motion is off. */
@Composable
fun tvFocusScale(focused: Boolean, animated: Boolean): Float {
    if (!animated) return 1f
    val dimens = LocalTvDimens.current
    val scale by animateFloatAsState(
        targetValue = if (focused) dimens.focusScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tvFocusScale"
    )
    return scale
}

/**
 * A TV card: surface that rings, lifts and scales when the remote lands on it.
 *
 * [focused] is driven by the caller's focus observation so a rail can scroll
 * itself when an item takes focus.
 */
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
        color = if (focused) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (focused) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (focused) 12.dp else 0.dp,
        modifier = modifier
            .scale(scale)
            .tvFocusable(focusRequester = focusRequester, onFocused = onFocused, onBlurred = onBlurred)
    ) {
        content()
    }
}

/**
 * A button sized for a remote: never below [TvDimens.minTarget] tall, with a
 * ring and a container swap on focus so the selection reads from the couch.
 */
@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    focusRequester: FocusRequester? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ),
    focusedColors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val dimens = LocalTvDimens.current
    var buttonModifier = modifier
    if (focusRequester != null) buttonModifier = buttonModifier.focusRequester(focusRequester)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = if (isFocused) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.primary) else null,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        colors = if (isFocused) focusedColors else colors,
        contentPadding = contentPadding,
        modifier = buttonModifier
            .height(dimens.buttonHeight)
            .tvFocusTreatment(shape)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        content()
    }
}

/** TV outlined button: transparent until focused, then a ring and a fill. */
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val dimens = LocalTvDimens.current
    var buttonModifier = modifier
    if (focusRequester != null) buttonModifier = buttonModifier.focusRequester(focusRequester)

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = if (isFocused) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.primary) else border,
        colors = if (isFocused) focusedColors else colors,
        contentPadding = contentPadding,
        modifier = buttonModifier
            .height(dimens.buttonHeight)
            .tvFocusTreatment(shape)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        content()
    }
}

/**
 * The standard TV list row (settings, sources, episodes, downloads).
 *
 * Full width, remote-sized, ringed when focused, filled when selected. Rows
 * are what makes a TV screen scannable from the couch: one line, one decision.
 */
@Composable
fun TvListRow(
    onClick: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    trailingText: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val dimens = LocalTvDimens.current
    var rowModifier = modifier
    if (focusRequester != null) rowModifier = rowModifier.focusRequester(focusRequester)

    val highlighted = isFocused || selected
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (isFocused) BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.primary) else null,
        modifier = rowModifier
            .height(dimens.listRowHeight)
            .tvFocusTreatment(MaterialTheme.shapes.large)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.paddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.let {
                Icon(it, contentDescription = null)
                Spacer(Modifier.width(dimens.paddingMedium))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                supporting?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailingText?.let {
                Spacer(Modifier.width(dimens.paddingMedium))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            trailing?.let {
                Spacer(Modifier.width(dimens.paddingSmall))
                it()
            }
        }
    }
}

/** TV icon button with a ring and a container swap when focused. */
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
    val dimens = LocalTvDimens.current
    var iconModifier = modifier
    if (focusRequester != null) iconModifier = iconModifier.focusRequester(focusRequester)

    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = iconModifier
            .height(dimens.minTarget)
            .width(dimens.minTarget)
            .tvFocusTreatment(CircleShape)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        content()
    }
}

/**
 * A selectable chip (season, filter, quality).
 *
 * Chips on TV need a *filled* selected state: at a distance an outline-only
 * difference between "this season" and "that season" is invisible, and the
 * remote lands on the wrong one.
 */
@Composable
fun TvChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    leading: ImageVector? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val dimens = LocalTvDimens.current
    var chipModifier = modifier
    if (focusRequester != null) chipModifier = chipModifier.focusRequester(focusRequester)

    val highlighted = isFocused || selected
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = when {
            highlighted -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when {
            highlighted -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = if (isFocused) {
            BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.onPrimary)
        } else {
            null
        },
        modifier = chipModifier
            .height(dimens.chipHeight)
            .tvFocusTreatment(CircleShape)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.paddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Local focus holder so rails and rows can react when the remote lands on a child. */
@Composable
fun rememberTvFocused(initial: Boolean = false): MutableState<Boolean> =
    remember { mutableStateOf(initial) }

/**
 * The focus ring colour, exposed so TV screens painting custom chrome (cards,
 * navigation rails) match the buttons instead of inventing their own.
 */
val tvFocusColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/** Convenience for the many "does this row have focus" paddings. */
@Composable
fun Modifier.focusRingIf(focused: Boolean, shape: Shape, width: Dp = LocalTvDimens.current.focusRingWidth): Modifier =
    if (focused) this.border(width, tvFocusColor, shape) else this