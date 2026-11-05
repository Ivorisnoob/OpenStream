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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
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
    SAVED("tv_saved", Icons.Default.Bookmark, R.string.nav_saved),
    CALENDAR("tv_calendar", Icons.Default.CalendarMonth, R.string.calendar_title),
    DOWNLOADS("tv_downloads", Icons.Default.Download, R.string.nav_downloads),
    EXTENSIONS("tv_extensions", Icons.Default.Extension, R.string.mk_title),
    SETTINGS("tv_settings", Icons.Default.Settings, R.string.st_title);

    companion object {
        /** Bottom-bar style destinations, in order. */
        val topLevel: List<TvDestination> = listOf(HOME, SEARCH, SAVED, CALENDAR, DOWNLOADS, EXTENSIONS, SETTINGS)
    }
}

/** A centered loading state for the 10-foot screen. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.LoadingIndicator()
    }
}
