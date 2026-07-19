package com.example.tigerplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PipVisualizerSurface(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    // 1. OPTIMIZED STATE COLLECTION: Observe only what's required to prevent constant recomposition
    val uiState = playerViewModel.uiState.collectAsStateWithLifecycle().value
    
    val currentTrackId = remember(uiState.currentTrack?.id) { uiState.currentTrack?.id }
    val isPlaying = uiState.isPlaying
    val waveform = uiState.currentWaveform
    val reactiveFrame = uiState.audioReactiveFrame
    val trackColor = playerViewModel.trackColor.collectAsStateWithLifecycle().value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // 2. VISUAL CONTINUITY: Subtle ambient glow based on album art
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(trackColor.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        if (currentTrackId != null) {
            FluidVortexRenderer(
                isPlaying = isPlaying,
                amplitudes = waveform,
                audioReactive = reactiveFrame,
                trackId = currentTrackId,
                isReducedComplexity = true, // 3. PERFORMANCE: Low-power mode for PiP
                modifier = Modifier.fillMaxSize()
            )
            
            // 4. UX: Frozen/Desaturated state indicator when paused
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Paused",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        } else {
            Text(
                text = "TigerPlayer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

