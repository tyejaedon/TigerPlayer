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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        items(uiState.artists, key = { it.name }) { artist ->
            val profile = artistProfiles[artist.name]

            val localArtwork = remember(artist.name, uiState.tracks) {
                uiState.tracks.firstOrNull {
                    // THE FIX 1: Safely ignore case just in case metadata is weird
                    ArtistUtils.getBaseArtist(it.artist).equals(artist.name, ignoreCase = true)
                }?.artworkUri
            }

            LaunchedEffect(artist.name) {
                if (profile == null) {
                    viewModel.fetchArtistProfile(artist.name)
                }
            }

            ArtistListItem(
                artistName = artist.name,
                trackCount = artist.trackCount,
                albumCount = artist.albumCount,
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
    // THE FIX 2: Wrapped in Surface for the premium glass effect
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .bounceClick { onClick() }
            .glassEffect(MaterialTheme.shapes.large),
        color = Color.Black.copy(alpha = 0.2f) // Deep glass background
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = artworkUri,
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White, // High contrast text
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$albumCount Albums • $trackCount Songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Icon(
                imageVector = WitcherIcons.Options,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}