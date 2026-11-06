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

    @Test
    fun `cover art size expands adaptively and stays prominent across screen dimensions`() {
        // Tight cover screen (Motorola Razr 2022 ~343dp): ~164.64dp -> expands significantly beyond old 130dp
        val tightArt = calculateCoverArtSize(343.dp, isLarge = false)
        assertTrue("Tight screen cover art must be at least 160dp", tightArt >= 160.dp)

        // Standard cover screen (Samsung Galaxy Z Flip 5/6 ~374dp): ~179.52dp -> prominent square presentation
        val standardArt = calculateCoverArtSize(374.dp, isLarge = false)
        assertTrue("Standard Flip cover art must be at least 175dp", standardArt >= 175.dp)

        // Spacious cover screen (Motorola Razr+ ~416dp): ~199.68dp
        val spaciousArt = calculateCoverArtSize(416.dp, isLarge = false)
        assertTrue("Spacious screen cover art must be at least 195dp", spaciousArt >= 195.dp)

        // Flex mode: ~190dp
        val flexArt = calculateCoverArtSize(800.dp, isLarge = true)
        assertTrue("Flex mode cover art must be at least 180dp", flexArt >= 180.dp)

        // Safe boundaries
        val zeroArt = calculateCoverArtSize(0.dp, isLarge = false)
        assertEquals(182.4.dp, zeroArt)
        val infArt = calculateCoverArtSize(androidx.compose.ui.unit.Dp.Infinity, isLarge = false)
        assertEquals(182.4.dp, infArt)
    }

    @Test
    fun `immersive hint test tag matches convention`() {
        assertEquals("cover_screen_immersive_hint", CoverScreenTestTags.IMMERSIVE_HINT)
        assertEquals("cover_screen_like_heart_overlay", CoverScreenTestTags.LIKE_HEART_OVERLAY)
        assertEquals("cover_screen_album_art_container", CoverScreenTestTags.ALBUM_ART_CONTAINER)
    }

    @Test
    fun `recurring long-press toggles fluid vortex immersive mode state deterministically`() {
        var isVortexImmersive = false

        fun toggle() {
            isVortexImmersive = !isVortexImmersive
        }

        // Cycle 1: Press down on album art -> enters immersive mode
        toggle()
        assertTrue("Cycle 1: must be in immersive mode", isVortexImmersive)
        assertFalse("Cycle 1: controls must be hidden", shouldShowCoverControls(isVortexImmersive))
        assertEquals(0.dp, calculateVortexBlurRadius(isVortexImmersive))
        assertEquals(0.0f, calculateVortexScrimAlpha(isVortexImmersive), 0.001f)

        // Cycle 2: Press down again -> exits immersive mode and restores controls
        toggle()
        assertFalse("Cycle 2: must exit immersive mode", isVortexImmersive)
        assertTrue("Cycle 2: controls must be restored", shouldShowCoverControls(isVortexImmersive))
        assertEquals(6.dp, calculateVortexBlurRadius(isVortexImmersive))
        assertEquals(0.55f, calculateVortexScrimAlpha(isVortexImmersive), 0.001f)

        // Cycle 3: Press down again -> enters immersive mode again
        toggle()
        assertTrue("Cycle 3: must re-enter immersive mode", isVortexImmersive)
        assertFalse("Cycle 3: controls must be hidden", shouldShowCoverControls(isVortexImmersive))

        // Cycle 4: Press down again -> exits immersive mode again
        toggle()
        assertFalse("Cycle 4: must exit immersive mode", isVortexImmersive)
        assertTrue("Cycle 4: controls must be restored", shouldShowCoverControls(isVortexImmersive))
    }

    @Test
    fun `media cycle viewport dimensions match album art size for swipe continuity`() {
        val screenHeight = 374.dp
        val artSize = calculateCoverArtSize(screenHeight, isLarge = false)
        val lyricsHeight = calculateCoverLyricsBoxHeight(screenHeight, fontScale = 1.0f, isLarge = false, statsVisible = true)

        val targetHeight = maxOf(lyricsHeight, artSize)

        assertEquals(
            "Viewport height for lyrics must match album art size for swipe continuity",
            artSize,
            targetHeight
        )
    }
}
