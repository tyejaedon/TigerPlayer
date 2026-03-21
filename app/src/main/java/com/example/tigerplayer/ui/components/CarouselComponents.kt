package com.example.tigerplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.home.UserStatistics
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick // The heavy physical click

// 1. The Large "Discover" Carousel
@Composable
fun DiscoverCarousel(
    tracks: List<AudioTrack>,
    onTrackClick: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(tracks) { track ->
            LargeAlbumCard(
                track = track,
                onClick = { onTrackClick(track) }
            )
        }
    }
}

// 2. The Smaller "Recently Played" Row
@Composable
fun RecentlyPlayedRow(
    tracks: List<AudioTrack>,
    onTrackClick: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tracks) { track ->
            SmallTrackCard(
                track = track,
                onClick = { onTrackClick(track) }
            )
        }
    }
}

// --- The Individual Cards (Armor Plated) ---

@Composable
private fun LargeAlbumCard(
    track: AudioTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .bounceClick { onClick() } // Heavy interaction
    ) {
        // High-Quality Art Display with sharp Witcher cuts
        AsyncImage(
            model = track.artworkUri,
            contentDescription = "Album Art for ${track.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(200.dp)
                .height(200.dp)
                .clip(MaterialTheme.shapes.medium)
        )

        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )

        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmallTrackCard(
    track: AudioTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .bounceClick { onClick() }
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = "Thumbnail for ${track.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .height(120.dp)
                .clip(MaterialTheme.shapes.small) // Sharp corner cuts
        )

        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

// --- The Statistics Banner ---

@Composable
fun UserStatisticsHeader(statistics: UserStatistics) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.large, // Uses the massive angular cut on the corners!
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant) // Dark leather tone
                .padding(20.dp)
        ) {
            Text(
                text = "Library Records", // Fits the medieval tone better
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, // Igni Red
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    icon = WitcherIcons.Duration,
                    value = "${statistics.totalListeningTimeHours}h",
                    label = "Listened",
                    modifier = Modifier.weight(1f)
                )

                StatItem(
                    icon = WitcherIcons.Headphones,
                    value = statistics.topGenre,
                    label = "Top Genre",
                    modifier = Modifier.weight(1f)
                )

                StatItem(
                    icon = WitcherIcons.LosslessBadge,
                    value = statistics.losslessTrackCount.toString(),
                    label = "Lossless",
                    isHighlighted = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val iconColor = if (isHighlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (isHighlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 4.dp) // Gives slight breathing room between columns
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small) // Sharp cuts for the icon background
                .background(iconColor.copy(alpha = 0.1f))
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // THE FIX: Added maxLines and TextAlign to stop alignment "crapping"
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}