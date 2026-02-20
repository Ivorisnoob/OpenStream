package com.ivor.openanime.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.ivor.openanime.presentation.details.DetailsScreen
import com.ivor.openanime.presentation.home.HomeScreen
import com.ivor.openanime.presentation.downloads.DownloadsScreen
import com.ivor.openanime.presentation.player.PlayerScreen
import com.ivor.openanime.presentation.search.SearchScreen
import com.ivor.openanime.presentation.watch_history.WatchHistoryScreen
import com.ivor.openanime.presentation.watch_later.WatchLaterScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    windowSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val bottomNavItems = listOf(Screen.Home, Screen.Search, Screen.WatchLater, Screen.Downloads, Screen.History)
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }
    val isCompact = windowSizeClass == WindowWidthSizeClass.Compact

    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showBottomBar && !isCompact,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut()
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationRailItem(
                        selected = selected,
                        onClick = {
                            if (selected && screen.route == Screen.Search.route) {
                                navController.currentBackStackEntry?.savedStateHandle?.set("focusSearch", System.currentTimeMillis())
                            }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon!!, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize()
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

            composable(Screen.Search.route) { backStackEntry ->
                val focusTrigger = backStackEntry.savedStateHandle.getStateFlow("focusSearch", 0L).collectAsState().value
                SearchScreen(
                    onBackClick = { navController.popBackStack() },
                    onAnimeClick = { animeId, mediaType ->
                        navController.navigate(Screen.Details.createRoute(mediaType, animeId))
                    },
                    focusTrigger = focusTrigger
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

        // Expressive Floating Navigation for Mobile (Compact screens)
        androidx.compose.animation.AnimatedVisibility(
            visible = showBottomBar && isCompact,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.padding(bottom = 50.dp),
                content = {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        
                        if (selected) {
                            FilledIconButton(
                                onClick = {
                                    if (screen.route == Screen.Search.route) {
                                        navController.currentBackStackEntry?.savedStateHandle?.set("focusSearch", System.currentTimeMillis())
                                    }
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(screen.icon!!, contentDescription = screen.label)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(screen.icon!!, contentDescription = screen.label)
                            }
                        }
                    }
                }
            )
        }
    }
}
}
