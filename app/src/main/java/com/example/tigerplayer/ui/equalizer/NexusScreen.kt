package com.example.tigerplayer.ui.equalizer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import kotlin.math.*

private val AardBlue = Color(0xFF4FC3F7)
private val IgniRed = Color(0xFFFF5252)
private val NeuralPurple = Color(0xFFB388FF)
private val BitPerfectGold = Color(0xFFFFD700)

@Composable
fun AuralNexusScreen(
    viewModel: AuralNexusViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val presets = listOf("Neural Adaptive", "Night Drive", "Pure Vocal", "Studio Flat")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030406))
    ) {
        NebulaBackground(uiState.frequencyResponseCurve)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(uiState.currentMood, onClose)

            NexusSpatialCanvas(
                nodes = uiState.nodes,
                modifier = Modifier.weight(1f),
                onNodeDragged = viewModel::moveNode
            )

            Text(
                text = "ACOUSTIC ALIGNMENTS",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
            )
            PresetSelector(presets, uiState.currentMood, viewModel)
        }
    }
}

@Composable
private fun Header(currentMood: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "AURAL NEXUS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentMood.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = AardBlue,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                .bounceClick { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
fun NexusSpatialCanvas(
    nodes: List<SpatialNode>,
    modifier: Modifier = Modifier,
    onNodeDragged: (String, Offset) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .shadow(16.dp, RoundedCornerShape(32.dp), spotColor = AardBlue.copy(alpha = 0.2f))
            .glassEffect(RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(32.dp))
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2
        val cy = h / 2

        // --- GRID & RADAR DEPTH ---
        Canvas(Modifier.fillMaxSize()) {
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)

            for (i in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f / i),
                    radius = (w * 0.22f * i),
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f, pathEffect = dashEffect)
                )
            }

            drawLine(Color.White.copy(0.06f), Offset(0f, cy), Offset(w, cy), strokeWidth = 2f)
            drawLine(Color.White.copy(0.06f), Offset(cx, 0f), Offset(cx, h), strokeWidth = 2f)
        }

        // --- CELESTIAL NODES ---
        nodes.forEach { node ->
            // FIXED: Key local drag states to individual node IDs to avoid loop reuse bugs
            var isDragging by remember(node.id) { mutableStateOf(false) }
            var dragOffset by remember(node.id) { mutableStateOf(node.spatialPos) }

            // FIXED: Update dragOffset locally if ViewModel state changes from outside (Preset selections)
            LaunchedEffect(node.spatialPos) {
                if (!isDragging) {
                    dragOffset = node.spatialPos
                }
            }

            val targetPos = if (isDragging) dragOffset else node.spatialPos

            // Smoothed spring only fires on release or preset modification, not during dragging
            val animatedPos by animateOffsetAsState(
                targetValue = targetPos,
                animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.65f, stiffness = 250f),
                label = "nodeAnim_${node.id}"
            )

            val px = cx + animatedPos.x * cx
            val py = cy + animatedPos.y * cy

            val glowIntensity = (1f - (abs(animatedPos.y))).coerceIn(0.2f, 1f)

            Box(
                modifier = Modifier
                    .offset(
                        with(LocalDensity.current) { px.toDp() - 36.dp },
                        with(LocalDensity.current) { py.toDp() - 36.dp }
                    )
                    .size(72.dp)
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragOffset = node.spatialPos
                            },
                            onDragEnd = {
                                isDragging = false
                                // Magnetic snapping on axis lines applied purely on end of drag
                                val snapX = if (abs(dragOffset.x) < 0.08f) 0f else dragOffset.x
                                val snapY = if (abs(dragOffset.y) < 0.08f) 0f else dragOffset.y
                                onNodeDragged(node.id, Offset(snapX, snapY))
                            },
                            onDragCancel = {
                                isDragging = false
                                onNodeDragged(node.id, dragOffset)
                            }
                        ) { change, dragAmount ->
                            change.consume()

                            val newX = (dragOffset.x + dragAmount.x / cx).coerceIn(-1f, 1f)
                            val newY = (dragOffset.y + dragAmount.y / cy).coerceIn(-1f, 1f)

                            dragOffset = Offset(newX, newY)
                            onNodeDragged(node.id, dragOffset)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(node.color.copy(alpha = glowIntensity * 0.6f), Color.Transparent)
                        ),
                        radius = size.width / 2f
                    )
                    drawCircle(
                        color = node.color,
                        radius = 12.dp.toPx()
                    )
                    drawCircle(
                        color = Color(0xFF030406),
                        radius = 6.dp.toPx()
                    )
                }

                Text(
                    text = node.label.uppercase(),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 8.dp)
                )
            }
        }

        // --- CENTER CORE (USER ANCHOR) ---
        val infiniteTransition = rememberInfiniteTransition(label = "CorePulse")
        val pulseRadius by infiniteTransition.animateFloat(
            initialValue = 0.7f, targetValue = 1.3f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color.White.copy(0.1f), Color.Transparent)),
                    radius = (size.width / 2f) * pulseRadius
                )
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.White.copy(alpha = 0.8f), CircleShape)
                    .shadow(8.dp, CircleShape, spotColor = Color.White)
            )
        }
    }
}

@Composable
fun PresetSelector(
    presets: List<String>,
    selected: String,
    viewModel: AuralNexusViewModel
) {
    LazyRow(
        modifier = Modifier.padding(bottom = 32.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(presets) { mood ->
            val active = mood == selected

            val bgColor by animateColorAsState(
                targetValue = if (active) AardBlue else Color.White.copy(alpha = 0.05f),
                label = "presetBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (active) Color.Black else Color.White,
                label = "presetText"
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(1.dp, if (active) Color.Transparent else Color.White.copy(alpha = 0.1f), CircleShape)
                    .bounceClick { viewModel.setMoodPreset(mood) }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = mood.uppercase(),
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun NebulaBackground(points: List<Offset>) {
    val alphaAnim by rememberInfiniteTransition(label = "NebulaAlphaTransition").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "NebulaAlpha"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(24.dp)
    ) {
        if (points.isEmpty()) return@Canvas

        val path = Path()
        val w = size.width
        val h = size.height
        val cy = h / 2

        val first = points.first()
        path.moveTo(first.x * w, cy + first.y * cy * 0.8f)

        for (i in 1 until points.size) {
            val p = points[i - 1]
            val c = points[i]

            val px = p.x * w
            val py = cy + p.y * cy * 0.8f
            val cx = c.x * w
            val cy2 = cy + c.y * cy * 0.8f

            val midX = (px + cx) / 2
            path.cubicTo(midX, py, midX, cy2, cx, cy2)
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(IgniRed, BitPerfectGold, AardBlue, NeuralPurple)),
            style = Stroke(width = 120f, cap = StrokeCap.Round),
            alpha = alphaAnim * 0.5f
        )

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(IgniRed, BitPerfectGold, AardBlue, NeuralPurple)),
            style = Stroke(width = 30f, cap = StrokeCap.Round),
            alpha = alphaAnim
        )

        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.6f),
            style = Stroke(width = 3f)
        )
    }
}