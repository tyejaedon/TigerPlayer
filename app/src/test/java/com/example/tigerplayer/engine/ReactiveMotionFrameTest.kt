package com.example.tigerplayer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveMotionFrameTest {

    @Test
    fun fromAudio_defaults_match_expected_baseline_motion() {
        val motion = FluidRenderer.ReactiveMotionFrame.fromAudio(AudioReactiveFrame())

        assertEquals(-0.45f, motion.expansion, 0.0001f)
        assertEquals(0.35f, motion.flowSpeed, 0.0001f)
        assertEquals(0f, motion.turbulence, 0.0001f)
    }

    @Test
    fun fromAudio_clamps_negative_inputs_before_mapping() {
        val motion = FluidRenderer.ReactiveMotionFrame.fromAudio(
            AudioReactiveFrame(bass = -2f, treble = -1f, energy = -5f, flux = -3f)
        )

        assertEquals(-0.45f, motion.expansion, 0.0001f)
        assertEquals(0.35f, motion.flowSpeed, 0.0001f)
        assertEquals(0f, motion.turbulence, 0.0001f)
    }

    @Test
    fun fromAudio_clamps_overflow_inputs_and_honors_upper_envelope() {
        val motion = FluidRenderer.ReactiveMotionFrame.fromAudio(
            AudioReactiveFrame(bass = 8f, treble = 9f, energy = 4f, flux = 3f)
        )

        assertEquals(0.95f, motion.expansion, 0.0001f)
        assertEquals(5.15f, motion.flowSpeed, 0.0001f)
        assertEquals(1.75f, motion.turbulence, 0.0001f)

        assertTrue(motion.expansion in -0.55f..0.95f)
        assertTrue(motion.flowSpeed in 0.25f..5.5f)
        assertTrue(motion.turbulence in 0f..2.2f)
    }

    @Test
    fun fromAudio_bass_increase_raises_flow_speed() {
        val lowBass = FluidRenderer.ReactiveMotionFrame.fromAudio(
            AudioReactiveFrame(bass = 0.1f)
        )
        val highBass = FluidRenderer.ReactiveMotionFrame.fromAudio(
            AudioReactiveFrame(bass = 0.9f)
        )

        assertTrue(highBass.flowSpeed > lowBass.flowSpeed)
    }

    @Test
    fun fromAudio_flux_and_energy_shift_expansion_outward() {
        val inward = FluidRenderer.ReactiveMotionFrame.fromAudio(
            AudioReactiveFrame(energy = 0.05f, flux = 0.05f)
        )
        val outward = FluidRenderer.ReactiveMotionFrame.fromAudio(
            AudioReactiveFrame(energy = 0.95f, flux = 0.95f)
        )

        assertTrue(outward.expansion > inward.expansion)
    }
}

