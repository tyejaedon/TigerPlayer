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
    onNavigateToSettings: () -> Unit // NEW: Support navigation to settings
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
            bottomBar = {
                // Unified Glass Column for MiniPlayer + Navigation
                Column(
                    modifier = Modifier.glassEffect(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        borderColor = Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    val currentTrack = uiState.currentTrack
                    if (currentTrack != null && !isPlayerExpanded) {
                        val trackProgress = if (currentTrack.durationMs > 0) {
                            (uiState.currentPosition.toFloat() / currentTrack.durationMs).coerceIn(0f, 1f)
                        } else 0f

                        MiniPlayer(
                            track = currentTrack,
                            isPlaying = uiState.isPlaying,
                            progress = trackProgress,
                            onPlayPauseClick = { playerViewModel.togglePlayPause() },
                            onNextClick = { playerViewModel.skipToNext() },
                            onExpandClick = { isPlayerExpanded = true }
                        )
                    }

                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        tabs.forEach { tab ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = { Text(tab.title) },
                                selected = isSelected,
                                onClick = {
                                    // THE CLEARING RITUAL: Clear search results when switching tabs
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
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            // Tab Content Host
            Box(modifier = Modifier.padding(innerPadding)) {
                NavHost(
                    navController = tabNavController,
                    startDestination = BottomNavTab.Home.route,
                    enterTransition = { fadeIn(tween(400)) },
                    exitTransition = { fadeOut(tween(400)) }
                ) {
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
        // Automatically scan archives when the app launches
        LaunchedEffect(Unit) {
            playerViewModel.loadLocalAudio(forceRefresh = false)
        }
    }
}