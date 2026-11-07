package com.tigerplayer.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tigerplayer.engine.AudioReactiveFrame
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * High-performance Composite Frequency Response Curve renderer.
 *
 * Renders the real-time transfer function of all active biquad filters across
 * the audible logarithmic spectrum (20 Hz - 20 kHz, -15 dB to +15 dB).
 */
@Composable
fun EqualizerFrequencyCurve(
    points: List<Offset>,
    bands: List<ParametricBand>,
    isInteractive: Boolean = false,
    selectedBandIndex: Int = 0,
    onNodeDragged: ((bandIndex: Int, newFreqHz: Float, newGainDb: Float) -> Unit)? = null,
    onBandSelected: ((Int) -> Unit)? = null,
    audioFrame: AudioReactiveFrame? = null,
    modifier: Modifier = Modifier,
    curveColor: Color = TigerCyberCyan,
    showGrid: Boolean = true
) {
    val minLog = remember { log10(20.0) }
    val maxLog = remember { log10(20000.0) }
    val rangeLog = remember { maxLog - minLog }

    // Keep latest state references so pointerInput never restarts mid-drag when bands update
    val currentBands by rememberUpdatedState(bands)
    val currentOnNodeDragged by rememberUpdatedState(onNodeDragged)
    val currentOnBandSelected by rememberUpdatedState(onBandSelected)

    // Pre-allocate paths and effects to avoid per-frame GC allocations during dragging
    val curvePath = remember { Path() }
    val fillPath = remember { Path() }
    val dashedEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }
    val dbSteps = remember { listOf(12f, 6f, 0f, -6f, -12f) }
    val markerFreqs = remember { listOf(100.0, 1000.0, 10000.0) }

    val interactiveModifier = if (isInteractive) {
        modifier
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w <= 0f || h <= 0f) return@detectTapGestures

                    val hitRadiusPx = 48.dp.toPx()
                    val closest = currentBands.mapIndexedNotNull { idx, band ->
                        if (!band.isEnabled) return@mapIndexedNotNull null
                        val freqLog = log10(band.frequencyHz.toDouble().coerceIn(20.0, 20000.0))
                        val nx = ((freqLog - minLog) / rangeLog).toFloat() * w
                        val ny = (0.5f - (band.gainDb / 30f)) * h
                        val dist = hypot(nx - tapOffset.x, ny - tapOffset.y)
                        Triple(idx, dist, band)
                    }.minByOrNull { it.second }

                    if (closest != null && closest.second <= hitRadiusPx) {
                        currentOnBandSelected?.invoke(closest.first)
                    }
                }
            }
            .pointerInput(Unit) {
                var activeDragIndex = -1
                var dragPositionPx = Offset.Zero

                detectDragGestures(
                    onDragStart = { startOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w > 0f && h > 0f) {
                            val hitRadiusPx = 48.dp.toPx()
                            val closest = currentBands.mapIndexedNotNull { idx, band ->
                                if (!band.isEnabled) return@mapIndexedNotNull null
                                val freqLog = log10(band.frequencyHz.toDouble().coerceIn(20.0, 20000.0))
                                val nx = ((freqLog - minLog) / rangeLog).toFloat() * w
                                val ny = (0.5f - (band.gainDb / 30f)) * h
                                val dist = hypot(nx - startOffset.x, ny - startOffset.y)
                                Triple(idx, Offset(nx, ny), dist)
                            }.minByOrNull { it.third }

                            if (closest != null && closest.third < hitRadiusPx) {
                                activeDragIndex = closest.first
                                dragPositionPx = closest.second
                                currentOnBandSelected?.invoke(closest.first)
                            } else {
                                activeDragIndex = -1
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (activeDragIndex in currentBands.indices) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w > 0f && h > 0f) {
                                val newX = (dragPositionPx.x + dragAmount.x).coerceIn(0f, w)
                                val newY = (dragPositionPx.y + dragAmount.y).coerceIn(0f, h)
                                dragPositionPx = Offset(newX, newY)

                                val newFraction = (newX / w).toDouble().coerceIn(0.0, 1.0)
                                val targetFreq = 10.0.pow(minLog + newFraction * rangeLog).toFloat()

                                val gainFraction = 0.5f - (newY / h)
                                val targetGain = (gainFraction * 30f).coerceIn(-15f, 15f)

                                currentOnNodeDragged?.invoke(activeDragIndex, targetFreq, targetGain)
                                change.consume()
                            }
                        }
                    },
                    onDragEnd = { activeDragIndex = -1 },
                    onDragCancel = { activeDragIndex = -1 }
                )
            }
    } else {
        modifier
    }

    Canvas(modifier = interactiveModifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (w <= 0f || h <= 0f) return@Canvas

        // 0. Render Real-Time Spectrum Analyzer Bars in Background
        audioFrame?.let { frame ->
            val bandsCount = frame.spectralBands.size
            if (bandsCount > 0) {
                val barWidth = w / (bandsCount * 2f)
                frame.spectralBands.forEachIndexed { i, energy ->
                    val bandCenterPx = ((i + 0.5f) / bandsCount) * w
                    val barHeight = energy.coerceIn(0f, 1f) * (h * 0.7f)
                    val left = bandCenterPx - (barWidth / 2f)
                    val top = h - barHeight
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                curveColor.copy(alpha = 0.25f),
                                curveColor.copy(alpha = 0.05f)
                            ),
                            startY = top,
                            endY = h
                        ),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                    )
                }
            }
        }

        // 1. Draw Grid Lines
        if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.08f)
            val centerLineColor = Color.White.copy(alpha = 0.22f)

            // Horizontal Decibel lines: +12dB, +6dB, 0dB, -6dB, -12dB
            dbSteps.forEach { db ->
                val y = (0.5f - (db / 30f)) * h
                drawLine(
                    color = if (db == 0f) centerLineColor else gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (db == 0f) 1.5f else 1f,
                    pathEffect = if (db != 0f) dashedEffect else null
                )
            }

            // Vertical logarithmic frequency grid lines (100 Hz, 1 kHz, 10 kHz)
            markerFreqs.forEach { f ->
                val fLog = log10(f)
                val x = ((fLog - minLog) / rangeLog).toFloat() * w
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
            }
        }

        // 2. Render Smooth Frequency Response Curve
        if (points.isNotEmpty()) {
            curvePath.reset()
            fillPath.reset()

            val firstPoint = points.first()
            val startX = firstPoint.x * w
            val startY = ((firstPoint.y + 1f) / 2f) * h
            curvePath.moveTo(startX, startY)
            fillPath.moveTo(startX, h)
            fillPath.lineTo(startX, startY)

            for (i in 1 until points.size) {
                val pt = points[i]
                val prev = points[i - 1]
                val px = prev.x * w
                val py = ((prev.y + 1f) / 2f) * h
                val cx = pt.x * w
                val cy = ((pt.y + 1f) / 2f) * h

                val midX = (px + cx) / 2f
                val midY = (py + cy) / 2f
                curvePath.quadraticTo(px, py, midX, midY)
                fillPath.quadraticTo(px, py, midX, midY)
            }

            val lastPoint = points.last()
            val endX = lastPoint.x * w
            val endY = ((lastPoint.y + 1f) / 2f) * h
            curvePath.lineTo(endX, endY)
            fillPath.lineTo(endX, endY)
            fillPath.lineTo(endX, h)
            fillPath.close()

            // Subtle gradient fill under curve
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        curveColor.copy(alpha = 0.22f),
                        curveColor.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            // Neon stroke
            drawPath(
                path = curvePath,
                color = curveColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // 3. Render Band Nodes
        bands.forEachIndexed { idx, band ->
            if (!band.isEnabled) return@forEachIndexed

            val freqLog = log10(band.frequencyHz.toDouble().coerceIn(20.0, 20000.0))
            val nx = ((freqLog - minLog) / rangeLog).toFloat() * w
            val ny = (0.5f - (band.gainDb / 30f)) * h
            val centerOffset = Offset(nx, ny)

            val isSelected = idx == selectedBandIndex

            // Halo glow for selected node
            if (isSelected) {
                drawCircle(
                    color = band.color.copy(alpha = 0.35f),
                    radius = 16.dp.toPx(),
                    center = centerOffset
                )
            }

            // Outer ring
            drawCircle(
                color = if (isSelected) TigerNeonOrange else band.color,
                radius = if (isSelected) 8.dp.toPx() else 6.dp.toPx(),
                center = centerOffset
            )

            // Center solid dot
            drawCircle(
                color = Color.White,
                radius = if (isSelected) 4.dp.toPx() else 2.5.dp.toPx(),
                center = centerOffset
            )
        }
    }
}
