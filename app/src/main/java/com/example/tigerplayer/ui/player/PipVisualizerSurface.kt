package com.example.tigerplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PipVisualizerSurface(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState = playerViewModel.uiState.collectAsStateWithLifecycle().value
    val currentTrack = uiState.currentTrack

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (currentTrack != null) {
            FluidVortexRenderer(
                isPlaying = uiState.isPlaying,
                amplitudes = uiState.currentWaveform,
                audioReactive = uiState.audioReactiveFrame,
                trackId = currentTrack.id,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "TigerPlayer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

