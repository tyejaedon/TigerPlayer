package com.example.tigerplayer.ui.components

import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.*

private val AardBlue = Color(0xFF007AFF)


// =====================================================
// SECTION HEADER (Cinematic Title Block)
// =====================================================

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}


// =====================================================
// DISCOVER CAROUSEL (Main Entry Point)
// =====================================================

@Composable
fun DiscoverCarousel(
    tracks: List<AudioTrack>,
    onTrackClick: (AudioTrack) -> Unit
) {
    Column {
        SectionHeader(
            title = "NEW DISCOVERIES",
            subtitle = "Unearthed from your sonic archive"
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = tracks,
                key = { it.id }
            ) { track ->
                HeroAlbumCard(
                    track = track,
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}


// =====================================================
// RECENTLY PLAYED ROW (History Stream)
// =====================================================

@RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
@Composable
fun RecentlyPlayedRow(
    tracks: List<AudioTrack>,
    viewModel: PlayerViewModel,
    onTrackClick: (AudioTrack) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column {
        SectionHeader(
            title = "RECENT CONTRACTS",
            subtitle = "Echoes of your listening history"
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = tracks,
                key = { index, track -> "${track.id}-$index" }
            ) { _, track ->
                MiniTrackCard(
                    track = track,
                    isActive = uiState.currentTrack?.id == track.id,
                    isPlaying = uiState.isPlaying,
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}


// =====================================================
// HERO ALBUM CARD (Main Visual Focus)
// =====================================================

@Composable
private fun HeroAlbumCard(
    track: AudioTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .bounceClick { onClick() }
    ) {
        Box {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(28.dp)
                    )
                    .shadow(20.dp, RoundedCornerShape(28.dp))
            )

            // cinematic gradient overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            if (track.mimeType.contains("flac", true)) {
                LosslessBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.artist.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = AardBlue,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 1
        )
    }
}


// =====================================================
// MINI TRACK CARD (Compact Experience View)
// =====================================================

@Composable
fun MiniTrackCard(
    track: AudioTrack,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(280.dp)
            .glassEffect(MaterialTheme.shapes.large)
            .bounceClick { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            fallback = painterResource(R.drawable.ic_tiger_logo),
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(14.dp)
                )
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = track.artist.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1
            )
        }

        if (isActive && isPlaying) {
            Icon(
                imageVector = WitcherIcons.VolumeUp,
                contentDescription = null,
                tint = AardBlue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


// =====================================================
// LOSSLESS BADGE (High Fidelity Indicator)
// =====================================================

@Composable
private fun LosslessBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.85f),
        shape = CircleShape,
        border = BorderStroke(1.dp, AardBlue.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = WitcherIcons.HighRes,
                contentDescription = null,
                tint = AardBlue,
                modifier = Modifier.size(12.dp)
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = "HI-RES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }
    }
}