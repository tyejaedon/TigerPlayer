package com.tigerplayer.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying elevated active lyrics positioning, adaptive vertical padding,
 * and deterministic scroll offset calculations.
 *
 * Regression test suite for GitHub Issue #411:
 * "fix(ui): elevate active lyrics position and enable manual scroll browsing in player and cover screen"
 */
class PlayerLyricsPositionTest {

    @Test
    fun `default active position ratio is elevated in upper third`() {
        assertEquals(0.30f, DEFAULT_LYRICS_ACTIVE_POSITION_RATIO, 0.001f)
        assertTrue(
            "Active lyrics ratio should be elevated higher than center (0.5f)",
            DEFAULT_LYRICS_ACTIVE_POSITION_RATIO < 0.50f
        )
    }

    @Test
    fun `manual scroll cooldown allows generous browsing time`() {
        assertEquals(3500L, DEFAULT_LYRICS_MANUAL_SCROLL_COOLDOWN_MS)
        assertTrue(
            "Manual scroll cooldown should provide at least 3 seconds of calm reading time",
            DEFAULT_LYRICS_MANUAL_SCROLL_COOLDOWN_MS >= 3000L
        )
    }

    @Test
    fun `calculateLyricsTargetY docks active line in upper portion of viewport`() {
        // Full player screen (~1000px viewport)
        val fullPlayerTarget = calculateLyricsTargetY(1000f, 0.30f)
        assertEquals(300f, fullPlayerTarget, 0.1f)

        // Compact cover screen viewport (~400px viewport)
        val coverScreenTarget = calculateLyricsTargetY(400f, 0.30f)
        assertEquals(120f, coverScreenTarget, 0.1f)

        // Edge case: ratio clamping
        assertEquals(100f, calculateLyricsTargetY(1000f, 0.02f), 0.1f) // clamped to 0.1f
        assertEquals(900f, calculateLyricsTargetY(1000f, 0.98f), 0.1f) // clamped to 0.9f
    }

    @Test
    fun `calculateLyricsContentPadding provides complementary top and bottom padding`() {
        val maxHeight = 500.dp
        val (topPadding, bottomPadding) = calculateLyricsContentPadding(maxHeight, 0.30f)

        assertEquals(150.dp, topPadding)
        assertEquals(350.dp, bottomPadding)
        assertEquals(maxHeight, topPadding + bottomPadding)
    }

    @Test
    fun `calculateLyricsContentPadding on cover screen ensures first and last items can dock`() {
        val coverHeight = 160.dp
        val (topPadding, bottomPadding) = calculateLyricsContentPadding(coverHeight, 0.30f)

        assertEquals(48.dp, topPadding)
        assertEquals(112.dp, bottomPadding)
        assertTrue("Top padding must align with upper portion", topPadding < coverHeight / 2)
        assertTrue("Bottom padding must allow last lines to scroll up", bottomPadding > coverHeight / 2)
    }

    @Test
    fun `calculateLyricsScrollOffset returns non-negative offset centering item at target`() {
        val viewportHeightPx = 1000f
        val activeRatio = 0.30f
        val targetY = viewportHeightPx * activeRatio // 300f
        val topPaddingPx = targetY // 300f
        val itemHeightPx = 60f

        val scrollOffset = calculateLyricsScrollOffset(
            topPaddingPx = topPaddingPx,
            targetY = targetY,
            itemHeightPx = itemHeightPx
        )

        // Item top will be placed at topPaddingPx - scrollOffset = 300 - 30 = 270.
        // Item center will be 270 + (60 / 2) = 300 = targetY.
        assertEquals(30, scrollOffset)
        assertTrue("Scroll offset must be non-negative to prevent bottom overshoot", scrollOffset >= 0)
    }

    @Test
    fun `calculateLyricsScrollOffset never throws items to viewport bottom`() {
        // Previous bug passed negative centerOffset: -((viewportHeight / 2) - (approxItemHeightPx / 2))
        // which pushed items to the very bottom edge.
        val targetY = 150f
        val topPaddingPx = 150f
        val itemHeightPx = 40f

        val scrollOffset = calculateLyricsScrollOffset(
            topPaddingPx = topPaddingPx,
            targetY = targetY,
            itemHeightPx = itemHeightPx
        )

        assertEquals(20, scrollOffset)
        assertTrue(scrollOffset >= 0)
    }
}
