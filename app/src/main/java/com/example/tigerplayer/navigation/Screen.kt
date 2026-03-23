package com.example.tigerplayer.navigation

import android.net.Uri
import com.example.tigerplayer.ui.theme.WitcherIcons
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Global Screen Routes
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Permission : Screen("permission")

    // The Main App Container (Bottom Nav Host)
    object MainApp : Screen("main_app")

    // The Ritual Gateway (Navidrome Login)
    object NavidromeLogin : Screen("navidrome_login")

    // --- Settings & Global Routes ---
    object Settings : Screen("settings")

    // --- Global Detail Routes (These will cover the Bottom Bar) ---

    object ArtistDetail : Screen("artist_detail/{artistName}") {
        fun createRoute(name: String) = "artist_detail/${Uri.encode(name)}"
    }

    object AlbumDetail : Screen("album_details/{albumName}") {
        fun createRoute(name: String) = "album_details/${Uri.encode(name)}"
    }

    object LocalPlaylist : Screen("local_playlist/{playlistId}/{playlistName}") {
        fun createRoute(id: Long, name: String) = "local_playlist/$id/${Uri.encode(name)}"
    }

    object SpotifyPlaylist : Screen("spotify_playlist/{playlistId}/{playlistName}?imageUrl={imageUrl}") {
        fun createRoute(id: String, name: String, url: String?): String {
            val encodedName = Uri.encode(name)
            val encodedUrl = url?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()) } ?: ""
            return "spotify_playlist/$id/$encodedName?imageUrl=$encodedUrl"
        }
    }

    object SpotifyAlbum : Screen("spotify_album/{albumId}/{albumName}?imageUrl={imageUrl}") {
        fun createRoute(id: String, name: String, url: String?): String {
            val encodedName = Uri.encode(name)
            val encodedUrl = url?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()) } ?: ""
            return "spotify_album/$id/$encodedName?imageUrl=$encodedUrl"
        }
    }
}

/**
 * Bottom Navigation Bar Tabs
 */
sealed class BottomNavTab(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavTab("tab_home", "Home", WitcherIcons.Home)
    object Library : BottomNavTab("tab_library", "Library", WitcherIcons.Library)
    object Cloud : BottomNavTab("tab_cloud", "Remote", WitcherIcons.Cloud)
}