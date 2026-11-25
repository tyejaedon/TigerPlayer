package com.tigerplayer.service

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.tigerplayer.data.local.ReplayGainMode
import com.tigerplayer.engine.AcousticEnvironmentMode
import com.tigerplayer.engine.AdaptiveDspEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GaplessTransitionTest {

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

    private fun buildContinuousCosineBuffer(
        sampleRate: Int,
        frequencyHz: Float,
        startFrame: Int,
        frames: Int,
        amplitude: Float
    ): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val frameIndex = startFrame + i
            val t = frameIndex.toDouble() / sampleRate.toDouble()
            val sample = (cos(2.0 * PI * frequencyHz * t) * amplitude).coerceIn(-0.98, 0.98)
            val pcm = (sample * Short.MAX_VALUE).toInt().toShort()
            buffer.putShort(pcm)
            buffer.putShort(pcm)
        }
        buffer.flip()
        return buffer
    }

    private fun drainShorts(buffer: ByteBuffer): ShortArray {
        val copy = buffer.duplicate().order(ByteOrder.nativeOrder())
        val count = copy.remaining() / 2
        val result = ShortArray(count)
        for (i in 0 until count) {
            result[i] = copy.getShort()
        }
        return result
    }

    @Test
    fun configure_withUnchangedFormat_returnsIdenticalOutputFormatAndDoesNotFlush() {
        val context = mockHeadphonesContext()
        val engine = spyk(AdaptiveDspEngine(context))
        val format = AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)

        val firstOutFormat = engine.configure(format)
        engine.setAcousticEnvironmentMode(AcousticEnvironmentMode.CONCERT_HALL)

        val track1 = buildContinuousCosineBuffer(44_100, 440f, startFrame = 0, frames = 1024, amplitude = 0.6f)
        engine.queueInput(track1)
        engine.getOutput()

        val secondOutFormat = engine.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        assertEquals(firstOutFormat, secondOutFormat)
        verify(exactly = 0) { engine.flush() }

        // Same rate and channel count with 24-bit input must also keep output format identical without flushing
        val thirdOutFormat = engine.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_24BIT))
        assertEquals(firstOutFormat, thirdOutFormat)
        verify(exactly = 0) { engine.flush() }
        // init() + first configure() = 2 device profile reads; repeated same-rate/channel configures must short-circuit
        verify(exactly = 2) { context.getSystemService(Context.AUDIO_SERVICE) }
    }

    @Test
    fun consecutivePcmBuffers_sameFormat_preserveSampleCountWithNoInsertedZeros() {
        val engine = AdaptiveDspEngine(mockHeadphonesContext())
        val sampleRate = 44_100
        val framesPerTrack = 2048
        val format = AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)

        engine.configure(format)
        engine.setIsLosslessStream(true)

        // Cosine wave starts at peak (+0.7 amplitude) so frame 0 is strongly non-zero
        val track1 = buildContinuousCosineBuffer(sampleRate, 220f, startFrame = 0, frames = framesPerTrack, amplitude = 0.7f)
        engine.queueInput(track1)
        val out1 = drainShorts(engine.getOutput())

        // Simulate Media3 format check at gapless track boundary
        engine.configure(format)

        val track2 = buildContinuousCosineBuffer(sampleRate, 220f, startFrame = framesPerTrack, frames = framesPerTrack, amplitude = 0.7f)
        engine.queueInput(track2)
        val out2 = drainShorts(engine.getOutput())

        engine.queueEndOfStream()
        val eosTail = engine.getOutput()

        assertEquals("Track 1 output sample count must match input", framesPerTrack * 2, out1.size)
        assertEquals("Track 2 output sample count must match input", framesPerTrack * 2, out2.size)
        assertEquals("EOS must not append extra padding frames", 0, eosTail.remaining())
        assertTrue("Engine should report ended after EOS", engine.isEnded())

        // Verify no leading zeros were inserted at start of Track 1 or boundary of Track 2
        assertTrue("Track 1 sample 0 must not be zero-padded (actual=${out1[0]})", abs(out1[0].toInt()) > 10_000)

        var boundaryZeros = 0
        for (i in 0 until 64) {
            if (out2[i * 2].toInt() == 0 && out2[i * 2 + 1].toInt() == 0) {
                boundaryZeros++
            }
        }
        assertTrue("Track boundary must not inject silence frames (zeros=$boundaryZeros)", boundaryZeros <= 2)
    }

    @Test
    fun replayGainChangeAtTrackTransition_rampsPerSampleWithoutDiscontinuity() {
        val engine = AdaptiveDspEngine(mockHeadphonesContext())
        val sampleRate = 48_000
        // 2000 frames @ 240 Hz / 48 kHz = exact 10.0 cycles, placing the boundary right at cosine peak (+1.0)
        val framesPerTrack = 2000
        val format = AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)

        engine.configure(format)
        engine.setIsLosslessStream(true)
        engine.setReplayGainConfig(
            mode = ReplayGainMode.TRACK,
            preampDb = 0f,
            preventClipping = true,
            isBitPerfect = false
        )

        // Track 1 at -6 dB ReplayGain (~0.501x)
        engine.setReplayGainTrackData(
            trackGainDb = -6.0,
            albumGainDb = -6.0,
            trackPeak = 1.0,
            albumPeak = 1.0,
            isSequentialAlbumPlay = false
        )

        val track1 = buildContinuousCosineBuffer(sampleRate, 240f, startFrame = 0, frames = framesPerTrack, amplitude = 0.85f)
        engine.queueInput(track1)
        val out1 = drainShorts(engine.getOutput())

        // Transition to Track 2 at 0 dB ReplayGain (1.0x) right at the positive cosine peak
        engine.configure(format)
        engine.setReplayGainTrackData(
            trackGainDb = 0.0,
            albumGainDb = 0.0,
            trackPeak = 1.0,
            albumPeak = 1.0,
            isSequentialAlbumPlay = false
        )

        val track2 = buildContinuousCosineBuffer(sampleRate, 240f, startFrame = framesPerTrack, frames = framesPerTrack, amplitude = 0.85f)
        engine.queueInput(track2)
        val out2 = drainShorts(engine.getOutput())

        // An un-ramped +6 dB step at cosine peak (0.85 * 32767 ≈ 27,852) would jump ~13,900 PCM units.
        // With per-sample ramping, the step at frame 0 is bounded well under 1,000 PCM units.
        val lastTrack1Left = out1[out1.size - 2].toInt()
        val firstTrack2Left = out2[0].toInt()
        val boundaryDelta = abs(firstTrack2Left - lastTrack1Left)
        assertTrue(
            "Boundary transition must ramp smoothly without click discontinuity (boundaryDelta=$boundaryDelta)",
            boundaryDelta < 1_000
        )

        var maxRampDelta = 0
        for (frame in 1 until 512) {
            val delta = abs(out2[frame * 2].toInt() - out2[(frame - 1) * 2].toInt())
            if (delta > maxRampDelta) maxRampDelta = delta
        }
        assertTrue(
            "Per-sample ReplayGain ramp must remain smooth across initial frames (maxRampDelta=$maxRampDelta)",
            maxRampDelta < 1_500
        )

        // Verify gain has converged to 0 dB (~0.85 * 32767 ≈ 27,852) by the tail of Track 2
        var tailPeak = 0
        for (frame in (framesPerTrack - 256) until framesPerTrack) {
            val sample = abs(out2[frame * 2].toInt())
            if (sample > tailPeak) tailPeak = sample
        }
        assertTrue("Track 2 tail should reach full 0 dB ReplayGain level (tailPeak=$tailPeak)", tailPeak > 25_000)
    }
}
