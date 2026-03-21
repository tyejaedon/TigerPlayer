package com.example.tigerplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
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
            // THE FIX: Explicitly observe the profile for THIS artist
            val profile = artistProfiles[artist.name]

            val localArtwork = remember(artist.name, uiState.tracks) {
                uiState.tracks.firstOrNull {
                    ArtistUtils.getBaseArtist(it.artist) == artist.name
                }?.artworkUri
            }

            // Trigger fetch only if we don't have it yet
            LaunchedEffect(artist.name) {
                if (profile == null) {
                    viewModel.fetchArtistProfile(artist.name)
                }
            }

            ArtistListItem(
                artistName = artist.name,
                trackCount = artist.trackCount,
                albumCount = artist.albumCount,
                // Priority: Spotify High-Res > Local Album Art > Default
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                text = "$albumCount Albums • $trackCount Songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        Icon(
            imageVector = WitcherIcons.Options,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
    }
}
