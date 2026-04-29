package com.example.tigerplayer.ui.constellation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.constellation.NodeType
import com.example.tigerplayer.constellation.PositionedNode
import com.example.tigerplayer.ui.theme.glassEffect
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ConstellationScreen(
    viewModel: ConstellationViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // --- MAP-STYLE CAMERA ENGINE ---
    // Start zoomed out so the vast universe fits
    val cameraScale = remember { Animatable(0.15f) }
    val cameraPan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    var focusedNodeId by remember { mutableStateOf<String?>(null) }

    // --- CONTINUOUS TIME ENGINE ---
    val infiniteTransition = rememberInfiniteTransition(label = "TimeEngine")
    val timeClock by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(100000, easing = LinearEasing)), label = "OrbitalTime"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030508)) // Deep Void Space
    ) {
        when (val state = uiState) {
            is ConstellationState.Loading -> ConstellationLoadingState()
            is ConstellationState.Error -> ErrorState(state.message, onClose)
            is ConstellationState.Success -> {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 🔥 MULTI-TOUCH GESTURE DETECTOR
                        .pointerInput(Unit) {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                scope.launch {
                                    val newScale = (cameraScale.value * zoomChange).coerceIn(0.05f, 2.5f)
                                    cameraScale.snapTo(newScale)
                                    cameraPan.snapTo(cameraPan.value + panChange)
                                }
                            }
                        }
                        // 🔥 DOUBLE-TAP TO WARP
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    val center = Offset(size.width / 2f, size.height / 2f)

                                    val tappedNode = state.nodes.values.find { node ->
                                        if (node.type == NodeType.GALAXY_CORE) return@find false
                                        val realPos = calculatePosition(node, timeClock, state.nodes)
                                        val screenPos = Offset(
                                            center.x + (realPos.x * cameraScale.value) + cameraPan.value.x,
                                            center.y + (realPos.y * cameraScale.value) + cameraPan.value.y
                                        )
                                        val visualRadius = node.weight * cameraScale.value.coerceIn(0.6f, 1.5f)
                                        (tapOffset - screenPos).getDistance() <= visualRadius * 1.5f
                                    }

                                    if (tappedNode != null) {
                                        focusedNodeId = tappedNode.id
                                        scope.launch {
                                            val targetScale = if (tappedNode.type == NodeType.ALBUM) 1.2f else 0.8f
                                            val nodePos = calculatePosition(tappedNode, timeClock, state.nodes)

                                            cameraScale.animateTo(targetScale, spring(stiffness = Spring.StiffnessLow))
                                            cameraPan.animateTo(-nodePos * targetScale, spring(stiffness = Spring.StiffnessLow))
                                        }
                                    }
                                },
                                onTap = {
                                    focusedNodeId = null
                                }
                            )
                        }
                ) {
                    // --- 1. ASTRONOMICAL TRAJECTORIES & LINKS (CANVAS) ---
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val scale = cameraScale.value
                        val pan = cameraPan.value

                        // Orbital Rings
                        state.nodes.values.forEach { node ->
                            if (node.orbitRadius > 0f && node.type != NodeType.TRACK) {
                                val orbitCenter = if (node.orbitCenterId != null) {
                                    calculatePosition(state.nodes[node.orbitCenterId]!!, timeClock, state.nodes)
                                } else node.baseOrbitCenter

                                val screenCenter = Offset(
                                    center.x + (orbitCenter.x * scale) + pan.x,
                                    center.y + (orbitCenter.y * scale) + pan.y
                                )

                                drawCircle(
                                    color = node.color.copy(alpha = 0.08f),
                                    radius = node.orbitRadius * scale,
                                    center = screenCenter,
                                    style = Stroke(
                                        width = 1.5f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f))
                                    )
                                )
                            }
                        }

                        // Neural Tethers (Edges)
                        state.edges.forEach { edge ->
                            val src = state.nodes[edge.sourceId]
                            val tgt = state.nodes[edge.targetId]
                            if (src != null && tgt != null) {
                                val srcReal = calculatePosition(src, timeClock, state.nodes)
                                val tgtReal = calculatePosition(tgt, timeClock, state.nodes)

                                val srcScreen = Offset(center.x + (srcReal.x * scale) + pan.x, center.y + (srcReal.y * scale) + pan.y)
                                val tgtScreen = Offset(center.x + (tgtReal.x * scale) + pan.x, center.y + (tgtReal.y * scale) + pan.y)

                                val isFocused = focusedNodeId == null || focusedNodeId == src.id || focusedNodeId == tgt.id
                                val lineAlpha = if (isFocused) edge.strength else 0.02f

                                drawLine(
                                    brush = Brush.linearGradient(listOf(src.color.copy(alpha = lineAlpha), tgt.color.copy(alpha = lineAlpha))),
                                    start = srcScreen,
                                    end = tgtScreen,
                                    strokeWidth = if (isFocused) 4f else 1f
                                )
                            }
                        }
                    }

                    // --- 2. CELESTIAL BODIES (COMPOSE NODES) ---
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val canvasCenter = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)

                        state.nodes.values.filter { it.type != NodeType.GALAXY_CORE }.forEach { node ->

                            val isVisible = when (node.type) {
                                NodeType.ARTIST -> true
                                NodeType.ALBUM -> cameraScale.value > 0.25f
                                NodeType.TRACK -> true
                                else -> false
                            }

                            if (isVisible) {
                                val isFocused = focusedNodeId == null || focusedNodeId == node.id || node.orbitCenterId == focusedNodeId
                                val targetAlpha = if (isFocused) 1f else 0.3f
                                val animatedAlpha by animateFloatAsState(targetValue = targetAlpha, label = "alpha")

                                val visualScale = cameraScale.value.coerceIn(0.6f, 1.5f)
                                val nodeRadius = node.weight * visualScale

                                val realPos = calculatePosition(node, timeClock, state.nodes)
                                val screenX = canvasCenter.x + (realPos.x * cameraScale.value) + cameraPan.value.x
                                val screenY = canvasCenter.y + (realPos.y * cameraScale.value) + cameraPan.value.y

                                // Cull off-screen nodes to maintain 120fps performance
                                if (screenX > -500f && screenX < canvasCenter.x * 2 + 500f &&
                                    screenY > -500f && screenY < canvasCenter.y * 2 + 500f) {

                                    Box(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                translationX = screenX - nodeRadius
                                                translationY = screenY - nodeRadius
                                                alpha = animatedAlpha
                                            }
                                            .size((nodeRadius * 2).dp)
                                    ) {
                                        CelestialNodeRenderer(
                                            node = node,
                                            visualScale = visualScale,
                                            onClick = { focusedNodeId = node.id }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 3. UI OVERLAY ---
                ConstellationOverlay(
                    insight = state.insightMessage,
                    onClose = onClose,
                    onRecenter = {
                        scope.launch {
                            cameraScale.animateTo(0.15f, spring(stiffness = Spring.StiffnessLow))
                            cameraPan.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessLow))
                            focusedNodeId = null
                        }
                    }
                )
            }
        }
    }
}

/**
 * Pure mathematical function to compute the exact orbital position of a node at any given time.
 */
private fun calculatePosition(node: PositionedNode, time: Float, allNodes: Map<String, PositionedNode>): Offset {
    val center = if (node.orbitCenterId != null) {
        val parent = allNodes[node.orbitCenterId]
        if (parent != null) calculatePosition(parent, time, allNodes) else node.baseOrbitCenter
    } else {
        node.baseOrbitCenter
    }

    val currentAngle = node.baseAngle + (time * node.orbitSpeed)
    val rx = center.x + cos(currentAngle) * node.orbitRadius
    val ry = center.y + sin(currentAngle) * node.orbitRadius

    return Offset(rx, ry)
}

// =====================================================================
// RENDERING COMPONENTS
// =====================================================================

@Composable
fun CelestialNodeRenderer(node: PositionedNode, visualScale: Float, onClick: () -> Unit) {
    val shape = if (node.type == NodeType.ALBUM) RoundedCornerShape(20) else CircleShape

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Star Core Image
        AsyncImage(
            model = node.imageUrl,
            contentDescription = node.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(0.75f)
                .shadow(if (node.type == NodeType.ARTIST) 24.dp else 8.dp, shape, spotColor = node.color)
                .clip(shape)
                .border(if (node.type == NodeType.ARTIST) 2.dp else 1.dp, node.color.copy(alpha = 0.8f), shape)
                .clickable { onClick() }
        )

        // Floating Data Badge (Planets/Albums)
        if (node.type == NodeType.ALBUM && visualScale > 0.8f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                    .border(1.dp, node.color, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${node.playCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Floating Name Plate
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 20.dp)
                .glassEffect(RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = node.label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (node.type == NodeType.ARTIST) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ConstellationOverlay(insight: String, onClose: () -> Unit, onRecenter: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
            Text("COGNITIVE GALAXY", color = Color.White.copy(alpha = 0.5f), letterSpacing = 2.sp, fontWeight = FontWeight.Black)

            IconButton(onClick = onRecenter, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                Icon(Icons.Rounded.FilterCenterFocus, "Recenter", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = Color(0xFFB388FF))
                .background(Color(0xFF121212).copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFFB388FF).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).background(Color(0xFFB388FF).copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFB388FF))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("COSMIC INSIGHT", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB388FF), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(insight, style = MaterialTheme.typography.bodyMedium, color = Color.White, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun ConstellationLoadingState() {
    val transition = rememberInfiniteTransition(label = "loading")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "spin"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .background(Brush.sweepGradient(listOf(Color(0xFFB388FF), Color.Transparent)), CircleShape)
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.fillMaxSize().padding(16.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "CALCULATING ORBITAL MECHANICS...",
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 3.sp,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun ErrorState(message: String, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Close, null, tint = Color(0xFFFF5252), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))) {
                Text("RETURN", color = Color.White)
            }
        }
    }
}