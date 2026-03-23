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

@Composable
private fun ActionListItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isHighlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun CustomPlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .glassEffect(MaterialTheme.shapes.large)
            .bounceClick { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = WitcherIcons.Playlist,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f), MaterialTheme.shapes.extraLarge)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Forge New Playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), CircleShape)
                    .glassEffect(CircleShape),
                placeholder = { Text("Name your collection...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                shape = CircleShape
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { if (text.isNotBlank()) onCreate(text) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = text.isNotBlank()
                ) {
                    Text("CREATE", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}