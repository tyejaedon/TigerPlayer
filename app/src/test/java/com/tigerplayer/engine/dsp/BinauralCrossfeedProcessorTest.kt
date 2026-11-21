package com.tigerplayer.engine

import com.tigerplayer.data.local.CrossfeedPreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinauralCrossfeedProcessorTest {

    @Test
    fun impulseResponse_verifiesExactItdDelayAccuracyAcrossSampleRates() {
        val sampleRates = listOf(44_100, 48_000, 96_000)
        val presets = listOf(CrossfeedPreset.SUBTLE, CrossfeedPreset.CLASSIC_STUDIO)

        for (sampleRate in sampleRates) {
            for (preset in presets) {
                val processor = BinauralCrossfeedProcessor()
                processor.configure(sampleRate)
                val groupDelay = processor.getGroupDelaySamples()
                val totalFrames = 512
                val outL = FloatArray(totalFrames)
                val outR = FloatArray(totalFrames)

                for (n in 0 until totalFrames) {
                    val inL = if (n == 0) 1.0f else 0.0f
                    processor.process(
                        inputL = inL,
                        inputR = 0.0f,
                        channels = 2,
                        preset = preset,
                        bypassForSpeaker = false,
                        isBitPerfect = false
                    )
                    outL[n] = processor.outL
                    outR[n] = processor.outR
                }

                // Direct impulse peak must align cleanly with the FIR group delay
                val directPeakIdx = outL.indices.maxByOrNull { abs(outL[it]) } ?: -1
                assertEquals(
                    "Direct channel impulse peak must align at groupDelay for $sampleRate Hz / $preset",
                    groupDelay,
                    directPeakIdx
                )

                // Compute signed energy centroid of crossfeed impulse to sub-sample accuracy
                var sumR = 0.0
                var weightedSumR = 0.0
                for (n in 0 until totalFrames) {
                    sumR += outR[n].toDouble()
                    weightedSumR += n.toDouble() * outR[n].toDouble()
                }
                assertTrue("Crossfeed impulse sum must match feedGain (sum=$sumR)", abs(sumR - preset.feedGain) < 0.01)

                val measuredItdSamples = (weightedSumR / sumR) - groupDelay.toDouble()
                val measuredItdMicros = (measuredItdSamples / sampleRate.toDouble()) * 1_000_000.0
                assertEquals(
                    "ITD delay at $sampleRate Hz for $preset must match target ${preset.delayMicros} us",
                    preset.delayMicros.toDouble(),
                    measuredItdMicros,
                    15.0
                )
            }
        }
    }

    @Test
    fun frequencyResponse_verifiesLowPassIsolationAndMinus6dBPerOctaveRolloff() {
        val sampleRate = 48_000
        val preset = CrossfeedPreset.CLASSIC_STUDIO
        val processor = BinauralCrossfeedProcessor()

        fun measureCrossfeedGainDb(freqHz: Float): Float {
            processor.configure(sampleRate)
            val warmupFrames = 1024
            val measureFrames = 3072
            var sumSqIn = 0.0
            var sumSqCross = 0.0

            for (n in 0 until warmupFrames + measureFrames) {
                val sampleL = sin(2.0 * PI * freqHz * n / sampleRate.toDouble()).toFloat()
                processor.process(
                    inputL = sampleL,
                    inputR = 0f,
                    channels = 2,
                    preset = preset,
                    bypassForSpeaker = false,
                    isBitPerfect = false
                )
                if (n >= warmupFrames) {
                    sumSqIn += sampleL * sampleL
                    sumSqCross += processor.outR * processor.outR
                }
            }
            val rmsIn = sqrt(sumSqIn / measureFrames)
            val rmsCross = sqrt(sumSqCross / measureFrames)
            return (20.0 * log10((rmsCross / rmsIn).coerceAtLeast(1e-9))).toFloat()
        }

        val gain100 = measureCrossfeedGainDb(100f)
        val gain700 = measureCrossfeedGainDb(700f)
        val gain1400 = measureCrossfeedGainDb(1400f)
        val gain2100 = measureCrossfeedGainDb(2100f)
        val gain2800 = measureCrossfeedGainDb(2800f)
        val gain4200 = measureCrossfeedGainDb(4200f)
        val gain5600 = measureCrossfeedGainDb(5600f)
        val gain11200 = measureCrossfeedGainDb(11200f)

        val expectedLowFreqGainDb = (20.0 * log10(preset.feedGain.toDouble())).toFloat()
        assertEquals("100 Hz crossfeed passband must match feedGain", expectedLowFreqGainDb, gain100, 0.5f)
        assertEquals("700 Hz cutoff must be -3 dB below passband", -3.01f, gain700 - gain100, 0.5f)

        val slopeOctMid = gain4200 - gain2100
        val slopeOctHigh = gain5600 - gain2800
        assertEquals("2.1 kHz -> 4.2 kHz must roll off at -6 dB/octave", -6.0f, slopeOctMid, 0.7f)
        assertEquals("2.8 kHz -> 5.6 kHz must roll off at -6 dB/octave", -6.0f, slopeOctHigh, 0.7f)
        assertTrue(
            "5.6 kHz -> 11.2 kHz must continue rolling off monotonically (delta=${gain11200 - gain5600})",
            gain11200 <= gain5600 - 5.5f
        )

        val isolationDb = gain100 - gain11200
        assertTrue("High-frequency 11.2 kHz crossfeed isolation must exceed 20 dB (actual=$isolationDb)", isolationDb > 20f)
        assertTrue("1.4 kHz must sit smoothly between 700 Hz and 2.8 kHz", gain1400 < gain700 && gain1400 > gain2800)
    }

    @Test
    fun centerPannedMonoSignal_hasZeroCombFilteringAndLinearPhase() {
        val sampleRate = 48_000
        val processor = BinauralCrossfeedProcessor()
        val testFrequencies = listOf(100f, 700f, 1667f, 3000f, 8000f)

        for (freq in testFrequencies) {
            processor.configure(sampleRate)
            val groupDelay = processor.getGroupDelaySamples()
            val totalFrames = 2048
            val input = FloatArray(totalFrames) { n ->
                sin(2.0 * PI * freq * n / sampleRate.toDouble()).toFloat()
            }

            var maxErr = 0f
            for (n in 0 until totalFrames) {
                processor.process(
                    inputL = input[n],
                    inputR = input[n],
                    channels = 2,
                    preset = CrossfeedPreset.CLASSIC_STUDIO,
                    bypassForSpeaker = false,
                    isBitPerfect = false
                )
                if (n >= groupDelay + 32) {
                    val expected = input[n - groupDelay]
                    maxErr = maxOf(maxErr, abs(processor.outL - expected), abs(processor.outR - expected))
                }
            }
            assertTrue(
                "Center-panned mono at $freq Hz must have zero comb filtering and linear phase (maxErr=$maxErr)",
                maxErr < 1e-4f
            )
        }
    }

    @Test
    fun bypassPreset_returnsBitExactZeroLatencyStereo() {
        val processor = BinauralCrossfeedProcessor()
        processor.configure(48_000)

        processor.process(
            inputL = 0.73f,
            inputR = -0.41f,
            channels = 2,
            preset = CrossfeedPreset.OFF,
            bypassForSpeaker = false,
            isBitPerfect = false
        )

        assertEquals(0.73f, processor.outL, 1e-6f)
        assertEquals(-0.41f, processor.outR, 1e-6f)
    }

    @Test
    fun speakerBypassAndBitPerfect_disableCrossfeedLeakage() {
        val processor = BinauralCrossfeedProcessor()
        processor.configure(48_000)

        repeat(256) {
            processor.process(
                inputL = 0.8f,
                inputR = 0.0f,
                channels = 2,
                preset = CrossfeedPreset.CLASSIC_STUDIO,
                bypassForSpeaker = true,
                isBitPerfect = false
            )
            assertEquals("Speaker bypass must produce zero crossfeed leakage", 0.0f, processor.outR, 1e-6f)
        }

        processor.reset()
        repeat(256) {
            processor.process(
                inputL = 0.8f,
                inputR = 0.0f,
                channels = 2,
                preset = CrossfeedPreset.CLASSIC_STUDIO,
                bypassForSpeaker = false,
                isBitPerfect = true
            )
            assertEquals("Bit-perfect mode must produce zero crossfeed leakage", 0.0f, processor.outR, 1e-6f)
        }
    }

    @Test
    fun reset_clearsInternalDelayAndFilterState() {
        val processor = BinauralCrossfeedProcessor()
        processor.configure(48_000)

        repeat(128) {
            processor.process(
                inputL = 0.9f,
                inputR = -0.9f,
                channels = 2,
                preset = CrossfeedPreset.CLASSIC_STUDIO,
                bypassForSpeaker = false,
                isBitPerfect = false
            )
        }

        processor.reset()
        processor.process(
            inputL = 0f,
            inputR = 0f,
            channels = 2,
            preset = CrossfeedPreset.CLASSIC_STUDIO,
            bypassForSpeaker = false,
            isBitPerfect = false
        )

        assertEquals(0f, processor.outL, 1e-6f)
        assertEquals(0f, processor.outR, 1e-6f)
    }
}
