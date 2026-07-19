package com.example.tigerplayer.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
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

        assertTrue("Center content should remain strongly suppressed in instruments-only mode", energy / frames < 0.06f)
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

    @Test
    fun hot_input_stays_bounded_with_all_stems_enabled() {
        val prism = PrismIsolator()
        prism.configure(48_000)

        var peak = 0f
        val frames = 16_384
        for (i in 0 until frames) {
            val t = i / 48_000f
            val bass = sin((2.0 * PI * 60.0 * t)).toFloat() * 0.92f
            val vocal = sin((2.0 * PI * 1400.0 * t)).toFloat() * 0.88f
            val high = sin((2.0 * PI * 5200.0 * t)).toFloat() * 0.82f
            val left = (bass + vocal + high).coerceIn(-1f, 1f)
            val right = (bass + vocal - high).coerceIn(-1f, 1f)

            val frame = prism.process(
                left = left,
                right = right,
                channels = 2,
                vocalsGain = 1f,
                beatsGain = 1f,
                instrumentsGain = 1f
            )
            peak = max(peak, max(abs(frame.left), abs(frame.right)))
        }

        assertTrue("Prism output should stay safely bounded", peak <= 1.0f)
    }

    @Test
    fun mono_track_retains_presence_in_instruments_mode() {
        val prism = PrismIsolator()
        prism.configure(48_000)

        var energy = 0f
        val frames = 4096
        for (i in 0 until frames) {
            val t = i / 48_000f
            val mono = sin((2.0 * PI * 4200.0 * t)).toFloat() * 0.62f
            val frame = prism.process(
                left = mono,
                right = mono,
                channels = 2,
                vocalsGain = 0f,
                beatsGain = 0f,
                instrumentsGain = 1f
            )
            energy += abs(frame.left) + abs(frame.right)
        }

        assertTrue("Instruments mode should keep a faint mono-compatible high-band presence", energy / frames > 0.004f)
    }
}

