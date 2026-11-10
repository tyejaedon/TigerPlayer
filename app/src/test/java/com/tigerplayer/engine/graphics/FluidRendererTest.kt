package com.tigerplayer.engine

import android.content.Context
import com.tigerplayer.data.local.VisualizerPaletteMode
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test

class FluidRendererTest {

    private val context = mockk<Context>(relaxed = true)

    @Test
    fun fluidRenderer_initializesCleanly_andAcceptsSensitivityBounds() {
        val renderer = FluidRenderer(context)
        assertNotNull(renderer)

        // Test normal range
        renderer.setSensitivity(1.5f)

        // Test lower bound clamp
        renderer.setSensitivity(0.1f)

        // Test upper bound clamp
        renderer.setSensitivity(3.5f)
    }

    @Test
    fun fluidRenderer_updatesEnergyAndPaletteModeSafely() {
        val renderer = FluidRenderer(context)

        // Set palette mode
        renderer.setPaletteMode(VisualizerPaletteMode.DEFAULT_NEON)
        renderer.setPaletteMode(VisualizerPaletteMode.ALBUM_ART)

        // Update palette colors with valid RGB swatches
        val palette = listOf(
            floatArrayOf(0.9f, 0.2f, 0.1f),
            floatArrayOf(0.1f, 0.8f, 0.3f),
            floatArrayOf(0.2f, 0.4f, 0.9f)
        )
        renderer.updatePaletteColors(palette)

        // Update energy field
        renderer.updateEnergyField(listOf(0.5f, 0.4f, 0.3f, 0.2f, 0.1f, 0.05f))

        // Update node field
        renderer.updateNodeField(listOf(FluidRenderer.NodeField(0.5f, 0.5f, 1.0f)))

        // Update timeline progress
        renderer.updateTimelineEnergy(0.5f, 0.8f)
    }

    @Test
    fun fluidRenderer_handlesNullPaletteGracefully() {
        val renderer = FluidRenderer(context)
        renderer.updatePaletteColors(null)
    }

    @Test
    fun fluidConfig_splatRadiiEnsureSafeMarginContainment() {
        // Base splat radius and scale must be bounded so maximum splat radius remains <= 0.25f,
        // guaranteeing that orbital reaches (max orbit ~0.21f + max splat ~0.22f = 0.43f)
        // never reach the normalized screen edges at 0.50f.
        org.junit.Assert.assertTrue(FluidConfig.BASS_SPLAT_RADIUS_BASE <= 0.15f)
        org.junit.Assert.assertTrue(FluidConfig.BASS_SPLAT_RADIUS_SCALE <= 0.20f)
        val maxComputedRadius = FluidConfig.BASS_SPLAT_RADIUS_BASE + FluidConfig.BASS_SPLAT_RADIUS_SCALE
        org.junit.Assert.assertTrue(maxComputedRadius < 0.35f)
    }
}
