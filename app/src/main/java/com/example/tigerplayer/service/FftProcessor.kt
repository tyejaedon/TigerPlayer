package com.example.tigerplayer.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

private const val UPDATE_INTERVAL_MS = 24L // ~42 FPS, sweet spot for visuals vs CPU

@Singleton
@OptIn(UnstableApi::class)
class FftProcessor @Inject constructor() : BaseAudioProcessor() {
    
    // Analysis variables
    private val fftSize = 512
    private val buffer = FloatArray(fftSize)
    private var ptr = 0

    private val _bands = MutableStateFlow(List(6) { 0f })
    val bands: StateFlow<List<Float>> = _bands

    private var lastUpdateTime = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Support 16-bit and Float PCM for High-Fidelity FLAC
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        
        return if (supported) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        // 1. Prepare Output Buffer (Handled by BaseAudioProcessor)
        val outputBuffer = replaceOutputBuffer(remaining)

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
        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            val floatBuf = bufferIn.asFloatBuffer()
            while (floatBuf.hasRemaining() && ptr < fftSize) {
                // STEREO AWARENESS: Average samples across channels
                var sum = 0f
                for (i_ch in 0 until channelCount) {
                    if (floatBuf.hasRemaining()) sum += floatBuf.get()
                }
                buffer[ptr++] = sum / channelCount
                
                if (ptr >= fftSize) { 
                    performAnalysis()
                    ptr = 0 
                }
            }
        } else {
            val shortBuf = bufferIn.asShortBuffer()
            while (shortBuf.hasRemaining() && ptr < fftSize) {
                var sum = 0f
                for (i_ch in 0 until channelCount) {
                    if (shortBuf.hasRemaining()) sum += (shortBuf.get() / 32768f)
                }
                buffer[ptr++] = sum / channelCount
                
                if (ptr >= fftSize) { 
                    performAnalysis()
                    ptr = 0 
                }
            }
        }
    }

    private fun performAnalysis() {
        val result = FloatArray(6)
        val prev = _bands.value
        
        for (i in 0 until fftSize) {
            val mag = abs(buffer[i])
            val bin = (i.toFloat() / fftSize * 6).toInt().coerceIn(0, 5)
            result[bin] = max(result[bin], mag)
        }
        
        // SMOOTHING: High-integration factor (0.65) for cinematic stability
        val smoothed = List(6) { i ->
            val p = prev.getOrNull(i) ?: 0f
            (p * 0.65f + result[i] * 0.35f)
        }
        
        _bands.value = smoothed
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() { ptr = 0 }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onReset() { ptr = 0 }
}
