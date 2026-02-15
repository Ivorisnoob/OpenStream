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
import com.ivor.openanime.presentation.downloads.DownloadsScreen
import com.ivor.openanime.presentation.player.PlayerScreen
import com.ivor.openanime.presentation.search.SearchScreen
import com.ivor.openanime.presentation.watch_history.WatchHistoryScreen
import com.ivor.openanime.presentation.watch_later.WatchLaterScreen

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed class Screen(val route: String, val label: String = "", val icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Search : Screen("search", "Search", Icons.Default.Search)
    data object WatchLater : Screen("watch_later", "Saved", Icons.Default.Bookmark)
    data object Downloads : Screen("downloads", "Downloads", Icons.Default.Download)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Details : Screen("details/{mediaType}/{animeId}") {
        fun createRoute(mediaType: String, animeId: Int) = "details/$mediaType/$animeId"
    }
    data object Player : Screen("player/{mediaType}/{animeId}/{season}/{episode}?downloadId={downloadId}") {
        fun createRoute(mediaType: String, animeId: Int, season: Int, episode: Int, downloadId: String? = null): String {
            val base = "player/$mediaType/$animeId/$season/$episode"
            return if (downloadId != null) "$base?downloadId=$downloadId" else base
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val bottomNavItems = listOf(Screen.Home, Screen.Search, Screen.WatchLater, Screen.Downloads, Screen.History)
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Details.createRoute("tv", animeId))
                    },
                    onSearchClick = {},
                    onHistoryClick = {}
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onBackClick = { navController.popBackStack() },
                    onAnimeClick = { animeId, mediaType ->
                        navController.navigate(Screen.Details.createRoute(mediaType, animeId))
                    }
                )
            }

            composable(Screen.WatchLater.route) {
                WatchLaterScreen(
                    onBackClick = { navController.popBackStack() },
                    onAnimeClick = { animeId, mediaType ->
                        navController.navigate(Screen.Details.createRoute(mediaType, animeId))
                    }
                )
            }

            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onBackClick = { navController.popBackStack() },
                    onDownloadClick = { download ->
                        navController.navigate(
                            Screen.Player.createRoute(
                                mediaType = download.mediaType,
                                animeId = download.tmdbId,
                                season = download.season,
                                episode = download.episode,
                                downloadId = download.downloadId
                            )
                        )
                    }
                )
            }

            composable(Screen.History.route) {
                WatchHistoryScreen(
                    onBackClick = { navController.popBackStack() },
                    onAnimeClick = { animeId, mediaType ->
                        navController.navigate(Screen.Details.createRoute(mediaType, animeId))
                    }
                )
            }
            
            composable(
                route = Screen.Details.route,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("animeId") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "tv"
                val animeId = backStackEntry.arguments?.getInt("animeId") ?: return@composable
                DetailsScreen(
                    mediaType = mediaType,
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { season, episode ->
                        navController.navigate(Screen.Player.createRoute(mediaType, animeId, season, episode))
                    }
                )
            }

            composable(
                route = Screen.Player.route,
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
            ) { backStackEntry ->
                val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "tv"
                val animeId = backStackEntry.arguments?.getInt("animeId") ?: return@composable
                val season = backStackEntry.arguments?.getInt("season") ?: return@composable
                val episode = backStackEntry.arguments?.getInt("episode") ?: return@composable
                val downloadId = backStackEntry.arguments?.getString("downloadId")
                
                PlayerScreen(
                    mediaType = mediaType,
                    tmdbId = animeId,
                    season = season,
                    episode = episode,
                    downloadId = downloadId,
                    onBackClick = { navController.popBackStack() },
                    onEpisodeClick = { newEpisode ->
                        navController.navigate(Screen.Player.createRoute(mediaType, animeId, season, newEpisode)) {
                            popUpTo(Screen.Player.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
