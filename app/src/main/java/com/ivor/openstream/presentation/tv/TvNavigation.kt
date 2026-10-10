package com.ivor.openstream.presentation.tv

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ivor.openstream.presentation.downloads.DownloadsScreen
import com.ivor.openstream.presentation.marketplace.MarketplaceScreen
import com.ivor.openstream.presentation.player.PlayerScreen
import com.ivor.openstream.presentation.search.SearchScreen
import com.ivor.openstream.presentation.settings.SettingsScreen

private object TvRoutes {
    const val HOME = "tv_home"
    const val DETAILS = "tv_details/{mediaType}/{animeId}"
    const val SEARCH = "tv_search"
    const val SETTINGS = "tv_settings"
    const val MARKETPLACE = "tv_marketplace"
    const val DOWNLOADS = "tv_downloads"
    const val PLAYER = "tv_player/{mediaType}/{animeId}/{season}/{episode}"

    fun details(mediaType: String, animeId: Int) = "tv_details/$mediaType/$animeId"
    fun player(mediaType: String, animeId: Int, season: Int, episode: Int) =
        "tv_player/$mediaType/$animeId/$season/$episode"
}

/**
 * TV navigation: home, details, search, settings and playback.
 * Search, settings, marketplace, downloads and the player are the shared
 * touch screens — every control is clickable and therefore D-pad focusable —
 * while home and details are 10-foot layouts built for the remote.
 */
@Composable
fun TvNavigation() {
    val navController = rememberNavController()
    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(TvRoutes.HOME)
            launchSingleTop = true
        }
    }
    NavHost(navController = navController, startDestination = TvRoutes.HOME) {
        composable(TvRoutes.HOME) {
            TvHomeScreen(
                onOpenTitle = { mediaType, id ->
                    navController.navigate(TvRoutes.details(mediaType, id))
                },
                onResume = { progress ->
                    navController.navigate(
                        TvRoutes.player(progress.mediaType, progress.tmdbId, progress.season, progress.episode)
                    )
                },
                onSearch = { openTab(TvRoutes.SEARCH) },
                onSettings = { openTab(TvRoutes.SETTINGS) }
            )
        }
        composable(
            route = TvRoutes.DETAILS,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("animeId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "tv"
            val animeId = backStackEntry.arguments?.getInt("animeId") ?: return@composable
            TvDetailsScreen(
                mediaType = mediaType,
                animeId = animeId,
                onBackClick = { navController.popBackStack() },
                onPlay = { season, episode ->
                    navController.navigate(TvRoutes.player(mediaType, animeId, season, episode))
                }
            )
        }
        composable(TvRoutes.SEARCH) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onAnimeClick = { animeId, mediaType ->
                    navController.navigate(TvRoutes.details(mediaType, animeId))
                }
            )
        }
        composable(TvRoutes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onOpenMarketplace = { navController.navigate(TvRoutes.MARKETPLACE) }
            )
        }
        composable(TvRoutes.MARKETPLACE) {
            MarketplaceScreen(onBackClick = { navController.popBackStack() })
        }
        composable(TvRoutes.DOWNLOADS) {
            DownloadsScreen(
                onBackClick = { navController.popBackStack() },
                onDownloadClick = { download ->
                    navController.navigate(
                        TvRoutes.player(download.mediaType, download.tmdbId, download.season, download.episode)
                    )
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
        ) { backStackEntry ->
            val args = backStackEntry.arguments ?: return@composable
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
                    navController.navigate(TvRoutes.details(type, id)) { launchSingleTop = true }
                },
                onOpenTitle = { id, type ->
                    navController.navigate(TvRoutes.details(type, id)) { launchSingleTop = true }
                }
            )
        }
    }
}
