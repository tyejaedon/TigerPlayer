package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying gesture discrimination between single taps, double taps,
 * and swipe cancellations in [TapGestureClassifier] for the Cover Screen.
 */
class CoverScreenGesturesTest {

    private val classifier = TapGestureClassifier(
        doubleTapTimeoutMs = 300L,
        maxDoubleTapDistancePx = 60f
    )

    @Test
    fun `single tap without double-tap listener dispatches immediately`() {
        val result = classifier.onSlopTap(
            position = Offset(100f, 100f),
            hasDoubleTapListener = false,
            currentTimeMs = 1_000L
        )

        assertEquals(TapGestureClassifier.Result.SingleTapImmediate, result)
    }

    @Test
    fun `first tap with double-tap listener active schedules single tap`() {
        val result = classifier.onSlopTap(
            position = Offset(100f, 100f),
            hasDoubleTapListener = true,
            currentTimeMs = 1_000L
        )

        assertEquals(TapGestureClassifier.Result.ScheduleSingleTap, result)
    }

    @Test
    fun `second tap within timeout and slop detects double tap and suppresses single tap`() {
        // First tap at t = 1000ms
        val firstTap = classifier.onSlopTap(
            position = Offset(100f, 100f),
            hasDoubleTapListener = true,
            currentTimeMs = 1_000L
        )
        assertEquals(TapGestureClassifier.Result.ScheduleSingleTap, firstTap)

        // Second tap at t = 1150ms (within 300ms window, 10px distance)
        val secondTap = classifier.onSlopTap(
            position = Offset(105f, 105f),
            hasDoubleTapListener = true,
            currentTimeMs = 1_150L
        )
        assertEquals(TapGestureClassifier.Result.DoubleTap, secondTap)
    }

    @Test
    fun `second tap exceeding timeout is classified as a new scheduled single tap`() {
        // First tap at t = 1000ms
        classifier.onSlopTap(
            position = Offset(100f, 100f),
            hasDoubleTapListener = true,
            currentTimeMs = 1_000L
        )

        // Second tap at t = 1350ms (350ms later > 300ms window)
        val secondTap = classifier.onSlopTap(
            position = Offset(105f, 105f),
            hasDoubleTapListener = true,
            currentTimeMs = 1_350L
        )
        assertEquals(TapGestureClassifier.Result.ScheduleSingleTap, secondTap)
    }

    @Test
    fun `second tap outside spatial tolerance is classified as a new scheduled single tap`() {
        // First tap at (100, 100)
        classifier.onSlopTap(
            position = Offset(100f, 100f),
            hasDoubleTapListener = true,
            currentTimeMs = 1_000L
        )

        // Second tap at (200, 200) -> distance ~ 141px > 60px max distance
        val secondTap = classifier.onSlopTap(
            position = Offset(200f, 200f),
            hasDoubleTapListener = true,
            currentTimeMs = 1_150L
        )
        assertEquals(TapGestureClassifier.Result.ScheduleSingleTap, secondTap)
    }

    @Test
    fun `reset clears double tap state so subsequent tap is treated as new first tap`() {
        // First tap at t = 1000ms
        classifier.onSlopTap(
            position = Offset(100f, 100f),
            hasDoubleTapListener = true,
            currentTimeMs = 1_000L
        )

        // Swipe or reset occurs
        classifier.reset()

        // Tap at t = 1100ms
        val tapAfterReset = classifier.onSlopTap(
            position = Offset(102f, 102f),
            hasDoubleTapListener = true,
            currentTimeMs = 1_100L
        )
        assertEquals(TapGestureClassifier.Result.ScheduleSingleTap, tapAfterReset)
    }

    @Test
    fun `rapid triple tap sequence yields schedule, double tap, then schedule`() {
        val tap1 = classifier.onSlopTap(Offset(50f, 50f), hasDoubleTapListener = true, currentTimeMs = 1_000L)
        val tap2 = classifier.onSlopTap(Offset(52f, 51f), hasDoubleTapListener = true, currentTimeMs = 1_120L)
        val tap3 = classifier.onSlopTap(Offset(51f, 50f), hasDoubleTapListener = true, currentTimeMs = 1_220L)

        assertEquals(TapGestureClassifier.Result.ScheduleSingleTap, tap1)
        assertEquals(TapGestureClassifier.Result.DoubleTap, tap2)
        assertEquals(TapGestureClassifier.Result.ScheduleSingleTap, tap3)
    }

    @Test
    fun `isCoverScreenSystemEdge identifies all four 12-percent edge exclusion bands`() {
        val width = 400f
        val height = 400f
        // 12% is 48px from each edge. Left: <48, Right: >352, Top: <48, Bottom: >352

        // Left edge
        assertTrue(isCoverScreenSystemEdge(Offset(20f, 200f), width, height))
        // Right edge
        assertTrue(isCoverScreenSystemEdge(Offset(380f, 200f), width, height))
        // Top edge (Android status bar / notification shade pull-down)
        assertTrue(isCoverScreenSystemEdge(Offset(200f, 20f), width, height))
        // Bottom edge (Android system navigation bar)
        assertTrue(isCoverScreenSystemEdge(Offset(200f, 380f), width, height))

        // Center / non-edge zone (e.g. 200, 200)
        assertFalse(isCoverScreenSystemEdge(Offset(200f, 200f), width, height))
        // Just inside boundaries (e.g. 50, 50)
        assertFalse(isCoverScreenSystemEdge(Offset(50f, 50f), width, height))
    }

    @Test
    fun `isCoverScreenSystemEdge handles invalid or degenerate dimensions safely`() {
        assertFalse(isCoverScreenSystemEdge(Offset(10f, 10f), 0f, 0f))
        assertFalse(isCoverScreenSystemEdge(Offset(10f, 10f), -100f, 400f))
    }

    @Test
    fun `shouldIgnoreCoverScreenGesture ignores gestures when modal overlay is visible`() {
        val position = Offset(100f, 100f)
        val result = shouldIgnoreCoverScreenGesture(
            isVortexImmersive = false,
            hasModalOverlay = true,
            positionInRoot = position,
            seekerTouchBounds = null,
            mediaCycleTouchBounds = null,
            controlsTouchBounds = null
        )
        assertTrue(result)
    }

    @Test
    fun `shouldIgnoreCoverScreenGesture allows full screen gestures when vortex immersive is active`() {
        val childBounds = Rect(50f, 50f, 250f, 250f)
        val positionInside = Offset(100f, 100f)

        // Even if touch is inside child bounds, immersive mode takes precedence and unblocks ambient gestures
        val result = shouldIgnoreCoverScreenGesture(
            isVortexImmersive = true,
            hasModalOverlay = false,
            positionInRoot = positionInside,
            seekerTouchBounds = childBounds,
            mediaCycleTouchBounds = childBounds,
            controlsTouchBounds = childBounds
        )
        assertFalse(result)
    }

    @Test
    fun `shouldIgnoreCoverScreenGesture ignores touches on seeker, media cycle, or controls bounds`() {
        val seekerBounds = Rect(10f, 300f, 390f, 350f)
        val mediaCycleBounds = Rect(20f, 50f, 380f, 250f)
        val controlsBounds = Rect(20f, 250f, 380f, 300f)

        // Inside seeker
        assertTrue(
            shouldIgnoreCoverScreenGesture(
                isVortexImmersive = false,
                hasModalOverlay = false,
                positionInRoot = Offset(200f, 320f),
                seekerTouchBounds = seekerBounds,
                mediaCycleTouchBounds = mediaCycleBounds,
                controlsTouchBounds = controlsBounds
            )
        )

        // Inside media cycle viewport (album art / lyrics)
        assertTrue(
            shouldIgnoreCoverScreenGesture(
                isVortexImmersive = false,
                hasModalOverlay = false,
                positionInRoot = Offset(200f, 150f),
                seekerTouchBounds = seekerBounds,
                mediaCycleTouchBounds = mediaCycleBounds,
                controlsTouchBounds = controlsBounds
            )
        )

        // Inside controls
        assertTrue(
            shouldIgnoreCoverScreenGesture(
                isVortexImmersive = false,
                hasModalOverlay = false,
                positionInRoot = Offset(200f, 275f),
                seekerTouchBounds = seekerBounds,
                mediaCycleTouchBounds = mediaCycleBounds,
                controlsTouchBounds = controlsBounds
            )
        )

        // Outside all interactive child bounds (on background arena) -> allows ambient gesture
        assertFalse(
            shouldIgnoreCoverScreenGesture(
                isVortexImmersive = false,
                hasModalOverlay = false,
                positionInRoot = Offset(200f, 380f),
                seekerTouchBounds = seekerBounds,
                mediaCycleTouchBounds = mediaCycleBounds,
                controlsTouchBounds = controlsBounds
            )
        )
    }
}
