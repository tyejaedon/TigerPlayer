package com.example.tigerplayer.ui.coverscreen

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
}

