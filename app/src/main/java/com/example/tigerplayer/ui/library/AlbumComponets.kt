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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

// --- VANGUARD CONSTANTS ---


// ==========================================
// --- 1. THE MAIN GRID (Adaptive Layout) ---
// ==========================================

@Composable
fun AlbumGridCard(
    title: String,
    artist: String,
    artworkUri: Any?,
    trackCount: Int,
    modifier: Modifier = Modifier, // THE FIX: Receives 3D math from the Grid
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- THE VOLUME COVER (Glass Armor) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // Specular highlight catching the "light"
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            AsyncImage(
                model = artworkUri,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier.fillMaxSize()
            )

            // The Inner Vignette (Depth Ritual)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- METADATA ---
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = artist.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = " • $trackCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.aardBlue,
                fontWeight = FontWeight.Black
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

@Composable
fun AlbumsGrid(
    viewModel: PlayerViewModel,
    onAlbumClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val albums = uiState.albums
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 24.dp,
            bottom = 140.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        items(albums, key = { it }) { albumName ->
            val albumTrack = remember(albumName) {
                uiState.tracks.find { it.album == albumName }
            }

            // We use a Box to calculate the per-item 3D math
            var itemYOffset by remember { mutableStateOf(0f) }
            val screenHeight = 2000f // Approximate, will be refined by viewport

            AlbumGridCard(
                title = albumName,
                artist = albumTrack?.artist ?: "Unknown Artist",
                artworkUri = albumTrack?.artworkUri,
                trackCount = uiState.tracks.count { it.album == albumName },
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        // Track the item's center Y relative to the screen
                        itemYOffset = coordinates.positionInWindow().y + (coordinates.size.height / 2)
                    }
                    .graphicsLayer {
                        // --- THE GEAR ROTATION LOGIC ---
                        val viewportCenter = size.height * 2.5f // Adjust center point
                        val distanceFromCenter = (itemYOffset - viewportCenter) / viewportCenter
                        val coercedOffset = distanceFromCenter.coerceIn(-1f, 1f)

                        // 1. Tilt on the X-axis (Vertical Gear Effect)
                        rotationX = coercedOffset * -35f

                        // 2. Perspective Camera
                        cameraDistance = 12f * density

                        // 3. Scale and Alpha fade at the edges
                        val scale = 1f - (kotlin.math.abs(coercedOffset) * 0.15f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (kotlin.math.abs(coercedOffset) * 0.4f)
                    },
                onClick = { onAlbumClick(albumName) }
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