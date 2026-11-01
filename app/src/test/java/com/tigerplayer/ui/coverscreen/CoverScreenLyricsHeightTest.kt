package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying adaptive lyrics box height across various font scales (1.0x, 1.25x, 1.5x, 2.0x)
 * and screen heights (tight, standard, spacious cover screens, and flex mode split displays).
 *
 * Regression test suite for GitHub Issue #252:
 * "Cover screen: LyricsDisplay is hard-clipped to 72 dp when stats are visible"
 */
class CoverScreenLyricsHeightTest {

    @Test
    fun `min line height scales dynamically with font scale and exceeds 72dp cutoff`() {
        // At default 1.0x font scale: nominal line height + vertical padding + clearance = 80dp (> 72dp)
        val minHeight1x = calculateCoverLyricsMinLineHeight(1.0f)
        assertEquals(80.dp, minHeight1x)
        assertTrue("1.0x font scale must exceed old 72dp cutoff", minHeight1x > 72.dp)

        // At 1.25x font scale: active lyric line is larger, requiring at least 90dp
        val minHeight125x = calculateCoverLyricsMinLineHeight(1.25f)
        assertEquals(90.dp, minHeight125x)
        assertTrue("1.25x font scale requires at least 90dp", minHeight125x >= 90.dp)

        // At 1.5x font scale (accessibility): requires at least 100dp
        val minHeight15x = calculateCoverLyricsMinLineHeight(1.5f)
        assertEquals(100.dp, minHeight15x)
        assertTrue("1.5x font scale requires at least 100dp", minHeight15x >= 100.dp)

        // At 2.0x font scale: requires at least 120dp
        val minHeight2x = calculateCoverLyricsMinLineHeight(2.0f)
        assertEquals(120.dp, minHeight2x)
        assertTrue("2.0x font scale requires at least 120dp", minHeight2x >= 120.dp)
    }

    @Test
    fun `min line height enforces sensible floor for small font scales`() {
        // Very small font scales (e.g. 0.85x or smaller) should have an effective minimum floor
        val minHeightSmall = calculateCoverLyricsMinLineHeight(0.85f)
        assertEquals(74.dp, minHeightSmall)
        assertTrue("Small font scale must still provide safe clearance", minHeightSmall > 72.dp)

        val minHeightZero = calculateCoverLyricsMinLineHeight(0.0f)
        assertEquals(74.dp, minHeightZero)
    }

    @Test
    fun `tight cover screen (Motorola Razr 2022 ~343dp) restricts to min line height to keep stats visible`() {
        val screenHeight = 343.dp

        // At 1.0x font scale: exactly 1 readable line (80dp), leaving room for stats & discovery
        val height1x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.0f,
            isLarge = false,
            statsVisible = true
        )
        assertEquals(80.dp, height1x)

        // At 1.25x font scale: expands to 90dp to prevent mid-glyph clipping
        val height125x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.25f,
            isLarge = false,
            statsVisible = true
        )
        assertEquals(90.dp, height125x)

        // At 1.5x font scale: expands to 100dp
        val height15x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.5f,
            isLarge = false,
            statsVisible = true
        )
        assertEquals(100.dp, height15x)
    }

    @Test
    fun `standard cover screen (Samsung Galaxy Z Flip ~400dp) expands proportionally`() {
        val screenHeight = 400.dp

        // Surplus height = 400 - 360 = 40dp; 25% allocation = +10dp
        val height1x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.0f,
            isLarge = false,
            statsVisible = true
        )
        assertEquals(90.dp, height1x)
        assertTrue("400dp screen provides more room than 343dp screen", height1x > 80.dp)

        val height125x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.25f,
            isLarge = false,
            statsVisible = true
        )
        assertEquals(100.dp, height125x)

        val height15x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.5f,
            isLarge = false,
            statsVisible = true
        )
        assertEquals(110.dp, height15x)
    }

    @Test
    fun `spacious cover screen (Motorola Razr+ ~413dp) allocates extra room up to safe cap`() {
        val screenHeight = 413.dp

        val height1x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.0f,
            isLarge = false,
            statsVisible = true
        )
        // 80dp + (413 - 360) * 0.25 = 80 + 13.25 = 93.25dp
        assertEquals(93.25.dp, height1x)

        val height15x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.5f,
            isLarge = false,
            statsVisible = true
        )
        assertEquals(113.25.dp, height15x)

        // Very large cover screen (e.g. 500dp) stays capped at 120dp for compact mode
        val heightLargeCover = calculateCoverLyricsBoxHeight(
            screenHeightDp = 500.dp,
            fontScale = 1.0f,
            isLarge = false,
            statsVisible = true
        )
        assertEquals(115.dp, heightLargeCover)

        val heightHugeCover = calculateCoverLyricsBoxHeight(
            screenHeightDp = 600.dp,
            fontScale = 1.0f,
            isLarge = false,
            statsVisible = true
        )
        assertEquals(120.dp, heightHugeCover)
    }

    @Test
    fun `flex mode (hinge posture) expands beyond 72dp to display multiple lines`() {
        // Typical folding phone unfolded height: 800-1000dp
        val screenHeight = 800.dp

        val flexHeight1x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.0f,
            isLarge = true,
            statsVisible = true
        )
        // 800 * 0.16 = 128dp
        assertEquals(128.dp, flexHeight1x)
        assertTrue("Flex mode lyrics box must not be clamped to 72dp", flexHeight1x >= 120.dp)

        val flexHeight1000 = calculateCoverLyricsBoxHeight(
            screenHeightDp = 1000.dp,
            fontScale = 1.0f,
            isLarge = true,
            statsVisible = true
        )
        // Capped at 160dp
        assertEquals(160.dp, flexHeight1000)

        // At 1.5x font scale, flex mode satisfies minimum line height requirement
        val flexHeight15x = calculateCoverLyricsBoxHeight(
            screenHeightDp = screenHeight,
            fontScale = 1.5f,
            isLarge = true,
            statsVisible = true
        )
        assertTrue(flexHeight15x >= calculateCoverLyricsMinLineHeight(1.5f))
    }

    @Test
    fun `when stats are hidden, lyrics container expands to standard nominal height`() {
        // Compact cover screen without stats
        val compactNoStats = calculateCoverLyricsBoxHeight(
            screenHeightDp = 400.dp,
            fontScale = 1.0f,
            isLarge = false,
            statsVisible = false
        )
        assertEquals(130.dp, compactNoStats)

        // Flex mode without stats
        val flexNoStats = calculateCoverLyricsBoxHeight(
            screenHeightDp = 800.dp,
            fontScale = 1.0f,
            isLarge = true,
            statsVisible = false
        )
        assertEquals(180.dp, flexNoStats)

        // High font scale without stats still respects minLineHeight
        val flexHugeFont = calculateCoverLyricsBoxHeight(
            screenHeightDp = 800.dp,
            fontScale = 4.0f,
            isLarge = true,
            statsVisible = false
        )
        assertTrue(flexHugeFont >= calculateCoverLyricsMinLineHeight(4.0f))
    }

    @Test
    fun `handles invalid or infinite screen dimensions gracefully`() {
        val zeroHeight = calculateCoverLyricsBoxHeight(
            screenHeightDp = 0.dp,
            fontScale = 1.0f,
            isLarge = false,
            statsVisible = true
        )
        assertTrue(zeroHeight >= 80.dp)

        val infHeight = calculateCoverLyricsBoxHeight(
            screenHeightDp = Dp.Infinity,
            fontScale = 1.0f,
            isLarge = false,
            statsVisible = true
        )
        assertTrue(infHeight >= 80.dp)
    }
}
