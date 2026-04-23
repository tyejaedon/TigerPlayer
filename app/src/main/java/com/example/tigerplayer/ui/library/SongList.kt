package com.example.tigerplayer.ui.library

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.tigerGlow
import java.util.concurrent.TimeUnit

// --- VANGUARD CONSTANTS (Dark Mode Optimized) ---


// ==========================================
// --- 1. THE MAIN SONG ITEM (Library/Search) ---
// ==========================================


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
    val secondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val aardPulse = if (isCurrentTrack && isPlaying) rememberAardPulse() else 1f

    // THE FIX: Format the duration once here to avoid redundant calls
    val displayDuration = remember(track.durationMs) { formatDuration(track.durationMs) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp) // Tighter vertical spacing for lists
            .bounceClick { onClick() }
            .glassEffect(MaterialTheme.shapes.large),
        // S22 AMOLED Optimization: Deep translucency
        color = if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f),
        border = if (isCurrentTrack) BorderStroke(1.dp, MaterialTheme.aardBlue.copy(alpha = 0.3f))
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- 1. THE ARTWORK ARMOR ---
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    fallback = painterResource(R.drawable.ic_tiger_logo),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), MaterialTheme.shapes.medium)
                        // Pulsing alpha when active
                        .graphicsLayer { alpha = if (isCurrentTrack && isPlaying) aardPulse else 1f }
                )

                // Active Pulse Indicator (The Witcher's Sign)
                if (isCurrentTrack && isPlaying) {
                    Icon(
                        imageVector = WitcherIcons.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).shadow(8.dp, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // --- 2. THE METADATA ---
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isCurrentTrack) MaterialTheme.aardBlue else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = track.artist.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.7f) else secondaryText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // --- 3. THE DURATION (New Addition) ---
            Text(
                text = displayDuration,
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.6f) else secondaryText,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // --- 4. OPTIONS PORTAL ---
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = WitcherIcons.Options,
                    contentDescription = "Song Options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    modifier = Modifier.size(18.dp)
                )
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


