package com.tigerplayer.ui.coverscreen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.constellation.ConstellationPhysics
import com.tigerplayer.constellation.NodeType
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

    // Zoom and Pan state suited for reeled-in galaxy on cover screen
    var scale by remember { mutableFloatStateOf(0.55f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Continuous 60 FPS motion time clock engine
    val infiniteTransition = rememberInfiniteTransition(label = "CoverTimeEngine")
    val timeClock by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(100000, easing = LinearEasing)),
        label = "CoverOrbitalTime"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF05070C))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.2f, 2.5f)
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(s.nodes) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val centerX = size.width / 2f + offsetX
                                    val centerY = size.height / 2f + offsetY

                                    val tapped = s.nodes.values.find { node ->
                                        if (node.type == NodeType.GALAXY_CORE) return@find false
                                        val pos = ConstellationPhysics.calculatePosition(node, timeClock, s.nodes)
                                        val screenPos = Offset(
                                            centerX + pos.x * scale,
                                            centerY + pos.y * scale
                                        )
                                        val hitRadius = (16f * node.scale * scale).coerceAtLeast(24f)
                                        (tapOffset - screenPos).getDistance() <= hitRadius
                                    }
                                    selectedNode = tapped
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f + offsetX
                        val centerY = size.height / 2f + offsetY

                        // 1. Draw Orbital Rings
                        s.nodes.values.forEach { node ->
                            if (node.orbitRadius > 0f && node.type != NodeType.TRACK) {
                                val orbitCenter = if (node.orbitCenterId != null) {
                                    s.nodes[node.orbitCenterId]?.let { parent ->
                                        ConstellationPhysics.calculatePosition(parent, timeClock, s.nodes)
                                    } ?: node.baseOrbitCenter
                                } else node.baseOrbitCenter

                                val screenCenter = Offset(
                                    centerX + orbitCenter.x * scale,
                                    centerY + orbitCenter.y * scale
                                )

                                drawCircle(
                                    color = node.color.copy(alpha = 0.06f),
                                    radius = node.orbitRadius * scale,
                                    center = screenCenter,
                                    style = Stroke(
                                        width = 1f * scale,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                                    )
                                )
                            }
                        }

                        // 2. Draw Neural Tethers (Edges) with live physics position calculation
                        s.edges.forEach { edge ->
                            val source = s.nodes[edge.sourceId]
                            val target = s.nodes[edge.targetId]
                            if (source != null && target != null) {
                                val sourcePos = ConstellationPhysics.calculatePosition(source, timeClock, s.nodes)
                                val targetPos = ConstellationPhysics.calculatePosition(target, timeClock, s.nodes)

                                val startX = centerX + sourcePos.x * scale
                                val startY = centerY + sourcePos.y * scale
                                val endX = centerX + targetPos.x * scale
                                val endY = centerY + targetPos.y * scale

                                val lineAlpha = (edge.strength * 0.4f).coerceIn(0.05f, 0.7f)

                                drawLine(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            source.color.copy(alpha = lineAlpha),
                                            target.color.copy(alpha = lineAlpha)
                                        )
                                    ),
                                    start = Offset(startX, startY),
                                    end = Offset(endX, endY),
                                    strokeWidth = 1.5f * scale
                                )
                            }
                        }

                        // 3. Draw Celestial Bodies (Nodes) with dynamic orbital position
                        s.nodes.values.forEach { node ->
                            val pos = ConstellationPhysics.calculatePosition(node, timeClock, s.nodes)
                            val nodeX = centerX + pos.x * scale
                            val nodeY = centerY + pos.y * scale

                            val isSelected = selectedNode?.id == node.id
                            val baseRadius = when (node.type) {
                                NodeType.GALAXY_CORE -> 22f
                                NodeType.ARTIST -> 14f
                                NodeType.ALBUM -> 10f
                                NodeType.TRACK -> 6f
                            }
                            val radius = (baseRadius * node.scale * scale).coerceIn(4f, 32f)

                            // Outer glow aura
                            drawCircle(
                                color = node.color.copy(alpha = if (isSelected) 0.45f else 0.15f),
                                radius = radius * 1.6f,
                                center = Offset(nodeX, nodeY)
                            )
                            // Node solid sphere
                            drawCircle(
                                color = if (isSelected) Color.White else node.color.copy(alpha = 0.9f),
                                radius = radius,
                                center = Offset(nodeX, nodeY)
                            )
                            // Subtle rim stroke
                            drawCircle(
                                color = Color.White.copy(alpha = 0.5f),
                                radius = radius,
                                center = Offset(nodeX, nodeY),
                                style = Stroke(width = if (isSelected) 2f else 1f)
                            )
                        }
                    }
                }

                // Bottom Insight / Selected Node Card
                if (selectedNode != null) {
                    val curr = selectedNode!!
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp)
                            .glassEffect(RoundedCornerShape(16.dp)),
                        color = Color.Black.copy(alpha = 0.75f),
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
                                    text = curr.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TigerCyberCyan
                                )
                                val subtitle = when (curr.type) {
                                    NodeType.ARTIST -> "Artist • ${curr.playCount} plays"
                                    NodeType.ALBUM -> "Album • ${curr.playCount} plays"
                                    NodeType.TRACK -> "Track • ${curr.playCount} plays"
                                    NodeType.GALAXY_CORE -> "Galaxy Core"
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            if (curr.type == NodeType.ARTIST) {
                                Button(
                                    onClick = { onArtistSelected(curr.label) },
                                    colors = ButtonDefaults.buttonColors(containerColor = TigerNeonOrange)
                                ) {
                                    Text("Explore")
                                }
                            } else {
                                IconButton(onClick = { selectedNode = null }) {
                                    Text("✕", color = Color.White.copy(alpha = 0.6f))
                                }
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
