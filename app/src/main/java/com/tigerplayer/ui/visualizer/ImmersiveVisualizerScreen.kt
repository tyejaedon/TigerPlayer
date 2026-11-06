package com.tigerplayer.ui.visualizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tigerplayer.ui.equalizer.EqualizerHubViewModel
import com.tigerplayer.ui.equalizer.NexusSpatialCanvas

@Composable
fun ImmersiveVisualizerScreen(
    viewModel: EqualizerHubViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // We reuse the NexusSpatialCanvas which already wraps FluidRenderEngine inside AndroidView
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        NexusSpatialCanvas(
            nodes = uiState.spatialNodes,
            curvePoints = uiState.frequencyCurvePoints,
            audioFrame = uiState.audioReactiveFrame,
            modifier = Modifier.fillMaxSize(),
            onNodeDragged = viewModel::moveSpatialNode
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Visualizer",
                tint = Color.White
            )
        }
    }

    DisposableEffect(Unit) {
        // We ensure Spatial Nexus Engine is ON when we open the view
        viewModel.setActiveEngineSource(com.tigerplayer.ui.equalizer.EqualizerEngineSource.SPATIAL_NODES)
        onDispose {
            // Revert to Parametric EQ when closing to not leave the nodes active (or leave it active if the user wanted it, but let's let the Hub manage it)
            viewModel.setActiveEngineSource(com.tigerplayer.ui.equalizer.EqualizerEngineSource.PARAMETRIC_BANDS)
        }
    }
}
