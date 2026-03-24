package com.example.tigerplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

@Composable
fun PlaylistsList(
    viewModel: PlayerViewModel,
    onNavigateToPlaylist: (Long, String) -> Unit = { _, _ -> }
) {
    val customPlaylists by viewModel.customPlaylists.collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }

    val likedSongsPlaylist = remember(customPlaylists) {
        customPlaylists.find { it.name.equals("Liked Songs", ignoreCase = true) }
    }
    val standardPlaylists = remember(customPlaylists) {
        customPlaylists.filter { !it.name.equals("Liked Songs", ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // --- 1. The Liked Songs Archive (Top Priority) ---
            item {
                LikedSongsCard(
                    trackCount = likedSongsPlaylist?.trackCount ?: 0,
                    onClick = {
                        // THE FIX: Always navigate, even if empty. Use -3L as the "Empty Liked Songs" flag
                        val playlistId = likedSongsPlaylist?.id ?: -3L
                        onNavigateToPlaylist(playlistId, "Liked Songs")
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // --- 2. Action: Create New ---
            item {
                ActionListItem(
                    icon = WitcherIcons.Add,
                    title = "Forge New Playlist",
                    onClick = { showCreateDialog = true },
                    isHighlight = true
                )
            }

            // --- 3. Smart/Auto Playlists ---
            item {
                ActionListItem(
                    icon = WitcherIcons.Favorite,
                    title = "Favorites",
                    onClick = { onNavigateToPlaylist(-1L, "Favorites") }
                )
            }

            item {
                ActionListItem(
                    icon = WitcherIcons.Duration,
                    title = "Recently Added",
                    onClick = { onNavigateToPlaylist(-2L, "Recently Added") }
                )
            }

            // --- 4. User-Forged Playlists ---
            if (standardPlaylists.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Collections",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
                    )
                }

                items(standardPlaylists) { playlist ->
                    CustomPlaylistRow(
                        playlist = playlist,
                        onClick = { onNavigateToPlaylist(playlist.id, playlist.name) }
                    )
                }
            }
        }

        // --- 5. The Creation Overlay ---
        if (showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name ->
                    viewModel.createPlaylist(name)
                    showCreateDialog = false
                }
            )
        }
    }
}

