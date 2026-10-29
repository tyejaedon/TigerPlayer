package com.tigerplayer.ui.coverscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that [CoverScreenWindowState] and [resolveIsCoverScreen] remain stable
 * when the soft keyboard (IME) compresses viewport dimensions.
 *
 * Regression test suite for GitHub Issue #260:
 * "Cover screen: prevent unmounting and viewport clipping during IME keyboard resize"
 */
class CoverScreenWindowStateTest {

    @Test
    fun `isCoverScreen remains true when height is compressed by keyboard insets on clamshell cover`() {
        // Un-insetted clamshell cover display (e.g. Samsung Galaxy Z Flip): ~300x300 dp
        val baseWidthDp = 300
        val baseHeightDp = 300

        assertTrue(
            "Base cover display must resolve to cover screen",
            resolveIsCoverScreen(
                widthDp = baseWidthDp,
                heightDp = baseHeightDp,
                hasSeparatingHinge = false,
                isSecondaryDisplay = false
            )
        )

        // When soft keyboard (IME) opens, available height drops drastically (e.g. from 300 to 140 dp)
        val compressedHeightDp = 140
        val imeInsetBottomDp = 160

        // Direct check without IME insets would falsely fail the heuristic because 140 < 200 dp
        assertFalse(
            "Direct heuristic with compressed height falls below 200 dp floor",
            isCoverScreenHeuristic(baseWidthDp, compressedHeightDp)
        )

        // With IME insets accounted for, resolveIsCoverScreen must remain true
        assertTrue(
            "resolveIsCoverScreen must remain true when height is compressed by IME insets",
            resolveIsCoverScreen(
                widthDp = baseWidthDp,
                heightDp = compressedHeightDp,
                hasSeparatingHinge = false,
                isSecondaryDisplay = false,
                imeBottomDp = imeInsetBottomDp
            )
        )
    }

    @Test
    fun `resolveBaseWindowDimensions reconstructs un-insetted height when activity is null`() {
        val configurationWidthDp = 274
        val configurationHeightDp = 130
        val imeBottomDp = 154

        val (baseW, baseH) = resolveBaseWindowDimensions(
            activity = null,
            configurationWidthDp = configurationWidthDp,
            configurationHeightDp = configurationHeightDp,
            density = 2.625f,
            imeBottomDp = imeBottomDp
        )

        assertEquals(274, baseW)
        assertEquals(284, baseH)
        assertTrue(
            "Reconstructed dimensions must pass cover screen heuristic",
            isCoverScreenHeuristic(baseW, baseH)
        )
    }

    @Test
    fun `full-size phone displays are not mistaken for cover screen even when compressed by keyboard`() {
        // Phone display: 412x915 dp. With keyboard: height drops to 450 dp, IME = 465 dp.
        val widthDp = 412
        val compressedHeightDp = 450
        val imeBottomDp = 465

        assertFalse(
            "Full phone display must not be detected as cover screen during IME resize",
            resolveIsCoverScreen(
                widthDp = widthDp,
                heightDp = compressedHeightDp,
                hasSeparatingHinge = false,
                isSecondaryDisplay = false,
                imeBottomDp = imeBottomDp
            )
        )
    }

    @Test
    fun `multi-window mode is rejected even if keyboard insets would place it in cover band`() {
        assertFalse(
            "Multi-window mode must be rejected outright",
            resolveIsCoverScreen(
                widthDp = 300,
                heightDp = 140,
                hasSeparatingHinge = false,
                isSecondaryDisplay = false,
                isInMultiWindowMode = true,
                imeBottomDp = 160
            )
        )
    }

    @Test
    fun `secondary display identity remains authoritative regardless of keyboard compression`() {
        // True secondary display (Motorola Razr outer display)
        assertTrue(
            "Secondary display identity wins unconditionally regardless of height compression",
            resolveIsCoverScreen(
                widthDp = 246,
                heightDp = 120,
                hasSeparatingHinge = false,
                isSecondaryDisplay = true,
                imeBottomDp = 0
            )
        )
    }

    @Test
    fun `forcePreview overrides all heuristics and returns true on standard phone displays`() {
        assertFalse(
            "Standard phone without forcePreview is false",
            resolveIsCoverScreen(
                widthDp = 412,
                heightDp = 915,
                hasSeparatingHinge = false,
                isSecondaryDisplay = false,
                forcePreview = false
            )
        )

        assertTrue(
            "Standard phone with forcePreview is true",
            resolveIsCoverScreen(
                widthDp = 412,
                heightDp = 915,
                hasSeparatingHinge = true,
                isSecondaryDisplay = false,
                isInMultiWindowMode = true,
                forcePreview = true
            )
        )
    }

    @Test
    fun `separating hinge rejects cover screen even with keyboard insets`() {
        assertFalse(
            "Separating hinge indicates opened/flex state, not outer cover display",
            resolveIsCoverScreen(
                widthDp = 300,
                heightDp = 140,
                hasSeparatingHinge = true,
                isSecondaryDisplay = false,
                imeBottomDp = 160
            )
        )
    }
}
