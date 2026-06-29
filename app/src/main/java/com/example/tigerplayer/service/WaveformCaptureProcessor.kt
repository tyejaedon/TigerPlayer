package com.example.tigerplayer.service

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.math.abs

@OptIn(UnstableApi::class)
class WaveformCaptureProcessor @Inject constructor() : BaseAudioProcessor() {

    private val _waveform = MutableStateFlow(List(64) { 0f })
    val waveform: StateFlow<List<Float>> = _waveform

    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL_MS = 40L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // We accept the input format as is and return it as the output format (passthrough)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // 1. ALWAYS provide output to avoid audio dropouts
        // replaceOutputBuffer allocates/resizes the internal buffer and copies data
        val buffer = replaceOutputBuffer(remaining)

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            // 2. ANALYZE a duplicate to avoid moving the original buffer's position
            val analysisBuffer = inputBuffer.duplicate().order(java.nio.ByteOrder.nativeOrder())
            processWaveform(analysisBuffer)
            lastUpdateTime = currentTime
        }

        // 3. Move the data to the output buffer for the next processor in the chain
        buffer.put(inputBuffer)
        buffer.flip()
    }

    private fun processWaveform(buffer: java.nio.ByteBuffer) {
        val numBins = 64
        val amplitudes = FloatArray(numBins)
        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)

        if (inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            val floatBuffer = buffer.asFloatBuffer()
            val samplesSize = floatBuffer.remaining()
            if (samplesSize == 0) return

            val chunkSize = (samplesSize / numBins).coerceAtLeast(1)
            for (i in 0 until numBins) {
                var maxVal = 0f
                for (j in 0 until chunkSize) {
                    val baseIndex = i * chunkSize + j
                    // STEREO AWARENESS: Consider all channels in this sample position
                    for (c in 0 until channelCount) {
                        val index = baseIndex + c
                        if (index < samplesSize) {
                            val sample = abs(floatBuffer.get(index))
                            if (sample > maxVal) maxVal = sample
                        }
                    }
                }
                amplitudes[i] = maxVal.coerceIn(0f, 1f)
            }
        } else {
            val shortBuffer = buffer.asShortBuffer()
            val samplesSize = shortBuffer.remaining()
            if (samplesSize == 0) return

            val chunkSize = (samplesSize / numBins).coerceAtLeast(1)
            for (i in 0 until numBins) {
                var maxVal = 0f
                for (j in 0 until chunkSize) {
                    val baseIndex = i * chunkSize + j
                    for (c in 0 until channelCount) {
                        val index = baseIndex + c
                        if (index < samplesSize) {
                            val sample = abs(shortBuffer.get(index).toFloat())
                            if (sample > maxVal) maxVal = sample
                        }
                    }
                }
                amplitudes[i] = (maxVal / 32768f).coerceIn(0f, 1f)
            }
        }

        // SMOOTHING: High-integration factor (0.75) to prevent visual jitter
        val prev = _waveform.value
        val smoothed = List(numBins) { i ->
            val p = prev.getOrNull(i) ?: 0f
            (p * 0.75f + amplitudes[i] * 0.25f)
        }

        _waveform.value = smoothed
    }

    override fun onFlush() {
        lastUpdateTime = 0L
    }

    override fun onReset() {
        lastUpdateTime = 0L
    }
}