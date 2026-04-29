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


@Composable
fun ArchiveSongRow(
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val secondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    val displayDuration = remember(track.durationMs) {
        formatDuration(track.durationMs)
    }

    // Smooth identity transitions instead of binary state jumps
    val activeProgress by animateFloatAsState(
        targetValue = if (isCurrentTrack) 1f else 0f,
        label = "activeProgress"
    )

    val pulseAlpha = if (isCurrentTrack && isPlaying) rememberAardPulse() else 1f

    val elevation = animateDpAsState(
        targetValue = if (isCurrentTrack) 6.dp else 0.dp,
        label = "elevation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .bounceClick { onClick() }
            .graphicsLayer {
                scaleX = 1f + (activeProgress * 0.01f)
                scaleY = 1f + (activeProgress * 0.01f)
                shadowElevation = elevation.value.toPx()
                alpha = 1f
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = 0.92f + (activeProgress * 0.05f)
        ),
        border = BorderStroke(
            1.dp,
            if (isCurrentTrack)
                MaterialTheme.aardBlue.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // --- ARTWORK (now with motion depth) ---
            Box(
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    fallback = painterResource(R.drawable.ic_tiger_logo),
                    modifier = Modifier
                        .size(54.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .graphicsLayer {
                            alpha = 0.95f + (activeProgress * 0.05f)
                            scaleX = 1f + (activeProgress * 0.03f)
                            scaleY = 1f + (activeProgress * 0.03f)
                        }
                )

                // Active playback indicator
                if (isCurrentTrack && isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                MaterialTheme.aardBlue.copy(alpha = 0.12f),
                                MaterialTheme.shapes.medium
                            )
                    )

                    Icon(
                        imageVector = WitcherIcons.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.aardBlue.copy(alpha = pulseAlpha),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // --- TEXT BLOCK ---
            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isCurrentTrack)
                        MaterialTheme.aardBlue
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = track.artist.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentTrack)
                        MaterialTheme.aardBlue.copy(alpha = 0.75f)
                    else
                        secondaryText,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // --- DURATION (subtle but stable) ---
            Text(
                text = displayDuration,
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrentTrack)
                    MaterialTheme.aardBlue.copy(alpha = 0.7f)
                else
                    secondaryText,
                modifier = Modifier.padding(horizontal = 10.dp)
            )

            // --- OPTIONS BUTTON (isolated interaction zone) ---
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .bounceClick { onOptionsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = WitcherIcons.Options,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
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


