package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.graphics.Path
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoverScreenPerformanceTest {

    // --- 1. ANIMATION THROTTLING & RENDERING ACTIVE STATE ---

    @Test
    fun `animation is only enabled when playback is active and screen rendering is active`() {
        // Both active: should animate
        assertTrue(CoverScreenPerformanceManager.shouldAnimate(isPlaying = true, isRenderingActive = true))
        assertFalse(CoverScreenPerformanceManager.shouldThrottleAnimation(isPlaying = true, isRenderingActive = true))

        // Playback paused: should throttle
        assertFalse(CoverScreenPerformanceManager.shouldAnimate(isPlaying = false, isRenderingActive = true))
        assertTrue(CoverScreenPerformanceManager.shouldThrottleAnimation(isPlaying = false, isRenderingActive = true))

        // Screen inactive / backgrounded / overlay open: should throttle
        assertFalse(CoverScreenPerformanceManager.shouldAnimate(isPlaying = true, isRenderingActive = false))
        assertTrue(CoverScreenPerformanceManager.shouldThrottleAnimation(isPlaying = true, isRenderingActive = false))

        // Both paused & inactive: should throttle
        assertFalse(CoverScreenPerformanceManager.shouldAnimate(isPlaying = false, isRenderingActive = false))
        assertTrue(CoverScreenPerformanceManager.shouldThrottleAnimation(isPlaying = false, isRenderingActive = false))
    }

    // --- 2. FRAME RATE LIMITER & INTERVAL CALCULATION ---

    @Test
    fun `frame rate limiter calculates correct intervals for standard refresh rates`() {
        // 60 FPS cap: ~16ms interval
        assertEquals(16L, CoverScreenPerformanceManager.calculateFrameIntervalMs(60))
        assertEquals(16_666_666L, CoverScreenPerformanceManager.calculateFrameIntervalNs(60))

        // 30 FPS throttled mode: ~33ms interval
        assertEquals(33L, CoverScreenPerformanceManager.calculateFrameIntervalMs(30))
        assertEquals(33_333_333L, CoverScreenPerformanceManager.calculateFrameIntervalNs(30))

        // 120 FPS high refresh: ~8ms interval
        assertEquals(8L, CoverScreenPerformanceManager.calculateFrameIntervalMs(120))
        assertEquals(8_333_333L, CoverScreenPerformanceManager.calculateFrameIntervalNs(120))
    }

    @Test
    fun `frame rate limiter safely clamps extreme target fps inputs`() {
        // Coerces 0 or negative FPS to minimum safe 1 FPS (1000ms)
        assertEquals(1000L, CoverScreenPerformanceManager.calculateFrameIntervalMs(0))
        assertEquals(1000L, CoverScreenPerformanceManager.calculateFrameIntervalMs(-30))

        // Clamps rates above 120 FPS to 120 FPS
        assertEquals(8L, CoverScreenPerformanceManager.calculateFrameIntervalMs(240))
    }

    @Test
    fun `shouldRenderFrame throttles frames based on elapsed time`() {
        val targetFps = 60
        val intervalMs = CoverScreenPerformanceManager.calculateFrameIntervalMs(targetFps) // 16ms

        // First frame always renders
        assertTrue(CoverScreenPerformanceManager.shouldRenderFrame(currentTimeMs = 100L, lastFrameTimeMs = 0L, targetFps = targetFps))

        // Less than frame interval has elapsed: skip frame
        assertFalse(CoverScreenPerformanceManager.shouldRenderFrame(currentTimeMs = 110L, lastFrameTimeMs = 100L, targetFps = targetFps)) // 10ms < 16ms

        // Frame interval has exactly or more elapsed: render frame
        assertTrue(CoverScreenPerformanceManager.shouldRenderFrame(currentTimeMs = 116L, lastFrameTimeMs = 100L, targetFps = targetFps)) // 16ms >= 16ms
        assertTrue(CoverScreenPerformanceManager.shouldRenderFrame(currentTimeMs = 125L, lastFrameTimeMs = 100L, targetFps = targetFps)) // 25ms >= 16ms
    }

    // --- 3. WAVEFORM POINT CALCULATIONS & PATH GENERATION STABILITY ---

    @Test
    fun `waveform point calculation handles empty or null amplitude lists gracefully`() {
        val emptyPoints = CoverScreenPerformanceManager.calculateWaveformPoints(emptyList(), 32)
        assertEquals(32, emptyPoints.size)
        assertTrue(emptyPoints.all { it == 0.2f })

        val nullPoints = CoverScreenPerformanceManager.calculateWaveformPoints(null, 32)
        assertEquals(32, nullPoints.size)
        assertTrue(nullPoints.all { it == 0.2f })
    }

    @Test
    fun `waveform point calculation downsamples amplitudes within 0 to 1 range`() {
        val inputAmplitudes = (0..100).map { it / 100f }
        val points = CoverScreenPerformanceManager.calculateWaveformPoints(inputAmplitudes, 32)

        assertEquals(32, points.size)
        assertTrue(points.all { it in 0f..1f })
        // Monotonically increasing verification
        assertTrue(points.first() <= points.last())
    }

    @Test
    fun `waveform point calculation sanitizes NaN, infinite, and out-of-range inputs`() {
        val noisyAmplitudes = listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            -1.5f,
            2.5f,
            0.5f
        )
        val points = CoverScreenPerformanceManager.calculateWaveformPoints(noisyAmplitudes, 16)

        assertEquals(16, points.size)
        for (pt in points) {
            assertTrue("Point must be finite", pt.isFinite())
            assertTrue("Point must be in 0f..1f range, but was $pt", pt in 0f..1f)
        }
    }

    @Test
    fun `populateWaveformPath creates stable paths and reuses Path instances`() {
        val path = Path()
        val points = FloatArray(32) { 0.5f }

        // Standard dimensions
        val resultPath = CoverScreenPerformanceManager.populateWaveformPath(
            path = path,
            points = points,
            width = 300f,
            height = 100f,
            phase = 0.5f,
            bass = 0.4f,
            energy = 0.8f
        )
        // Must return the exact same instance (zero allocations)
        assertEquals(path, resultPath)

        // Multiple invocations reset and repopulate cleanly
        CoverScreenPerformanceManager.populateWaveformPath(
            path = path,
            points = points,
            width = 300f,
            height = 100f,
            phase = 1.0f,
            bass = 0.2f,
            energy = 0.6f
        )
        assertEquals(path, resultPath)
    }

    @Test
    fun `populateWaveformPath handles zero or negative dimensions safely`() {
        val path = Path()
        val points = FloatArray(32) { 0.5f }

        CoverScreenPerformanceManager.populateWaveformPath(
            path = path,
            points = points,
            width = 0f,
            height = 100f,
            phase = 0f,
            bass = 0f,
            energy = 0f
        )
        assertTrue("Path should be empty on zero width", path.isEmpty)

        CoverScreenPerformanceManager.populateWaveformPath(
            path = path,
            points = points,
            width = 100f,
            height = -50f,
            phase = 0f,
            bass = 0f,
            energy = 0f
        )
        assertTrue("Path should be empty on negative height", path.isEmpty)
    }

    @Test
    fun `populateWaveformPath survives NaN and infinite dynamic inputs without crashing`() {
        val path = Path()
        val points = FloatArray(32) { 0.5f }

        CoverScreenPerformanceManager.populateWaveformPath(
            path = path,
            points = points,
            width = 300f,
            height = 100f,
            phase = Float.NaN,
            bass = Float.POSITIVE_INFINITY,
            energy = Float.NEGATIVE_INFINITY
        )
        assertFalse("Path should be successfully populated with sanitized values", path.isEmpty)
    }

    // --- 4. LIFECYCLE EVENT STATE HANDLING ---

    @Test
    fun `lifecycle event state transitions appropriately for cover screen rendering`() {
        // ON_RESUME activates rendering
        assertTrue(
            CoverScreenPerformanceManager.resolveLifecycleRenderingState(
                event = Lifecycle.Event.ON_RESUME,
                currentState = false
            )
        )

        // ON_PAUSE, ON_STOP, ON_DESTROY deactivate rendering
        assertFalse(
            CoverScreenPerformanceManager.resolveLifecycleRenderingState(
                event = Lifecycle.Event.ON_PAUSE,
                currentState = true
            )
        )
        assertFalse(
            CoverScreenPerformanceManager.resolveLifecycleRenderingState(
                event = Lifecycle.Event.ON_STOP,
                currentState = true
            )
        )
        assertFalse(
            CoverScreenPerformanceManager.resolveLifecycleRenderingState(
                event = Lifecycle.Event.ON_DESTROY,
                currentState = true
            )
        )

        // Neutral events preserve the existing state
        assertTrue(
            CoverScreenPerformanceManager.resolveLifecycleRenderingState(
                event = Lifecycle.Event.ON_CREATE,
                currentState = true
            )
        )
        assertFalse(
            CoverScreenPerformanceManager.resolveLifecycleRenderingState(
                event = Lifecycle.Event.ON_START,
                currentState = false
            )
        )
    }

    @Test
    fun `isLifecycleStateActive requires at least RESUMED state`() {
        assertTrue(CoverScreenPerformanceManager.isLifecycleStateActive(Lifecycle.State.RESUMED))
        assertFalse(CoverScreenPerformanceManager.isLifecycleStateActive(Lifecycle.State.STARTED))
        assertFalse(CoverScreenPerformanceManager.isLifecycleStateActive(Lifecycle.State.CREATED))
        assertFalse(CoverScreenPerformanceManager.isLifecycleStateActive(Lifecycle.State.INITIALIZED))
        assertFalse(CoverScreenPerformanceManager.isLifecycleStateActive(Lifecycle.State.DESTROYED))
    }

    // --- 5. MODAL OVERLAY GATING ---

    @Test
    fun `isModalOverlayActive detects any active modal overlay`() {
        assertFalse(
            CoverScreenPerformanceManager.isModalOverlayActive(
                queueVisible = false,
                libraryVisible = false,
                playbackSettingsVisible = false
            )
        )

        assertTrue(
            CoverScreenPerformanceManager.isModalOverlayActive(
                queueVisible = true,
                libraryVisible = false,
                playbackSettingsVisible = false
            )
        )
        assertTrue(
            CoverScreenPerformanceManager.isModalOverlayActive(
                queueVisible = false,
                libraryVisible = true,
                playbackSettingsVisible = false
            )
        )
        assertTrue(
            CoverScreenPerformanceManager.isModalOverlayActive(
                queueVisible = false,
                libraryVisible = false,
                playbackSettingsVisible = true
            )
        )
    }
}
