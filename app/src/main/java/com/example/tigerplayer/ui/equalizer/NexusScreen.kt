package com.example.tigerplayer.ui.equalizer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tigerplayer.engine.AudioReactiveFrame
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import kotlin.math.abs

private val CyberCyan = Color(0xFF00E5FF)
private val ToxicLime = Color(0xFF39FF14)
private val HotPink = Color(0xFFFF007F)
private val ElectricAmber = Color(0xFFFFD500)
private val NeonWhite = Color(0xFFF9FDFF)
private val NexusBg = Color(0xFF030308)

@Composable
fun AuralNexusScreen(
    viewModel: AuralNexusViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioFrame by viewModel.audioReactiveFrame.collectAsStateWithLifecycle()
    val presets = remember { listOf("Neural Adaptive", "Night Drive", "Pure Vocal", "Studio Flat") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBg)
    ) {
        NebulaBackground(
            points = uiState.frequencyResponseCurve,
            audioFrame = audioFrame,
            modifier = Modifier.fillMaxSize()
        )

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(uiState.currentMood, onClose)

            NexusSpatialCanvas(
                nodes = uiState.nodes,
                curvePoints = uiState.frequencyResponseCurve,
                audioFrame = audioFrame,
                modifier = Modifier.weight(1f),
                onNodeDragged = viewModel::moveNode
            )

            Text(
                text = "ACOUSTIC ALIGNMENTS",
                style = MaterialTheme.typography.labelSmall,
                color = NeonWhite.copy(alpha = 0.56f),
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
                color = NeonWhite
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
                .background(NeonWhite.copy(alpha = 0.08f), CircleShape)
                .bounceClick { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = NeonWhite)
        }
    }
}

@Composable
private fun NexusSpatialCanvas(
    nodes: List<SpatialNode>,
    curvePoints: List<Offset>,
    audioFrame: AudioReactiveFrame,
    modifier: Modifier = Modifier,
    onNodeDragged: (String, Offset) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val reactiveFrame by rememberUpdatedState(audioFrame)
    val scratchPath = remember { Path() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .shadow(20.dp, RoundedCornerShape(32.dp), spotColor = CyberCyan.copy(alpha = 0.24f))
            .glassEffect(RoundedCornerShape(32.dp))
            .border(1.dp, NeonWhite.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val cx = widthPx * 0.5f
        val cy = heightPx * 0.5f

        Canvas(Modifier.fillMaxSize()) {
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)

            for (i in 1..4) {
                drawCircle(
                    color = NeonWhite.copy(alpha = 0.045f / i),
                    radius = widthPx * 0.19f * i,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.8f, pathEffect = dashEffect)
                )
            }
            drawLine(NeonWhite.copy(0.08f), Offset(0f, cy), Offset(widthPx, cy), strokeWidth = 1.8f)
            drawLine(NeonWhite.copy(0.07f), Offset(cx, 0f), Offset(cx, heightPx), strokeWidth = 1.5f)

            // OPTIMIZED NEON TUBE RENDER
            scratchPath.rewind()
            buildSmoothCurvePath(
                path = scratchPath,
                points = curvePoints,
                width = size.width,
                centerY = size.height * 0.5f,
                yScale = size.height * 0.38f
            )
            val tubeGradient = Brush.horizontalGradient(listOf(HotPink, ElectricAmber, ToxicLime, CyberCyan))
            drawPath(scratchPath, tubeGradient, alpha = 0.14f, style = Stroke(42f, cap = StrokeCap.Round))
            drawPath(scratchPath, tubeGradient, alpha = 0.45f, style = Stroke(18f, cap = StrokeCap.Round))
            drawPath(scratchPath, tubeGradient, alpha = 0.95f, style = Stroke(6f, cap = StrokeCap.Round))
            drawPath(scratchPath, NeonWhite, style = Stroke(2.1f, cap = StrokeCap.Round))

            val corePulse = (0.72f + reactiveFrame.bass * 0.9f).coerceIn(0.72f, 1.8f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(CyberCyan.copy(alpha = 0.30f + reactiveFrame.bass * 0.42f), Color.Transparent)
                ),
                center = Offset(cx, cy),
                radius = 38.dp.toPx() * corePulse
            )
            drawCircle(
                color = NeonWhite.copy(alpha = 0.92f),
                center = Offset(cx, cy),
                radius = 4.5.dp.toPx() + reactiveFrame.bass * 3.5.dp.toPx()
            )
        }

        nodes.forEach { node ->
            var isDragging by remember(node.id) { mutableStateOf(false) }
            var dragOffset by remember(node.id) { mutableStateOf(node.spatialPos) }
            var dragTick by remember(node.id) { mutableIntStateOf(0) }

            LaunchedEffect(node.spatialPos) {
                if (!isDragging) dragOffset = node.spatialPos
            }

            val targetPos = if (isDragging) dragOffset else node.spatialPos
            val animatedPos by animateOffsetAsState(
                targetValue = targetPos,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 290f),
                label = "nexus_node_${node.id}"
            )

            val px = cx + animatedPos.x * cx
            val py = cy + animatedPos.y * cy

            val bandPeak = nodeBandPeak(node.id, reactiveFrame)
            val glowIntensity = (0.20f + bandPeak * 0.80f).coerceIn(0.20f, 1f)

            Box(
                modifier = Modifier
                    .offset(
                        with(LocalDensity.current) { px.toDp() - 34.dp },
                        with(LocalDensity.current) { py.toDp() - 34.dp }
                    )
                    .size(68.dp)
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragOffset = node.spatialPos
                                dragTick = 0
                            },
                            onDragEnd = {
                                isDragging = false
                                val snapX = if (abs(dragOffset.x) < 0.07f) 0f else dragOffset.x
                                val snapY = if (abs(dragOffset.y) < 0.07f) 0f else dragOffset.y
                                val snapped = Offset(snapX, snapY)
                                onNodeDragged(node.id, snapped)

                                if (snapY == 0f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
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

                            dragTick += 1
                            if (dragTick % 6 == 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(node.color.copy(alpha = 0.75f * glowIntensity), Color.Transparent)
                        ),
                        radius = size.width * (0.45f + glowIntensity * 0.5f)
                    )
                    drawCircle(color = node.color, radius = 12.dp.toPx())
                    drawCircle(color = NexusBg, radius = 6.dp.toPx())
                }

                Text(
                    text = node.label.uppercase(),
                    color = NeonWhite.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PresetSelector(
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
                targetValue = if (active) CyberCyan else NeonWhite.copy(alpha = 0.05f),
                label = "nexus_preset_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (active) Color.Black else NeonWhite,
                label = "nexus_preset_text"
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(1.dp, if (active) Color.Transparent else NeonWhite.copy(alpha = 0.10f), CircleShape)
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
private fun NebulaBackground(
    points: List<Offset>,
    audioFrame: AudioReactiveFrame,
    modifier: Modifier = Modifier
) {
    val fluxGlow by animateFloatAsState(
        targetValue = (0.18f + audioFrame.flux * 0.78f).coerceIn(0.18f, 1f),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 180f),
        label = "nebula_flux"
    )
    val energyWidth by animateFloatAsState(
        targetValue = 50f + audioFrame.energy * 120f,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 210f),
        label = "nebula_width"
    )

    val cachedPath = remember { Path() }

    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        cachedPath.rewind()
        buildSmoothCurvePath(
            path = cachedPath,
            points = points,
            width = size.width,
            centerY = size.height * 0.5f,
            yScale = size.height * 0.42f
        )
        
        val gradient = Brush.horizontalGradient(listOf(HotPink, ElectricAmber, ToxicLime, CyberCyan))

        drawPath(
            path = cachedPath,
            brush = gradient,
            style = Stroke(width = energyWidth * 1.95f, cap = StrokeCap.Round),
            alpha = 0.13f + fluxGlow * 0.18f
        )
        drawPath(
            path = cachedPath,
            brush = gradient,
            style = Stroke(width = energyWidth, cap = StrokeCap.Round),
            alpha = 0.2f + fluxGlow * 0.42f
        )
        drawPath(
            path = cachedPath,
            color = NeonWhite.copy(alpha = 0.72f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round)
        )
    }
}

private fun nodeBandPeak(nodeId: String, frame: AudioReactiveFrame): Float {
    return when (nodeId) {
        "sub" -> frame.bass
        "warmth", "presence" -> frame.mid
        "air" -> frame.treble
        else -> frame.energy
    }.coerceIn(0f, 1f)
}


private fun buildSmoothCurvePath(
    path: Path,
    points: List<Offset>,
    width: Float,
    centerY: Float,
    yScale: Float
) {
    if (points.isEmpty()) return

    val first = points.first()
    path.moveTo(first.x * width, centerY + first.y * yScale)

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]

        val prevX = prev.x * width
        val prevY = centerY + prev.y * yScale
        val currX = curr.x * width
        val currY = centerY + curr.y * yScale

        val controlX = (prevX + currX) * 0.5f
        path.cubicTo(controlX, prevY, controlX, currY, currX, currY)
    }
}
