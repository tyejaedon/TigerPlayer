package com.example.tigerplayer.ui.coverscreen

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverScreenHeuristicTest {

    @Test
    fun identifies_flip_cover_sized_displays() {
        assertTrue(isCoverScreenHeuristic(widthDp = 372, heightDp = 400))
        assertTrue(isCoverScreenHeuristic(widthDp = 360, heightDp = 390))
    }

    @Test
    fun rejects_regular_phone_display_sizes() {
        assertFalse(isCoverScreenHeuristic(widthDp = 412, heightDp = 915))
        assertFalse(isCoverScreenHeuristic(widthDp = 800, heightDp = 360))
    }

    @Test
    fun `default display id is not a secondary display`() {
        assertFalse(isSecondaryDisplayIdentity(Display.DEFAULT_DISPLAY))
    }

    @Test
    fun `non-default display id is treated as a secondary display`() {
        assertTrue(isSecondaryDisplayIdentity(1))
        assertTrue(isSecondaryDisplayIdentity(2))
    }

    @Test
    fun `secondary display identity is authoritative regardless of dp size`() {
        // A true secondary display (e.g. a Motorola external panel) should report cover-screen
        // state even when its reported dp size looks like a full-size phone display, because
        // there is no ambiguity about which physical panel is hosting the window.
        assertTrue(
            resolveIsCoverScreen(
                widthDp = 800,
                heightDp = 1200,
                hasSeparatingHinge = false,
                isSecondaryDisplay = true
            )
        )
    }

    @Test
    fun `resize-model devices fall back to the dp and hinge heuristic`() {
        assertTrue(
            resolveIsCoverScreen(
                widthDp = 372,
                heightDp = 400,
                hasSeparatingHinge = false,
                isSecondaryDisplay = false
            )
        )
        assertFalse(
            resolveIsCoverScreen(
                widthDp = 372,
                heightDp = 400,
                hasSeparatingHinge = true,
                isSecondaryDisplay = false
            )
        )
        assertFalse(
            resolveIsCoverScreen(
                widthDp = 412,
                heightDp = 915,
                hasSeparatingHinge = false,
                isSecondaryDisplay = false
            )
        )
    }
}

