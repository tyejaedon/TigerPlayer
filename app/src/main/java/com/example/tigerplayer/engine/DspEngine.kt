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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

data class AudioReactiveFrame(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val energy: Float = 0f,
    val centroid: Float = 0.5f,
    val flux: Float = 0f
)

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
    @Volatile private var acousticEnvironmentMode: AcousticEnvironmentMode = AcousticEnvironmentMode.STUDIO
    @Volatile private var prismMode: PrismMode = PrismMode.BYPASS
    @Volatile private var prismTargetMix = PrismMixLevels()

    private var prismCurrentVocals = 1f
    private var prismCurrentBeats = 1f
    private var prismCurrentInstruments = 1f
    private val prismIsolator = PrismIsolator()

    private val _audioReactiveFrame = MutableStateFlow(AudioReactiveFrame())
    val audioReactiveFrame: StateFlow<AudioReactiveFrame> = _audioReactiveFrame.asStateFlow()

    private var vinylNoiseSeed = 0x13579BDF.toInt()
    private var vinylNoiseDc = 0f
    private val hallProcessor = SchroederHallProcessor()

    // Lightweight real-time spectrum approximation for visualizers.
    private var lpBass = 0f
    private var lpMid = 0f
    private var prevEnergy = 0f
    private var analysisFrameCounter = 0
    private var bassAccumulator = 0f
    private var midAccumulator = 0f
    private var trebleAccumulator = 0f
    private var energyAccumulator = 0f

    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    private fun nextNoise(): Float {
        vinylNoiseSeed = vinylNoiseSeed * 1664525 + 1013904223
        val normalized = ((vinylNoiseSeed ushr 8) and 0x00FFFFFF) / 16777215f
        return normalized * 2f - 1f
    }

    private fun applyVinylWarmth(sample: Float): Float {
        val driven = (sample * 1.16f).coerceIn(-1.2f, 1.2f)
        val softSaturation = driven - ((driven * driven * driven) / 3f)
        val secondHarmonic = driven * abs(driven) * 0.055f

        val rawNoise = nextNoise() * 0.0016f
        vinylNoiseDc += (rawNoise - vinylNoiseDc) * 0.004f
        val hiss = rawNoise - vinylNoiseDc

        return (sample * 0.84f + softSaturation * 0.13f + secondHarmonic + hiss).coerceIn(-1.15f, 1.15f)
    }

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

    fun setAcousticEnvironmentMode(mode: AcousticEnvironmentMode) {
        acousticEnvironmentMode = mode
    }

    fun getAcousticEnvironmentMode(): AcousticEnvironmentMode = acousticEnvironmentMode

    fun setPrismMode(mode: PrismMode) {
        prismMode = mode
    }

    fun updatePrismMix(vocals: Float, beats: Float, instruments: Float) {
        prismTargetMix = PrismMixLevels(
            vocals = vocals.coerceIn(0f, 1f),
            beats = beats.coerceIn(0f, 1f),
            instruments = instruments.coerceIn(0f, 1f)
        )
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
        hallProcessor.configure(inputAudioFormat.sampleRate.coerceAtLeast(8000))
        prismIsolator.configure(inputAudioFormat.sampleRate.coerceAtLeast(8000))

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
        val localEnvironmentMode = acousticEnvironmentMode
        val localPrismMode = prismMode
        val localPrismTargetMix = prismTargetMix
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        val bassCoef = (90f / sampleRate).coerceIn(0.002f, 0.25f)
        val midCoef = (1200f / sampleRate).coerceIn(0.002f, 0.35f)
        val prismMixSmoothing = (36f / sampleRate).coerceIn(0.0008f, 0.05f)

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

            when (localEnvironmentMode) {
                AcousticEnvironmentMode.STUDIO -> Unit
                AcousticEnvironmentMode.VINYL_WARMTH -> {
                    sampleL = applyVinylWarmth(sampleL)
                    sampleR = if (channels == 2) applyVinylWarmth(sampleR) else sampleL
                }
                AcousticEnvironmentMode.CONCERT_HALL -> {
                    hallProcessor.process(sampleL, sampleR, channels)
                    sampleL = hallProcessor.outL
                    sampleR = hallProcessor.outR
                }
            }

            if (localPrismMode == PrismMode.ISOLATION) {
                prismCurrentVocals += (localPrismTargetMix.vocals - prismCurrentVocals) * prismMixSmoothing
                prismCurrentBeats += (localPrismTargetMix.beats - prismCurrentBeats) * prismMixSmoothing
                prismCurrentInstruments += (localPrismTargetMix.instruments - prismCurrentInstruments) * prismMixSmoothing

                val prismFrame = prismIsolator.process(
                    left = sampleL,
                    right = sampleR,
                    channels = channels,
                    vocalsGain = prismCurrentVocals,
                    beatsGain = prismCurrentBeats,
                    instrumentsGain = prismCurrentInstruments
                )
                sampleL = prismFrame.left
                sampleR = prismFrame.right
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

            // --- Real-time band analysis used by waveform/vortex visualizers ---
            val mono = (sampleL + sampleR) * 0.5f
            lpBass += bassCoef * (mono - lpBass)
            lpMid += midCoef * (mono - lpMid)
            val bass = abs(lpBass)
            val mid = abs(lpMid - lpBass)
            val treble = abs(mono - lpMid)
            val energy = abs(mono)

            bassAccumulator += bass
            midAccumulator += mid
            trebleAccumulator += treble
            energyAccumulator += energy
            analysisFrameCounter += 1

            if (analysisFrameCounter >= 1024) {
                val inv = 1f / analysisFrameCounter.toFloat()
                val bassAvg = (bassAccumulator * inv * 3.4f)
                val midAvg = (midAccumulator * inv * 3.8f)
                val trebleAvg = (trebleAccumulator * inv * 4.6f)
                val energyAvg = (energyAccumulator * inv * 3.2f)

                val total = bassAvg + midAvg + trebleAvg + 1e-5f
                val centroid = ((bassAvg * 0.12f) + (midAvg * 0.45f) + (trebleAvg * 0.85f)) / total
                val flux = max(0f, energyAvg - prevEnergy) * 2.2f
                prevEnergy += (energyAvg - prevEnergy) * 0.2f

                _audioReactiveFrame.value = AudioReactiveFrame(
                    bass = clamp01(bassAvg),
                    mid = clamp01(midAvg),
                    treble = clamp01(trebleAvg),
                    energy = clamp01(energyAvg),
                    centroid = clamp01(centroid),
                    flux = clamp01(flux)
                )

                analysisFrameCounter = 0
                bassAccumulator = 0f
                midAccumulator = 0f
                trebleAccumulator = 0f
                energyAccumulator = 0f
            }

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
        vinylNoiseSeed = 0x13579BDF.toInt()
        vinylNoiseDc = 0f
        hallProcessor.reset()
        prismIsolator.reset()
        prismCurrentVocals = prismTargetMix.vocals
        prismCurrentBeats = prismTargetMix.beats
        prismCurrentInstruments = prismTargetMix.instruments
        lpBass = 0f
        lpMid = 0f
        prevEnergy = 0f
        analysisFrameCounter = 0
        bassAccumulator = 0f
        midAccumulator = 0f
        trebleAccumulator = 0f
        energyAccumulator = 0f
        _audioReactiveFrame.value = AudioReactiveFrame()
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
    }
}

private class SchroederHallProcessor {
    var outL: Float = 0f
        private set
    var outR: Float = 0f
        private set

    private var combL: Array<FloatArray> = emptyArray()
    private var combR: Array<FloatArray> = emptyArray()
    private var combIndexL = IntArray(0)
    private var combIndexR = IntArray(0)
    private var combStoreL = FloatArray(0)
    private var combStoreR = FloatArray(0)

    private var allPassL: Array<FloatArray> = emptyArray()
    private var allPassR: Array<FloatArray> = emptyArray()
    private var allPassIndexL = IntArray(0)
    private var allPassIndexR = IntArray(0)

    private var haasL = FloatArray(1)
    private var haasR = FloatArray(1)
    private var haasIndexL = 0
    private var haasIndexR = 0

    private val combMs = floatArrayOf(31.1f, 37.1f, 41.1f, 43.7f)
    private val allPassMs = floatArrayOf(5.0f, 1.7f)

    fun configure(sampleRate: Int) {
        combL = Array(combMs.size) { i -> FloatArray(((sampleRate * combMs[i]) / 1000f).toInt().coerceAtLeast(1)) }
        combR = Array(combMs.size) { i -> FloatArray(((sampleRate * (combMs[i] + 0.8f)) / 1000f).toInt().coerceAtLeast(1)) }
        combIndexL = IntArray(combMs.size)
        combIndexR = IntArray(combMs.size)
        combStoreL = FloatArray(combMs.size)
        combStoreR = FloatArray(combMs.size)

        allPassL = Array(allPassMs.size) { i -> FloatArray(((sampleRate * allPassMs[i]) / 1000f).toInt().coerceAtLeast(1)) }
        allPassR = Array(allPassMs.size) { i -> FloatArray(((sampleRate * (allPassMs[i] + 0.3f)) / 1000f).toInt().coerceAtLeast(1)) }
        allPassIndexL = IntArray(allPassMs.size)
        allPassIndexR = IntArray(allPassMs.size)

        haasL = FloatArray((sampleRate * 0.013f).toInt().coerceAtLeast(1))
        haasR = FloatArray((sampleRate * 0.019f).toInt().coerceAtLeast(1))
        haasIndexL = 0
        haasIndexR = 0
    }

    fun process(inputL: Float, inputR: Float, channels: Int) {
        if (combL.isEmpty()) {
            outL = inputL
            outR = if (channels == 2) inputR else inputL
            return
        }

        val stereoR = if (channels == 2) inputR else inputL
        val earlyL = writeDelay(haasL, inputL + stereoR * 0.18f, true)
        val earlyR = writeDelay(haasR, stereoR + inputL * 0.18f, false)
        val diffuseIn = ((inputL + stereoR) * 0.5f + (earlyL + earlyR) * 0.25f) * 0.95f

        val tailL = processTank(
            diffuseIn,
            combL,
            combIndexL,
            combStoreL,
            allPassL,
            allPassIndexL
        )
        val tailR = processTank(
            diffuseIn,
            combR,
            combIndexR,
            combStoreR,
            allPassR,
            allPassIndexR
        )

        outL = (inputL * 0.74f + earlyL * 0.10f + tailL * 0.30f).coerceIn(-1.25f, 1.25f)
        outR = (stereoR * 0.74f + earlyR * 0.10f + tailR * 0.30f).coerceIn(-1.25f, 1.25f)
    }

    fun reset() {
        combL.forEach { it.fill(0f) }
        combR.forEach { it.fill(0f) }
        allPassL.forEach { it.fill(0f) }
        allPassR.forEach { it.fill(0f) }
        combIndexL.fill(0)
        combIndexR.fill(0)
        combStoreL.fill(0f)
        combStoreR.fill(0f)
        allPassIndexL.fill(0)
        allPassIndexR.fill(0)
        haasL.fill(0f)
        haasR.fill(0f)
        haasIndexL = 0
        haasIndexR = 0
        outL = 0f
        outR = 0f
    }

    private fun writeDelay(buffer: FloatArray, input: Float, isLeft: Boolean): Float {
        val index = if (isLeft) haasIndexL else haasIndexR
        val delayed = buffer[index]
        buffer[index] = input

        if (isLeft) {
            haasIndexL = (index + 1) % buffer.size
        } else {
            haasIndexR = (index + 1) % buffer.size
        }

        return delayed
    }

    private fun processTank(
        input: Float,
        comb: Array<FloatArray>,
        combIndices: IntArray,
        combStore: FloatArray,
        allPass: Array<FloatArray>,
        allPassIndices: IntArray
    ): Float {
        var sum = 0f

        for (i in comb.indices) {
            val buffer = comb[i]
            val idx = combIndices[i]
            val delayed = buffer[idx]

            combStore[i] += (delayed - combStore[i]) * 0.17f
            buffer[idx] = input + combStore[i] * 0.79f
            combIndices[i] = (idx + 1) % buffer.size
            sum += delayed
        }

        var output = sum * (1f / comb.size.coerceAtLeast(1))

        for (i in allPass.indices) {
            val buffer = allPass[i]
            val idx = allPassIndices[i]
            val delayed = buffer[idx]
            buffer[idx] = output + delayed * 0.5f
            output = delayed - output
            allPassIndices[i] = (idx + 1) % buffer.size
        }

        return output
    }
}

enum class AcousticEnvironmentMode { STUDIO, VINYL_WARMTH, CONCERT_HALL }

enum class PrismMode { BYPASS, ISOLATION }

data class PrismMixLevels(
    val vocals: Float = 1f,
    val beats: Float = 1f,
    val instruments: Float = 1f
)

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

internal data class StereoFrame(val left: Float, val right: Float)

internal class PrismIsolator {
    private var sampleRateHz = 48_000f

    // Vocals (center/mid): steep HP 200Hz + LP 3000Hz (two cascaded stages each)
    private val vocalHp1 = MonoBiquad()
    private val vocalHp2 = MonoBiquad()
    private val vocalLp1 = MonoBiquad()
    private val vocalLp2 = MonoBiquad()

    // Beats/Bass: steep LP 150Hz
    private val beatsLp1 = MonoBiquad()
    private val beatsLp2 = MonoBiquad()

    // Instruments from side channel: remove sub mud so side bed is cleaner.
    private val sideHp1 = MonoBiquad()
    private val sideHp2 = MonoBiquad()

    fun configure(sampleRate: Int) {
        sampleRateHz = sampleRate.toFloat().coerceAtLeast(8_000f)

        vocalHp1.configureHighPass(sampleRateHz, 200f, 0.707f)
        vocalHp2.configureHighPass(sampleRateHz, 200f, 0.707f)
        vocalLp1.configureLowPass(sampleRateHz, 3000f, 0.707f)
        vocalLp2.configureLowPass(sampleRateHz, 3000f, 0.707f)

        beatsLp1.configureLowPass(sampleRateHz, 150f, 0.707f)
        beatsLp2.configureLowPass(sampleRateHz, 150f, 0.707f)

        sideHp1.configureHighPass(sampleRateHz, 180f, 0.707f)
        sideHp2.configureHighPass(sampleRateHz, 180f, 0.707f)
    }

    fun process(
        left: Float,
        right: Float,
        channels: Int,
        vocalsGain: Float,
        beatsGain: Float,
        instrumentsGain: Float
    ): StereoFrame {
        val stereoRight = if (channels == 2) right else left

        // Mid/Side decomposition.
        val mid = (left + stereoRight) * 0.5f
        val side = if (channels == 2) (left - stereoRight) * 0.5f else 0f

        val vocals = vocalLp2.process(vocalLp1.process(vocalHp2.process(vocalHp1.process(mid))))
        val beats = beatsLp2.process(beatsLp1.process(mid))
        val instruments = sideHp2.process(sideHp1.process(side))

        // Recombine user-controlled stems back to stereo.
        val outMid = vocals * vocalsGain + beats * beatsGain
        val outSide = instruments * instrumentsGain

        val outL = (outMid + outSide).coerceIn(-1.2f, 1.2f)
        val outR = if (channels == 2) (outMid - outSide).coerceIn(-1.2f, 1.2f) else outL

        return StereoFrame(outL, outR)
    }

    fun reset() {
        vocalHp1.reset(); vocalHp2.reset(); vocalLp1.reset(); vocalLp2.reset()
        beatsLp1.reset(); beatsLp2.reset()
        sideHp1.reset(); sideHp2.reset()
    }
}

internal class MonoBiquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun configureLowPass(sampleRate: Float, cutoffHz: Float, q: Float) {
        configure(sampleRate, cutoffHz, q, highPass = false)
    }

    fun configureHighPass(sampleRate: Float, cutoffHz: Float, q: Float) {
        configure(sampleRate, cutoffHz, q, highPass = true)
    }

    private fun configure(sampleRate: Float, cutoffHz: Float, q: Float, highPass: Boolean) {
        val omega = (2.0 * Math.PI * (cutoffHz.coerceAtLeast(20f) / sampleRate)).toFloat()
        val sinW = sin(omega)
        val cosW = cos(omega)
        val alpha = sinW / (2f * q.coerceAtLeast(0.1f))

        val rawB0: Float
        val rawB1: Float
        val rawB2: Float
        val rawA0 = 1f + alpha
        val rawA1 = -2f * cosW
        val rawA2 = 1f - alpha

        if (highPass) {
            rawB0 = (1f + cosW) * 0.5f
            rawB1 = -(1f + cosW)
            rawB2 = (1f + cosW) * 0.5f
        } else {
            rawB0 = (1f - cosW) * 0.5f
            rawB1 = 1f - cosW
            rawB2 = (1f - cosW) * 0.5f
        }

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun process(input: Float): Float {
        val out = b0 * input + z1
        z1 = b1 * input - a1 * out + z2
        z2 = b2 * input - a2 * out
        return out
    }

    fun reset() {
        z1 = 0f
        z2 = 0f
    }
}
