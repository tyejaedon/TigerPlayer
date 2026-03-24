package com.example.tigerplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

private val AardBlue = Color(0xFF007AFF)
private val LosslessGold = Color(0xFFFFD700)

@Composable
fun DiscoverCarousel(
    tracks: List<AudioTrack>,
    onTrackClick: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(title = "THE VANGUARD", subtitle = "New discoveries from the archives")

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(tracks) { track ->
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(title = "RECENT CONTRACTS", subtitle = "Echoes of past chants")

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(tracks) { track ->
                SmallTrackCard(
                    track = track,
                    onClick = { onTrackClick(track) }
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
                    .border(1.dp, Color.White.copy(alpha = 0.1f), MaterialTheme.shapes.extraLarge)
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
private fun SmallTrackCard(
    track: AudioTrack,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(260.dp)
            .height(80.dp)
            .glassEffect(MaterialTheme.shapes.large)
            .bounceClick { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.medium)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Icon(
            imageVector = WitcherIcons.Play,
            contentDescription = null,
            tint = AardBlue.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp).padding(end = 4.dp)
        )
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
        color = Color.Black.copy(alpha = 0.6f),
        shape = CircleShape,
        border = BorderStroke(1.dp, AardBlue.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = WitcherIcons.HiRes, // Ensure you have this icon
                contentDescription = null,
                tint = AardBlue,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = "HI-RES",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
    }
}