package com.ivor.openanime.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ivor.openanime.presentation.details.DetailsScreen
import com.ivor.openanime.presentation.home.HomeScreen
import com.ivor.openanime.presentation.player.PlayerScreen
import com.ivor.openanime.presentation.search.SearchScreen
import com.ivor.openanime.presentation.watch_history.WatchHistoryScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object History : Screen("history")
    data object Details : Screen("details/{animeId}") {
        fun createRoute(animeId: Int) = "details/$animeId"
    }
    data object Player : Screen("player/{animeId}/{season}/{episode}") {
        fun createRoute(animeId: Int, season: Int, episode: Int) = "player/$animeId/$season/$episode"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onAnimeClick = { animeId ->
                    navController.navigate(Screen.Details.createRoute(animeId))
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onHistoryClick = {
                    navController.navigate(Screen.History.route)
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onAnimeClick = { animeId ->
                    navController.navigate(Screen.Details.createRoute(animeId))
                }
            )
        }

        composable(Screen.History.route) {
            WatchHistoryScreen(
                onBackClick = { navController.popBackStack() },
                onAnimeClick = { animeId ->
                    navController.navigate(Screen.Details.createRoute(animeId))
                }
            )
        }
        
        composable(
            route = Screen.Details.route,
            arguments = listOf(navArgument("animeId") { type = NavType.IntType })
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getInt("animeId") ?: return@composable
            DetailsScreen(
                onBackClick = { navController.popBackStack() },
                onPlayClick = { season, episode ->
                    navController.navigate(Screen.Player.createRoute(animeId, season, episode))
                }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("animeId") { type = NavType.IntType },
                navArgument("season") { type = NavType.IntType },
                navArgument("episode") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getInt("animeId") ?: return@composable
            val season = backStackEntry.arguments?.getInt("season") ?: return@composable
            val episode = backStackEntry.arguments?.getInt("episode") ?: return@composable
            
            PlayerScreen(
                tmdbId = animeId,
                season = season,
                episode = episode,
                onBackClick = { navController.popBackStack() },
                onEpisodeClick = { newEpisode ->
                    navController.navigate(Screen.Player.createRoute(animeId, season, newEpisode)) {
                        // Pop up to the current player screen to avoid deep stacking
                        popUpTo(Screen.Player.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
