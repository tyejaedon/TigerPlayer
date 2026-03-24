package com.example.tigerplayer.ui.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.bounceClick

@Composable
fun AlbumsGridV1(
    viewModel: PlayerViewModel,
    onAlbumClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tracks = uiState.tracks

    // 1. Group the flat list of tracks into albums
    val albums = remember(tracks) {
        tracks.groupBy { it.album }.entries.toList()
    }

    // 2. Build the Adaptive Grid
    LazyVerticalGrid(
        // Adaptive sizing means it will fit 2 columns on phones, and 3-4 on tablets automatically
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(albums) { entry ->
            val albumName = entry.key
            val albumTracks = entry.value
            // Grab the first track of the album to use its artwork and artist name
            val firstTrack = albumTracks.first()

            AlbumGridItem(
                albumName = albumName,
                artistName = firstTrack.artist,
                artworkUri = firstTrack.artworkUri,
                trackCount = albumTracks.size,
                onClick = { onAlbumClick(albumName) }
            )
        }
    }
}



// --- The Reusable Grid Card ---

