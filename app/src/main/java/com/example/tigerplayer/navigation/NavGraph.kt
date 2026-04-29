package com.example.tigerplayer.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.example.tigerplayer.R
import com.example.tigerplayer.ui.cloud.*
import com.example.tigerplayer.ui.home.HomeViewModel
import com.example.tigerplayer.ui.library.*
import com.example.tigerplayer.ui.main.MainScreen
import com.example.tigerplayer.ui.permissions.PermissionScreen
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// ----------------------------------
// 🔹 Branding
// ----------------------------------

@Composable
fun TigerBranding() {
    Image(
        painter = painterResource(id = R.drawable.ic_tiger_logo),
        contentDescription = "Tiger Player Logo",
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}

// ----------------------------------
// 🔹 Navigation Graph
// ----------------------------------

@Composable
fun TigerPlayerNavGraph(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,

        // ✨ Smooth modern motion (Samsung-like)
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 2 },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(250))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 4 },
                animationSpec = tween(300)
            ) + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 4 },
                animationSpec = tween(300)
            ) + fadeIn(tween(250))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it / 2 },
                animationSpec = tween(300)
            ) + fadeOut(tween(200))
        }
    ) {

        // ----------------------------------
        // 🔹 Splash Screen
        // ----------------------------------

        composable(Screen.Splash.route) {

            LaunchedEffect(Unit) {
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val hasPermission = ContextCompat.checkSelfPermission(
                    context, permission
                ) == PackageManager.PERMISSION_GRANTED

                navController.navigate(
                    if (hasPermission) Screen.MainApp.route else Screen.Permission.route
                ) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                    launchSingleTop = true
                }
            }

            SplashContent()
        }

        // ----------------------------------
        // 🔹 Permission
        // ----------------------------------

        composable(Screen.Permission.route) {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Screen.MainApp.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                }
            )
        }

        // ----------------------------------
        // 🔹 Main App Shell
        // ----------------------------------

        composable(Screen.MainApp.route) {
            MainScreen(
                playerViewModel = playerViewModel,
                homeViewModel = homeViewModel,

                onNavigateToSpotifyPlaylist = { id, name, url ->
                    navController.navigate(Screen.SpotifyPlaylist.createRoute(id, name, url))
                },
                onNavigateToSpotifyAlbum = { id, name, url ->
                    navController.navigate(Screen.SpotifyAlbum.createRoute(id, name, url))
                },
                onNavigateToArtist = {
                    navController.navigate(Screen.ArtistDetail.createRoute(it))
                },
                onNavigateToAlbum = {
                    navController.navigate(Screen.AlbumDetail.createRoute(it))
                },
                onNavigateToPlaylist = { id, name ->
                    navController.navigate(Screen.LocalPlaylist.createRoute(id, name))
                },
                onNavigateToNavidromeLogin = {
                    navController.navigate(Screen.NavidromeLogin.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // ----------------------------------
        // 🔹 Settings
        // ----------------------------------

        composable(Screen.Settings.route) {
            SettingsScreen(onBackClick = { navController.popBackStack() })
        }

        // ----------------------------------
        // 🔹 Artist
        // ----------------------------------

        composable(
            Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) {
            val name = it.decodeArg("artistName")

            ArtistDetailsScreen(
                artistName = name,
                viewModel = playerViewModel,
                onBackClick = { navController.popBackStack() },
                onAlbumClick = { album ->
                    navController.navigate(Screen.AlbumDetail.createRoute(album))
                }
            )
        }

        // ----------------------------------
        // 🔹 Album
        // ----------------------------------

        composable(
            Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumName") { type = NavType.StringType })
        ) {
            val name = it.decodeArg("albumName")

            AlbumDetailsScreen(
                albumName = name,
                viewModel = playerViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // ----------------------------------
        // 🔹 Local Playlist
        // ----------------------------------

        composable(
            Screen.LocalPlaylist.route,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType },
                navArgument("playlistName") { type = NavType.StringType }
            )
        ) {
            val id = it.arguments?.getLong("playlistId") ?: -1L
            val name = it.decodeArg("playlistName", "Playlist")

            PlaylistDetailsScreen(
                playlistId = id,
                playlistName = name,
                viewModel = playerViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // ----------------------------------
        // 🔹 Navidrome
        // ----------------------------------

        composable(Screen.NavidromeLogin.route) {
            NavidromeLoginScreen(
                viewModel = playerViewModel,
                onLoginSuccess = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() }
            )
        }

        // ----------------------------------
        // 🔹 Spotify Album
        // ----------------------------------

        composable(
            Screen.SpotifyAlbum.route,
            arguments = listOf(
                navArgument("albumId") { type = NavType.StringType },
                navArgument("albumName") { type = NavType.StringType },
                navArgument("imageUrl") { nullable = true }
            )
        ) {
            SpotifyAlbumDetailScreen(
                albumId = it.getStringArg("albumId"),
                albumName = it.getStringArg("albumName"),
                albumImageUrl = it.decodeNullableUrl("imageUrl"),
                viewModel = hiltViewModel(),
                onBackClick = { navController.popBackStack() }
            )
        }

        // ----------------------------------
        // 🔹 Spotify Playlist
        // ----------------------------------

        composable(
            Screen.SpotifyPlaylist.route,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType },
                navArgument("playlistName") { type = NavType.StringType },
                navArgument("imageUrl") { nullable = true }
            )
        ) {
            SpotifyPlaylistScreen(
                playlistId = it.getStringArg("playlistId"),
                playlistName = it.getStringArg("playlistName"),
                playlistImageUrl = it.decodeNullableUrl("imageUrl"),
                viewModel = hiltViewModel(),
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

// ----------------------------------
// 🔹 Splash UI (cleaned)
// ----------------------------------

@Composable
private fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TigerBranding()
            Spacer(Modifier.height(24.dp))
            Text(
                text = "TIGER PLAYER",
                style = MaterialTheme.typography.headlineMedium,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

// ----------------------------------
// 🔹 Helpers (BIG CLEANUP WIN)
// ----------------------------------

private fun NavBackStackEntry.getStringArg(key: String): String {
    return arguments?.getString(key) ?: ""
}

private fun NavBackStackEntry.decodeArg(
    key: String,
    fallback: String = ""
): String {
    return Uri.decode(arguments?.getString(key) ?: fallback)
}

private fun NavBackStackEntry.decodeNullableUrl(key: String): String? {
    val raw = arguments?.getString(key)
    return raw?.takeIf { it.isNotEmpty() }?.let {
        URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
    }
}