@file:Suppress("AssignedValueIsNeverRead")
@file:SuppressLint("NewApi")

package com.example.tigerplayer.ui.main

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.tigerplayer.navigation.BottomNavTab
import com.example.tigerplayer.ui.cloud.CloudScreen
import com.example.tigerplayer.ui.youtube.YouTubeSearchScreen
import com.example.tigerplayer.ui.home.HomeScreen
import com.example.tigerplayer.ui.home.HomeViewModel
import com.example.tigerplayer.ui.library.LibraryScreen
import com.example.tigerplayer.ui.library.ScanningOverlay
import com.example.tigerplayer.ui.player.FullPlayerScreen
import com.example.tigerplayer.ui.player.MiniPlayer
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.queue.QueueScreen

// ------------------------------
// UI STATE MACHINE (CLEAN CONTROL)
// ------------------------------
private enum class PlayerSheetState {
    MINI,
    EXPANDED
}

@Composable
fun MainScreen(
    // PlayerViewModel is passed from MainActivity because it dictates global UI (Mini/Full player)
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
    val haptic = LocalHapticFeedback.current

    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    var playerState by remember { mutableStateOf(PlayerSheetState.MINI) }
    val isExpanded = playerState == PlayerSheetState.EXPANDED
    val hasTrack = uiState.currentTrack != null

    // Let the physical back button close the full-screen player
    BackHandler(enabled = isExpanded) {
        playerState = PlayerSheetState.MINI
    }

    val tabs = listOf(
        BottomNavTab.Home,
        BottomNavTab.Library,
        BottomNavTab.Queue,
        BottomNavTab.Cloud
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ==============================
        // LAYER 1 — APP SHELL (WITH Z-AXIS PUSHBACK)
        // ==============================
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    val backStack by tabNavController.currentBackStackEntryAsState()
                    val destination = backStack?.destination

                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                playerViewModel.clearSearch()

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
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        ) { padding ->

            // ==============================
            // LAYER 2 — NAVIGATION STAGE
            // ==============================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .padding(bottom = if (hasTrack && !isExpanded) 92.dp else 0.dp)
            ) {
                NavHost(
                    navController = tabNavController,
                    startDestination = BottomNavTab.Home.route,
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(300))
                    }
                ) {
                    composable(BottomNavTab.Home.route) {
                        // 🔥 HILT INTEGRATION: The HomeViewModel is dynamically scoped right here!
                        // It will live as long as the NavHost exists.
                        val homeViewModel: HomeViewModel = hiltViewModel()

                        HomeScreen(
                            viewModel = playerViewModel,
                            homeViewModel = homeViewModel,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigatetoArtist = onNavigateToArtist
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

                    composable(BottomNavTab.Queue.route) {
                        QueueScreen(viewModel = playerViewModel)
                    }

                    composable(BottomNavTab.Cloud.route) {
                        // CloudScreen inside inherently uses hiltViewModel() based on your earlier code
                        CloudScreen(
                            onNavigateToSpotifyPlaylist = onNavigateToSpotifyPlaylist,
                            onNavigateToSpotifyAlbum = onNavigateToSpotifyAlbum,
                            onNavigateToNavidromeLogin = onNavigateToNavidromeLogin,
                            onNavigateToYouTubeSearch = {
                                tabNavController.navigate(com.example.tigerplayer.navigation.Screen.YouTubeSearch.route)
                            }
                        )
                    }

                    composable(com.example.tigerplayer.navigation.Screen.YouTubeSearch.route) {
                        YouTubeSearchScreen(
                            onBackClick = {
                                tabNavController.popBackStack()
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hasTrack && !isExpanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 64.dp, start = 12.dp, end = 12.dp),
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            MiniPlayer(
                viewModel = playerViewModel,
                onExpandClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    playerState = PlayerSheetState.EXPANDED
                }
            )
        }

        // ==============================
        // LAYER 3 — FULL PLAYER SHEET
        // ==============================
        AnimatedVisibility(
            visible = isExpanded,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 250f
                )
            ) + fadeIn(
                animationSpec = tween(200)
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = 350,
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(250)
            )
        ) {
            FullPlayerScreen(
                viewModel = playerViewModel,
                onCollapse = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    playerState = PlayerSheetState.MINI
                },
                onNavigateToAlbum = {
                    playerState = PlayerSheetState.MINI
                    onNavigateToAlbum(it)
                }
            )
        }

        // ==============================
        // LAYER 4 — SYSTEM OVERLAY
        // ==============================
        if (uiState.isScanning) {
            ScanningOverlay(
                progress = uiState.scanProgress,
                total = uiState.totalFilesToScan
            )
        }

        // ==============================
        // INIT
        // ==============================
        LaunchedEffect(Unit) {
            playerViewModel.loadLocalAudio(forceRefresh = false)
        }
    }
}