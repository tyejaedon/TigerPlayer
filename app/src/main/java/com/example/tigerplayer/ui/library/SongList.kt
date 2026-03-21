package com.example.tigerplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import java.util.concurrent.TimeUnit
import androidx.compose.ui.graphics.vector.ImageVector


@Composable
fun SongsList(viewModel: PlayerViewModel, onGoToAlbum: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    // THE FIX 1: Collect your forged playlists
    val playlists by viewModel.customPlaylists.collectAsState()

    val tracks = uiState.filteredTracks
    val currentTrack = uiState.currentTrack
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(tracks, key = { track -> track.id }) { track ->
                val isCurrentTrack = track.id == currentTrack?.id

                Column {
                    SongListItem(
                        track = track,
                        isCurrentTrack = isCurrentTrack,
                        onClick = { viewModel.playTrack(track) },
                        onOptionsClick = { trackForOptions = track }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                        thickness = 1.dp
                    )
                }
            }
        }
    }

    trackForOptions?.let { track ->
        SongOptionsSheet(
            track = track,
            playlists = playlists, // THE FIX 2: Pass them into the sheet
            onDismiss = { trackForOptions = null },
            onPlayNext = {
                viewModel.addToQueue(track)
                trackForOptions = null // Auto-close sheet
            },
            onAddToPlaylist = { playlistId ->
                // THE FIX 3: Push the track to the database
                viewModel.addTrackToPlaylist(playlistId, track)
                trackForOptions = null // Auto-close sheet
            },
            onGoToAlbum = { albumName ->
                onGoToAlbum(albumName)
                trackForOptions = null // Auto-close sheet
            }
        )
    }
}

@Composable
fun SongThumbnail(artworkUri: android.net.Uri?) {
    AsyncImage(
        model = artworkUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.medium)
    )
}

@Composable
private fun SongListItem(
    track: AudioTrack,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val titleColor = if (isCurrentTrack) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    val backgroundColor = if (isCurrentTrack) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .bounceClick { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongThumbnail(track.artworkUri)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // --- THE CLOUD ICON LOGIC ---
                if (track.isRemote) {
                    Icon(
                        imageVector = WitcherIcons.Cloud,
                        contentDescription = "Remote Archive",
                        tint = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.7f
                        ),
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp)
                    )
                }

                val subtitleText = buildString {
                    append(track.artist)
                    if (track.mimeType.contains("flac", true) || track.mimeType.contains(
                            "wav",
                            true
                        )
                    ) {
                        append(" • Lossless")
                    }
                    if (track.isRemote) {
                        append(" • Ritual Sync") // Thematic name for Navidrome tracks
                    }
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (track.isRemote && !isCurrentTrack)
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isCurrentTrack) {
            Icon(
                imageVector = WitcherIcons.VolumeUp,
                contentDescription = "Currently Playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }

        IconButton(onClick = onOptionsClick) {
            Icon(
                imageVector = WitcherIcons.Options,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}
