package com.ivor.openstream.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The window's width class, provided by `AppNavigation`. Screens use it for sizing decisions
 * (artwork, grid cells, padding); pane splits that depend on the space a screen actually gets
 * still measure it with `BoxWithConstraints`.
 */
val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }

/** Phones in portrait. The floating toolbar sits at the bottom only here. */
val isCompactWidth: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalWindowWidthClass.current == WindowWidthSizeClass.Compact

/** Tablets in landscape, unfolded foldables in landscape, desktop windows. */
val isExpandedWidth: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalWindowWidthClass.current == WindowWidthSizeClass.Expanded

/** Picks a value by width class: [compact] on phones, [medium] on small tablets, [expanded] on large windows. */
@Composable
@ReadOnlyComposable
fun <T> byWidth(compact: T, medium: T, expanded: T = medium): T = when (LocalWindowWidthClass.current) {
    WindowWidthSizeClass.Compact -> compact
    WindowWidthSizeClass.Medium -> medium
    else -> expanded
}

/**
 * Space to leave under scrolling content. Phones have the floating toolbar and the mini player
 * stacked at the bottom; wider windows keep navigation in the rail, so only the mini player remains.
 */
val bottomContentPadding: Dp
    @Composable @ReadOnlyComposable
    get() = if (isCompactWidth) 200.dp else 120.dp

/** Readable line length for text-heavy screens (settings, forms): centered, at most [max] wide. */
fun Modifier.readableWidth(max: Dp = ReadableMaxWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max)

val ReadableMaxWidth = 720.dp

/**
 * Hosts a full-width scrolling list whose content is centered at most [maxContentWidth] wide.
 * [content] gets the side gutter to add to its `contentPadding`, so the list still scrolls from
 * anywhere on a tablet while rows keep a comfortable length. Never less than [minGutter].
 */
@Composable
fun CenteredListBox(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 840.dp,
    minGutter: Dp = 0.dp,
    content: @Composable (gutter: Dp) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        content(((maxWidth - maxContentWidth) / 2).coerceAtLeast(minGutter))
    }
}
