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

enum class AcousticEnvironmentMode {
    OFF,
    VINYL_WARMTH,
    CONCERT_HALL
}

internal enum class OutputRouteProfile {
    SPEAKER,
    HEADPHONES,
    BLUETOOTH,
    UNKNOWN
}

internal data class AcousticEnvironmentTuning(
    val vinylWetTarget: Float,
    val vinylDrive: Float,
    val vinylHarmonicMix: Float,
    val vinylNoiseFloorL: Float,
    val vinylNoiseFloorR: Float,
    val vinylDryBlend: Float,
    val hallWetTarget: Float,
    val hallDecayScale: Float,
    val hallPreDelayDamping: Float,
    val hallDiffusion: Float
) {
    companion object {
        fun forProfile(profile: OutputRouteProfile): AcousticEnvironmentTuning {
            return when (profile) {
                OutputRouteProfile.SPEAKER -> AcousticEnvironmentTuning(
                    vinylWetTarget = 0.30f,
                    vinylDrive = 1.58f,
                    vinylHarmonicMix = 0.11f,
                    vinylNoiseFloorL = 0.00120f,
                    vinylNoiseFloorR = 0.00134f,
                    vinylDryBlend = 0.93f,
                    hallWetTarget = 0.26f,
                    hallDecayScale = 0.88f,
                    hallPreDelayDamping = 0.16f,
                    hallDiffusion = 0.48f
                )
                OutputRouteProfile.HEADPHONES -> AcousticEnvironmentTuning(
                    vinylWetTarget = 0.44f,
                    vinylDrive = 1.90f,
                    vinylHarmonicMix = 0.16f,
                    vinylNoiseFloorL = 0.00158f,
                    vinylNoiseFloorR = 0.00182f,
                    vinylDryBlend = 0.89f,
                    hallWetTarget = 0.40f,
                    hallDecayScale = 1.03f,
                    hallPreDelayDamping = 0.11f,
                    hallDiffusion = 0.56f
                )
                OutputRouteProfile.BLUETOOTH -> AcousticEnvironmentTuning(
                    vinylWetTarget = 0.35f,
                    vinylDrive = 1.72f,
                    vinylHarmonicMix = 0.13f,
                    vinylNoiseFloorL = 0.00134f,
                    vinylNoiseFloorR = 0.00152f,
                    vinylDryBlend = 0.91f,
                    hallWetTarget = 0.30f,
                    hallDecayScale = 0.94f,
                    hallPreDelayDamping = 0.13f,
                    hallDiffusion = 0.50f
                )
                OutputRouteProfile.UNKNOWN -> AcousticEnvironmentTuning(
                    vinylWetTarget = 0.38f,
                    vinylDrive = 1.75f,
                    vinylHarmonicMix = 0.14f,
                    vinylNoiseFloorL = 0.00170f,
                    vinylNoiseFloorR = 0.00195f,
                    vinylDryBlend = 0.90f,
                    hallWetTarget = 0.34f,
                    hallDecayScale = 0.98f,
                    hallPreDelayDamping = 0.12f,
                    hallDiffusion = 0.52f
                )
            }
        }
    }
}

@UnstableApi
@Singleton
class AdaptiveDspEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioProcessor {

    companion object {
        private const val PCM_FLOAT = 32768f
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
    @Volatile private var outputRouteProfile: OutputRouteProfile = OutputRouteProfile.UNKNOWN
    @Volatile private var environmentTuning = AcousticEnvironmentTuning.forProfile(OutputRouteProfile.UNKNOWN)
    private var agcEnvelope = 0f
    private var agcReleaseCoef = 0.00005f

    @Volatile private var pendingNodes: List<AcousticNode> = emptyList()

    private val _audioReactiveFrame = MutableStateFlow(AudioReactiveFrame())
    val audioReactiveFrame: StateFlow<AudioReactiveFrame> = _audioReactiveFrame.asStateFlow()

    // Lightweight real-time spectrum approximation for visualizers.
    private var lpBass = 0f
    private var lpMid = 0f
    private var prevEnergy = 0f
    private var analysisFrameCounter = 0
    private var bassAccumulator = 0f
    private var midAccumulator = 0f
    private var trebleAccumulator = 0f
    private var energyAccumulator = 0f

    @Volatile
    private var acousticEnvironmentMode: AcousticEnvironmentMode = AcousticEnvironmentMode.OFF
    private var environmentWetCurrent = 0f
    private var vinylNoiseState = 0x6A09E667u
    private val concertHallReverb = LightweightSchroederReverb()

    private val bleHeadsetDeviceType: Int? by lazy { lookupAudioDeviceType("TYPE_BLE_HEADSET") }
    private val bleSpeakerDeviceType: Int? by lazy { lookupAudioDeviceType("TYPE_BLE_SPEAKER") }

    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    fun setAcousticEnvironmentMode(mode: AcousticEnvironmentMode) {
        acousticEnvironmentMode = mode
    }

    fun getAcousticEnvironmentMode(): AcousticEnvironmentMode = acousticEnvironmentMode

    init {
        detectAndApplyDeviceProfile()
    }

    fun detectAndApplyDeviceProfile() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val isBluetooth = devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                isBleOutputType(it.type)
        }
        val isBuiltIn = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val isWiredHeadphones = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        outputRouteProfile = when {
            isWiredHeadphones -> OutputRouteProfile.HEADPHONES
            isBluetooth -> OutputRouteProfile.BLUETOOTH
            isBuiltIn -> OutputRouteProfile.SPEAKER
            else -> OutputRouteProfile.UNKNOWN
        }
        isHeadphones = outputRouteProfile == OutputRouteProfile.HEADPHONES ||
            outputRouteProfile == OutputRouteProfile.BLUETOOTH
        environmentTuning = AcousticEnvironmentTuning.forProfile(outputRouteProfile)
        concertHallReverb.setTuning(
            decayScale = environmentTuning.hallDecayScale,
            preDelayDamping = environmentTuning.hallPreDelayDamping,
            diffusion = environmentTuning.hallDiffusion
        )

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

    private fun lookupAudioDeviceType(fieldName: String): Int? {
        return runCatching {
            AudioDeviceInfo::class.java.getField(fieldName).getInt(null)
        }.getOrNull()
    }

    private fun isBleOutputType(type: Int): Boolean {
        return type == bleHeadsetDeviceType || type == bleSpeakerDeviceType
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
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        val envTuning = environmentTuning
        val bassCoef = (90f / sampleRate).coerceIn(0.002f, 0.25f)
        val midCoef = (1200f / sampleRate).coerceIn(0.002f, 0.35f)

        repeat(framesToProcess) {
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

            // Acoustic environment stage sits after tone shaping and before AGC.
            val envMode = acousticEnvironmentMode
            val envWetTarget = when (envMode) {
                AcousticEnvironmentMode.OFF -> 0f
                AcousticEnvironmentMode.VINYL_WARMTH -> envTuning.vinylWetTarget
                AcousticEnvironmentMode.CONCERT_HALL -> envTuning.hallWetTarget
            }
            environmentWetCurrent += (envWetTarget - environmentWetCurrent) * 0.0038f

            when (envMode) {
                AcousticEnvironmentMode.VINYL_WARMTH -> {
                    val vinylL = vinylWarmth(sampleL, channel = 0, tuning = envTuning)
                    val vinylR = vinylWarmth(sampleR, channel = if (channels == 2) 1 else 0, tuning = envTuning)
                    sampleL = DspMath.mixDryWet(sampleL, vinylL, environmentWetCurrent)
                    sampleR = DspMath.mixDryWet(sampleR, vinylR, environmentWetCurrent)
                }
                AcousticEnvironmentMode.CONCERT_HALL -> {
                    val wet = concertHallReverb.process(sampleL, sampleR, channels)
                    sampleL = DspMath.mixDryWet(sampleL, wet.first, environmentWetCurrent)
                    sampleR = DspMath.mixDryWet(sampleR, wet.second, environmentWetCurrent)
                }
                AcousticEnvironmentMode.OFF -> Unit
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

            outShortBuffer.put(DspMath.toPcm16(sampleL))
            if (channels == 2) {
                outShortBuffer.put(DspMath.toPcm16(sampleR))
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
        lpBass = 0f
        lpMid = 0f
        prevEnergy = 0f
        analysisFrameCounter = 0
        bassAccumulator = 0f
        midAccumulator = 0f
        trebleAccumulator = 0f
        energyAccumulator = 0f
        environmentWetCurrent = 0f
        concertHallReverb.reset()
        _audioReactiveFrame.value = AudioReactiveFrame()
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
    }

    private fun vinylWarmth(sample: Float, channel: Int, tuning: AcousticEnvironmentTuning): Float {
        val driven = (sample * tuning.vinylDrive).coerceIn(-1.5f, 1.5f)
        val third = (driven * driven * driven) / 3f
        val fifth = (driven * driven * driven * driven * driven) * 0.055f
        val harmonics = (driven - third + fifth) * 0.78f

        // Faint generated floor with slight channel decorrelation.
        val noiseFloor = nextVinylNoise() * if (channel == 0) tuning.vinylNoiseFloorL else tuning.vinylNoiseFloorR
        val dry = sample * tuning.vinylDryBlend
        val wet = harmonics * (1f - tuning.vinylDryBlend)
        return (dry + wet + noiseFloor).coerceIn(-1f, 1f)
    }

    private fun nextVinylNoise(): Float {
        vinylNoiseState = vinylNoiseState * 1664525u + 1013904223u
        val normalized = ((vinylNoiseState.toLong() and 0xFFFFFFFFL).toFloat() / 4294967295f)
        return normalized * 2f - 1f
    }
}

internal object DspMath {
    private const val PCM_MAX_INT = 32767
    private const val PCM_MIN_INT = -32768

    fun mixDryWet(dry: Float, wet: Float, wetAmount: Float): Float {
        val amount = wetAmount.coerceIn(0f, 1f)
        return (dry + (wet - dry) * amount).coerceIn(-1f, 1f)
    }

    fun toPcm16(sample: Float): Short {
        val clamped = sample.coerceIn(-1f, 1f)
        if (clamped <= -1f) return PCM_MIN_INT.toShort()
        if (clamped >= 1f) return PCM_MAX_INT.toShort()
        return (clamped * PCM_MAX_INT).toInt().coerceIn(PCM_MIN_INT, PCM_MAX_INT).toShort()
    }
}

private class LightweightSchroederReverb {
    // Tuned for subtle room bloom while staying mobile-friendly.
    private val baseCombFeedback = floatArrayOf(0.79f, 0.75f, 0.72f)
    private val baseAllPassGain = floatArrayOf(0.52f, 0.5f)
    private val combL = arrayOf(
        FeedbackComb(1499, baseCombFeedback[0]),
        FeedbackComb(1861, baseCombFeedback[1]),
        FeedbackComb(2137, baseCombFeedback[2])
    )
    private val combR = arrayOf(
        FeedbackComb(1559, baseCombFeedback[0]),
        FeedbackComb(1931, baseCombFeedback[1]),
        FeedbackComb(2203, baseCombFeedback[2])
    )
    private val allpassL = arrayOf(AllPass(347, baseAllPassGain[0]), AllPass(113, baseAllPassGain[1]))
    private val allpassR = arrayOf(AllPass(373, baseAllPassGain[0]), AllPass(127, baseAllPassGain[1]))

    private var lpL = 0f
    private var lpR = 0f
    private var preDelayDamping = 0.12f
    private var preDelayHighPass = 0.82f

    fun setTuning(decayScale: Float, preDelayDamping: Float, diffusion: Float) {
        val decay = decayScale.coerceIn(0.78f, 1.15f)
        val diffusionScale = diffusion.coerceIn(0.42f, 0.65f)

        combL.forEachIndexed { index, comb ->
            comb.setFeedback((baseCombFeedback[index] * decay).coerceIn(0.62f, 0.91f))
        }
        combR.forEachIndexed { index, comb ->
            comb.setFeedback((baseCombFeedback[index] * decay).coerceIn(0.62f, 0.91f))
        }

        allpassL.forEachIndexed { index, allPass ->
            allPass.setGain((baseAllPassGain[index] * (diffusionScale / 0.52f)).coerceIn(0.35f, 0.72f))
        }
        allpassR.forEachIndexed { index, allPass ->
            allPass.setGain((baseAllPassGain[index] * (diffusionScale / 0.52f)).coerceIn(0.35f, 0.72f))
        }

        this.preDelayDamping = preDelayDamping.coerceIn(0.08f, 0.24f)
        this.preDelayHighPass = (0.70f + this.preDelayDamping * 0.95f).coerceIn(0.70f, 0.92f)
    }

    fun process(inputL: Float, inputR: Float, channels: Int): Pair<Float, Float> {
        val monoIn = if (channels == 2) (inputL + inputR) * 0.5f else inputL
        val predelayL = highpassPreDelay(inputL, true)
        val predelayR = highpassPreDelay(if (channels == 2) inputR else monoIn, false)

        var wetL = 0f
        var wetR = 0f
        for (comb in combL) wetL += comb.process(predelayL)
        for (comb in combR) wetR += comb.process(predelayR)
        wetL /= combL.size.toFloat()
        wetR /= combR.size.toFloat()

        for (allPass in allpassL) wetL = allPass.process(wetL)
        for (allPass in allpassR) wetR = allPass.process(wetR)

        return Pair(wetL.coerceIn(-1f, 1f), wetR.coerceIn(-1f, 1f))
    }

    fun reset() {
        combL.forEach { it.reset() }
        combR.forEach { it.reset() }
        allpassL.forEach { it.reset() }
        allpassR.forEach { it.reset() }
        lpL = 0f
        lpR = 0f
    }

    private fun highpassPreDelay(input: Float, left: Boolean): Float {
        if (left) {
            lpL += (input - lpL) * preDelayDamping
            return (input - lpL * preDelayHighPass).coerceIn(-1f, 1f)
        }
        lpR += (input - lpR) * preDelayDamping
        return (input - lpR * preDelayHighPass).coerceIn(-1f, 1f)
    }

    private class FeedbackComb(size: Int, private var feedback: Float) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun setFeedback(value: Float) {
            feedback = value.coerceIn(0f, 0.97f)
        }

        fun process(input: Float): Float {
            val delayed = buffer[index]
            val output = delayed
            buffer[index] = (input + delayed * feedback).coerceIn(-1f, 1f)
            index += 1
            if (index >= buffer.size) index = 0
            return output
        }

        fun reset() {
            buffer.fill(0f)
            index = 0
        }
    }

    private class AllPass(size: Int, private var gain: Float) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun setGain(value: Float) {
            gain = value.coerceIn(0f, 0.9f)
        }

        fun process(input: Float): Float {
            val delayed = buffer[index]
            val output = -input + delayed
            buffer[index] = (input + delayed * gain).coerceIn(-1f, 1f)
            index += 1
            if (index >= buffer.size) index = 0
            return output
        }

        fun reset() {
            buffer.fill(0f)
            index = 0
        }
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