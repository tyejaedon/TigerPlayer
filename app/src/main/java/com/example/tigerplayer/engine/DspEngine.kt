package com.example.tigerplayer.engine

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.example.tigerplayer.utils.BiquadDesigner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@UnstableApi
@Singleton
class AdaptiveDspEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioProcessor {

    companion object {
        private const val PCM_FLOAT = 32768f
        private const val PCM_MAX_INT = 32767
        private const val PCM_MIN_INT = -32768
    }

    private var isActive = true
    private var inputEnded = false

    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET

    @Volatile private var activeFilters: List<BiquadFilter> = emptyList()
    @Volatile private var deviceCompensationFilters: List<BiquadFilter> = emptyList()

    private var isHeadphones = false
    private var agcEnvelope = 0f
    private var agcReleaseCoef = 0.00005f

    @Volatile private var pendingNodes: List<AcousticNode> = emptyList()

    init {
        detectAndApplyDeviceProfile()
    }

    fun detectAndApplyDeviceProfile() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val isBluetooth = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        val isBuiltIn = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        isHeadphones = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        val sampleRate = if (inputAudioFormat.sampleRate != AudioFormat.NOT_SET.sampleRate) inputAudioFormat.sampleRate else 48000
        agcReleaseCoef = 2.4f / sampleRate.toFloat()

        val newDeviceFilters = mutableListOf<BiquadFilter>()
        if (isBuiltIn) {
            newDeviceFilters.add(BiquadFilter(FilterType.LOW_SHELF, sampleRate, 150f, -6f, 0.707f))
            newDeviceFilters.add(BiquadFilter(FilterType.PEAKING, sampleRate, 2500f, 3f, 1.0f))
        } else if (isBluetooth) {
            newDeviceFilters.add(BiquadFilter(FilterType.HIGH_SHELF, sampleRate, 12000f, 2.5f, 0.707f))
        }

        deviceCompensationFilters = newDeviceFilters
    }

    fun updateAcousticNodes(nodes: List<AcousticNode>) {
        pendingNodes = nodes
        val sampleRate = if (inputAudioFormat.sampleRate != AudioFormat.NOT_SET.sampleRate) inputAudioFormat.sampleRate else 48000

        // Thread-safe filter recreation or parameter dispatch
        if (activeFilters.size == nodes.size) {
            nodes.forEachIndexed { index, node ->
                activeFilters[index].updateParameters(node.frequency, node.gainDb, node.qFactor, sampleRate)
            }
        } else {
            activeFilters = nodes.map { node ->
                BiquadFilter(node.filterType, sampleRate, node.frequency, node.gainDb, node.qFactor)
            }
        }
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            isActive = false
            return inputAudioFormat
        }
        val isNewSampleRate = this.inputAudioFormat.sampleRate != inputAudioFormat.sampleRate
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat
        detectAndApplyDeviceProfile()

        // FIXED: Re-apply current EQ nodes if track changes sample rates
        if (isNewSampleRate && pendingNodes.isNotEmpty()) {
            updateAcousticNodes(pendingNodes)
        }

        isActive = true
        return outputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    private fun replaceOutputBuffer(count: Int): ByteBuffer {
        if (buffer.capacity() < count) {
            buffer = ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val channels = inputAudioFormat.channelCount
        val frameSize = channels * 2
        val framesToProcess = remaining / frameSize

        if (framesToProcess == 0) return

        val requiredOutputBytes = framesToProcess * frameSize
        val outBuffer = replaceOutputBuffer(requiredOutputBytes)

        val shortBuffer = inputBuffer.asShortBuffer()
        val outShortBuffer = outBuffer.asShortBuffer()

        val localDeviceFilters = deviceCompensationFilters
        val localActiveFilters = activeFilters

        for (frame in 0 until framesToProcess) {
            var sampleL = shortBuffer.get().toFloat() / PCM_FLOAT
            var sampleR = if (channels == 2) shortBuffer.get().toFloat() / PCM_FLOAT else sampleL

            if (isHeadphones && channels == 2) {
                val mid = (sampleL + sampleR) * 0.5f
                val side = (sampleL - sampleR) * 0.5f
                val widenedSide = side * 1.15f
                sampleL = mid + widenedSide
                sampleR = mid - widenedSide
            }

            for (i in localDeviceFilters.indices) {
                sampleL = localDeviceFilters[i].process(sampleL, 0)
                if (channels == 2) sampleR = localDeviceFilters[i].process(sampleR, 1)
            }

            for (i in localActiveFilters.indices) {
                sampleL = localActiveFilters[i].process(sampleL, 0)
                if (channels == 2) sampleR = localActiveFilters[i].process(sampleR, 1)
            }

            // Real-time automatic gain limiting (prevent clipping)
            val maxPeak = max(abs(sampleL), abs(sampleR))
            if (maxPeak > agcEnvelope) {
                agcEnvelope = maxPeak
            } else {
                agcEnvelope += agcReleaseCoef * (maxPeak - agcEnvelope)
            }

            val reduction = if (agcEnvelope > 0.85f) 0.85f / agcEnvelope else 1f
            sampleL *= reduction
            sampleR *= reduction

            // Soft-clipping saturation function
            val clampL = sampleL.coerceIn(-1.25f, 1.25f)
            sampleL = if (abs(clampL) > 1f) sign(clampL) else clampL - ((clampL * clampL * clampL) / 3f)

            val clampR = sampleR.coerceIn(-1.25f, 1.25f)
            sampleR = if (abs(clampR) > 1f) sign(clampR) else clampR - ((clampR * clampR * clampR) / 3f)

            outShortBuffer.put((sampleL * PCM_MAX_INT).toInt().coerceIn(PCM_MIN_INT, PCM_MAX_INT).toShort())
            if (channels == 2) {
                outShortBuffer.put((sampleR * PCM_MAX_INT).toInt().coerceIn(PCM_MIN_INT, PCM_MAX_INT).toShort())
            }
        }

        inputBuffer.position(inputBuffer.position() + requiredOutputBytes)
        outBuffer.limit(requiredOutputBytes)
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        activeFilters.forEach { it.reset() }
        deviceCompensationFilters.forEach { it.reset() }
        agcEnvelope = 0f // FIXED: Clear AGC volume reduction upon seeking/re-buffering
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
    }
}

enum class FilterType { LOW_SHELF, PEAKING, HIGH_SHELF }

data class AcousticNode(
    val id: String, val label: String, val filterType: FilterType,
    val frequency: Float, val gainDb: Float, val qFactor: Float
)

/**
 * 🔒 THREAD-SAFE IMMUTABLE COEFFICIENT SWAPPER
 * This ensures parameters calculated on the CPU thread do not write-race
 * or split values while the Audio Processor is executing.
 */
class BiquadFilter(
    val type: FilterType, val sampleRate: Int,
    initialFreq: Float, initialGain: Float, initialQ: Float
) {
    class Coefficients(
        val b0: Float, val b1: Float, val b2: Float,
        val a1: Float, val a2: Float
    )

    // Thread-shared pointer updated atomically
    @Volatile private var currentTargetCoeffs: Coefficients

    // Render-thread local parameters
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private val z1 = FloatArray(2)
    private val z2 = FloatArray(2)

    init {
        val raw = BiquadDesigner.design(type, initialFreq, initialGain, initialQ, sampleRate.toFloat())
        val a0 = raw.a0.toFloat()
        currentTargetCoeffs = Coefficients(
            b0 = (raw.b0 / a0).toFloat(),
            b1 = (raw.b1 / a0).toFloat(),
            b2 = (raw.b2 / a0).toFloat(),
            a1 = (raw.a1 / a0).toFloat(),
            a2 = (raw.a2 / a0).toFloat()
        )
        // Instant pop-free initialization
        b0 = currentTargetCoeffs.b0
        b1 = currentTargetCoeffs.b1
        b2 = currentTargetCoeffs.b2
        a1 = currentTargetCoeffs.a1
        a2 = currentTargetCoeffs.a2
    }

    fun updateParameters(freq: Float, gainDb: Float, q: Float, sampleRate: Int) {
        val raw = BiquadDesigner.design(type, freq, gainDb, q, sampleRate.toFloat())
        val a0 = raw.a0.toFloat()
        // Atomic Volatile Swap
        currentTargetCoeffs = Coefficients(
            b0 = (raw.b0 / a0).toFloat(),
            b1 = (raw.b1 / a0).toFloat(),
            b2 = (raw.b2 / a0).toFloat(),
            a1 = (raw.a1 / a0).toFloat(),
            a2 = (raw.a2 / a0).toFloat()
        )
    }

    fun process(sample: Float, channel: Int): Float {
        val target = currentTargetCoeffs // Read volatile reference exactly once per process frame

        // Seamless real-time filter parameter interpolation (smoothing coefficient transitions)
        val smooth = 0.0025f
        b0 += (target.b0 - b0) * smooth
        b1 += (target.b1 - b1) * smooth
        b2 += (target.b2 - b2) * smooth
        a1 += (target.a1 - a1) * smooth
        a2 += (target.a2 - a2) * smooth

        val out = b0 * sample + z1[channel]
        z1[channel] = b1 * sample - a1 * out + z2[channel]
        z2[channel] = b2 * sample - a2 * out
        return out
    }

    fun reset() {
        z1.fill(0f)
        z2.fill(0f)
    }
}