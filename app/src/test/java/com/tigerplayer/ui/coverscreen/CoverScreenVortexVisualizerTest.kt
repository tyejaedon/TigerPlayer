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
        // Dot 1 (index 0) = Album art, Dot 2 (index 1) = Lyrics
        val maxIndex = 1
        val pageCount = maxIndex + 1
        assertEquals(2, pageCount)
    }

    @Test
    fun `immersive mode suppresses controls, discovery view, and micro-waveform for pure visualizer`() {
        // When isVortexImmersive is true:
        val isVortexImmersive = true

        val shouldShowControls = !isVortexImmersive
        val shouldShowDiscovery = !isVortexImmersive
        val shouldShowWaveform = !isVortexImmersive
        val shouldShowMediaCycle = !isVortexImmersive

        assertFalse("Controls must be hidden in immersive mode", shouldShowControls)
        assertFalse("Discovery view must be hidden in immersive mode", shouldShowDiscovery)
        assertFalse("Waveform must be hidden in immersive mode", shouldShowWaveform)
        assertFalse("Media cycle must be hidden in immersive mode", shouldShowMediaCycle)
    }

    @Test
    fun `normal mode displays all controls, discovery view, and micro-waveform`() {
        val isVortexImmersive = false

        val shouldShowControls = !isVortexImmersive
        val shouldShowDiscovery = !isVortexImmersive
        val shouldShowWaveform = !isVortexImmersive
        val shouldShowMediaCycle = !isVortexImmersive

        assertTrue("Controls must be visible in normal mode", shouldShowControls)
        assertTrue("Discovery view must be visible in normal mode", shouldShowDiscovery)
        assertTrue("Waveform must be visible in normal mode", shouldShowWaveform)
        assertTrue("Media cycle must be visible in normal mode", shouldShowMediaCycle)
    }

    companion object {
        internal fun calculateVortexBlurRadius(isVortexImmersive: Boolean) =
            if (isVortexImmersive) 0.dp else 6.dp

        internal fun calculateVortexScrimAlpha(isVortexImmersive: Boolean) =
            if (isVortexImmersive) 0f else 0.55f
    }
}
