package com.tigerplayer.ui.coverscreen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.constellation.PositionedNode
import com.tigerplayer.ui.constellation.ConstellationState
import com.tigerplayer.ui.constellation.ConstellationViewModel
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.glassEffect

@Composable
fun CoverScreenConstellationView(
    viewModel: ConstellationViewModel,
    onArtistSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedNode by remember { mutableStateOf<PositionedNode?>(null) }

    // Zoom and Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    ) {
        when (val s = state) {
            is ConstellationState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TigerCyberCyan)
                }
            }
            is ConstellationState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Cosmic distortion: ${s.message}", color = Color.Red)
                }
            }
            is ConstellationState.Success -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2f + offsetX
                    val centerY = size.height / 2f + offsetY

                    // Draw Edges
                    s.edges.forEach { edge ->
                        val source = s.nodes[edge.sourceId]
                        val target = s.nodes[edge.targetId]
                        if (source != null && target != null) {
                            val startX = centerX + source.baseOrbitCenter.x * scale
                            val startY = centerY + source.baseOrbitCenter.y * scale
                            val endX = centerX + target.baseOrbitCenter.x * scale
                            val endY = centerY + target.baseOrbitCenter.y * scale

                            drawLine(
                                color = Color.White.copy(alpha = 0.12f),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 1f * scale
                            )
                        }
                    }

                    // Draw Nodes
                    s.nodes.values.forEach { node ->
                        val nodeX = centerX + node.baseOrbitCenter.x * scale
                        val nodeY = centerY + node.baseOrbitCenter.y * scale
                        val radius = (12f * node.scale * scale).coerceIn(4f, 28f)

                        drawCircle(
                            color = node.color.copy(alpha = 0.8f),
                            radius = radius,
                            center = Offset(nodeX, nodeY)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.4f),
                            radius = radius,
                            center = Offset(nodeX, nodeY),
                            style = Stroke(width = 1f)
                        )
                    }
                }

                // Bottom Insight / Selected Artist Card
                if (selectedNode != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp)
                            .glassEffect(RoundedCornerShape(16.dp)),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        contentColor = Color.White
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedNode!!.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TigerCyberCyan
                                )
                                Text(
                                    text = "Plays: ${selectedNode!!.playCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Button(
                                onClick = { onArtistSelected(selectedNode!!.label) },
                                colors = ButtonDefaults.buttonColors(containerColor = TigerNeonOrange)
                            ) {
                                Text("Explore")
                            }
                        }
                    }
                } else {
                    // Quick instruction overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .glassEffect(RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = TigerCyberCyan, modifier = Modifier.size(12.dp))
                            Text("Constellation Galaxy Active", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}
