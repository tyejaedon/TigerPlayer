package com.tigerplayer.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchroederHallProcessorTest {

    @Test
    fun preDelay_delaysWetTailOnsetToExpectedSampleOffset() {
        val sampleRate = 48_000
        val processor = SchroederHallProcessor()
        processor.configure(sampleRate)

        val speakerPreDelaySamples = processor.preDelaySamplesFor(isBuiltInSpeaker = true)
        val headphonePreDelaySamples = processor.preDelaySamplesFor(isBuiltInSpeaker = false)

        val expectedMin = (sampleRate * 0.015f).toInt()
        val expectedMax = (sampleRate * 0.025f).toInt()
        assertTrue(
            "speaker pre-delay ($speakerPreDelaySamples) should be within 15-25 ms [$expectedMin, $expectedMax]",
            speakerPreDelaySamples in expectedMin..expectedMax
        )
        assertTrue(
            "headphone pre-delay ($headphonePreDelaySamples) should be within 15-25 ms [$expectedMin, $expectedMax]",
            headphonePreDelaySamples in expectedMin..expectedMax
        )

        // Feed a single impulse at sample 0 and silence afterward.
        // Before the pre-delay + shortest comb delay elapses, the reverb tank tail must be silent.
        val minCombSamples = ((sampleRate * 31.1f) / 1000f).toInt()
        val expectedTailOnset = speakerPreDelaySamples + minCombSamples

        var firstTailSample = -1
        for (i in 0 until expectedTailOnset + 200) {
            val input = if (i == 0) 1.0f else 0.0f
            processor.process(input, input, channels = 2, isBuiltInSpeaker = true)
            if (i > 0 && firstTailSample == -1 && abs(processor.lastTailL) > 1e-6f) {
                firstTailSample = i
            }
        }

        assertTrue("reverb tail should become active after impulse", firstTailSample > 0)
        assertTrue(
            "reverb tail onset ($firstTailSample) should be >= expectedTailOnset ($expectedTailOnset)",
            firstTailSample >= expectedTailOnset
        )
        assertTrue(
            "reverb tail onset ($firstTailSample) should occur shortly after expectedTailOnset ($expectedTailOnset)",
            firstTailSample <= expectedTailOnset + 4
        )
    }

    @Test
    fun impulseResponse_speakerPresetDecaysToMinus60dBWithinRt60Window() {
        val sampleRate = 48_000
        val processor = SchroederHallProcessor()
        processor.configure(sampleRate)

        // Measure tail energy over 2.0 seconds
        val totalSamples = sampleRate * 2
        val tailSamples = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            val input = if (i == 0) 1.0f else 0.0f
            processor.process(input, input, channels = 2, isBuiltInSpeaker = true)
            tailSamples[i] = processor.lastTailL
            assertFalse("NaN detected at sample $i", processor.outL.isNaN() || processor.outR.isNaN())
            assertFalse("Inf detected at sample $i", processor.outL.isInfinite() || processor.outR.isInfinite())
            assertTrue("output clipped at sample $i", abs(processor.outL) <= 1.0f && abs(processor.outR) <= 1.0f)
        }

        // Compute RMS in 50 ms windows to find peak tail energy and -60 dB decay time
        val windowSize = sampleRate / 20 // 50 ms
        var peakRms = 0f
        var peakWindowIndex = 0
        val windowRms = FloatArray(totalSamples / windowSize)
        for (w in windowRms.indices) {
            var sumSq = 0.0
            val start = w * windowSize
            for (j in start until start + windowSize) {
                val s = tailSamples[j].toDouble()
                sumSq += s * s
            }
            val rms = sqrt(sumSq / windowSize).toFloat()
            windowRms[w] = rms
            if (rms > peakRms) {
                peakRms = rms
                peakWindowIndex = w
            }
        }

        assertTrue("peak tail RMS should be positive", peakRms > 1e-4f)

        var minus60dBTimeSec = -1f
        for (w in peakWindowIndex until windowRms.size) {
            val db = if (windowRms[w] > 1e-9f) 20f * log10(windowRms[w] / peakRms) else -120f
            if (db <= -60f) {
                minus60dBTimeSec = ((w - peakWindowIndex) * windowSize).toFloat() / sampleRate.toFloat()
                break
            }
        }

        assertTrue("tail should decay by -60 dB within 2.0 s, got $minus60dBTimeSec", minus60dBTimeSec > 0f)
        assertTrue(
            "speaker RT60 ($minus60dBTimeSec s) should be within [0.8, 1.7] s",
            minus60dBTimeSec in 0.8f..1.7f
        )
    }

    @Test
    fun highPassFilter_attenuatesLowFrequencyReverbSendOnBuiltInSpeaker() {
        val sampleRate = 48_000
        val speakerProcessor = SchroederHallProcessor().apply { configure(sampleRate) }
        val headphoneProcessor = SchroederHallProcessor().apply { configure(sampleRate) }

        val frames = sampleRate / 2 // 0.5 s
        var speakerLowTailEnergy = 0.0
        var headphoneLowTailEnergy = 0.0
        var speakerHighTailEnergy = 0.0
        var headphoneHighTailEnergy = 0.0

        // 50 Hz low-frequency sine: speaker HPF (~200 Hz) should attenuate much more than headphone HPF
        for (i in 0 until frames) {
            val s = sin(2.0 * PI * 50.0 * i / sampleRate).toFloat() * 0.5f
            speakerProcessor.process(s, s, channels = 2, isBuiltInSpeaker = true)
            headphoneProcessor.process(s, s, channels = 2, isBuiltInSpeaker = false)
            if (i > sampleRate / 10) {
                speakerLowTailEnergy += speakerProcessor.lastTailL * speakerProcessor.lastTailL
                headphoneLowTailEnergy += headphoneProcessor.lastTailL * headphoneProcessor.lastTailL
            }
        }

        speakerProcessor.reset()
        headphoneProcessor.reset()

        // 2000 Hz mid-high sine: both should pass freely
        for (i in 0 until frames) {
            val s = sin(2.0 * PI * 2000.0 * i / sampleRate).toFloat() * 0.5f
            speakerProcessor.process(s, s, channels = 2, isBuiltInSpeaker = true)
            headphoneProcessor.process(s, s, channels = 2, isBuiltInSpeaker = false)
            if (i > sampleRate / 10) {
                speakerHighTailEnergy += speakerProcessor.lastTailL * speakerProcessor.lastTailL
                headphoneHighTailEnergy += headphoneProcessor.lastTailL * headphoneProcessor.lastTailL
            }
        }

        val speakerRatio = speakerLowTailEnergy / speakerHighTailEnergy.coerceAtLeast(1e-12)
        val headphoneRatio = headphoneLowTailEnergy / headphoneHighTailEnergy.coerceAtLeast(1e-12)

        assertTrue(
            "speaker 50Hz/2kHz tail energy ratio ($speakerRatio) should be much smaller than headphone ratio ($headphoneRatio)",
            speakerRatio < headphoneRatio * 0.5
        )
    }

    @Test
    fun tankStability_boundedUnderSustainedFullScaleDriveAndNoDenormals() {
        val sampleRate = 48_000
        val processor = SchroederHallProcessor().apply { configure(sampleRate) }

        // Drive with full-scale 1.0f square-like signal to stress-test tanh tank stability
        for (i in 0 until sampleRate) {
            val s = if ((i / 32) % 2 == 0) 1.0f else -1.0f
            processor.process(s, s, channels = 2, isBuiltInSpeaker = true)
            assertFalse("NaN under drive at $i", processor.outL.isNaN() || processor.outR.isNaN())
            assertFalse("Inf under drive at $i", processor.outL.isInfinite() || processor.outR.isInfinite())
            assertTrue("Output exceeded [-1, 1] at $i", abs(processor.outL) <= 1.0f && abs(processor.outR) <= 1.0f)
        }

        // Feed silence for 4 seconds and verify denormal flushing keeps states clean
        for (i in 0 until sampleRate * 4) {
            processor.process(0f, 0f, channels = 2, isBuiltInSpeaker = true)
            assertFalse("NaN during decay at $i", processor.outL.isNaN() || processor.outR.isNaN())
            val absL = abs(processor.outL)
            val absR = abs(processor.outR)
            assertTrue("Subnormal leak on L: $absL", absL == 0f || absL >= 1e-18f)
            assertTrue("Subnormal leak on R: $absR", absR == 0f || absR >= 1e-18f)
        }

        assertEquals(0f, processor.outL, 1e-6f)
        assertEquals(0f, processor.outR, 1e-6f)
    }
}
