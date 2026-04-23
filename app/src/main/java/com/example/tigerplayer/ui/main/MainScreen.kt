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
import com.example.tigerplayer.ui.home.HomeViewModel
import com.example.tigerplayer.ui.library.LibraryScreen
import com.example.tigerplayer.ui.library.ScanningOverlay
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
    onNavigateToSettings: () -> Unit,
    homeViewModel : HomeViewModel
) {
    val tabNavController = rememberNavController()
    val uiState by playerViewModel.uiState.collectAsState()
    var isPlayerExpanded by remember { mutableStateOf(false) }

    // THE WITCHER'S REFLEX: Handle back gesture to collapse the player securely
    BackHandler(enabled = isPlayerExpanded) { isPlayerExpanded = false }

    val tabs = listOf(BottomNavTab.Home, BottomNavTab.Library, BottomNavTab.Cloud)

    // THE MASTER BOX: The Z-Axis Controller
    Box(modifier = Modifier.fillMaxSize()) {

        // --- LAYER 1: THE APP SHELL (Bottom Z-Index) ---
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // The Consolidated Glass Deck
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    val currentTrack = uiState.currentTrack

                    // THE FIX: The Smooth Handoff Animation
                    AnimatedVisibility(
                        visible = currentTrack != null && !isPlayerExpanded,
                        enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                    ) {
                        Column {
                            MiniPlayer(
                                viewModel = playerViewModel,
                                onExpandClick = { isPlayerExpanded = true }
                            )

                            // Subtle visual separation
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                thickness = 0.5.dp, // S22 Precision line
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                        }
                    }

                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(72.dp)
                    ) {
                        val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        tabs.forEach { tab ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    // 1. THE UNCONDITIONAL WIPE
                                    // Clears the search whether swapping tabs OR resetting the current tab
                                    playerViewModel.clearSearch()

                                    // 2. THE NAVIGATION
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
            // --- LAYER 2: THE NAVIGATION VIEWPORT ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                NavHost(
                    navController = tabNavController,
                    startDestination = BottomNavTab.Home.route,
                    // THE SLY GLIDE: Optimized lateral transitions
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { 100 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { -100 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -100 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { 100 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(300))
                    }
                ) {
                    composable(BottomNavTab.Home.route) {
                        HomeScreen(
                            viewModel = playerViewModel,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigatetoArtist = onNavigateToArtist,
                            homeViewModel = homeViewModel
                        )
                    }

                    composable(BottomNavTab.Library.route) {
                        LibraryScreen(
                            viewModel = playerViewModel,
                            onNavigateToArtist = onNavigateToArtist,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToPlaylist = onNavigateToPlaylist,
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

        // --- LAYER 3: THE FULL PLAYER OVERLAY ---
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(450, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
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

        // --- LAYER 4: THE Z-AXIS ZENITH (Scanning Overlay) ---
        if (uiState.isScanning) {
            ScanningOverlay(
                progress = uiState.scanProgress,
                total = uiState.totalFilesToScan
            )
        }

        // --- THE INITIATION RITUAL ---
        LaunchedEffect(Unit) {
            playerViewModel.loadLocalAudio(forceRefresh = false)
        }
    }
}