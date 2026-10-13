package com.ivor.openstream.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusRequester
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object TvRoutes {
    const val DETAILS = "tv_details/{mediaType}/{animeId}"
    const val PLAYER = "tv_player/{mediaType}/{animeId}/{season}/{episode}"

    fun details(mediaType: String, animeId: Int) = "tv_details/$mediaType/$animeId"
    fun player(mediaType: String, animeId: Int, season: Int, episode: Int) =
        "tv_player/$mediaType/$animeId/$season/$episode"
}

/** Which top-level destination a route belongs under, so the rail can highlight it. */
private fun parentDestination(route: String?): TvDestination = when {
    route == null -> TvDestination.HOME
    route.startsWith("tv_details") || route.startsWith("tv_player") -> TvDestination.HOME
    else -> TvDestination.topLevel.firstOrNull { it.route == route } ?: TvDestination.HOME
}

/** Pushed routes sit on top of a tab and get their own visible back button. */
private fun isPlayerRoute(route: String?): Boolean = route?.startsWith(TvRoutes.PLAYER) == true

/**
 * The 10-foot app shell.
 *
 * Fixes the three navigation failures from the old TV build:
 *
 * - **Reachability.** A permanent [TvNavRail] puts every destination one press
 *   from every other. Previously the menu lived inside home's content, downloads
 *   had no entry at all, and no other screen could get back to the menu.
 * - **Back.** One decision in one place: a pushed screen pops, a top-level tab
 *   returns to Home, Home exits. Previously the player swallowed Back to leave
 *   a "fullscreen" that TV is permanently in, so the key looked dead.
 * - **Scale.** [TvTheme] wraps the whole shell, so the dense 10-foot scale
 *   reaches every screen, including the ones still shared with the phone.
 */
@Composable
fun TvNavigation() {
    TvTheme {
        val navController = rememberNavController()
        val dimens = LocalTvDimens.current
        val configuration = LocalConfiguration.current

        // Metrics follow the real window, minus the rail the shell reserves.
        val contentWidth = configuration.screenWidthDp - dimens.navRailWidth.value.toInt()
        val metrics = remember(contentWidth, configuration.screenHeightDp) {
            TvDimens.of(contentWidth, configuration.screenHeightDp)
        }

        CompositionLocalProvider(LocalTvDimens provides metrics) {
            TvAppScaffold(navController)
        }
    }
}

@Composable
private fun TvAppScaffold(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val selected = parentDestination(route)
    val navRequesters = rememberDestinationRequesters()
    var lastRoute by remember { mutableStateOf<String?>(null) }

    fun go(destination: TvDestination) {
        if (destination == selected) return
        navController.navigate(destination.route) {
            popUpTo(TvDestination.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // One back rule for the whole app. Home intentionally does nothing: the
    // system handles "back from the launcher activity" itself, and swallowing it
    // is what made the key feel broken.
    BackHandler(enabled = route != null && !isPlayerRoute(route)) {
        when {
            // Pushed screen (details): back to the tab underneath.
            route?.startsWith("tv_details") == true -> navController.popBackStack()
            // A top-level tab other than Home returns to Home.
            selected != TvDestination.HOME -> goHome(navController)
        }
    }
    // The player owns its own Back so it can close an open panel first.
    BackHandler(enabled = isPlayerRoute(route)) { navController.popBackStack() }

    Row(modifier = Modifier.fillMaxSize()) {
        TvNavRail(
            selected = selected,
            onSelect = ::go,
            requesters = navRequesters
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavHost(
                navController = navController,
                startDestination = TvDestination.HOME.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(TvDestination.HOME.route) {
                    TvHomeScreen(
                        onOpenTitle = { mediaType, id ->
                            navController.navigate(TvRoutes.details(mediaType, id))
                        },
                        onResume = { progress ->
                            navController.navigate(
                                TvRoutes.player(
                                    progress.mediaType,
                                    progress.tmdbId,
                                    progress.season,
                                    progress.episode
                                )
                            )
                        },
                        onProviders = { go(TvDestination.EXTENSIONS) },
                        onSearch = { go(TvDestination.SEARCH) },
                        onSettings = { go(TvDestination.SETTINGS) }
                    )
                }

                composable(TvDestination.SEARCH.route) {
                    TvSearchScreen(
                        onTitleClick = { animeId, mediaType ->
                            navController.navigate(TvRoutes.details(mediaType, animeId))
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
                                    download.episode
                                )
                            )
                        }
                    )
                }

                composable(TvDestination.EXTENSIONS.route) {
                    TvExtensionsScreen()
                }

                composable(TvDestination.SETTINGS.route) {
                    TvSettingsScreen(onOpenExtensions = { go(TvDestination.EXTENSIONS) })
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
                        onBack = { navController.popBackStack() },
                        onPlay = { season, episode ->
                            navController.navigate(TvRoutes.player(mediaType, animeId, season, episode))
                        }
                    )
                }

                composable(
                    route = TvRoutes.PLAYER,
                    arguments = listOf(
                        navArgument("mediaType") { type = NavType.StringType },
                        navArgument("animeId") { type = NavType.IntType },
                        navArgument("season") { type = NavType.IntType },
                        navArgument("episode") { type = NavType.IntType }
                    )
                ) { entry ->
                    val args = entry.arguments ?: return@composable
                    val mediaType = args.getString("mediaType") ?: "tv"
                    val animeId = args.getInt("animeId")
                    val season = args.getInt("season")
                    val episode = args.getInt("episode")
                    TvPlayerScreen(
                        mediaType = mediaType,
                        tmdbId = animeId,
                        season = season,
                        episode = episode,
                        onBackClick = { navController.popBackStack() },
                        onEpisodeClick = { newSeason, newEpisode ->
                            navController.navigate(
                                TvRoutes.player(mediaType, animeId, newSeason, newEpisode)
                            ) {
                                popUpTo(TvRoutes.PLAYER) { inclusive = true }
                            }
                        },
                        onOpenDetails = { type, id ->
                            navController.navigate(TvRoutes.details(type, id))
                        }
                    )
                }
            }
        }
    }

    // A route change pulls focus to the rail item for the new destination, so
    // pressing left always lands on something visible.
    LaunchedEffectRouteChange(route = route, lastRoute = lastRoute) { changed ->
        if (changed) lastRoute = route
        navRequesters[selected]?.let { runCatching { it.requestFocus() } }
    }
}

private fun goHome(navController: NavHostController) {
    navController.navigate(TvDestination.HOME.route) {
        popUpTo(TvDestination.HOME.route) { inclusive = true }
        launchSingleTop = true
    }
}

/** Fires [block] whenever [route] changes, with whether it actually changed. */
@Composable
private fun LaunchedEffectRouteChange(
    route: String?,
    lastRoute: String?,
    block: suspend (changed: Boolean) -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(route) {
        block(route != lastRoute)
    }
}