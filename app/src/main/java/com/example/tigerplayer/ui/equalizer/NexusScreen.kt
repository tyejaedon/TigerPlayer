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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.engine.AudioReactiveFrame
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import kotlin.math.*

private val CyberCyan = Color(0xFF00E5FF)
private val ToxicLime = Color(0xFF39FF14)
private val HotPink = Color(0xFFFF007F)
private val ElectricAmber = Color(0xFFFFD500)

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
        NebulaBackground(uiState.frequencyResponseCurve, uiState.audioReactiveFrame)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(uiState.currentMood, onClose)

            NexusSpatialCanvas(
                nodes = uiState.nodes,
                frequencyResponseCurve = uiState.frequencyResponseCurve,
                audioReactiveFrame = uiState.audioReactiveFrame,
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
                color = CyberCyan,
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
    frequencyResponseCurve: List<Offset>,
    audioReactiveFrame: AudioReactiveFrame,
    modifier: Modifier = Modifier,
    onNodeDragged: (String, Offset) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag("nexus_canvas")
            .shadow(16.dp, RoundedCornerShape(32.dp), spotColor = CyberCyan.copy(alpha = 0.2f))
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

        Canvas(Modifier.fillMaxSize()) {
            if (frequencyResponseCurve.isEmpty()) return@Canvas

            val path = Path()
            val first = frequencyResponseCurve.first()
            path.moveTo(first.x * size.width, size.height / 2f + first.y * size.height * 0.32f)

            for (i in 1 until frequencyResponseCurve.size) {
                val prev = frequencyResponseCurve[i - 1]
                val curr = frequencyResponseCurve[i]

                val px = prev.x * size.width
                val py = size.height / 2f + prev.y * size.height * 0.32f
                val cxPath = curr.x * size.width
                val cyPath = size.height / 2f + curr.y * size.height * 0.32f
                val midX = (px + cxPath) * 0.5f

                path.cubicTo(midX, py, midX, cyPath, cxPath, cyPath)
            }

            val glowWidth = 38f + audioReactiveFrame.flux * 26f + audioReactiveFrame.energy * 20f
            val beamWidth = 10f + audioReactiveFrame.energy * 9f
            val beamAlpha = (0.45f + audioReactiveFrame.flux * 0.35f).coerceIn(0.2f, 0.9f)

            // Neon tube effect: wide transparent glow -> colored beam -> white core.
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(HotPink, ElectricAmber, CyberCyan, ToxicLime)),
                style = Stroke(width = glowWidth, cap = StrokeCap.Round),
                alpha = beamAlpha * 0.35f
            )
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(HotPink, ElectricAmber, CyberCyan, ToxicLime)),
                style = Stroke(width = beamWidth, cap = StrokeCap.Round),
                alpha = beamAlpha
            )
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.78f),
                style = Stroke(width = 2f + audioReactiveFrame.treble * 2f, cap = StrokeCap.Round)
            )
        }

        // --- CELESTIAL NODES ---
        nodes.forEach { node ->
            // FIXED: Key local drag states to individual node IDs to avoid loop reuse bugs
            var isDragging by remember(node.id) { mutableStateOf(false) }
            var dragOffset by remember(node.id) { mutableStateOf(node.spatialPos) }
            var lastHapticBucket by remember(node.id) { mutableIntStateOf(Int.MIN_VALUE) }

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

            val bandPeak = when (node.id) {
                "sub" -> audioReactiveFrame.bass
                "warmth" -> audioReactiveFrame.mid
                "vocal" -> max(audioReactiveFrame.mid, audioReactiveFrame.treble * 0.85f)
                else -> audioReactiveFrame.treble
            }
            val glowIntensity = (0.25f + bandPeak * 0.95f + (1f - abs(animatedPos.y)) * 0.18f)
                .coerceIn(0.2f, 1.15f)

            Box(
                modifier = Modifier
                    .testTag("nexus_node_${node.id}")
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
                                if (snapY == 0f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
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

                            val xBucket = ((newX + 1f) * 8f).toInt().coerceIn(0, 16)
                            val yBucket = ((newY + 1f) * 8f).toInt().coerceIn(0, 16)
                            val hapticBucket = (xBucket shl 8) or yBucket
                            if (hapticBucket != lastHapticBucket) {
                                lastHapticBucket = hapticBucket
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }

                            dragOffset = Offset(newX, newY)
                            onNodeDragged(node.id, dragOffset)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(node.color.copy(alpha = glowIntensity * 0.6f), Color.Transparent)),
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

        // --- CENTER CORE (AUDIO-REACTIVE ANCHOR) ---
        val pulseRadius by animateFloatAsState(
            targetValue = (0.70f + audioReactiveFrame.bass * 0.85f + audioReactiveFrame.flux * 0.2f).coerceIn(0.65f, 1.6f),
            animationSpec = tween(160, easing = FastOutSlowInEasing),
            label = "corePulse"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            CyberCyan.copy(alpha = 0.10f + audioReactiveFrame.energy * 0.25f),
                            Color.Transparent
                        )
                    ),
                    radius = (size.width / 2f) * pulseRadius
                )
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.White.copy(alpha = 0.88f), CircleShape)
                    .shadow(8.dp, CircleShape, spotColor = CyberCyan.copy(alpha = 0.9f))
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
                targetValue = if (active) CyberCyan else Color.White.copy(alpha = 0.05f),
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
fun NebulaBackground(points: List<Offset>, audioReactiveFrame: AudioReactiveFrame) {
    val alphaAnim by animateFloatAsState(
        targetValue = (0.26f + audioReactiveFrame.flux * 0.40f + audioReactiveFrame.energy * 0.20f)
            .coerceIn(0.16f, 0.92f),
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "NebulaAlpha"
    )
    val reactiveStroke by animateFloatAsState(
        targetValue = 48f + audioReactiveFrame.energy * 84f + audioReactiveFrame.flux * 52f,
        animationSpec = tween(180),
        label = "NebulaStroke"
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
            brush = Brush.horizontalGradient(listOf(HotPink, ElectricAmber, CyberCyan, ToxicLime)),
            style = Stroke(width = reactiveStroke * 1.8f, cap = StrokeCap.Round),
            alpha = alphaAnim * 0.5f
        )

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(HotPink, ElectricAmber, CyberCyan, ToxicLime)),
            style = Stroke(width = reactiveStroke, cap = StrokeCap.Round),
            alpha = alphaAnim
        )

        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.6f),
            style = Stroke(width = 2f + audioReactiveFrame.treble * 2f)
        )
    }
}