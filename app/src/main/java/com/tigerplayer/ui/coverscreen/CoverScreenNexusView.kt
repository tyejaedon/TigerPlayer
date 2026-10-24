package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.ui.equalizer.AuralNexusViewModel
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.glassEffect
import kotlin.math.hypot

@Composable
fun CoverScreenNexusView(
    viewModel: AuralNexusViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedNodeId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
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
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close EQ", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Spatial Touch Pad for EQ Nodes
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                        .pointerInput(state.nodes) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val canvasWidth = size.width.toFloat()
                                    val canvasHeight = size.height.toFloat()
                                    if (canvasWidth > 0f && canvasHeight > 0f) {
                                        val clicked = state.nodes.minByOrNull { node ->
                                            val nx = (node.spatialPos.x + 1f) / 2f * canvasWidth
                                            val ny = (1f - (node.spatialPos.y + 1f) / 2f) * canvasHeight
                                            hypot(nx - offset.x, ny - offset.y)
                                        }
                                        if (clicked != null) {
                                            val nx = (clicked.spatialPos.x + 1f) / 2f * canvasWidth
                                            val ny = (1f - (clicked.spatialPos.y + 1f) / 2f) * canvasHeight
                                            if (hypot(nx - offset.x, ny - offset.y) < 50f) {
                                                selectedNodeId = clicked.id
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    val id = selectedNodeId
                                    if (id != null) {
                                        val canvasWidth = size.width.toFloat()
                                        val canvasHeight = size.height.toFloat()
                                        if (canvasWidth > 0f && canvasHeight > 0f) {
                                            val node = state.nodes.find { it.id == id }
                                            if (node != null) {
                                                val newX = (node.spatialPos.x + (dragAmount.x / (canvasWidth / 2f))).coerceIn(-1f, 1f)
                                                val newY = (node.spatialPos.y - (dragAmount.y / (canvasHeight / 2f))).coerceIn(-1f, 1f)
                                                viewModel.moveNode(id, Offset(newX, newY))
                                                change.consume()
                                            }
                                        }
                                    }
                                },
                                onDragEnd = { selectedNodeId = null },
                                onDragCancel = { selectedNodeId = null }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // Draw guidelines
                        drawLine(
                            color = Color.White.copy(alpha = 0.1f),
                            start = Offset(0f, canvasHeight / 2f),
                            end = Offset(canvasWidth, canvasHeight / 2f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.1f),
                            start = Offset(canvasWidth / 2f, 0f),
                            end = Offset(canvasWidth / 2f, canvasHeight),
                            strokeWidth = 1f
                        )

                        // Draw Nodes
                        state.nodes.forEach { node ->
                            val nx = (node.spatialPos.x + 1f) / 2f * canvasWidth
                            val ny = (1f - (node.spatialPos.y + 1f) / 2f) * canvasHeight

                            drawCircle(
                                color = node.color,
                                radius = 14f,
                                center = Offset(nx, ny)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 6f,
                                center = Offset(nx, ny)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active Preset / Mood Label
                Text(
                    text = "Active EQ Preset: ${state.currentMood}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
