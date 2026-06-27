package com.example.tigerplayer.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowStateCrossfadeMathTest {

    @Test
    fun normalizeCrossfadeSeconds_clampsToSupportedRange() {
        assertEquals(0, FlowStateCrossfadeMath.normalizeCrossfadeSeconds(-3))
        assertEquals(7, FlowStateCrossfadeMath.normalizeCrossfadeSeconds(7))
        assertEquals(12, FlowStateCrossfadeMath.normalizeCrossfadeSeconds(18))
    }

    @Test
    fun computeFadeOutDuration_respectsWindowAndMinGuard() {
        assertEquals(0L, FlowStateCrossfadeMath.computeFadeOutDuration(remainingMs = 5000L, windowMs = 0L))
        assertEquals(1200L, FlowStateCrossfadeMath.computeFadeOutDuration(remainingMs = 400L, windowMs = 7000L))
        assertEquals(3500L, FlowStateCrossfadeMath.computeFadeOutDuration(remainingMs = 3500L, windowMs = 7000L))
    }

    @Test
    fun shouldAbortFadeOut_returnsTrueForSkipAndPauseConflicts() {
        assertTrue(
            FlowStateCrossfadeMath.shouldAbortFadeOut(
                isPlaying = false,
                expectedMediaId = "track_a",
                currentMediaId = "track_a",
                remainingMs = 1200L,
                windowMs = 7000L,
                seekAbortMarginMs = 420L
            )
        )

        assertTrue(
            FlowStateCrossfadeMath.shouldAbortFadeOut(
                isPlaying = true,
                expectedMediaId = "track_a",
                currentMediaId = "track_b",
                remainingMs = 1200L,
                windowMs = 7000L,
                seekAbortMarginMs = 420L
            )
        )
    }

    @Test
    fun shouldAbortFadeOut_returnsTrueWhenUserSeeksAwayFromTail() {
        assertTrue(
            FlowStateCrossfadeMath.shouldAbortFadeOut(
                isPlaying = true,
                expectedMediaId = "track_a",
                currentMediaId = "track_a",
                remainingMs = 8000L,
                windowMs = 7000L,
                seekAbortMarginMs = 420L
            )
        )

        assertFalse(
            FlowStateCrossfadeMath.shouldAbortFadeOut(
                isPlaying = true,
                expectedMediaId = "track_a",
                currentMediaId = "track_a",
                remainingMs = 6800L,
                windowMs = 7000L,
                seekAbortMarginMs = 420L
            )
        )
    }
}

