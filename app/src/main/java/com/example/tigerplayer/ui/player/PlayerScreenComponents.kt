package com.example.tigerplayer.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.theme.aardBlue
import java.util.Random

// ==========================================
// --- 1. FULLSCREEN PLAYER SWITCHER ---
// ==========================================

@Composable
fun PlayerVisualContainer(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // We get the dominant track color. Fallback to aardBlue if unavailable.
    val trackColor by viewModel.trackColor.collectAsState()

    Crossfade(
        targetState = uiState.visualMode,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "VisualModeTransition",
        modifier = modifier
    ) { mode ->
        when (mode) {
            PlayerVisualMode.ARTWORK -> {
                AlbumArtView(
                    uiState = uiState,
                    onToggleMode = { viewModel.toggleVisualMode() }
                )
            }
            PlayerVisualMode.WAVEFORM -> {
                WaveformPlayerView(
                    uiState = uiState,
                    accentColor = trackColor,
                    onToggleMode = { viewModel.toggleVisualMode() }
                )
            }
        }
    }
}

// ==========================================
// --- 2. THE DEFAULT ALBUM ART MODE ---
// ==========================================

@Composable
fun AlbumArtView(
    uiState: PlayerUiState,
    onToggleMode: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(24.dp)
            .shadow(32.dp, RoundedCornerShape(32.dp), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(32.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disables the default Android ripple for a cleaner look
                onClick = onToggleMode
            )
            // 🔥 PREMIUM: Swipe to change view gesture
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 20 || dragAmount < -20) onToggleMode()
                }
            }
    ) {
        AsyncImage(
            model = uiState.currentTrack?.artworkUri,
            contentDescription = "Album Art",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

// ==========================================
// --- 3. THE WAVEFORM MODE ---
// ==========================================

@Composable
fun WaveformPlayerView(
    uiState: PlayerUiState,
    accentColor: Color,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Generates a consistent fake waveform bound specifically to the current track's ID
    val waveform = remember(uiState.currentTrack?.id) {
        generateFakeWaveform(
            uiState.currentTrack?.id ?: "TigerPlayer",
            45
        )
    }

    val progress = if ((uiState.currentTrack?.durationMs ?: 0L) > 0L) {
        uiState.currentPosition.toFloat() / uiState.currentTrack!!.durationMs.toFloat()
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Keeps the same square boundary as the album art
            .padding(horizontal = 24.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggleMode
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 20 || dragAmount < -20) onToggleMode()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        WaveformSeekBar(
            amplitudes = waveform,
            progress = progress,
            isPlaying = uiState.isPlaying,
            color = accentColor
        )
    }
}

// ==========================================
// --- 4. THE WAVEFORM SEEK BAR ---
// ==========================================



