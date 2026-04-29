package com.example.tigerplayer.ui.library

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.glassEffect
import java.util.concurrent.TimeUnit

import com.example.tigerplayer.ui.theme.aardBlue

// --- THE VANGUARD PALETTE (High Visibility) ---
val AardBlue = Color(0xFF4FC3F7) // Brighter blue for better dark-mode contrast
val IgniRed = Color(0xFFFF5252)  // Brighter red for visibility


@Composable
fun ScanningOverlay(progress: Int, total: Int) {
    val glassWhite = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val secondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)) // Deepened for focus
            .pointerInput(Unit) {
                // CRUCIAL: Consume all touches so the user can't click things underneath
                detectTapGestures {  }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp) // S22: Reasonable outer bounds
                .glassEffect(MaterialTheme.shapes.extraLarge)
                .background(glassWhite, MaterialTheme.shapes.extraLarge)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.shapes.extraLarge)
                .padding(horizontal = 24.dp, vertical = 32.dp), // S22: Tightened inner padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pulsing Icon Animation
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                label = "iconAlpha"
            )

            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = AardBlue,
                modifier = Modifier
                    .size(64.dp) // Scaled down from 80.dp
                    .graphicsLayer { this.alpha = alpha }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "INDEXING ARCHIVES",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp) // Sleeker progress bar
                    .clip(CircleShape),
                color = IgniRed,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            Text(
                text = "Manifesting track $progress of $total...",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.labelMedium, // Kept small to prevent wrapping
                color = secondaryText
            )
        }
    }
}

@Composable
fun ChapterSongRow(
    index: Int,
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val pulseAlpha = if (isCurrentTrack && isPlaying) rememberAardPulse() else 1f
    val displayIndex = (index + 1).toString().padStart(2, '0')
    val secondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    // THE CACHE RITUAL: Prevent unnecessary string manipulation on every recomposition
    val displayDuration = remember(track.durationMs) { formatDuration(track.durationMs) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // THE FIX: Applying clickable to the Row but ensuring the IconButton is a separate 'island'
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (isCurrentTrack) Modifier.background(
                    MaterialTheme.aardBlue.copy(alpha = 0.05f),
                    MaterialTheme.shapes.medium
                ) else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 1. THE COORDINATES (Index / Pulse) ---
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrentTrack && isPlaying) {
                Icon(
                    imageVector = WitcherIcons.VolumeUp, // Using VolumeUp for the "Active" state
                    contentDescription = null,
                    tint = MaterialTheme.aardBlue.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = displayIndex,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCurrentTrack) MaterialTheme.aardBlue
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Black
                )
            }
        }

        // --- 2. THE LORE (Metadata) ---
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentTrack) FontWeight.Black else FontWeight.Bold,
                color = if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = pulseAlpha)
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryText,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // --- 3. THE TEMPORAL DATA (Duration) ---
        Text(
            text = displayDuration,
            style = MaterialTheme.typography.labelMedium,
            color = secondaryText,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // --- 4. THE COMMAND PORTAL (Options) ---
        // Bumped to 40dp for S22 ergonomic parity
        IconButton(
            onClick = {
                // Using a separate click handler for the options to ensure
                // it consumes the touch event properly.
                onOptionsClick()
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = WitcherIcons.Options,
                contentDescription = "Lore Options",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
 fun rememberAardPulse(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "AardPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Alpha"
    )
    return alpha
}