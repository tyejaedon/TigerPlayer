package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
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
}
