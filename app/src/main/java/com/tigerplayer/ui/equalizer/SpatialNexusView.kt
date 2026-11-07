package com.tigerplayer.ui.equalizer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.glassEffect

@Composable
fun SpatialNexusView(
    modifier: Modifier = Modifier,
    viewModel: EqualizerHubViewModel = hiltViewModel(),
    onClose: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("SpatialNexusView"),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = TigerCyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SPATIAL NEXUS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            DspSignalChainPill(state = uiState)
            Spacer(modifier = Modifier.height(10.dp))

            EngineSourceToggle(
                title = "SPATIAL NEXUS",
                isActive = uiState.activeEngineSource == EqualizerEngineSource.SPATIAL_NODES,
                onToggle = { viewModel.setActiveEngineSource(EqualizerEngineSource.SPATIAL_NODES) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            NexusSpatialCanvas(
                nodes = uiState.spatialNodes,
                curvePoints = uiState.frequencyCurvePoints,
                audioFrame = uiState.audioReactiveFrame,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onNodeDragged = viewModel::moveSpatialNode
            )
            Text(
                text = "ALIGNMENTS",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.56f),
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
            )
            NexusMoodPresetSelector(
                presets = listOf("Neural Adaptive", "Night Drive", "Pure Vocal", "Studio Flat"),
                selected = uiState.spatialMood,
                onSelectMood = viewModel::setSpatialMoodPreset
            )
        }
    }
}
