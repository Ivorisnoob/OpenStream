package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.ivor.openstream.presentation.player.PlayerScreen

private object TvRoutes {
    const val DETAILS = "tv_details/{mediaType}/{animeId}"
    const val PLAYER = "tv_player/{mediaType}/{animeId}/{season}/{episode}?downloadId={downloadId}"

    fun details(mediaType: String, animeId: Int) = "tv_details/$mediaType/$animeId"
    /** With [downloadId] the player opens the copy on the device instead of searching sources. */
    fun player(mediaType: String, animeId: Int, season: Int, episode: Int, downloadId: String? = null) =
        "tv_player/$mediaType/$animeId/$season/$episode" +
            (downloadId?.let { "?downloadId=${android.net.Uri.encode(it)}" } ?: "")
}

private fun parentDestination(route: String?): TvDestination =
    TvDestination.topLevel.firstOrNull { it.route == route } ?: TvDestination.HOME

@Composable
fun TvNavigation() {
    TvTheme {
        val navController = rememberNavController()
        val configuration = LocalConfiguration.current
        // Screens still sized by TvDimens get the width right of the collapsed drawer.
        val contentWidth = configuration.screenWidthDp - TvLayout.ContentStart.value.toInt()
        val metrics = remember(contentWidth, configuration.screenHeightDp) {
            TvDimens.of(contentWidth, configuration.screenHeightDp)
        }
        CompositionLocalProvider(LocalTvDimens provides metrics) {
            TvAppScaffold(navController)
        }
    }
}

/**
 * The TV shell: a navigation drawer that sits collapsed to icons over the content and opens with
 * labels when it takes focus (Left from a screen's first column). Back is left to the back stack:
 * Details and the player go back, a tab goes back to Home, and Home leaves the app. Each screen
 * puts focus on its own first control; the shell never pulls focus into the drawer.
 */
@Composable
private fun TvAppScaffold(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val selected = parentDestination(route)
    // Player and Details are full screen: the drawer is for moving between tabs.
    val showDrawer = TvDestination.topLevel.any { it.route == route }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val requesters = remember { TvDestination.topLevel.associateWith { FocusRequester() } }

    fun go(destination: TvDestination) {
        drawerState.setValue(DrawerValue.Closed)
        if (destination == selected) return
        navController.navigate(destination.route) {
            popUpTo(TvDestination.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val background = MaterialTheme.colorScheme.background
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimBrush = Brush.horizontalGradient(listOf(background, background.copy(alpha = 0.6f), Color.Transparent)),
        drawerContent = {
            if (showDrawer) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp, vertical = 24.dp)
                        .selectableGroup()
                        .focusRestorer(requesters.getValue(selected)),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                ) {
                    TvDestination.topLevel.forEach { destination ->
                        NavigationDrawerItem(
                            selected = destination == selected,
                            onClick = { go(destination) },
                            leadingContent = { Icon(destination.icon, contentDescription = null) },
                            modifier = Modifier.focusRequester(requesters.getValue(destination))
                        ) {
                            Text(
                                text = stringResource(destination.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = TvDestination.HOME.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(TvDestination.HOME.route) {
                TvHomeScreen(
                    onOpenTitle = { mediaType, id -> navController.navigate(TvRoutes.details(mediaType, id)) },
                    onPlay = { mediaType, id, season, episode ->
                        navController.navigate(TvRoutes.player(mediaType, id, season, episode))
                    }
                )
            }

            composable(TvDestination.SEARCH.route) {
                TvSearchScreen(
                    onTitleClick = { animeId, mediaType -> navController.navigate(TvRoutes.details(mediaType, animeId)) }
                )
            }

            composable(TvDestination.SAVED.route) {
                TvSavedScreen(
                    onOpenTitle = { animeId, mediaType -> navController.navigate(TvRoutes.details(mediaType, animeId)) }
                )
            }

            composable(TvDestination.CALENDAR.route) {
                TvCalendarScreen(
                    onOpenDetails = { id, type -> navController.navigate(TvRoutes.details(type, id)) },
                    onPlay = { item ->
                        navController.navigate(
                            TvRoutes.player(item.mediaType, item.tmdbId, item.season ?: 0, item.episode ?: 0)
                        )
                    }
                )
            }
            composable(TvDestination.DOWNLOADS.route) {
                TvDownloadsScreen(
                    onPlay = { download ->
                        navController.navigate(
                            TvRoutes.player(
                                download.mediaType,
                                download.tmdbId,
                                download.season,
                                download.episode,
                                downloadId = download.downloadId
                            )
                        )
                    }
                )
            }

            composable(TvDestination.EXTENSIONS.route) {
                TvExtensionsScreen()
            }

            composable(TvDestination.SETTINGS.route) {
                TvSettingsScreen(
                    onOpenExtensions = { go(TvDestination.EXTENSIONS) }
                )
            }

            composable(
                route = TvRoutes.DETAILS,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("animeId") { type = NavType.IntType }
                )
            ) { entry ->
                val mediaType = entry.arguments?.getString("mediaType") ?: "tv"
                val animeId = entry.arguments?.getInt("animeId") ?: return@composable
                TvDetailsScreen(
                    mediaType = mediaType,
                    animeId = animeId,
                    onPlay = { season, episode ->
                        navController.navigate(TvRoutes.player(mediaType, animeId, season, episode))
                    },
                    onOpenTitle = { type, id -> navController.navigate(TvRoutes.details(type, id)) }
                )
            }

            composable(
                route = TvRoutes.PLAYER,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("animeId") { type = NavType.IntType },
                    navArgument("season") { type = NavType.IntType },
                    navArgument("episode") { type = NavType.IntType },
                    navArgument("downloadId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val args = entry.arguments ?: return@composable
                val mediaType = args.getString("mediaType") ?: "tv"
                val animeId = args.getInt("animeId")
                PlayerScreen(
                    mediaType = mediaType,
                    tmdbId = animeId,
                    season = args.getInt("season"),
                    episode = args.getInt("episode"),
                    downloadId = args.getString("downloadId"),
                    tvControls = true,
                    onBackClick = { navController.popBackStack() },
                    onEpisodeClick = { newSeason, newEpisode ->
                        navController.navigate(TvRoutes.player(mediaType, animeId, newSeason, newEpisode)) {
                            popUpTo(TvRoutes.PLAYER) { inclusive = true }
                        }
                    },
                    onOpenDetails = { type, id -> navController.navigate(TvRoutes.details(type, id)) }
                )
            }
        }
    }
}
