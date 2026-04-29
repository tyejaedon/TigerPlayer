package com.example.tigerplayer.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.igniRed

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
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            // Custom Glass Drag Handle
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(36.dp, 4.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
            )
        }
    ) {
        // --- 1. THE PROFOUND GLASS BODY ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                .glassEffect(RoundedCornerShape(32.dp)), // Heavy rounding
            color = Color.Transparent, // Managed by glassEffect
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f), // Specular rim light
                        Color.Transparent
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // --- HEADER: THE MEDALLION ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        fallback = painterResource(R.drawable.ic_tiger_logo)
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // --- ACTIONS RITUAL ---
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SheetActionRow(
                        icon = WitcherIcons.Play,
                        text = "Play Next",
                        accentColor = MaterialTheme.aardBlue
                    ) {
                        onPlayNext()
                        onDismiss()
                    }

                    SheetActionRow(
                        icon = WitcherIcons.Add,
                        text = "Add to Playlist",
                        accentColor = MaterialTheme.aardBlue
                    ) {
                        showPlaylistSelector = true
                    }

                    SheetActionRow(
                        icon = WitcherIcons.Album,
                        text = "Go to Album",
                        accentColor = MaterialTheme.igniRed
                    ) {
                        onGoToAlbum(track.album)
                        onDismiss()
                    }
                }
            }
        }
    }

    // --- 2. THE FLOATING GRIMOIRE (Playlist Selector) ---
    if (showPlaylistSelector) {
        Dialog(onDismissRequest = { }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.5f)
                    .glassEffect(MaterialTheme.shapes.extraLarge),
                color = Color.Transparent,
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "SELECT COLLECTION",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    if (playlists.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No grimoires forged yet.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(playlists, key = { it.id }) { playlist ->
                                PlaylistSelectionRow(playlist) {
                                    onAddToPlaylist(playlist.id)
                                    onDismiss()
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
private fun SheetActionRow(
    icon: ImageVector,
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The Icon Orb
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accentColor.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, accentColor.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun PlaylistSelectionRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(WitcherIcons.Playlist, null, tint = MaterialTheme.aardBlue)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

