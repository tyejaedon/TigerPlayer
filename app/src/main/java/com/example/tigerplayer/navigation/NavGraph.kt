package com.example.tigerplayer.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel // THE FIX: Modern Hilt Import
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.tigerplayer.R
import com.example.tigerplayer.ui.cloud.SpotifyAlbumDetailScreen
import com.example.tigerplayer.ui.cloud.SpotifyPlaylistScreen
import com.example.tigerplayer.ui.library.AlbumDetailsScreen
import com.example.tigerplayer.ui.library.ArtistDetailsScreen
import com.example.tigerplayer.ui.library.NavidromeLoginScreen
import com.example.tigerplayer.ui.library.PlaylistDetailsScreen
import com.example.tigerplayer.ui.main.MainScreen
import com.example.tigerplayer.ui.permissions.PermissionScreen
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.example.tigerplayer.ui.home.HomeViewModel

@Composable
fun TigerBranding() {
    Image(
        painter = painterResource(id = R.drawable.ic_tiger_logo),
        contentDescription = "The Tiger Head Logo",
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun TigerPlayerNavGraph(navController: NavHostController, playerViewModel: PlayerViewModel,homeViewModel: HomeViewModel) {
    val context = LocalContext.current

    // THE FIX: Removed sharedPlayerViewModel to ensure the music never skips
    // and relies entirely on the playerViewModel passed from the MainActivity.

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        composable(route = Screen.Splash.route) {
            LaunchedEffect(key1 = Unit) {
                val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val hasPermission = ContextCompat.checkSelfPermission(
                    context, audioPermission
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    navController.navigate(Screen.MainApp.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    navController.navigate(Screen.Permission.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TigerBranding()
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "TIGER PLAYER",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 4.sp
                    )
                }
            }
        }

        composable(route = Screen.Permission.route) {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Screen.MainApp.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.MainApp.route) {
            MainScreen(
                playerViewModel = playerViewModel,
                onNavigateToSpotifyPlaylist = { id, name, url ->
                    navController.navigate(Screen.SpotifyPlaylist.createRoute(id, name, url))
                },
                onNavigateToSpotifyAlbum = { id, name, url ->
                    navController.navigate(Screen.SpotifyAlbum.createRoute(id, name, url))
                },
                onNavigateToArtist = { name ->
                    navController.navigate(Screen.ArtistDetail.createRoute(name))
                },
                onNavigateToAlbum = { name ->
                    navController.navigate(Screen.AlbumDetail.createRoute(name))
                },
                onNavigateToNavidromeLogin = {
                    navController.navigate(Screen.NavidromeLogin.route)
                },
                onNavigateToPlaylist = { id, name ->
                    navController.navigate(Screen.LocalPlaylist.createRoute(id, name))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                homeViewModel = homeViewModel
            )
        }

        // --- Settings Route ---
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = Uri.decode(backStackEntry.arguments?.getString("artistName") ?: "")
            ArtistDetailsScreen(
                artistName = name,
                viewModel = playerViewModel,
                onBackClick = { navController.popBackStack() },
                // THE FIX: Wire up the album cards to navigate deeper into the archive!
                onAlbumClick = { albumName ->
                    // Make sure to encode the name in case the album has a slash or question mark
                    val safeAlbumName = Uri.encode(albumName)
                    navController.navigate(Screen.AlbumDetail.createRoute(safeAlbumName))
                }
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumName") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = Uri.decode(backStackEntry.arguments?.getString("albumName") ?: "")
            AlbumDetailsScreen(
                albumName = name,
                viewModel = playerViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.LocalPlaylist.route,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType },
                navArgument("playlistName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("playlistId") ?: -1L
            val name = Uri.decode(backStackEntry.arguments?.getString("playlistName") ?: "Playlist")

            PlaylistDetailsScreen(
                playlistId = id,
                playlistName = name,
                viewModel = playerViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(route = Screen.NavidromeLogin.route) {
            NavidromeLoginScreen(
                viewModel = playerViewModel,
                onLoginSuccess = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SpotifyAlbum.route,
            arguments = listOf(
                navArgument("albumId") { type = NavType.StringType },
                navArgument("albumName") { type = NavType.StringType },
                navArgument("imageUrl") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
            val encodedUrl = backStackEntry.arguments?.getString("imageUrl")
            val imageUrl = encodedUrl?.takeIf { it.isNotEmpty() }?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            }

            SpotifyAlbumDetailScreen(
                albumId = albumId,
                albumName = albumName,
                albumImageUrl = imageUrl,
                viewModel = hiltViewModel(), // Scoped locally to this screen
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SpotifyPlaylist.route,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType },
                navArgument("playlistName") { type = NavType.StringType },
                navArgument("imageUrl") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            val playlistName = backStackEntry.arguments?.getString("playlistName") ?: "Playlist"
            val encodedUrl = backStackEntry.arguments?.getString("imageUrl")
            val imageUrl = encodedUrl?.takeIf { it.isNotEmpty() }?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            }

            SpotifyPlaylistScreen(
                playlistId = playlistId,
                playlistName = playlistName,
                playlistImageUrl = imageUrl,
                viewModel = hiltViewModel(), // Scoped locally to this screen
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}