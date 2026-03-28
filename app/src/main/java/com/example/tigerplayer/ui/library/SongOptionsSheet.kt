package com.example.tigerplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    track: AudioTrack,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onGoToAlbum: (String) -> Unit
) {
    var showPlaylistSelector by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            // --- HEADER WITH ARTWORK ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    contentScale = ContentScale.Crop,
                    fallback = painterResource(R.drawable.ic_tiger_logo) // Ensure you have this
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            // --- ACTIONS ---
            SheetActionRow(icon = WitcherIcons.Play, text = "Play Next", onClick = {
                onPlayNext()
                onDismiss()
            })

            SheetActionRow(icon = WitcherIcons.Add, text = "Add to Playlist", onClick = {
                showPlaylistSelector = true
            })

            SheetActionRow(icon = WitcherIcons.Album, text = "Go to Album", onClick = {
                onGoToAlbum(track.album)
                onDismiss()
            })
        }
    }

    // --- THE PLAYLIST SELECTOR DIALOG ---
    if (showPlaylistSelector) {
        Dialog(onDismissRequest = { showPlaylistSelector = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .glassEffect(MaterialTheme.shapes.large),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "ADD TO COLLECTION",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (playlists.isEmpty()) {
                        Text("No custom collections forged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn {
                            items(playlists) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .bounceClick {
                                            onAddToPlaylist(playlist.id)
                                            showPlaylistSelector = false
                                            onDismiss() // Close the whole bottom sheet
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(WitcherIcons.Playlist, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .bounceClick { onClick() }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = text, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}