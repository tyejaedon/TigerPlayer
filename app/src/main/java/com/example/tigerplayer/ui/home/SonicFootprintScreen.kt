package com.example.tigerplayer.ui.home

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tigerplayer.ui.theme.TigerCyberCyan
import com.example.tigerplayer.ui.theme.TigerNeonOrange
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
                        fontWeight = FontWeight.Black
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Your on-device listening fingerprint, generated from local history only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = TigerCyberCyan.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadarChart(
                            values = state.axisValues,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Lifetime Listening: ${"%.1f".format(state.totalListeningHours)}h",
                            style = MaterialTheme.typography.titleSmall,
                            color = TigerNeonOrange,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            item {
                Text(
                    text = "TOP GENRE TAGS",
                    style = MaterialTheme.typography.labelLarge,
                    color = TigerCyberCyan,
                    fontWeight = FontWeight.Black
                )
            }

            items(state.topTags) { (genre, minutes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(1.dp, TigerNeonOrange.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = genre.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${minutes.toInt()} min",
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
private fun RadarChart(
    values: Map<SonicAxis, Float>,
    modifier: Modifier = Modifier
) {
    val axes = SonicAxis.entries

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.33f
        val rings = 5

        repeat(rings) { ringIndex ->
            val ratio = (ringIndex + 1) / rings.toFloat()
            drawCircle(
                color = TigerCyberCyan.copy(alpha = 0.10f + ratio * 0.05f),
                radius = radius * ratio,
                center = center,
                style = Stroke(width = 1.2f)
            )
        }

        axes.forEachIndexed { index, axis ->
            val angle = ((2.0 * PI) / axes.size) * index - PI / 2.0
            val axisPoint = Offset(
                x = center.x + cos(angle).toFloat() * radius,
                y = center.y + sin(angle).toFloat() * radius
            )

            drawLine(
                color = TigerCyberCyan.copy(alpha = 0.35f),
                start = center,
                end = axisPoint,
                strokeWidth = 1.4f,
                cap = StrokeCap.Round
            )

            drawIntoCanvas { canvas ->
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(220, 230, 230, 235)
                    textSize = 30f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                val labelOffset = Offset(
                    x = center.x + cos(angle).toFloat() * (radius + 36f),
                    y = center.y + sin(angle).toFloat() * (radius + 36f)
                )
                canvas.nativeCanvas.drawText(axis.label, labelOffset.x, labelOffset.y, textPaint)
            }
        }

        val footprintPoints = axes.mapIndexed { index, axis ->
            val value = (values[axis] ?: 0f).coerceIn(0f, 1f)
            val angle = ((2.0 * PI) / axes.size) * index - PI / 2.0
            Offset(
                x = center.x + cos(angle).toFloat() * radius * value,
                y = center.y + sin(angle).toFloat() * radius * value
            )
        }

        val polygonPath = Path().apply {
            if (footprintPoints.isNotEmpty()) {
                moveTo(footprintPoints.first().x, footprintPoints.first().y)
                for (i in 1 until footprintPoints.size) {
                    lineTo(footprintPoints[i].x, footprintPoints[i].y)
                }
                close()
            }
        }

        drawPath(
            path = polygonPath,
            color = TigerCyberCyan.copy(alpha = 0.18f)
        )

        drawIntoCanvas { canvas ->
            val glowPaint = Paint().apply {
                color = TigerNeonOrange.copy(alpha = 0.52f)
                style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                strokeWidth = 9f
                asFrameworkPaint().maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawPath(polygonPath, glowPaint)
        }

        drawPath(
            path = polygonPath,
            color = TigerNeonOrange,
            style = Stroke(width = 3.2f)
        )

        footprintPoints.forEach { point ->
            drawCircle(
                color = TigerNeonOrange,
                radius = 6f,
                center = point
            )
        }

        drawCircle(
            color = TigerCyberCyan,
            radius = 5f,
            center = center
        )

        drawRect(
            color = Color.Transparent,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            style = Stroke(width = 0f)
        )
    }

    Text(
        text = "Axis values are normalized from your top local genres.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

