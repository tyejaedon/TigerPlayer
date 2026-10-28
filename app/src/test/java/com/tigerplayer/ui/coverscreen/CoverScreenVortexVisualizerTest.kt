package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the Fluid Vortex background and Immersive Vortex Mode behavior
 * on the Cover Screen.
 */
class CoverScreenVortexVisualizerTest {

    @Test
    fun `vortex blur targets minimal blur in normal mode and zero in immersive mode`() {
        val normalBlur = calculateVortexBlurRadius(isVortexImmersive = false)
        val immersiveBlur = calculateVortexBlurRadius(isVortexImmersive = true)

        assertEquals(6.dp, normalBlur)
        assertEquals(0.dp, immersiveBlur)
    }

    @Test
    fun `scrim alpha is dimmed in normal mode and completely transparent in immersive mode`() {
        val normalScrim = calculateVortexScrimAlpha(isVortexImmersive = false)
        val immersiveScrim = calculateVortexScrimAlpha(isVortexImmersive = true)

        assertEquals(0.55f, normalScrim, 0.001f)
        assertEquals(0.0f, immersiveScrim, 0.001f)
    }

    @Test
    fun `media cycle viewport target count is exactly two for album artwork and synced lyrics`() {
        // Dot 1 (index 0) = Album art, Dot 2 (index 1) = Synced Lyrics
        val pageCount = COVER_MEDIA_CYCLE_PAGE_COUNT
        assertEquals(2, pageCount)
    }

    @Test
    fun `immersive mode suppresses controls and micro-waveform for pure visualizer`() {
        assertFalse("Controls must be hidden in immersive mode", shouldShowCoverControls(isVortexImmersive = true))
        assertFalse(
            "Waveform must be hidden in immersive mode",
            shouldShowCoverWaveform(hasModalOverlay = false, fullBleedLyricsVisible = false, isVortexImmersive = true)
        )
    }

    @Test
    fun `normal mode displays controls and micro-waveform when not occluded`() {
        assertTrue("Controls must be visible in normal mode", shouldShowCoverControls(isVortexImmersive = false))
        assertTrue(
            "Waveform must be visible in normal mode without overlay or full-bleed lyrics",
            shouldShowCoverWaveform(hasModalOverlay = false, fullBleedLyricsVisible = false, isVortexImmersive = false)
        )
        assertFalse(
            "Waveform must be suppressed when modal overlay is visible",
            shouldShowCoverWaveform(hasModalOverlay = true, fullBleedLyricsVisible = false, isVortexImmersive = false)
        )
        assertFalse(
            "Waveform must be suppressed when full bleed lyrics are visible",
            shouldShowCoverWaveform(hasModalOverlay = false, fullBleedLyricsVisible = true, isVortexImmersive = false)
        )
    }
}
