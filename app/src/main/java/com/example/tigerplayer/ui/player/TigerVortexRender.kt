package com.example.tigerplayer.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.tigerplayer.engine.AudioReactiveFrame
import com.example.tigerplayer.engine.FluidRenderer
import com.example.tigerplayer.engine.FluidVortexView

// ============================================================================
// 1. TOP LAYER: COMPOSE WRAPPER
// ============================================================================

/**
 * A highly performant Compose wrapper for the OpenGL Fluid Vortex.
 * It passes the current audio amplitude to the GPU for live reaction.
 */
@Composable
fun FluidVortexRenderer(
    isPlaying: Boolean,
    amplitudes: List<Float>,
    audioReactive: AudioReactiveFrame,
    trackId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(context.applicationContext) {
        FluidRenderer(context.applicationContext)
    }

    val waveformAvg = remember(amplitudes) {
        if (amplitudes.isEmpty()) 0f else amplitudes.average().toFloat().coerceIn(0f, 1f)
    }

    val trackPulse = remember(trackId) {
        ((trackId.hashCode().toUInt().toLong() and 0xFFL).toFloat() / 255f).coerceIn(0f, 1f)
    }

    LaunchedEffect(audioReactive, waveformAvg) {
        val frame = audioReactive.copy(
            energy = (audioReactive.energy * 0.75f + waveformAvg * 0.25f).coerceIn(0f, 1f)
        )
        renderer.updateAudioReactiveFrame(frame)
    }

    LaunchedEffect(trackPulse) {
        renderer.updateTimelineEnergy(progress = trackPulse, amp = trackPulse)
    }

    AndroidView(
        factory = { context ->
            FluidVortexView(context).apply {
                setFluidRenderer(renderer)
            }
        },
        modifier = modifier,
        update = { view ->
            if (isPlaying) view.onResume() else view.onPause()
        }
    )
}
