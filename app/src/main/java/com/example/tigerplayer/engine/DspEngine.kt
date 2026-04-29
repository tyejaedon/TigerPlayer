package com.example.tigerplayer.engine.dsp

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
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

    private var isActive = true
    private var inputEnded = false // 🔥 THE FIX: Safely tracks the actual end of the audio stream

    private var pendingOutputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET

    private val filters = mutableListOf<BiquadFilter>()
    private val deviceCompensationFilters = mutableListOf<BiquadFilter>()

    private var isHeadphones = false
    private var agcEnvelope = 0f

    init {
        detectAndApplyDeviceProfile()
    }

    fun detectAndApplyDeviceProfile() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        deviceCompensationFilters.clear()

        val isBluetooth = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        val isBuiltIn = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        isHeadphones = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        val sampleRate = if (inputAudioFormat.sampleRate != AudioFormat.NOT_SET.sampleRate) inputAudioFormat.sampleRate else 48000

        if (isBuiltIn) {
            deviceCompensationFilters.add(BiquadFilter(FilterType.LOW_SHELF, sampleRate, 150f, -6f, 0.707f))
            deviceCompensationFilters.add(BiquadFilter(FilterType.PEAKING, sampleRate, 2500f, 3f, 1.0f))
        } else if (isBluetooth) {
            deviceCompensationFilters.add(BiquadFilter(FilterType.HIGH_SHELF, sampleRate, 12000f, 2.5f, 0.707f))
        }
    }

    fun updateAcousticNodes(nodes: List<AcousticNode>) {
        val sampleRate = if (inputAudioFormat.sampleRate != AudioFormat.NOT_SET.sampleRate) inputAudioFormat.sampleRate else 48000

        if (filters.size == nodes.size) {
            nodes.forEachIndexed { index, node ->
                filters[index].updateParameters(node.frequency, node.gainDb, node.qFactor)
            }
        } else {
            filters.clear()
            nodes.forEach { node ->
                filters.add(BiquadFilter(node.filterType, sampleRate, node.frequency, node.gainDb, node.qFactor))
            }
        }
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            isActive = false
            return inputAudioFormat
        }

        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        detectAndApplyDeviceProfile()
        isActive = true
        return outputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val shortBuffer = inputBuffer.asShortBuffer()
        val outShortBuffer = outputBuffer.asShortBuffer()
        val channels = inputAudioFormat.channelCount

        while (shortBuffer.remaining() >= channels) {

            var sampleL = shortBuffer.get().toFloat() / 32768f
            var sampleR = if (channels == 2) shortBuffer.get().toFloat() / 32768f else sampleL

            if (isHeadphones && channels == 2) {
                val mid = (sampleL + sampleR) * 0.5f
                val side = (sampleL - sampleR) * 0.5f
                val widenedSide = side * 1.15f
                sampleL = mid + widenedSide
                sampleR = mid - widenedSide
            }

            for (i in deviceCompensationFilters.indices) {
                sampleL = deviceCompensationFilters[i].process(sampleL, 0)
                if (channels == 2) sampleR = deviceCompensationFilters[i].process(sampleR, 1)
            }

            for (i in filters.indices) {
                sampleL = filters[i].process(sampleL, 0)
                if (channels == 2) sampleR = filters[i].process(sampleR, 1)
            }

            val maxPeak = max(abs(sampleL), abs(sampleR))

            if (maxPeak > agcEnvelope) {
                agcEnvelope = maxPeak
            } else {
                agcEnvelope += 0.00005f * (maxPeak - agcEnvelope)
            }

            val reduction = if (agcEnvelope > 0.85f) 0.85f / agcEnvelope else 1f
            sampleL *= reduction
            sampleR *= reduction

            val clampL = sampleL.coerceIn(-1.25f, 1.25f)
            sampleL = if (abs(clampL) > 1f) sign(clampL) else clampL - (clampL.pow(3) / 3f)

            val clampR = sampleR.coerceIn(-1.25f, 1.25f)
            sampleR = if (abs(clampR) > 1f) sign(clampR) else clampR - (clampR.pow(3) / 3f)

            outShortBuffer.put((sampleL * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            if (channels == 2) {
                outShortBuffer.put((sampleR * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }

        inputBuffer.position(inputBuffer.position() + remaining)

        // 🔥 THE FIX: Accurately set the byte limit based on the exact number of shorts processed
        outputBuffer.limit(outShortBuffer.position() * 2)
        pendingOutputBuffer = outputBuffer
    }

    // 🔥 THE FIX: Explicitly mark the stream as ended only when ExoPlayer tells us it is
    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = pendingOutputBuffer
        pendingOutputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    // 🔥 THE FIX: Prevents ExoPlayer from thinking the song is over mid-playback
    override fun isEnded(): Boolean {
        return inputEnded && pendingOutputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer.clear()
        pendingOutputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false // 🔥 THE FIX: Reset the input flag so the next track plays successfully
        filters.forEach { it.reset() }
        deviceCompensationFilters.forEach { it.reset() }
    }

    override fun reset() {
        flush()
    }
}

enum class FilterType { LOW_SHELF, PEAKING, HIGH_SHELF }

class BiquadFilter(
    val type: FilterType,
    val sampleRate: Int,
    initialFreq: Float,
    initialGain: Float,
    initialQ: Float
) {
    private var targetB0 = 0f; private var targetB1 = 0f; private var targetB2 = 0f
    private var targetA1 = 0f; private var targetA2 = 0f

    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f

    private val z1 = FloatArray(2)
    private val z2 = FloatArray(2)

    init {
        updateParameters(initialFreq, initialGain, initialQ)
        b0 = targetB0; b1 = targetB1; b2 = targetB2; a1 = targetA1; a2 = targetA2
    }

    fun updateParameters(freq: Float, gainDb: Float, q: Float) {
        val coeffs = BiquadDesigner.design(
            node = AcousticNode(
                id = "internal_calc",
                label = "calc",
                filterType = type,
                frequency = freq,
                gainDb = gainDb,
                qFactor = q,
                color = androidx.compose.ui.graphics.Color.Transparent
            ),
            sampleRate = sampleRate.toFloat()
        )

        val a0 = coeffs.a0.toFloat()
        targetB0 = (coeffs.b0 / a0).toFloat()
        targetB1 = (coeffs.b1 / a0).toFloat()
        targetB2 = (coeffs.b2 / a0).toFloat()
        targetA1 = (coeffs.a1 / a0).toFloat()
        targetA2 = (coeffs.a2 / a0).toFloat()
    }

    fun process(sample: Float, channel: Int): Float {
        val smooth = 0.002f

        b0 += (targetB0 - b0) * smooth
        b1 += (targetB1 - b1) * smooth
        b2 += (targetB2 - b2) * smooth
        a1 += (targetA1 - a1) * smooth
        a2 += (targetA2 - a2) * smooth

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

data class AcousticNode(
    val id: String,
    val label: String,
    val filterType: FilterType,
    val frequency: Float,
    val gainDb: Float,
    val qFactor: Float,
    val color: androidx.compose.ui.graphics.Color
)