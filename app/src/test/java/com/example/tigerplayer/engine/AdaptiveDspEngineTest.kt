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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDspEngineTest {

    @Test
    fun queueInput_writesBoundedOutput_and_endOfStreamTransitionsToEnded() {
        val context = mockk<Context>()
        val audioManager = mockk<AudioManager>()
        val builtInSpeaker = mockk<AudioDeviceInfo>()

        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(builtInSpeaker)
        every { builtInSpeaker.type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

        val engine = AdaptiveDspEngine(context)

        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        engine.configure(format)

        val inputBytes = 8 // 2 stereo frames, 16-bit PCM
        val input = ByteBuffer.allocateDirect(inputBytes).order(ByteOrder.nativeOrder())
        input.putShort(1200)
        input.putShort(-1200)
        input.putShort(800)
        input.putShort(-800)
        input.flip()

        engine.queueInput(input)
        val output = engine.getOutput()

        assertTrue("output should not overflow input size", output.remaining() <= inputBytes)
        assertEquals("expected frame-size parity for passthrough processing", inputBytes, output.remaining())
        assertFalse("engine should not be ended before EOS", engine.isEnded())

        engine.queueEndOfStream()
        engine.getOutput() // drain remaining output buffer if any

        assertTrue("engine should report ended after EOS is queued and output is drained", engine.isEnded())
    }
}


