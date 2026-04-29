package com.example.tigerplayer.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
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

// ------------------------------
// UI STATE MACHINE (CLEAN CONTROL)
// ------------------------------
private enum class PlayerSheetState {
    COLLAPSED,
    MINI,
    EXPANDED
}

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
    homeViewModel: HomeViewModel
) {
    val tabNavController = rememberNavController()
    val haptic = LocalHapticFeedback.current

    // THE FIX: Uses Lifecycle-Aware state collection to prevent background CPU drain
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    var playerState by remember { mutableStateOf(PlayerSheetState.MINI) }
    val isExpanded = playerState == PlayerSheetState.EXPANDED
    val hasTrack = uiState.currentTrack != null

    BackHandler(enabled = isExpanded) {
        playerState = PlayerSheetState.MINI
    }

    // --- Z-AXIS MODAL PHYSICS (Apple Music / Spotify Style) ---
    val appScale by animateFloatAsState(
        targetValue = if (isExpanded) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 250f),
        label = "AppScale"
    )
    val appCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 32.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 250f),
        label = "AppCornerRadius"
    )
    val appAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 0.4f else 1f,
        animationSpec = tween(400),
        label = "AppAlpha"
    )

    val tabs = listOf(
        BottomNavTab.Home,
        BottomNavTab.Library,
        BottomNavTab.Cloud
    )

    // A Black root background so when the app scales down, it fades into darkness
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ==============================
        // LAYER 1 — APP SHELL (WITH Z-AXIS PUSHBACK)
        // ==============================
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = appScale
                    scaleY = appScale
                    alpha = appAlpha
                }
                .clip(RoundedCornerShape(appCornerRadius)),
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                ) {
                    // MINI PLAYER (Spotify-style persistent dock)
                    AnimatedVisibility(
                        visible = hasTrack && !isExpanded,
                        enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(250)) + fadeOut(tween(200))
                    ) {
                        Column {
                            MiniPlayer(
                                viewModel = playerViewModel,
                                onExpandClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    playerState = PlayerSheetState.EXPANDED
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }

                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.background(Color.Transparent)
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
            }
        ) { padding ->

            // ==============================
            // LAYER 2 — NAVIGATION STAGE
            // ==============================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                NavHost(
                    navController = tabNavController,
                    startDestination = BottomNavTab.Home.route,
                    // Silky Smooth Navigation Transitions
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth }, // from right edge
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