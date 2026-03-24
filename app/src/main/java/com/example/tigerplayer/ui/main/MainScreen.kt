package com.example.tigerplayer.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.tigerplayer.navigation.BottomNavTab
import com.example.tigerplayer.ui.cloud.CloudScreen
import com.example.tigerplayer.ui.home.HomeScreen
import com.example.tigerplayer.ui.library.LibraryScreen
import com.example.tigerplayer.ui.player.FullPlayerScreen
import com.example.tigerplayer.ui.player.MiniPlayer
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.glassEffect

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToSpotifyPlaylist: (String, String, String?) -> Unit,
    onNavigateToSpotifyAlbum: (String, String, String?) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToNavidromeLogin: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToPlaylist: (Long, String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val tabNavController = rememberNavController()
    val uiState by playerViewModel.uiState.collectAsState()
    var isPlayerExpanded by remember { mutableStateOf(false) }

    // THE WITCHER'S REFLEX: Handle back gesture to collapse the player
    BackHandler(enabled = isPlayerExpanded) { isPlayerExpanded = false }

    val tabs = listOf(BottomNavTab.Home, BottomNavTab.Library, BottomNavTab.Cloud)

    // THE MASTER BOX: Layering the Full Player over the Main UI
    Box(modifier = Modifier.fillMaxSize()) {

        // --- LAYER 1: The App Shell (Scaffold) ---
        Scaffold(
            // Use background color so the Glass Bar has something to "frost"
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Apply glassEffect once to the entire bottom stack
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    val currentTrack = uiState.currentTrack

                    // MiniPlayer remains consistent inside the glass container
                    if (currentTrack != null && !isPlayerExpanded) {
                        MiniPlayer(
                            viewModel = playerViewModel,
                            onExpandClick = { isPlayerExpanded = true }
                        )

                        // Subtle visual separation
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    }

                    NavigationBar(
                        containerColor = Color.Transparent, // Let the Column's glass show
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(80.dp)
                    ) {
                        val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        tabs.forEach { tab ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (!isSelected) {
                                        playerViewModel.clearSearch()
                                    }
                                    tabNavController.navigate(tab.route) {
                                        popUpTo(tabNavController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                NavHost(
                    navController = tabNavController,
                    startDestination = BottomNavTab.Home.route,
                    // THE GLOBAL RITUAL: All tab switches will use these animations
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { 300 }, // Slide in from the right
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(400))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { -300 }, // Slide out to the left
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(400))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -300 }, // Slide in from the left
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(400))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { 300 }, // Slide out to the right
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(400))
                    }
                ){
                    composable(BottomNavTab.Home.route) {
                        HomeScreen(
                            viewModel = playerViewModel,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }

                    composable(BottomNavTab.Library.route) {
                        LibraryScreen(
                            viewModel = playerViewModel,
                            onNavigateToArtist = onNavigateToArtist,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToPlaylist = onNavigateToPlaylist
                        )
                    }

                    composable(BottomNavTab.Cloud.route) {
                        CloudScreen(
                            onNavigateToSpotifyPlaylist = onNavigateToSpotifyPlaylist,
                            onNavigateToSpotifyAlbum = onNavigateToSpotifyAlbum,
                            onNavigateToNavidromeLogin = onNavigateToNavidromeLogin
                        )
                    }
                }
            }
        }

        // --- LAYER 2: The Full Player Overlay ---
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(450, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(450, easing = FastOutSlowInEasing)
            )
        ) {
            FullPlayerScreen(
                viewModel = playerViewModel,
                onCollapse = { isPlayerExpanded = false },
                onNavigateToAlbum = { albumName ->
                    isPlayerExpanded = false
                    onNavigateToAlbum(albumName)
                }
            )
        }

        // --- THE INITIATION RITUAL ---
        LaunchedEffect(Unit) {
            playerViewModel.loadLocalAudio(forceRefresh = false)
        }
    }
}