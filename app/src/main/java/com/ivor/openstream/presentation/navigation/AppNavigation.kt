package com.ivor.openstream.presentation.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.ivor.openstream.R
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
import com.ivor.openstream.presentation.details.DetailsScreen
import com.ivor.openstream.presentation.person.PersonScreen
import com.ivor.openstream.presentation.home.HomeScreen
import com.ivor.openstream.presentation.downloads.DownloadsScreen
import com.ivor.openstream.presentation.player.PlayerScreen
import com.ivor.openstream.presentation.search.SearchScreen
import com.ivor.openstream.presentation.watch_history.WatchHistoryScreen
import com.ivor.openstream.presentation.watch_later.WatchLaterScreen
import com.ivor.openstream.presentation.update.UpdateScreen
import com.ivor.openstream.presentation.settings.SettingsScreen
import com.ivor.openstream.presentation.marketplace.MarketplaceScreen
import com.ivor.openstream.presentation.player.session.MiniPlayer
import com.ivor.openstream.presentation.lists.CustomListScreen
import com.ivor.openstream.presentation.profiles.ManageProfilesScreen
import com.ivor.openstream.presentation.profiles.ProfilesViewModel
import com.ivor.openstream.presentation.profiles.WhoIsWatchingScreen
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import com.ivor.openstream.presentation.welcome.WelcomeSheet
import com.ivor.openstream.presentation.shortcuts.AppShortcut
import com.ivor.openstream.presentation.shortcuts.ShortcutRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ivor.openstream.ui.theme.ExpressiveShapes
import com.ivor.openstream.presentation.player.session.MiniPlayerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.CompositionLocalProvider
import com.ivor.openstream.presentation.components.LocalWindowWidthClass
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

sealed class Screen(
    val route: String,
    val label: String = "",
    @StringRes val labelRes: Int? = null,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    data object Home : Screen("home", "Home", R.string.nav_home, Icons.Default.Home)
    data object Search : Screen("search", "Search", R.string.nav_search, Icons.Default.Search)
    data object WatchLater : Screen("watch_later", "Saved", R.string.nav_saved, Icons.Default.Bookmark)
    data object Downloads : Screen("downloads", "Downloads", R.string.nav_downloads, Icons.Default.Download)
    data object History : Screen("history", "History", R.string.nav_history, Icons.Default.History)
    data object Update : Screen("update")
    data object Settings : Screen("settings")
    data object Marketplace : Screen("marketplace")
    data object Person : Screen("person/{personId}") {
        fun createRoute(personId: Int) = "person/$personId"
    }

    data object Profiles : Screen("profiles")

    data object CustomList : Screen("list/{listId}") {
        fun createRoute(listId: Long) = "list/$listId"
    }

    data object Details : Screen("details/{mediaType}/{animeId}") {
        fun createRoute(mediaType: String, animeId: Int) = "details/$mediaType/$animeId"
    }

    data object Player : Screen("player/{mediaType}/{animeId}/{season}/{episode}?downloadId={downloadId}") {
        fun createRoute(
            mediaType: String,
            animeId: Int,
            season: Int,
            episode: Int,
            downloadId: String? = null
        ): String {
            val base = "player/$mediaType/$animeId/$season/$episode"
            return if (downloadId != null) "$base?downloadId=$downloadId" else base
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    windowSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    shortcutRequest: ShortcutRequest? = null,
    onShortcutHandled: () -> Unit = {},
    deepLinkRequest: DeepLinkRequest? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(Screen.Home, Screen.Search, Screen.WatchLater, Screen.Downloads, Screen.History)
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }
    val isCompact = windowSizeClass == WindowWidthSizeClass.Compact
    val isOnPlayer = currentDestination?.route?.startsWith("player/") == true
    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
    val nowPlaying by miniPlayerViewModel.session.nowPlaying.collectAsState()
    val appViewModel: AppViewModel = hiltViewModel()
    val profilesViewModel: ProfilesViewModel = hiltViewModel()
    val profiles by profilesViewModel.profiles.collectAsState()
    val activeProfile by profilesViewModel.activeProfile.collectAsState()
    val showLaunchPicker by profilesViewModel.showLaunchPicker.collectAsState()
    var showProfilePicker by rememberSaveable { mutableStateOf(false) }
    val isOnline by appViewModel.isOnline.collectAsState()
    var toolbarHeightPx by remember { mutableIntStateOf(0) }

    fun openTab(screen: Screen) {
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(shortcutRequest) {
        val request = shortcutRequest ?: return@LaunchedEffect
        when (request.shortcut) {
            AppShortcut.SEARCH -> openTab(Screen.Search)
            AppShortcut.DOWNLOADS -> openTab(Screen.Downloads)
            AppShortcut.CONTINUE_WATCHING -> {
                val latest = appViewModel.latestContinueWatching()
                if (latest == null) {
                    openTab(Screen.Home)
                } else {
                    navController.navigate(
                        Screen.Player.createRoute(latest.mediaType, latest.tmdbId, latest.season, latest.episode)
                    ) { launchSingleTop = true }
                }
            }
        }
        onShortcutHandled()
    }

    LaunchedEffect(deepLinkRequest) {
        val request = deepLinkRequest ?: return@LaunchedEffect
        navController.navigate(Screen.Details.createRoute(request.mediaType, request.tmdbId))
        onDeepLinkHandled()
    }

    // Last top-level tab the user was on, so the rail keeps showing where they came from on Details etc.
    var lastTabRoute by rememberSaveable { mutableStateOf(Screen.Home.route) }
    LaunchedEffect(currentDestination?.route) {
        currentDestination?.route?.takeIf { route -> bottomNavItems.any { it.route == route } }?.let { lastTabRoute = it }
    }
    // Tablets keep the rail on every screen but the player; phones only show the toolbar on tabs.
    val showRail = !isCompact && !isOnPlayer

    CompositionLocalProvider(LocalWindowWidthClass provides windowSizeClass) {
    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showRail,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut()
        ) {
            // Compact rail (labels under icons) on the page's own background, centered on the
            // edge: it reads as part of the screen rather than a separate drawer strip.
            WideNavigationRail(
                modifier = Modifier.fillMaxHeight(),
                colors = WideNavigationRailDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                arrangement = Arrangement.Center
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = screen.route == lastTabRoute
                    WideNavigationRailItem(
                        selected = selected,
                        railExpanded = false,
                        onClick = {
                            if (selected && screen.route == Screen.Search.route) {
                                navController.currentBackStackEntry?.savedStateHandle?.set(
                                    "focusSearch",
                                    System.currentTimeMillis()
                                )
                            }
                            openTab(screen)
                        },
                        icon = { Icon(screen.icon!!, contentDescription = null) },
                        label = { screen.labelRes?.let { Text(stringResource(it)) } ?: Text(screen.label) }
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
                modifier = Modifier.fillMaxSize(),
                enterTransition = NavTransitions.enter,
                exitTransition = NavTransitions.exit,
                popEnterTransition = NavTransitions.popEnter,
                popExitTransition = NavTransitions.popExit
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onAnimeClick = { animeId, mediaType ->
                            navController.navigate(Screen.Details.createRoute(mediaType, animeId))
                        },
                        onResume = { progress ->
                            navController.navigate(
                                Screen.Player.createRoute(
                                    mediaType = progress.mediaType,
                                    animeId = progress.tmdbId,
                                    season = progress.season,
                                    episode = progress.episode
                                )
                            )
                        },
                        onOpenDetails = { mediaType, id ->
                            navController.navigate(Screen.Details.createRoute(mediaType, id))
                        },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) },
                        onUpdateClick = { navController.navigate(Screen.Update.route) },
                        profile = activeProfile,
                        onSwitchProfile = { showProfilePicker = true }
                    )
                }

                composable(Screen.Search.route) { backStackEntry ->
                    val focusTrigger =
                        backStackEntry.savedStateHandle.getStateFlow("focusSearch", 0L).collectAsState().value
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
                        },
                        onOpenList = { listId ->
                            navController.navigate(Screen.CustomList.createRoute(listId))
                        }
                    )
                }

                composable(
                    route = Screen.CustomList.route,
                    arguments = listOf(navArgument("listId") { type = NavType.LongType })
                ) {
                    CustomListScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenTitle = { id, type ->
                            navController.navigate(Screen.Details.createRoute(type, id))
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
                        onResume = { entry ->
                            navController.navigate(
                                Screen.Player.createRoute(entry.mediaType, entry.tmdbId, entry.season, entry.episode)
                            )
                        },
                        onOpenDetails = { mediaType, id ->
                            navController.navigate(Screen.Details.createRoute(mediaType, id))
                        }
                    )
                }

                composable(Screen.Update.route) {
                    UpdateScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenMarketplace = { navController.navigate(Screen.Marketplace.route) },
                        onOpenProfiles = { navController.navigate(Screen.Profiles.route) }
                    )
                }

                composable(Screen.Profiles.route) {
                    ManageProfilesScreen(
                        viewModel = profilesViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(Screen.Marketplace.route) {
                    MarketplaceScreen(
                        onBackClick = { navController.popBackStack() }
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
                        },
                        onOpenTitle = { id, type ->
                            navController.navigate(Screen.Details.createRoute(type, id))
                        },
                        onOpenPerson = { personId ->
                            navController.navigate(Screen.Person.createRoute(personId))
                        },
                        onOpenDownloads = {
                            navController.navigate(Screen.Downloads.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(
                    route = Screen.Person.route,
                    arguments = listOf(navArgument("personId") { type = NavType.IntType })
                ) {
                    PersonScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenTitle = { id, type ->
                            navController.navigate(Screen.Details.createRoute(type, id))
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
                        onOpenDetails = { type, id ->
                            navController.navigate(Screen.Details.createRoute(type, id)) { launchSingleTop = true }
                        },
                        onOpenTitle = { id, type ->
                            navController.navigate(Screen.Details.createRoute(type, id))
                        },
                        onEpisodeClick = { newSeason, newEpisode ->
                            navController.navigate(Screen.Player.createRoute(mediaType, animeId, newSeason, newEpisode)) {
                                popUpTo(Screen.Player.route) { inclusive = true }
                            }
                        }
                    )
                }
            }

            // First launch only: sets expectations about which titles can play.
            WelcomeSheet()

            val onDownloads = currentDestination?.route == Screen.Downloads.route
            androidx.compose.animation.AnimatedVisibility(
                visible = !isOnline && !isOnPlayer,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OfflineBanner(
                    showDownloadsAction = !onDownloads,
                    onOpenDownloads = { openTab(Screen.Downloads) }
                )
            }

            // Keeps the stream going while browsing; sits just above the floating toolbar. The
            // toolbar is placed from the window edge, so stack on its measured height rather than
            // on the navigation bar inset (which shrinks in dp at higher display densities).
            val showToolbar = showBottomBar && isCompact
            val toolbarTop = with(LocalDensity.current) {
                ToolbarBottomOffset + if (toolbarHeightPx > 0) toolbarHeightPx.toDp() else 66.dp
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !isOnPlayer && nowPlaying != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (showToolbar) Modifier.padding(bottom = toolbarTop + 12.dp)
                        else Modifier.navigationBarsPadding().padding(bottom = 16.dp)
                    )
                    .padding(horizontal = 16.dp)
                    // A phone-width bar on tablets instead of a strip across the whole window.
                    .widthIn(max = 560.dp)
            ) {
                MiniPlayer(
                    viewModel = miniPlayerViewModel,
                    onExpand = { item ->
                        navController.navigate(
                            Screen.Player.createRoute(
                                mediaType = item.mediaType,
                                animeId = item.tmdbId,
                                season = item.season,
                                episode = item.episode,
                                downloadId = item.downloadId
                            )
                        ) { launchSingleTop = true }
                    }
                )
            }

            // Expressive Floating Navigation for Mobile (Compact screens)
            androidx.compose.animation.AnimatedVisibility(
                visible = showBottomBar && isCompact,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                // Tap a tab, or drag across the toolbar to scrub between them (one tick per tab,
                // switching when the finger lifts).
                val haptics = LocalHapticFeedback.current
                val currentIndex = bottomNavItems.indexOfFirst { screen ->
                    currentDestination?.hierarchy?.any { it.route == screen.route } == true
                }
                var scrubIndex by remember { mutableStateOf<Int?>(null) }
                var scrubTravel by remember { mutableFloatStateOf(0f) }
                val stepPx = with(LocalDensity.current) { 50.dp.toPx() }
                val latestIndex by rememberUpdatedState(currentIndex)
                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier
                        .padding(bottom = ToolbarBottomOffset)
                        .onSizeChanged { toolbarHeightPx = it.height }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    scrubTravel = 0f
                                    scrubIndex = latestIndex.coerceAtLeast(0)
                                },
                                onDragEnd = {
                                    val target = scrubIndex
                                    scrubIndex = null
                                    if (target != null && target != latestIndex) openTab(bottomNavItems[target])
                                },
                                onDragCancel = { scrubIndex = null },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    scrubTravel += amount
                                    val start = latestIndex.coerceAtLeast(0)
                                    val next = (start + (scrubTravel / stepPx).toInt()).coerceIn(0, bottomNavItems.lastIndex)
                                    if (next != scrubIndex) {
                                        scrubIndex = next
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                }
                            )
                        },
                    content = {
                        val shownIndex = scrubIndex ?: currentIndex
                        bottomNavItems.forEachIndexed { index, screen ->
                            val selected = index == shownIndex
                            val onClick = {
                                if (selected && screen.route == Screen.Search.route) {
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "focusSearch",
                                        System.currentTimeMillis()
                                    )
                                }
                                openTab(screen)
                            }
                            if (selected) {
                                FilledIconButton(onClick = onClick, modifier = Modifier.size(50.dp)) {
                                    Icon(screen.icon!!, contentDescription = screen.labelRes?.let { stringResource(it) } ?: screen.label)
                                }
                            } else {
                                IconButton(onClick = onClick, modifier = Modifier.size(50.dp)) {
                                    Icon(screen.icon!!, contentDescription = screen.labelRes?.let { stringResource(it) } ?: screen.label)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
    }

    // "Who's watching?": on launch with two or more profiles, or from the Home avatar.
    androidx.compose.animation.AnimatedVisibility(
        visible = showLaunchPicker || showProfilePicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        BackHandler(enabled = showProfilePicker && !showLaunchPicker) { showProfilePicker = false }
        WhoIsWatchingScreen(
            profiles = profiles,
            activeId = activeProfile?.id,
            viewModel = profilesViewModel,
            onSelect = { profile ->
                profilesViewModel.select(profile.id)
                showProfilePicker = false
                openTab(Screen.Home)
            },
            onManage = {
                profilesViewModel.dismissLaunchPicker()
                showProfilePicker = false
                navController.navigate(Screen.Profiles.route) { launchSingleTop = true }
            }
        )
    }
}

/** Gap between the floating toolbar and the bottom edge of the window. */
private val ToolbarBottomOffset = 50.dp

@Composable
private fun OfflineBanner(showDownloadsAction: Boolean, onOpenDownloads: () -> Unit) {
    Surface(
        shape = ExpressiveShapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.offline_banner), style = MaterialTheme.typography.labelLarge)
            if (showDownloadsAction) {
                TextButton(onClick = onOpenDownloads) {
                    Text(stringResource(R.string.nav_downloads), color = MaterialTheme.colorScheme.inversePrimary)
                }
            } else {
                Spacer(Modifier.size(width = 10.dp, height = 40.dp))
            }
        }
    }
}
