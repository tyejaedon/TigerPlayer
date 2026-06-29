package com.example.tigerplayer.ui.home

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tigerplayer.ui.theme.PremiumGlassCard
import com.example.tigerplayer.ui.theme.TigerCyberCyan
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonicFootprintScreen(
    onClose: () -> Unit,
    viewModel: SonicFootprintViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SONIC FOOTPRINT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClose()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.glassEffect(RectangleShape)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Your digital listening DNA, distilled from your local archives.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                TimeRangeSelector(
                    selectedRange = state.timeRange,
                    onRangeSelected = viewModel::setTimeRange
                )
            }

            item {
                PremiumGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    borderWidth = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RadarChart(
                            values = state.axisValues,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "LIFETIME LISTENING: ${"%.1f".format(state.totalListeningHours)}H",
                            style = MaterialTheme.typography.titleSmall,
                            color = TigerNeonOrange,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            item {
                Text(
                    text = "NEURAL GENRE TAGS",
                    style = MaterialTheme.typography.labelLarge,
                    color = TigerCyberCyan,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            items(state.topTags) { (genre, minutes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                        .border(1.dp, TigerNeonOrange.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = genre.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${minutes.toInt()} MIN",
                        style = MaterialTheme.typography.labelLarge,
                        color = TigerNeonOrange,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selectedRange: FootprintTimeRange,
    onRangeSelected: (FootprintTimeRange) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FootprintTimeRange.entries.forEach { range ->
            val isSelected = range == selectedRange
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) TigerCyberCyan else Color.White.copy(alpha = 0.05f),
                label = "range_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
                label = "range_text"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(1.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.1f), CircleShape)
                    .bounceClick { onRangeSelected(range) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun RadarChart(
    values: Map<SonicAxis, Float>,
    modifier: Modifier = Modifier
) {
    val axes = SonicAxis.entries
    val density = LocalDensity.current
    
    // Smooth growth animation on entry
    var animateEntry by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateEntry = true }
    
    val entryScale by animateFloatAsState(
        targetValue = if (animateEntry) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "radar_growth"
    )

    val cachedPath = remember { Path() }
    val labelTextSize = with(density) { 11.sp.toPx() }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.35f
        val rings = 5

        // 1. Draw Static Rings
        repeat(rings) { ringIndex ->
            val ratio = (ringIndex + 1) / rings.toFloat()
            drawCircle(
                color = TigerCyberCyan.copy(alpha = 0.08f + ratio * 0.04f),
                radius = radius * ratio,
                center = center,
                style = Stroke(width = 1f)
            )
        }

        // 2. Draw Axis Spokes and Labels
        axes.forEachIndexed { index, axis ->
            val angle = ((2.0 * PI) / axes.size) * index - PI / 2.0
            val axisPoint = Offset(
                x = center.x + cos(angle).toFloat() * radius,
                y = center.y + sin(angle).toFloat() * radius
            )

            drawLine(
                color = TigerCyberCyan.copy(alpha = 0.25f),
                start = center,
                end = axisPoint,
                strokeWidth = 1f,
                cap = StrokeCap.Round
            )

            drawIntoCanvas { canvas ->
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    alpha = 140
                    textSize = labelTextSize
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    isAntiAlias = true
                }
                val labelRadius = radius + 32f
                val lx = center.x + cos(angle).toFloat() * labelRadius
                val ly = center.y + sin(angle).toFloat() * labelRadius + (labelTextSize / 3f)
                
                canvas.nativeCanvas.drawText(axis.label, lx, ly, textPaint)
            }
        }

        // 3. Compute Footprint Polygon
        val footprintPoints = axes.mapIndexed { index, axis ->
            val value = ((values[axis] ?: 0f) * entryScale).coerceIn(0f, 1f)
            val angle = ((2.0 * PI) / axes.size) * index - PI / 2.0
            Offset(
                x = center.x + cos(angle).toFloat() * radius * value,
                y = center.y + sin(angle).toFloat() * radius * value
            )
        }

        if (footprintPoints.isNotEmpty()) {
            cachedPath.rewind()
            cachedPath.moveTo(footprintPoints.first().x, footprintPoints.first().y)
            for (i in 1 until footprintPoints.size) {
                cachedPath.lineTo(footprintPoints[i].x, footprintPoints[i].y)
            }
            cachedPath.close()

            // 4. Draw Polygon Fill & Glow
            drawPath(
                path = cachedPath,
                color = TigerNeonOrange.copy(alpha = 0.15f)
            )

            drawIntoCanvas { canvas ->
                val glowPaint = Paint().apply {
                    color = TigerNeonOrange.copy(alpha = 0.4f)
                    style = PaintingStyle.Stroke
                    strokeWidth = 10f
                    asFrameworkPaint().maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(cachedPath, glowPaint)
            }

            drawPath(
                path = cachedPath,
                color = TigerNeonOrange,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 5. Draw Vertices
            footprintPoints.forEach { point ->
                drawCircle(color = TigerNeonOrange, radius = 5f, center = point)
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = 8f,
                    center = point,
                    style = Stroke(width = 1f)
                )
            }
        }

        // Center hub
        drawCircle(color = TigerCyberCyan, radius = 4f, center = center)
    }
}

