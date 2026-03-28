package com.example.tigerplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.tigerGlow

private val AardBlue = Color(0xFF007AFF)
private val LosslessGold = Color(0xFFFFD700)

@Composable
fun DiscoverCarousel(
    tracks: List<AudioTrack>,
    onTrackClick: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Aesthetic Polish: Uppercase to match the rest of your headers
        SectionHeader(title = "NEW DISCOVERIES", subtitle = "Unearthed from the archives")

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(
                items = tracks,
                key = { it.id } // THE FIX: Performance Anchor
            ) { track ->
                LargeAlbumCard(
                    track = track,
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}

@Composable
fun RecentlyPlayedRow(
    tracks: List<AudioTrack>,
    onTrackClick: (AudioTrack) -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Removed unused homeState to prevent unnecessary recompositions

    Column(modifier = modifier) {
        SectionHeader(title = "RECENT CONTRACTS", subtitle = "Echoes of past chants")

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = tracks,
                key = { it.id } // THE FIX: Performance Anchor
            ) { track ->
                SmallTrackCard(
                    track = track,
                    isActive = uiState.currentTrack?.id == track.id,
                    isPlaying = uiState.isPlaying,
                    onClick = { onTrackClick(track) },
                    // THE FIX: Proper lambda scope. Empty for now unless you have an Options menu.
                    onMoreClick = { /* Open options dialog here in the future */ }
                    // Notice we don't pass a 'modifier' here, so it safely defaults to 280.dp!
                )
            }
        }
    }
}

// --- The Mastercrafted Cards ---

@Composable
private fun LargeAlbumCard(
    track: AudioTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .bounceClick { onClick() }
    ) {
        Box {
            // THE IMAGE: Sharp cuts with a subtle Aard glow border
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier
                    .size(220.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.shapes.extraLarge)
            )

            // THE LOSSLESS BADGE: Appears if bit depth is high (FLAC ritual)
            if (track.mimeType.contains("flac", ignoreCase = true)) {
                LosslessBadge(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
        }

        // TEXT OVERLAY: Glassmorphic label attached to the card
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = AardBlue,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}


@Composable
fun SmallTrackCard(
    track: AudioTrack,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    // THE FIX: Expose the modifier so it doesn't break LazyRows!
    modifier: Modifier = Modifier.width(280.dp)
) {
    val primaryText = MaterialTheme.colorScheme.onSurface
    val secondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Row(
        // THE FIX: Apply the passed modifier instead of hardcoding fillMaxWidth()
        modifier = modifier
            .glassEffect(MaterialTheme.shapes.large)
            .bounceClick { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .then(if (isActive) Modifier.tigerGlow() else Modifier)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier.fillMaxSize()
            )

            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = WitcherIcons.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive) MaterialTheme.aardBlue else primaryText,
                fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.aardBlue.copy(alpha = 0.7f) else secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.5.sp
            )
        }

        if (isActive) {
            Icon(
                imageVector = WitcherIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.aardBlue,
                modifier = Modifier.size(20.dp).padding(end = 8.dp)
            )
        } else {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = WitcherIcons.Options,
                    contentDescription = "Options",
                    tint = secondaryText.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// --- Internal Ornaments ---

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun LosslessBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        // S22 Optimization: Increased alpha to 0.85f to block busy art patterns
        color = Color.Black.copy(alpha = 0.85f),
        shape = CircleShape,
        // Thicker, high-contrast border to separate from light album covers
        border = BorderStroke(1.dp, AardBlue.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .graphicsLayer {
                    // Subtle shadow to lift the text off the dark surface
                    shadowElevation = 4f
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = WitcherIcons.HighRes,
                contentDescription = null,
                // Using a slightly "Electric" version of AardBlue for visibility
                tint = AardBlue,
                modifier = Modifier.size(12.dp) // Bumped from 10dp
            )
            Text(
                text = "HI-RES",
                // Using labelMedium from our new S22 Typography (9sp)
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White, // Force white for absolute contrast
                letterSpacing = 1.sp
            )
        }
    }
}