package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.ui.equalizer.EqualizerEngineSource
import com.tigerplayer.ui.equalizer.EqualizerHubViewModel
import com.tigerplayer.ui.equalizer.NexusMoodPresetSelector
import com.tigerplayer.ui.equalizer.NexusSpatialCanvas
import com.tigerplayer.ui.equalizer.TwoWayEngineSourceSwitcher
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.glassEffect

@Composable
fun CoverScreenNexusView(
    viewModel: EqualizerHubViewModel = hiltViewModel(),
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHubMode by remember { mutableStateOf(false) }

    if (isHubMode) {
        com.tigerplayer.ui.equalizer.EqualizerHubView(
            isCoverScreen = true,
            onClose = { isHubMode = false },
            modifier = modifier
        )
        return
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onClose)
            .testTag("CoverScreenNexusView")
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .glassEffect(RoundedCornerShape(24.dp)),
            color = Color(0xFF17181D).copy(alpha = 0.8f),
            shape = RoundedCornerShape(24.dp),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Nexus Spatial EQ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TigerCyberCyan
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { isHubMode = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PEQ HUB →",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = com.tigerplayer.ui.theme.TigerNeonOrange
                            )
                        }

                        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close EQ", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TwoWayEngineSourceSwitcher(
                    activeSource = state.activeEngineSource,
                    onSelectSource = viewModel::setActiveEngineSource,
                    allowToggleOff = true,
                    onToggleOff = { viewModel.setActiveEngineSource(EqualizerEngineSource.PARAMETRIC_BANDS) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Reactive 2D Spatial Canvas with Magnification Lens
                NexusSpatialCanvas(
                    nodes = state.spatialNodes,
                    curvePoints = state.frequencyCurvePoints,
                    audioFrame = state.audioReactiveFrame,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    onNodeDragged = viewModel::moveSpatialNode
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Mood Selector
                NexusMoodPresetSelector(
                    presets = listOf("Neural Adaptive", "Night Drive", "Pure Vocal", "Studio Flat"),
                    selected = state.spatialMood,
                    onSelectMood = viewModel::setSpatialMoodPreset
                )
            }
        }
    }
}
