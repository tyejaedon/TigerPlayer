package com.example.tigerplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.utils.ArtistUtils

@Composable
fun ArtistsList(
    viewModel: PlayerViewModel,
    onArtistClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val artistProfiles by viewModel.artistDetails.collectAsState()

    // THE PERFORMANCE RITUAL:
    // Instead of searching tracks inside the loop, we create a quick lookup map.
    // This turns O(N*M) search into O(1) lookup during scrolling.
    val localArtworkMap = remember(uiState.tracks) {
        uiState.tracks
            .distinctBy { it.artist.lowercase() }
            .associate { ArtistUtils.getBaseArtist(it.artist) to it.artworkUri }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Extra bottom padding so the last artist isn't hidden by the MiniPlayer
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        items(
            items = uiState.artists,
            key = { it.name } // Critical for smooth animations
        ) { artist ->
            val profile = artistProfiles[artist.name]
            val localArtwork = localArtworkMap[artist.name]

            // Automatic Lore Retrieval: Triggers when the artist card enters the viewport
            LaunchedEffect(artist.name) {
                if (profile == null) {
                    viewModel.fetchArtistProfile(artist.name)
                }
            }

            ArtistListItem(
                artistName = artist.name,
                trackCount = artist.trackCount,
                albumCount = artist.albumCount,
                // Priority: 1. Cloud Profile Image | 2. Local File Tag | 3. Tiger Logo
                artworkUri = profile?.imageUrl ?: localArtwork,
                onClick = { onArtistClick(artist.name) }
            )
        }
    }
}

@Composable
private fun ArtistListItem(
    artistName: String,
    trackCount: Int,
    albumCount: Int,
    artworkUri: Any?,
    onClick: () -> Unit
) {
    // We use MaterialTheme colors to ensure the glass is legible in Light & Dark mode
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .bounceClick { onClick() }
            .glassEffect(MaterialTheme.shapes.large),
        color = Color.Transparent // Surface handles the glass tint via the glassEffect modifier
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High-fidelity Artist Portrait
            AsyncImage(
                model = artworkUri,
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                // Fallback icon while loading or if no art exists
                placeholder = painterResource(id = com.example.tigerplayer.R.drawable.ic_tiger_logo),
                error = painterResource(id = com.example.tigerplayer.R.drawable.ic_tiger_logo),
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$albumCount Albums • $trackCount Tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = subTextColor,
                    fontWeight = FontWeight.Medium
                )
            }

            // Minimalist Witcher Chevron
            Icon(
                imageVector = WitcherIcons.Options,
                contentDescription = "Artist Lore",
                tint = subTextColor.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}