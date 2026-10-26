package com.tigerplayer.ui.coverscreen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tigerplayer.ui.theme.TigerNeonOrange

@Composable
internal fun CoverMicroWaveform(
    amplitudes: List<Float>,
    bass: Float,
    energy: Float,
    isPlaying: Boolean = true,
    isRenderingActive: Boolean = true,
    duration: Long = 1L,
    currentPosition: Long = 0L,
    onSeek: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shouldAnimate = CoverScreenPerformanceManager.shouldAnimate(isPlaying, isRenderingActive)
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val (phase, animatedEnergy) = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "CoverWave")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 820, easing = LinearEasing)),
            label = "CoverWavePhase"
        )
        val energyState by animateFloatAsState(
            targetValue = (0.2f + energy * 0.9f).coerceIn(0.2f, 1.2f),
            animationSpec = tween(120),
            label = "CoverWaveEnergy"
        )
        Pair(animatedPhase, energyState)
    } else {
        Pair(0f, 0.2f)
    }

    val points = remember(amplitudes) {
        CoverScreenPerformanceManager.calculateWaveformPoints(amplitudes, 32)
    }
    val reusablePath = remember { Path() }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            var widthPx = 0f
            detectDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    widthPx = size.width.toFloat()
                    dragProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                },
                onDragEnd = {
                    isDragging = false
                    onSeek((dragProgress * duration).toLong())
                },
                onDragCancel = { isDragging = false }
            ) { change, _ ->
                dragProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                change.consume()
            }
        }
    ) {
        val centerY = size.height / 2f
        val progress = if (isDragging) dragProgress else (currentPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val progressX = size.width * progress

        val path = CoverScreenPerformanceManager.populateWaveformPath(
            path = reusablePath,
            points = points,
            width = size.width,
            height = size.height,
            phase = phase,
            bass = if (shouldAnimate) bass else 0f,
            energy = animatedEnergy
        )

        // Background Path
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.15f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )

        drawPath(
            path = path,
            color = TigerNeonOrange.copy(alpha = 0.32f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = TigerNeonOrange,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )

        // Seeker Thumb
        drawCircle(
            color = Color.White,
            radius = 4.dp.toPx(),
            center = Offset(progressX, centerY)
        )
    }
}
