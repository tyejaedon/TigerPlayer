package com.example.tigerplayer.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class PrismIsolatorTest {

    @Test
    fun center_only_signal_is_suppressed_when_only_instruments_enabled() {
        val prism = PrismIsolator()
        prism.configure(48_000)

        var energy = 0f
        val frames = 4096
        for (i in 0 until frames) {
            val t = i / 48_000f
            val centerVoice = sin((2.0 * PI * 1100.0 * t)).toFloat() * 0.6f
            val frame = prism.process(
                left = centerVoice,
                right = centerVoice,
                channels = 2,
                vocalsGain = 0f,
                beatsGain = 0f,
                instrumentsGain = 1f
            )
            energy += abs(frame.left) + abs(frame.right)
        }

        assertTrue("Center content should be nearly cancelled in side-only mode", energy / frames < 0.03f)
    }

    @Test
    fun low_frequency_content_survives_when_beats_enabled() {
        val prism = PrismIsolator()
        prism.configure(48_000)

        var beatsEnergy = 0f
        val frames = 4096
        for (i in 0 until frames) {
            val t = i / 48_000f
            val bass = sin((2.0 * PI * 60.0 * t)).toFloat() * 0.75f
            val frame = prism.process(
                left = bass,
                right = bass,
                channels = 2,
                vocalsGain = 0f,
                beatsGain = 1f,
                instrumentsGain = 0f
            )
            beatsEnergy += abs(frame.left) + abs(frame.right)
        }

        assertTrue("Bass stem should remain audible in beats-only mode", beatsEnergy / frames > 0.12f)
    }
}

