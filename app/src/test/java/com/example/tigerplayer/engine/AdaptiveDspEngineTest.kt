package com.example.tigerplayer.engine

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
}
