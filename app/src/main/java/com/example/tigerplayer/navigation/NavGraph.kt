package com.example.tigerplayer.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.tigerplayer.R
import com.example.tigerplayer.ui.cloud.CloudViewModel
import com.example.tigerplayer.ui.cloud.SpotifyAlbumDetailScreen
import com.example.tigerplayer.ui.cloud.SpotifyPlaylistScreen
import com.example.tigerplayer.ui.library.AlbumDetailsScreen
import com.example.tigerplayer.ui.library.ArtistDetailsScreen
import com.example.tigerplayer.ui.library.NavidromeLoginScreen
import com.example.tigerplayer.ui.library.PlaylistDetailsScreen
import com.example.tigerplayer.ui.main.MainScreen
import com.example.tigerplayer.ui.permissions.PermissionScreen
import com.example.tigerplayer.ui.player.PlayerViewModel
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
fun TigerPlayerNavGraph(navController: NavHostController) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // --- 1. Splash Screen Ritual ---
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

        // --- 2. Permission Gateway ---
        composable(route = Screen.Permission.route) {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Screen.MainApp.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                }
            )
        }

        // --- 3. Main Application Hub ---
        composable(route = Screen.MainApp.route) {
            MainScreen(
                playerViewModel = hiltViewModel(),
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
                // THE FIX: Wire the Local Playlist Navigation up to the NavHost
                onNavigateToPlaylist = { id, name ->
                    val encodedName = Uri.encode(name) // Safely encode spaces
                    navController.navigate("local_playlist/$id/$encodedName")
                }
            )
        }

        // --- 4. GLOBAL DETAIL SCREENS (Local Archives) ---

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = Uri.decode(backStackEntry.arguments?.getString("artistName") ?: "")
            ArtistDetailsScreen(artistName = name, viewModel = hiltViewModel(), onBackClick = { navController.popBackStack() })
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumName") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = Uri.decode(backStackEntry.arguments?.getString("albumName") ?: "")
            AlbumDetailsScreen(albumName = name, viewModel = hiltViewModel(), onBackClick = { navController.popBackStack() })
        }

        // THE FIX: The missing Local Playlist Route!
        composable(
            route = "local_playlist/{playlistId}/{playlistName}",
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
                viewModel = hiltViewModel(),
                onBackClick = { navController.popBackStack() }
            )
        }

        // --- 5. Navidrome Login ---
        composable(route = Screen.NavidromeLogin.route) {
            val playerViewModel: PlayerViewModel = hiltViewModel()
            NavidromeLoginScreen(
                viewModel = playerViewModel,
                onLoginSuccess = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() }
            )
        }

        // --- 6. Spotify Cloud Routes ---
        composable(
            route = "spotify_album/{albumId}/{albumName}?imageUrl={imageUrl}",
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
                viewModel = hiltViewModel(),
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = "spotify_playlist/{playlistId}/{playlistName}?imageUrl={imageUrl}",
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType },
                navArgument("playlistName") { type = NavType.StringType },
                navArgument("imageUrl") { type = NavType.StringType; nullable = true; defaultValue = null }
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
                viewModel = hiltViewModel(),
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}