package com.tigerplayer.ui.player

import com.tigerplayer.data.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsViewportCoordinatorTest {

    private val sampleLines = listOf(
        LyricLine(timeMs = 1_000L, text = "Line 1"),
        LyricLine(timeMs = 5_000L, text = "Line 2"),
        LyricLine(timeMs = 10_000L, text = "Line 3"),
        LyricLine(timeMs = 10_400L, text = "Line 4 (Short gap)"),
        LyricLine(timeMs = 30_000L, text = "Line 5 (Long gap)")
    )

    @Test
    fun `empty lines return sentinel state`() {
        val state = LyricsViewportCoordinator.calculateAnticipatoryState(
            lines = emptyList(),
            currentPositionMs = 5000L
        )
        assertEquals(-1, state.activeIndex)
        assertEquals(-1, state.targetScrollIndex)
        assertEquals(0f, state.transitionProgress, 0.001f)
    }

    @Test
    fun `pre-roll position before first line defaults to index 0 with no transition`() {
        val state = LyricsViewportCoordinator.calculateAnticipatoryState(
            lines = sampleLines,
            currentPositionMs = 0L,
            leadWindowMs = 750L
        )
        assertEquals(0, state.activeIndex)
        assertEquals(0, state.targetScrollIndex)
        assertEquals(0f, state.transitionProgress, 0.001f)
        assertEquals(0f, state.focalIndex, 0.001f)
    }

    @Test
    fun `mid-line position before lead window stays resting on active line`() {
        // Line 1 is 1000ms, Line 2 is 5000ms. Lead window = 750ms. Transition starts at 4250ms.
        val state = LyricsViewportCoordinator.calculateAnticipatoryState(
            lines = sampleLines,
            currentPositionMs = 3000L,
            leadWindowMs = 750L
        )
        assertEquals(0, state.activeIndex)
        assertEquals(0, state.targetScrollIndex)
        assertEquals(0f, state.transitionProgress, 0.001f)
        assertEquals(0f, state.focalIndex, 0.001f)
    }

    @Test
    fun `entering lead window advances targetScrollIndex and smoothly increases progress`() {
        // Transition starts at 5000 - 750 = 4250ms.
        // At 4625ms (halfway through 750ms window):
        val state = LyricsViewportCoordinator.calculateAnticipatoryState(
            lines = sampleLines,
            currentPositionMs = 4625L,
            leadWindowMs = 750L
        )
        assertEquals(0, state.activeIndex)
        assertEquals(1, state.targetScrollIndex) // Target anticipates Line 2
        assertTrue("Transition progress should be between 0 and 1", state.transitionProgress in 0.1f..0.9f)
        assertTrue("Focal index should be interpolating from 0 to 1", state.focalIndex in 0.1f..0.9f)
    }

    @Test
    fun `at exact line onset active index advances and completes transition`() {
        val state = LyricsViewportCoordinator.calculateAnticipatoryState(
            lines = sampleLines,
            currentPositionMs = 5000L,
            leadWindowMs = 750L
        )
        assertEquals(1, state.activeIndex)
        assertEquals(1, state.targetScrollIndex)
        assertEquals(0f, state.transitionProgress, 0.001f)
        assertEquals(1.0f, state.focalIndex, 0.001f)
    }

    @Test
    fun `last line anchors without exceeding bounds`() {
        val state = LyricsViewportCoordinator.calculateAnticipatoryState(
            lines = sampleLines,
            currentPositionMs = 35_000L,
            leadWindowMs = 750L
        )
        assertEquals(4, state.activeIndex)
        assertEquals(4, state.targetScrollIndex)
        assertEquals(0f, state.transitionProgress, 0.001f)
        assertEquals(4f, state.focalIndex, 0.001f)
    }

    @Test
    fun `short line gap clamps effective lead window to prevent premature transition`() {
        // Line 3 is 10000ms, Line 4 is 10400ms. Gap is 400ms.
        // Effective lead should clamp to (400 * 3) / 4 = 300ms.
        val state = LyricsViewportCoordinator.calculateAnticipatoryState(
            lines = sampleLines,
            currentPositionMs = 10_050L,
            leadWindowMs = 750L
        )
        assertEquals(300L, state.effectiveLeadWindowMs)
        assertEquals(2, state.activeIndex)
        assertEquals(2, state.targetScrollIndex) // 10050 < 10400 - 300 = 10100 -> stays resting
    }

    @Test
    fun `long instrumental break rests active line until lead window arrives`() {
        // Line 4 is 10400ms, Line 5 is 30000ms.
        // Lead window is 750ms. Transition start = 29250ms.
        val restingState = LyricsViewportCoordinator.calculateAnticipatoryState(
            lines = sampleLines,
            currentPositionMs = 20_000L,
            leadWindowMs = 750L
        )
        assertEquals(3, restingState.activeIndex)
        assertEquals(3, restingState.targetScrollIndex)
        assertEquals(0f, restingState.transitionProgress, 0.001f)

        val anticipatoryState = LyricsViewportCoordinator.calculateAnticipatoryState(
            lines = sampleLines,
            currentPositionMs = 29_625L,
            leadWindowMs = 750L
        )
        assertEquals(3, anticipatoryState.activeIndex)
        assertEquals(4, anticipatoryState.targetScrollIndex)
        assertTrue(anticipatoryState.transitionProgress > 0f)
    }

    @Test
    fun `projection produces 3D cylindrical values aligned with focal index`() {
        // Focal index at 1.0f (Line 1 active)
        val center = LyricsViewportCoordinator.calculateProjection(itemIndex = 1, focalIndex = 1.0f)
        assertEquals(0f, center.rotationX, 0.001f)
        assertEquals(1.05f, center.scale, 0.001f)
        assertEquals(1.0f, center.alpha, 0.001f)

        val lineAbove = LyricsViewportCoordinator.calculateProjection(itemIndex = 0, focalIndex = 1.0f)
        assertTrue("Line above center must have negative rotationX (tilt backward)", lineAbove.rotationX < 0f)
        assertTrue("Line above center must have reduced scale", lineAbove.scale < 1.0f)
        assertTrue("Line above center must have reduced alpha", lineAbove.alpha < 1.0f)

        val lineBelow = LyricsViewportCoordinator.calculateProjection(itemIndex = 2, focalIndex = 1.0f)
        assertTrue("Line below center must have positive rotationX (tilt upward)", lineBelow.rotationX > 0f)
        assertTrue("Line below center must have reduced scale", lineBelow.scale < 1.0f)
        assertTrue("Line below center must have reduced alpha", lineBelow.alpha < 1.0f)
    }

    @Test
    fun `center offset calculation precisely aligns item midpoint with viewport midpoint`() {
        val viewportHeight = 600
        val itemHeight = 60
        // Expected center offset: -((600 / 2) - (60 / 2)) = -(300 - 30) = -270
        val offset = LyricsViewportCoordinator.calculateCenterOffset(viewportHeight, itemHeight)
        assertEquals(-270, offset)
    }

    @Test
    fun `position extrapolation clamps to maximum bound and respects pause`() {
        val basePos = 10_000L
        val anchorTime = 1_000L

        // Extrapolates normally for small elapsed
        val pos50 = LyricsViewportCoordinator.extrapolatePosition(
            reportedPositionMs = basePos,
            lastAnchorMonotonicMs = anchorTime,
            currentMonotonicMs = anchorTime + 50L,
            isPlaying = true
        )
        assertEquals(10_050L, pos50)

        // Clamps to max 350ms
        val pos1000 = LyricsViewportCoordinator.extrapolatePosition(
            reportedPositionMs = basePos,
            lastAnchorMonotonicMs = anchorTime,
            currentMonotonicMs = anchorTime + 1000L,
            isPlaying = true
        )
        assertEquals(10_350L, pos1000)

        // Does not extrapolate when paused
        val posPaused = LyricsViewportCoordinator.extrapolatePosition(
            reportedPositionMs = basePos,
            lastAnchorMonotonicMs = anchorTime,
            currentMonotonicMs = anchorTime + 200L,
            isPlaying = false
        )
        assertEquals(basePos, posPaused)
    }
}
