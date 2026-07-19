package com.example.tigerplayer.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.example.tigerplayer.engine.AudioReactiveFrame
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun SmoothWaveform(
    amplitudes: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    audioReactive: AudioReactiveFrame,
    color: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500, easing = LinearEasing),
        label = "WaveProgress"
    )

    // Smooth reactive dampening
    val reactiveEnergy by animateFloatAsState(
        targetValue = if (isPlaying) audioReactive.energy else 0f,
        animationSpec = tween(350, easing = LinearOutSlowInEasing),
        label = "WaveEnergy"
    )
    val reactiveBass by animateFloatAsState(
        targetValue = if (isPlaying) audioReactive.bass else 0f,
        animationSpec = tween(350, easing = LinearOutSlowInEasing),
        label = "WaveBass"
    )
    val reactiveTreble by animateFloatAsState(
        targetValue = if (isPlaying) audioReactive.treble else 0f,
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "WaveTreble"
    )
    // FIX: Dampen flux to completely eliminate high-frequency visual flickering
    val reactiveFlux by animateFloatAsState(
        targetValue = if (isPlaying) audioReactive.flux else 0f,
        animationSpec = tween(200, easing = LinearOutSlowInEasing),
        label = "WaveFlux"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "WaveformMotion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isPlaying) 2200 else 4500,
                easing = LinearEasing
            )
        ),
        label = "WavePhase"
    )

    val sampled = remember(amplitudes) {
        if (amplitudes.isEmpty()) {
            List(72) { 0.05f }
        } else {
            List(72) { index ->
                val sourceIndex = ((index / 71f) * amplitudes.lastIndex.coerceAtLeast(0)).toInt()
                amplitudes[sourceIndex].coerceIn(0f, 1f)
            }
        }
    }

    // Reuse path structures to stop object allocations inside the draw path execution
    val topPath = remember { Path() }
    val bottomPath = remember { Path() }

    // FIX: Pre-compute the gradient brush outside the draw frame loop.
    // It will only re-allocate when the theme color changes.
    val horizontalGradientBrush = remember(color) {
        Brush.horizontalGradient(
            listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.85f), color.copy(alpha = 0.2f))
        )
    }

    Canvas(modifier = Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 20.dp)) {
        val centerY = size.height / 2f
        val stepX = size.width / (sampled.lastIndex.coerceAtLeast(1)).toFloat()

        // Auto-upscale computation
        val rawScale = (0.45f + reactiveEnergy * 1.2f + reactiveBass * 0.75f)
        val upscaleThreshold = 0.7f
        val autoGain = if (rawScale < upscaleThreshold) (upscaleThreshold / rawScale.coerceAtLeast(0.1f)) else 1.0f

        // FIX: Bound max scale amplitude to ensure the path stays completely inside the canvas height frame
        val reactiveScale = (rawScale * autoGain).coerceIn(0.5f, 2.0f)
        val maxVerticalExcursion = size.height * 0.22f * reactiveScale // Never exceeds size.height * 0.44f

        val sparkle = (0.08f + reactiveFlux * 0.22f).coerceAtMost(0.28f)
        val playedX = size.width * animatedProgress.coerceIn(0f, 1f)

        topPath.reset()
        topPath.moveTo(0f, centerY)
        sampled.forEachIndexed { index, amp ->
            val x = index * stepX
            val phaseOsc = sin(phase + index * (0.22f + reactiveTreble * 0.06f))
            val envelope = (amp * 0.75f + abs(phaseOsc) * 0.25f).coerceIn(0f, 1f)
            val y = centerY - envelope * maxVerticalExcursion
            topPath.lineTo(x, y)
        }

        bottomPath.reset()
        bottomPath.moveTo(0f, centerY)
        sampled.forEachIndexed { index, amp ->
            val x = index * stepX
            val phaseOsc = sin(phase + index * (0.22f + reactiveTreble * 0.06f) + 1.1f)
            val envelope = (amp * 0.70f + abs(phaseOsc) * 0.30f).coerceIn(0f, 1f)
            val y = centerY + envelope * maxVerticalExcursion
            bottomPath.lineTo(x, y)
        }

        // Midline base
        drawLine(
            color = color.copy(alpha = 0.16f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.5f
        )

        // Draw background paths using cached gradient descriptor brush
        drawPath(
            path = topPath,
            brush = horizontalGradientBrush,
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )
        drawPath(
            path = bottomPath,
            brush = horizontalGradientBrush,
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )

        // Draw foreground playback clipping masks
        val clipWidth = playedX.coerceAtLeast(0f)
        if (clipWidth > 0f) {
            clipRect(left = 0f, top = 0f, right = clipWidth, bottom = size.height) {
                drawPath(
                    path = topPath,
                    color = color.copy(alpha = (0.78f + sparkle).coerceAtMost(1f)),
                    style = Stroke(width = 2.8f, cap = StrokeCap.Round)
                )
                drawPath(
                    path = bottomPath,
                    color = color.copy(alpha = (0.72f + sparkle).coerceAtMost(1f)),
                    style = Stroke(width = 2.8f, cap = StrokeCap.Round)
                )
            }
        }
    }
}