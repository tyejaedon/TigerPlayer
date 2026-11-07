package com.tigerplayer.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying state evaluators for FullPlayerScreen in-place Immersive Vortex mode.
 */
class FullPlayerImmersiveVortexTest {

    @Test
    fun `vortex blur radius dissipates to 0dp in immersive mode and restores to 8dp in standard mode`() {
        assertEquals(0.dp, calculateFullPlayerVortexBlurRadius(isVortexImmersive = true))
        assertEquals(8.dp, calculateFullPlayerVortexBlurRadius(isVortexImmersive = false))
    }

    @Test
    fun `vortex scrim alpha drops to 0 in immersive mode and restores to 1 in standard mode`() {
        assertEquals(0f, calculateFullPlayerVortexScrimAlpha(isVortexImmersive = true), 0.001f)
        assertEquals(1f, calculateFullPlayerVortexScrimAlpha(isVortexImmersive = false), 0.001f)
    }

    @Test
    fun `foreground controls alpha fades to 0 in immersive mode and restores to 1 in standard mode`() {
        assertEquals(0f, calculateFullPlayerControlsAlpha(isVortexImmersive = true), 0.001f)
        assertEquals(1f, calculateFullPlayerControlsAlpha(isVortexImmersive = false), 0.001f)
    }

    @Test
    fun `shouldShowFullPlayerControls evaluates visibility based on immersive state`() {
        assertFalse(shouldShowFullPlayerControls(isVortexImmersive = true))
        assertTrue(shouldShowFullPlayerControls(isVortexImmersive = false))
    }
}
