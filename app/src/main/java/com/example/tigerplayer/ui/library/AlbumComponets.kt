package com.example.tigerplayer.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.tigerGlow

@Composable
fun AlbumSearchRow(track: AudioTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            fallback = painterResource(R.drawable.ic_tiger_logo),
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = track.album,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
fun AlbumCard(track: AudioTrack, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .bounceClick { onClick() }
            .glassEffect(MaterialTheme.shapes.medium)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            fallback = painterResource(R.drawable.ic_tiger_logo),
            modifier = Modifier
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.album,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
fun AlbumsTab(viewModel: PlayerViewModel, onNavigateToAlbum: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val albums = remember(uiState.tracks) {
        uiState.tracks.distinctBy { it.album.lowercase().trim() }.sortedBy { it.album }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums) { track ->
            AlbumCard(track) { onNavigateToAlbum(track.album) }
        }
    }
}
@Composable
fun AlbumGridItem(
    albumName: String,
    artistName: String,
    artworkUri: Uri?,
    trackCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
    ) {
        // High-Quality Square Album Art
        AsyncImage(
            model = artworkUri,
            contentDescription = "Album Art for $albumName",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Forces the image to be a perfect square regardless of column width
                .clip(MaterialTheme.shapes.medium) // Applies the sharp Witcher cut corners!
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 1. Album Name (Primary)
        Text(
            text = albumName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 2. Album Artist & Track Count (Secondary)
        Text(
            text = "$artistName • $trackCount Tracks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistAlbumCard(
    track: AudioTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(144.dp) // Slightly wider for better balance
            .bounceClick { onClick() }, // Restoring the "weighty" tactile feel
        horizontalAlignment = Alignment.Start // Gallery-style alignment
    ) {
        // --- THE CANVAS: Glow-Enhanced Artwork ---
        Box(
            modifier = Modifier
                .size(144.dp)
                .tigerGlow() // Using the signature glow instead of standard shadow
                .clip(RoundedCornerShape(18.dp)) // Softer, premium corners
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = "Album Art for ${track.album}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- THE METADATA: Manifesting the Year ---
        Text(
            text = track.album,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black, // High-contrast hierarchy
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = (-0.5).sp
        )

        // Adding your Year logic back in with better thematic contrast
        track.year?.let { year ->
            Text(
                text = year,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), // Witcher accent color
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        } ?: Text(
            text = "Unknown Era",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}