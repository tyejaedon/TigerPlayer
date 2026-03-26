package com.example.tigerplayer.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

// --- VANGUARD CONSTANTS ---


// ==========================================
// --- 1. THE MAIN GRID (Adaptive Layout) ---
// ==========================================

@Composable
fun AlbumsGrid(
    viewModel: PlayerViewModel,
    onAlbumClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tracks = uiState.tracks

    // Grouping tracks into unique Volumes (Albums)
    val albums = remember(tracks) {
        tracks.groupBy { it.album.trim() }
            .filterKeys { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }
            .entries.toList()
            .sortedBy { it.key }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(albums, key = { it.key }) { entry ->
            val firstTrack = entry.value.first()
            AlbumGridItem(
                albumName = entry.key,
                artistName = firstTrack.artist,
                artworkUri = firstTrack.artworkUri,
                trackCount = entry.value.size,
                onClick = { onAlbumClick(entry.key) }
            )
        }
    }
}

// ==========================================
// --- 2. THE GRID ITEM (Individual Card) ---
// ==========================================

@Composable
fun AlbumGridItem(
    albumName: String,
    artistName: String,
    artworkUri: android.net.Uri?,
    trackCount: Int,
    onClick: () -> Unit
) {
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val ArmorBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
    ) {
        // THE VOLUME COVER: 1:1 Aspect Ratio with Armor Border
        AlbumArtWithArmor(
            artworkUri = artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Metadata: High-Contrast Titles
        Text(
            text = albumName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = artistName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                letterSpacing = 0.5.sp
            )

            Text(
                text = " • $trackCount",
                style = MaterialTheme.typography.labelSmall,
                color = AardBlue,
                fontWeight = FontWeight.Black
            )
        }
    }
}

// ==========================================
// --- 3. THE HORIZONTAL ROW (Featured) ---
// ==========================================

@Composable
fun HorizontalAlbumRow(
    title: String,
    albums: List<Pair<String, List<AudioTrack>>>,
    onAlbumClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = AardBlue,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
// ✅ This Ritual Succeeds
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums) { (albumName, tracks) ->
                val firstTrack = tracks.first()
                Column(modifier = Modifier.width(140.dp).bounceClick { onAlbumClick(albumName) }) {
                    AlbumArtWithArmor(
                        artworkUri = firstTrack.artworkUri,
                        modifier = Modifier.size(140.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ==========================================
// --- 4. CORE UTILITIES (The Visual Identity) ---
// ==========================================

@Composable
fun AlbumArtWithArmor(
    artworkUri: android.net.Uri?,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = artworkUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        fallback = painterResource(R.drawable.ic_tiger_logo),
        error = painterResource(R.drawable.ic_tiger_logo),
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            // THE ARMOR BORDER: Essential for visibility on deep blacks
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.large
            )
    )
}

@Composable
fun AlbumMetadataBadge(text: String, isHighlight: Boolean = false) {
    Surface(
        color = if (isHighlight) AardBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = androidx.compose.foundation.shape.CircleShape,
        border = BorderStroke(1.dp, if (isHighlight) AardBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isHighlight) AardBlue else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            letterSpacing = 1.sp
        )
    }
}