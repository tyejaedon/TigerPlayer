package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.graphics.Path
import androidx.lifecycle.Lifecycle
import kotlin.math.sin

/**
 * Performance and frame rate throttling manager for the Cover Screen subsystem.
 *
 * Implements frame rate limiter logic, animation throttling, lifecycle-aware rendering gates,
 * and zero-allocation canvas path generation for compact outer displays (Motorola Razr &
 * Samsung Galaxy Z Flip).
 */
object CoverScreenPerformanceManager {
    const val TARGET_FPS_DEFAULT = 60
    const val TARGET_FPS_THROTTLED = 30
    const val FRAME_INTERVAL_60_FPS_MS = 16L
    const val FRAME_INTERVAL_60_FPS_NS = 16_666_666L

    /**
     * Calculates the frame interval in milliseconds for a target frame rate.
     * Frame rate is clamped to a safe range [1, 120] FPS.
     */
    fun calculateFrameIntervalMs(targetFps: Int = TARGET_FPS_DEFAULT): Long {
        val clampedFps = targetFps.coerceIn(1, 120)
        return (1000L / clampedFps)
    }

    /**
     * Calculates the frame interval in nanoseconds for a target frame rate.
     * Frame rate is clamped to a safe range [1, 120] FPS.
     */
    fun calculateFrameIntervalNs(targetFps: Int = TARGET_FPS_DEFAULT): Long {
        val clampedFps = targetFps.coerceIn(1, 120)
        return (1_000_000_000L / clampedFps)
    }

    /**
     * Determines whether enough time has elapsed to render the next frame under the target FPS limit.
     * Always returns true on initial frame ([lastFrameTimeMs] <= 0L).
     */
    fun shouldRenderFrame(
        currentTimeMs: Long,
        lastFrameTimeMs: Long,
        targetFps: Int = TARGET_FPS_DEFAULT
    ): Boolean {
        if (lastFrameTimeMs <= 0L) return true
        val intervalMs = calculateFrameIntervalMs(targetFps)
        return (currentTimeMs - lastFrameTimeMs) >= intervalMs
    }

    /**
     * Determines whether waveform animations should be throttled/paused.
     * Animations must freeze when audio is not actively playing or when the cover screen is inactive.
     */
    fun shouldThrottleAnimation(isPlaying: Boolean, isRenderingActive: Boolean): Boolean {
        return !isPlaying || !isRenderingActive
    }

    /**
     * Returns true when rendering should actively animate (both audio playing and screen active).
     */
    fun shouldAnimate(isPlaying: Boolean, isRenderingActive: Boolean): Boolean {
        return isPlaying && isRenderingActive
    }

    /**
     * Resolves rendering active state based on Android lifecycle events.
     * Returns true on [Lifecycle.Event.ON_RESUME], false on pause/stop/destroy,
     * or retains the current state for neutral events (e.g. onCreate, onStart).
     */
    fun resolveLifecycleRenderingState(
        event: Lifecycle.Event,
        currentState: Boolean = false
    ): Boolean {
        return when (event) {
            Lifecycle.Event.ON_RESUME -> true
            Lifecycle.Event.ON_PAUSE,
            Lifecycle.Event.ON_STOP,
            Lifecycle.Event.ON_DESTROY -> false
            else -> currentState
        }
    }

    /**
     * Returns true if the given [Lifecycle.State] indicates the window is active and resumed.
     */
    fun isLifecycleStateActive(state: Lifecycle.State): Boolean {
        return state.isAtLeast(Lifecycle.State.RESUMED)
    }

    /**
     * Determines whether any modal overlay (Queue, Library, Playback Settings) is currently visible,
     * which necessitates suppressing or throttling background animations and canvas drawing.
     */
    fun isModalOverlayActive(
        queueVisible: Boolean,
        libraryVisible: Boolean,
        playbackSettingsVisible: Boolean,
        prismVisible: Boolean = false,
        nexusVisible: Boolean = false
    ): Boolean {
        return queueVisible || libraryVisible || playbackSettingsVisible || prismVisible || nexusVisible
    }

    /**
     * Calculates sampling points for cover micro waveform from input amplitudes.
     * Guaranteed to return a valid [FloatArray] of size [pointCount], with all values in 0f..1f,
     * immune to NaNs, infinities, or empty/null inputs.
     */
    fun calculateWaveformPoints(
        amplitudes: List<Float>?,
        pointCount: Int = 32
    ): FloatArray {
        val safeCount = pointCount.coerceAtLeast(2)
        val result = FloatArray(safeCount)
        if (amplitudes.isNullOrEmpty()) {
            result.fill(0.2f)
            return result
        }

        val lastIdx = (amplitudes.size - 1).coerceAtLeast(0)
        val divisor = (safeCount - 1).toFloat()
        for (i in 0 until safeCount) {
            val sourceIndex = ((i / divisor) * lastIdx).toInt().coerceIn(0, lastIdx)
            val raw = amplitudes[sourceIndex]
            result[i] = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0.2f
        }
        return result
    }

    /**
     * Generates a waveform path into an existing [Path] object without allocating new Path instances.
     * Safe against negative or zero dimensions, and robust against NaN/infinite parameters.
     */
    fun populateWaveformPath(
        path: Path,
        points: FloatArray,
        width: Float,
        height: Float,
        phase: Float,
        bass: Float,
        energy: Float
    ): Path {
        path.reset()
        if (width <= 0f || height <= 0f || points.isEmpty()) {
            return path
        }

        val centerY = height / 2f
        val step = width / (points.size - 1).coerceAtLeast(1)
        path.moveTo(0f, centerY)

        val safePhase = if (phase.isFinite()) phase else 0f
        val safeBass = if (bass.isFinite()) bass.coerceIn(0f, 1f) else 0f
        val safeEnergy = if (energy.isFinite()) energy.coerceIn(0.2f, 1.2f) else 0.2f

        for (index in points.indices) {
            val amp = points[index]
            val x = index * step
            val oscillation = sin(safePhase + index * 0.42f)
            val reactive = (amp * 0.65f + safeBass * 0.35f) * safeEnergy
            val y = centerY + oscillation * reactive * (height * 0.48f)
            path.lineTo(x, y)
        }
        return path
    }
}
