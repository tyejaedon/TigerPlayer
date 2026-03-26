package com.example.tigerplayer.ui.library

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.tigerGlow
import java.util.concurrent.TimeUnit

// --- VANGUARD CONSTANTS (Dark Mode Optimized) ---


// ==========================================
// --- 1. THE MAIN SONG ITEM (Library/Search) ---
// ==========================================

@Composable
fun SongItem(
    track: AudioTrack,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val ArmorBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with Armor Border
        Box(
            modifier = Modifier
                .size(54.dp)
                .then(if (isActive) Modifier.tigerGlow() else Modifier)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
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
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(WitcherIcons.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive) AardBlue else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) AardBlue.copy(alpha = 0.7f) else SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.5.sp
            )
        }

        if (!isActive) {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryText.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        IconButton(onClick = onMoreClick) {
            Icon(WitcherIcons.Options, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }
    }
}

// ==========================================
// --- 2. THE CHAPTER ROW (Playlists/Albums) ---
// ==========================================



// ==========================================
// --- 3. THE ARCHIVE ROW (Home/Recent) ---
// ==========================================

@Composable
fun ArchiveSongRow(
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val PrimaryText = MaterialTheme.colorScheme.onSurface
    val SecondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val ArmorBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .bounceClick { onClick() }
            .glassEffect(MaterialTheme.shapes.large),
        color = if (isCurrentTrack) AardBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
        border = if (isCurrentTrack) BorderStroke(1.dp, AardBlue.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.shapes.medium)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isCurrentTrack) AardBlue else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentTrack) AardBlue.copy(alpha = 0.7f) else SecondaryText,
                    letterSpacing = 1.sp
                )
            }

            IconButton(onClick = onOptionsClick) {
                Icon(WitcherIcons.Options, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ==========================================
// --- 4. UTILITIES & ANIMATIONS ---
// ==========================================

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun rememberAardPulse(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "AardPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )
    return alpha
}

