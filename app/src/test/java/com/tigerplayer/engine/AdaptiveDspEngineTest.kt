package com.tigerplayer.engine

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import io.mockk.every
import io.mockk.mockk
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDspEngineTest {

    private fun mockContext(): Context {
        val context = mockk<Context>()
        val audioManager = mockk<AudioManager>()
        val builtInSpeaker = mockk<AudioDeviceInfo>()

        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(builtInSpeaker)
        every { builtInSpeaker.type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        every { builtInSpeaker.productName } returns "Built-in speaker"
        return context
    }

    private fun mockHeadphonesContext(): Context {
        val context = mockk<Context>()
        val audioManager = mockk<AudioManager>()
        val wiredHeadphones = mockk<AudioDeviceInfo>()

        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(wiredHeadphones)
        every { wiredHeadphones.type } returns AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        every { wiredHeadphones.productName } returns "Wired Studio Headphones"
        return context
    }

    private fun buildStereoSineBuffer(sampleRate: Int, frequencyHz: Float, frames: Int, amplitude: Float): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (index in 0 until frames) {
            val t = index.toDouble() / sampleRate.toDouble()
            val sample = (sin(2.0 * PI * frequencyHz * t) * amplitude)
                .coerceIn(-0.98, 0.98)
            val pcm = (sample * Short.MAX_VALUE).toInt().toShort()
            buffer.putShort(pcm)
            buffer.putShort(pcm)
        }
        buffer.flip()
        return buffer
    }

    @Test
    fun queueInput_writesBoundedOutput_and_endOfStreamTransitionsToEnded() {
        val context = mockContext()
        val engine = AdaptiveDspEngine(context)

        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        engine.configure(format)

        val inputBytes = 8 
        val input = ByteBuffer.allocateDirect(inputBytes).order(ByteOrder.nativeOrder())
        input.putShort(1200.toShort())
        input.putShort((-1200).toShort())
        input.putShort(800.toShort())
        input.putShort((-800).toShort())
        input.flip()

        engine.queueInput(input)
        val output = engine.getOutput()

        val expectedOutputBytes = 8
        assertEquals("expected PCM 16-bit output size (2 frames * 2 channels * 2 bytes)", expectedOutputBytes, output.remaining())
        assertFalse("engine should not be ended before EOS", engine.isEnded())

        engine.queueEndOfStream()
        engine.getOutput() 

        assertTrue("engine should report ended after EOS is queued and output is drained", engine.isEnded())
    }

    @Test
    fun audioReactiveFrame_usesRealSixBandFilterBank() {
        val engine = AdaptiveDspEngine(mockContext())
        val sampleRate = 48_000
        engine.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))

        val lowTone = buildStereoSineBuffer(sampleRate, frequencyHz = 60f, frames = 12_288, amplitude = 0.72f)
        engine.queueInput(lowTone)
        engine.getOutput()
        val lowFrame = engine.audioReactiveFrame.value

        assertEquals("six spectral bands expected", 6, lowFrame.spectralBands.size)
        assertTrue(
            "low-tone energy should dominate low bins",
            lowFrame.spectralBands[0] > lowFrame.spectralBands[4]
        )

        engine.flush()

        val highTone = buildStereoSineBuffer(sampleRate, frequencyHz = 6_000f, frames = 12_288, amplitude = 0.72f)
        engine.queueInput(highTone)
        engine.getOutput()
        val highFrame = engine.audioReactiveFrame.value

        assertTrue(
            "high-tone energy should dominate upper bins",
            highFrame.spectralBands[4] > highFrame.spectralBands[1]
        )
        assertTrue(
            "spectral centroid should increase for high-frequency input",
            highFrame.centroid > lowFrame.centroid
        )
    }

    private fun maxOutputPeak(buffer: ByteBuffer): Float {
        var maxPeak = 0f
        val copy = buffer.duplicate().order(ByteOrder.nativeOrder())
        while (copy.remaining() >= 2) {
            val sample = abs(copy.getShort().toFloat() / 32767f)
            maxPeak = max(maxPeak, sample)
        }
        return maxPeak
    }

    @Test
    fun losslessStream_withFlatEq_experiencesZeroGainReductionAt0dBFS() {
        val engine = AdaptiveDspEngine(mockHeadphonesContext())
        val sampleRate = 48_000
        engine.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        engine.setIsLosslessStream(true)
        engine.updateAcousticNodes(emptyList())

        // Audio peaking near 0 dBFS (0.98f amplitude)
        val input = buildStereoSineBuffer(sampleRate, frequencyHz = 1_000f, frames = 2_048, amplitude = 0.98f)
        engine.queueInput(input)
        val output = engine.getOutput()

        val peak = maxOutputPeak(output)
        // With the old 0.82f (-1.72 dBFS) limiter, peak was squashed to ~0.82f.
        // With transparent lossless bypass, peak is preserved near 0.98f (0 dB gain reduction).
        assertTrue("Lossless flat stream must preserve dynamic range without compression (peak=$peak)", peak >= 0.96f)
    }

    @Test
    fun losslessStream_withNegativeEqCuts_bypassesLimiterAndPreservesDynamics() {
        val engine = AdaptiveDspEngine(mockHeadphonesContext())
        val sampleRate = 48_000
        engine.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        engine.setIsLosslessStream(true)

        // Negative cut band (-4 dB at 10 kHz)
        val cutBand = AcousticNode("cut", "Treble Cut", FilterType.HIGH_SHELF, 10_000f, -4f, 0.707f)
        engine.updateAcousticNodes(listOf(cutBand))

        // Low frequency tone (100 Hz) outside the cut region
        val input = buildStereoSineBuffer(sampleRate, frequencyHz = 100f, frames = 2_048, amplitude = 0.95f)
        engine.queueInput(input)
        val output = engine.getOutput()

        val peak = maxOutputPeak(output)
        assertTrue("Lossless stream with negative EQ cut must not trigger AGC compression (peak=$peak)", peak >= 0.93f)
    }

    @Test
    fun unboostedAudio_near0dBFS_preservesDynamicRangeWithoutPumping() {
        val engine = AdaptiveDspEngine(mockHeadphonesContext())
        val sampleRate = 48_000
        engine.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        engine.setIsLosslessStream(false) // Non-lossless stream
        engine.updateAcousticNodes(emptyList())

        // Audio peaking at 0.95f (-0.45 dBFS)
        val input = buildStereoSineBuffer(sampleRate, frequencyHz = 1_000f, frames = 2_048, amplitude = 0.95f)
        engine.queueInput(input)
        val output = engine.getOutput()

        val peak = maxOutputPeak(output)
        // Previously clamped at 0.82f. Now transparent up to 1.0f (0.0 dBFS).
        assertTrue("Signals below 1.0f (0 dBFS) must experience 0 dB gain reduction (peak=$peak)", peak >= 0.93f)
    }

    @Test
    fun positiveEqBoost_exceeding0dBFS_isCleanlyLimitedWithoutClipping() {
        val engine = AdaptiveDspEngine(mockHeadphonesContext())
        val sampleRate = 48_000
        engine.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        engine.setIsLosslessStream(true)

        // Positive EQ boost that causes imminent digital overs
        val boostBand = AcousticNode("boost", "Mid Boost", FilterType.PEAKING, 1_000f, 6f, 1.0f)
        engine.updateAcousticNodes(listOf(boostBand))

        val input = buildStereoSineBuffer(sampleRate, frequencyHz = 1_000f, frames = 2_048, amplitude = 0.98f)
        engine.queueInput(input)
        val output = engine.getOutput()

        val peak = maxOutputPeak(output)
        // Limiter catches overs cleanly: peak must not exceed 1.0f (32767)
        assertTrue("Output peak under positive boost must not exceed 1.0f ceiling (peak=$peak)", peak <= 1.0001f)
        assertTrue("Output peak must be non-zero (peak=$peak)", peak > 0.5f)
    }

    @Test
    fun limiterRelease_recoversRapidlyWithoutAudiblePumping() {
        val engine = AdaptiveDspEngine(mockHeadphonesContext())
        val sampleRate = 48_000
        engine.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        engine.setIsLosslessStream(false)

        // Apply positive boost to trigger limiter
        val boostBand = AcousticNode("boost", "Bass Boost", FilterType.PEAKING, 200f, 6f, 1.0f)
        engine.updateAcousticNodes(listOf(boostBand))

        // Burst of loud audio triggering limiter
        val loudBurst = buildStereoSineBuffer(sampleRate, frequencyHz = 200f, frames = 1_024, amplitude = 0.98f)
        engine.queueInput(loudBurst)
        engine.getOutput()

        // Remove boost (flat EQ) and feed normal 0.5f audio to verify 50ms fast recovery
        engine.updateAcousticNodes(emptyList())
        val normalAudio = buildStereoSineBuffer(sampleRate, frequencyHz = 1_000f, frames = 3_840, amplitude = 0.50f)
        engine.queueInput(normalAudio)
        val normalOutput = engine.getOutput()

        val recoveredPeak = maxOutputPeak(normalOutput)
        // Within 80ms (3840 frames @ 48kHz), gain should recover cleanly back to near 0.50f (no 250ms pumping)
        assertTrue("Limiter must rapidly release within 80ms without lingering ducking (peak=$recoveredPeak)", recoveredPeak >= 0.47f)
    }
}
