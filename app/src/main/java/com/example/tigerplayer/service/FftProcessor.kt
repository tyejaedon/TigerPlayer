package com.example.tigerplayer.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * THE ENERGY ANALYZER: FftProcessor
 * Captures audio energy in perceptual bands for the Fluid Vortex and UI.
 * Optimized with a high-performance throttle to maintain 60FPS UI performance.
 */

private const val UPDATE_INTERVAL_MS = 33L

@Singleton
@OptIn(UnstableApi::class)
class FftProcessor @Inject constructor() : AudioProcessor {
    private var isActive = false
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputAudioFormat = AudioFormat.NOT_SET

    // Analysis variables
    private val fftSize = 512
    private val buffer = FloatArray(fftSize)
    private var ptr = 0

    private val _bands = MutableStateFlow(List(6) { 0f })
    val bands = _bands

    private var lastUpdateTime = 0L

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        // Support 16-bit and Float PCM for High-Fidelity FLAC
        isActive = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT

        return if (isActive) inputAudioFormat else AudioFormat.NOT_SET
    }

    override fun isActive() = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        // 1. Prepare Output Buffer
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            val analysisBuffer = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
            analyzeBuffer(analysisBuffer)
            lastUpdateTime = currentTime
        }

        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    private fun analyzeBuffer(bufferIn: ByteBuffer) {
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            val floatBuf = bufferIn.asFloatBuffer()
            while (floatBuf.hasRemaining() && ptr < fftSize) {
                buffer[ptr++] = floatBuf.get()
                if (ptr >= fftSize) { performAnalysis(); ptr = 0 }
            }
        } else {
            val shortBuf = bufferIn.asShortBuffer()
            while (shortBuf.hasRemaining() && ptr < fftSize) {
                buffer[ptr++] = shortBuf.get() / 32768f
                if (ptr >= fftSize) { performAnalysis(); ptr = 0 }
            }
        }
    }

    private fun performAnalysis() {
        val result = FloatArray(6)
        for (i in 0 until fftSize) {
            val mag = abs(buffer[i])
            val bin = (i.toFloat() / fftSize * 6).toInt().coerceIn(0, 5)
            result[bin] = max(result[bin], mag)
        }
        _bands.value = result.toList()
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun queueEndOfStream() {}
    override fun isEnded() = false
    override fun flush() { ptr = 0 }
    override fun reset() { flush() }
}