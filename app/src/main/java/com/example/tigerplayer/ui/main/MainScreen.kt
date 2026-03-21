package com.example.tigerplayer.ui.main

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tigerplayer.navigation.BottomNavTab
import com.example.tigerplayer.ui.cloud.CloudScreen
import com.example.tigerplayer.ui.home.HomeScreen
import com.example.tigerplayer.ui.library.LibraryScreen
import com.example.tigerplayer.ui.player.FullPlayerScreen
import com.example.tigerplayer.ui.player.MiniPlayer
import com.example.tigerplayer.ui.player.PlayerViewModel

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel,
    onNavigateToSpotifyPlaylist: (String, String, String?) -> Unit,
    onNavigateToSpotifyAlbum: (String, String, String?) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToNavidromeLogin: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    // THE FIX: Add the missing playlist navigation callback here!
    onNavigateToPlaylist: (Long, String) -> Unit
) {
    val tabNavController = rememberNavController()
    val uiState by playerViewModel.uiState.collectAsState()
    var isPlayerExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = isPlayerExpanded) { isPlayerExpanded = false }

    val tabs = listOf(BottomNavTab.Home, BottomNavTab.Library, BottomNavTab.Cloud)

    Scaffold(
        bottomBar = {
            Column {
                if (uiState.currentTrack != null && !isPlayerExpanded) {
                    MiniPlayer(
                        track = uiState.currentTrack!!,
                        isPlaying = uiState.isPlaying,
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onNextClick = { playerViewModel.skipToNext() },
                        onExpandClick = { isPlayerExpanded = true }
                    )
                }

                NavigationBar {
                    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    tabs.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
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
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = tabNavController,
                startDestination = BottomNavTab.Home.route,
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(300)) }
            ) {
                // 1. HOME
                composable(BottomNavTab.Home.route) {
                    HomeScreen(
                        viewModel = playerViewModel,
                        onNavigateToAlbum = onNavigateToAlbum
                    )
                }

                // 2. LIBRARY: Now uses the GLOBAL callbacks cleanly
                composable(BottomNavTab.Library.route) {
                    LibraryScreen(
                        viewModel = playerViewModel,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToPlaylist = onNavigateToPlaylist // THE FIX: Wire it in here!
                    )
                }

                // 3. CLOUD
                composable(BottomNavTab.Cloud.route) {
                    CloudScreen(
                        onNavigateToSpotifyPlaylist = onNavigateToSpotifyPlaylist,
                        onNavigateToSpotifyAlbum = onNavigateToSpotifyAlbum
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = isPlayerExpanded,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
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
}

@Composable
fun RequestStoragePermission(
    onPermissionGranted: () -> Unit
) {
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionToRequest)
    }
}