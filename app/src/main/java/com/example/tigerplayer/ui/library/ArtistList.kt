package com.example.tigerplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.utils.ArtistUtils

@Composable
fun ArtistsList(
    viewModel: PlayerViewModel,
    onArtistClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val artistProfiles by viewModel.artistDetails.collectAsState()

    // THE PERFORMANCE RITUAL (Maintained for O(1) high-speed lookups)
    val localArtworkMap = remember(uiState.tracks) {
        uiState.tracks
            .distinctBy { it.artist.lowercase() }
            .associate { ArtistUtils.getBaseArtist(it.artist) to it.artworkUri }
    }

    // THE FIX: Swapped to a 2-Column Grid
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        // Generous padding ensures the grid breathes and doesn't clip the screen edges
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp) // Slightly more vertical space for text breathing room
    ) {
        items(
            items = uiState.artists,
            key = { it.name } // Critical for smooth animations during fast scrolls
        ) { artist ->
            val profile = artistProfiles[artist.name]
            val localArtwork = localArtworkMap[artist.name]

            LaunchedEffect(artist.name) {
                if (profile == null) {
                    viewModel.fetchArtistProfile(artist.name)
                }
            }

            ArtistGridCard(
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
private fun ArtistGridCard(
    artistName: String,
    trackCount: Int,
    albumCount: Int,
    artworkUri: Any?,
    onClick: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- THE PROFOUND ARTWORK VIEWPORT ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Forces a perfect square regardless of screen size
                // 1. The Shadow Bleed: Casts a subtle, dark ambient glow behind the art
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // 2. The Specular Edge: A crisp inner ring catching the "light"
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
        ) {
            AsyncImage(
                model = artworkUri,
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_tiger_logo),
                error = painterResource(id = R.drawable.ic_tiger_logo),
                modifier = Modifier.fillMaxSize()
            )

            // 3. The Inner Vignette:
            // Creates depth by subtly darkening the inside edges of the image,
            // making the center pop out more dynamically.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f)
                            ),
                            radius = 400f // Pushes the darkness specifically to the corners
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- TYPOGRAPHY ---
        Text(
            text = artistName,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp) // Prevents text from perfectly touching the invisible bounds
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "$albumCount Albums • $trackCount Tracks",
            style = MaterialTheme.typography.labelSmall,
            color = subTextColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}