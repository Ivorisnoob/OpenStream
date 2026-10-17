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
import com.ivor.openstream.presentation.player.PlayerScreen

private object TvRoutes {
    const val DETAILS = "tv_details/{mediaType}/{animeId}"
    const val PLAYER = "tv_player/{mediaType}/{animeId}/{season}/{episode}"

    fun details(mediaType: String, animeId: Int) = "tv_details/$mediaType/$animeId"
    fun player(mediaType: String, animeId: Int, season: Int, episode: Int) =
        "tv_player/$mediaType/$animeId/$season/$episode"
}

private fun parentDestination(route: String?): TvDestination = when {
    route == null -> TvDestination.HOME
    route.startsWith("tv_details") || route.startsWith("tv_player") -> TvDestination.HOME
    else -> TvDestination.topLevel.firstOrNull { it.route == route } ?: TvDestination.HOME
}

private fun isPlayerRoute(route: String?): Boolean = route?.startsWith(TvRoutes.PLAYER) == true

@Composable
fun TvNavigation() {
    TvTheme {
        val navController = rememberNavController()
        val dimens = LocalTvDimens.current
        val configuration = LocalConfiguration.current

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

    BackHandler(enabled = route != null && !isPlayerRoute(route)) {
        when {
            route?.startsWith("tv_details") == true -> navController.popBackStack()
            selected != TvDestination.HOME -> {
                navController.navigate(TvDestination.HOME.route) {
                    popUpTo(TvDestination.HOME.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

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
                                TvRoutes.player(progress.mediaType, progress.tmdbId, progress.season, progress.episode)
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

                composable(TvDestination.SAVED.route) {
                    TvSavedScreen(
                        onOpenTitle = { animeId, mediaType ->
                            navController.navigate(TvRoutes.details(mediaType, animeId))
                        }
                    )
                }

                composable(TvDestination.DOWNLOADS.route) {
                    TvDownloadsScreen(
                        onPlay = { download ->
                            navController.navigate(
                                TvRoutes.player(download.mediaType, download.tmdbId, download.season, download.episode)
                            )
                        }
                    )
                }

                composable(TvDestination.EXTENSIONS.route) {
                    TvExtensionsScreen()
                }

                composable(TvDestination.SETTINGS.route) {
                    TvSettingsScreen(
                        onOpenExtensions = { navController.navigate(TvDestination.EXTENSIONS.route) }
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
                        onBackClick = { navController.popBackStack() },
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
                    PlayerScreen(
                        mediaType = mediaType,
                        tmdbId = animeId,
                        season = season,
                        episode = episode,
                        tvControls = true,
                        onBackClick = { navController.popBackStack() },
                        onEpisodeClick = { newSeason, newEpisode ->
                            navController.navigate(TvRoutes.player(mediaType, animeId, newSeason, newEpisode)) {
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