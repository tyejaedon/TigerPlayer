package com.tigerplayer.service

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaveformCaptureProcessorTest {

    @Test
    fun waveformCapture_whenInactive_passesBufferThroughWithoutAnalysis() {
        val processor = WaveformCaptureProcessor()
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)

        assertFalse("Capture should be inactive by default", processor.isWaveformCaptureActive())

        val input = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())
        for (i in 0 until 256) {
            input.putShort(16000.toShort())
            input.putShort(16000.toShort())
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.getOutput()

        assertEquals("Output buffer size must match input for lossless passthrough", 1024, output.remaining())
        val waveform = processor.waveform.value
        assertTrue("Waveform must remain all zeros when capture is inactive", waveform.all { it == 0f })
    }

    @Test
    fun waveformCapture_whenActive_computesPeakAmplitudes() {
        val processor = WaveformCaptureProcessor()
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)

        processor.setWaveformCaptureActive(true)
        assertTrue(processor.isWaveformCaptureActive())

        val input = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())
        for (i in 0 until 256) {
            input.putShort(20000.toShort())
            input.putShort(20000.toShort())
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.getOutput()

        assertEquals("Output buffer size must match input", 1024, output.remaining())
        val waveform = processor.waveform.value
        assertEquals("64 buckets expected", 64, waveform.size)
        assertTrue("Waveform buckets should capture peak amplitude when active", waveform.any { it > 0f })

        // When deactivated, state is reset
        processor.setWaveformCaptureActive(false)
        assertFalse(processor.isWaveformCaptureActive())
        assertTrue("Waveform should reset to all zeros when deactivated", processor.waveform.value.all { it == 0f })
    }
}
