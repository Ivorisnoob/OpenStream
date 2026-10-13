package com.ivor.openstream.presentation.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.R

/**
 * The destinations a TV can reach, and the rail that lets it.
 *
 * The old TV build kept its menu in a row of chips pinned to the top of the
 * home screen's *content*. That meant every other screen had to be reachable
 * only by going back to home first, and a screen entered from anywhere else
 * had no way back into the menu at all. A permanent rail is the TV idiom: the
 * remote can always cross left and land on it, and every destination is one
 * press away from every other one.
 */
enum class TvDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int
) {
    HOME("tv_home", Icons.Default.Home, R.string.nav_home),
    SEARCH("tv_search", Icons.Default.Search, R.string.nav_search),
    DOWNLOADS("tv_downloads", Icons.Default.Download, R.string.nav_downloads),
    EXTENSIONS("tv_extensions", Icons.Default.Extension, R.string.mk_title),
    SETTINGS("tv_settings", Icons.Default.Settings, R.string.st_title);

    companion object {
        /** Bottom-bar style destinations, in order. */
        val topLevel: List<TvDestination> = listOf(HOME, SEARCH, DOWNLOADS, EXTENSIONS, SETTINGS)
    }
}

/**
 * The permanent left navigation rail.
 *
 * Every item is a real focus stop with a filled selected state and a ring, so
 * the remote's position in the app is always legible. [selectedRequester] points
 * at the item for the current route: on a route change the rail pulls focus to
 * the new destination, which is what makes "the highlight does not go anywhere"
 * stop happening when you navigate into a screen.
 */
@Composable
fun TvNavRail(
    selected: TvDestination,
    onSelect: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
    requesters: Map<TvDestination, FocusRequester> = rememberDestinationRequesters()
) {
    val dimens = LocalTvDimens.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .width(dimens.navRailWidth)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.padding(vertical = dimens.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dimens.listRowGap)
        ) {
            TvDestination.topLevel.forEach { destination ->
                TvNavRailItem(
                    destination = destination,
                    selected = destination == selected,
                    focusRequester = requesters[destination],
                    onClick = { onSelect(destination) }
                )
            }
        }
    }
}

@Composable
private fun TvNavRailItem(
    destination: TvDestination,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val dimens = LocalTvDimens.current
    var isFocused by remember { mutableStateOf(false) }
    val highlighted = isFocused || selected

    val container by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "navItemColor"
    )
    val content by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.onPrimary
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(120),
        label = "navItemContent"
    )

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        border = if (selected && !isFocused) {
            BorderStroke(dimens.focusRingWidth, MaterialTheme.colorScheme.secondary)
        } else {
            null
        },
        modifier = Modifier
            .padding(horizontal = dimens.gutterTight)
            .width(dimens.navRailWidth - dimens.gutterTight * 2)
            .height(dimens.navRailItemHeight)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester).onFocusChanged { isFocused = it.isFocused }
                } else {
                    Modifier.onFocusChanged { isFocused = it.isFocused }
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(vertical = dimens.paddingSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                modifier = Modifier.size(dimens.navRailItemHeight * 0.34f)
            )
            Spacer(Modifier.height(dimens.paddingSmall / 2))
            Text(
                text = stringResource(destination.labelRes),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One FocusRequester per rail item, so a route change can pull focus to it. */
@Composable
fun rememberDestinationRequesters(): Map<TvDestination, FocusRequester> {
    val entries = TvDestination.topLevel.associateWith { FocusRequester() }
    return remember(entries) { entries }
}

/**
 * The header above TV content: back affordance, screen title, and a slot for
 * per-screen actions.
 *
 * The back button is present on every screen (not just home), because "gets
 * stuck on the back button" was a real failure: without a visible target the
 * viewer has to guess whether back is consumed by an invisible dialog.
 */
@Composable
fun TvTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    val dimens = LocalTvDimens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.topBarHeight)
            .padding(horizontal = dimens.gutter),
        verticalAlignment = Alignment.CenterVertically
    ) {
        onBack?.let { TvBackButton(onClick = it) }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/**
 * The TV back button.
 *
 * Focusable *and* labelled: on a remote it has to be a stop the viewer can see
 * and reach, not an affordance that only exists in the phone gesture area.
 */
@Composable
fun TvBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val dimens = LocalTvDimens.current
    TvIconButton(
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back)
        )
    }
    Spacer(Modifier.width(dimens.paddingSmall))
}

/**
 * A dimmed, non-focusable backdrop for TV overlays.
 *
 * Deliberately *not* clickable and *not* focusable: the old panel scrim was a
 * focusable node with no indication, so when it took focus the remote looked
 * completely dead. Closing an overlay is the panel's own back button or the
 * hardware back key.
 */
@Composable
fun TvScrim(modifier: Modifier = Modifier, alpha: Float = 0.55f) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = alpha))
    )
}

/** A centered loading state for the 10-foot screen. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.LoadingIndicator()
    }
}