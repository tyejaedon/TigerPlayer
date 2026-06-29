package com.example.tigerplayer.engine

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
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

    @Volatile private var activeFilters: List<StateVariableFilter> = emptyList()
    @Volatile private var deviceCompensationFilters: List<StateVariableFilter> = emptyList()

    private var isHeadphones = false
    private var agcEnvelope = 0f
    private var agcAttackCoef = 0.01f
    private var agcReleaseCoef = 0.00005f

    // LOOK-AHEAD BUFFER: 5ms at 48kHz is ~240 samples
    private val lookAheadBufferL = FloatArray(512)
    private val lookAheadBufferR = FloatArray(512)
    private var lookAheadIndex = 0
    private val lookAheadDelay = 240 // ~5ms at 48kHz

    // TPDF DITHER SEEDS
    private var ditherSeed1 = 0x12345678
    private var ditherSeed2 = 0x87654321

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
    
    // VINYL WOW LFO
    private var vinylWowPhase = 0f
    private val vinylWowFreq = 0.5f // 0.5 Hz Wow
    private val vinylWowBufferL = FloatArray(1024)
    private val vinylWowBufferR = FloatArray(1024)
    private var vinylWowIndex = 0

    // VINYL RIAA SHELF (SVF)
    private var vinylShelf: StateVariableFilter? = null

    // Lightweight real-time spectrum approximation for visualizers.
    private var lpBass = 0f
    private var lpMid = 0f
    private var lpTreble = 0f
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

    private fun applyVinylWarmth(sampleL: Float, sampleR: Float, channels: Int, sampleRate: Int): StereoFrame {
        // 1. WOW LFO (Pitch Modulation)
        vinylWowPhase += (2f * PI.toFloat() * vinylWowFreq) / sampleRate.toFloat()
        if (vinylWowPhase > 2f * PI.toFloat()) vinylWowPhase -= 2f * PI.toFloat()
        
        val modulation = sin(vinylWowPhase) * 1.5f + 2.0f // 0.5 to 3.5 samples delay
        
        vinylWowBufferL[vinylWowIndex] = sampleL
        vinylWowBufferR[vinylWowIndex] = sampleR
        
        val readIdx = (vinylWowIndex - modulation.toInt() + 1024) % 1024
        val frac = modulation - modulation.toInt()
        val nextIdx = (readIdx + 1) % 1024
        
        val wowL = vinylWowBufferL[readIdx] * (1f - frac) + vinylWowBufferL[nextIdx] * frac
        val wowR = vinylWowBufferR[readIdx] * (1f - frac) + vinylWowBufferR[nextIdx] * frac
        vinylWowIndex = (vinylWowIndex + 1) % 1024

        // 2. SATURATION (Non-linear)
        val drive = 1.18f
        val drivenL = (wowL * drive).coerceIn(-1.2f, 1.2f)
        val softL = drivenL - ((drivenL * drivenL * drivenL) / 3f)
        
        val drivenR = (wowR * drive).coerceIn(-1.2f, 1.2f)
        val softR = drivenR - ((drivenR * drivenR * drivenR) / 3f)

        // 3. NOISE & HISS
        val rawNoise = nextNoise() * 0.0018f
        vinylNoiseDc += (rawNoise - vinylNoiseDc) * 0.004f
        val hiss = rawNoise - vinylNoiseDc

        // 4. RIAA / WARMTH SHELF
        if (vinylShelf == null || vinylShelf?.sampleRate != sampleRate) {
            vinylShelf = StateVariableFilter(FilterType.LOW_SHELF, sampleRate, 350f, 2.5f, 0.5f)
        }
        
        var outL = (wowL * 0.82f + softL * 0.15f + hiss).coerceIn(-1.15f, 1.15f)
        var outR = (wowR * 0.82f + softR * 0.15f + hiss).coerceIn(-1.15f, 1.15f)
        
        outL = vinylShelf!!.process(outL, 0)
        if (channels == 2) outR = vinylShelf!!.process(outR, 1)

        return StereoFrame(outL, outR)
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
        agcAttackCoef = 1f - exp(-1f / (sampleRate * 0.005f)) // 5ms attack
        agcReleaseCoef = 1f - exp(-1f / (sampleRate * 0.250f)) // 250ms release

        val newDeviceFilters = mutableListOf<StateVariableFilter>()
        if (isBuiltIn) {
            newDeviceFilters.add(StateVariableFilter(FilterType.LOW_SHELF, sampleRate, 150f, -6f, 0.707f))
            newDeviceFilters.add(StateVariableFilter(FilterType.PEAKING, sampleRate, 2500f, 3f, 1.0f))
        } else if (isBluetooth) {
            newDeviceFilters.add(StateVariableFilter(FilterType.HIGH_SHELF, sampleRate, 12000f, 2.5f, 0.707f))
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
                StateVariableFilter(node.filterType, sampleRate, node.frequency, node.gainDb, node.qFactor)
            }
        }
    }

    fun setAcousticEnvironmentMode(mode: AcousticEnvironmentMode) {
        acousticEnvironmentMode = mode
    }

    fun getAcousticEnvironmentMode(): AcousticEnvironmentMode = acousticEnvironmentMode

    fun getSampleRate(): Int = if (inputAudioFormat.sampleRate != AudioFormat.NOT_SET.sampleRate) inputAudioFormat.sampleRate else 48000

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
        val bassCoefA = 1f - exp(-1f / (sampleRate * 0.010f)) // 10ms attack
        val bassCoefR = 1f - exp(-1f / (sampleRate * 0.080f)) // 80ms release
        val midCoefA = 1f - exp(-1f / (sampleRate * 0.015f))
        val midCoefR = 1f - exp(-1f / (sampleRate * 0.120f))
        val trebleCoefA = 1f - exp(-1f / (sampleRate * 0.020f))
        val trebleCoefR = 1f - exp(-1f / (sampleRate * 0.180f))

        val prismMixSmoothing = (36f / sampleRate).coerceIn(0.0008f, 0.05f)

        for (frame in 0 until framesToProcess) {
            var sampleL = shortBuffer.get().toFloat() / PCM_FLOAT
            var sampleR = if (channels == 2) shortBuffer.get().toFloat() / PCM_FLOAT else sampleL

            // 1. INPUT LOOK-AHEAD WRITE
            lookAheadBufferL[lookAheadIndex] = sampleL
            lookAheadBufferR[lookAheadIndex] = sampleR

            // 2. HEADPHONE WIDENING
            if (isHeadphones && channels == 2) {
                val mid = (sampleL + sampleR) * 0.5f
                val side = (sampleL - sampleR) * 0.5f
                val widenedSide = side * 1.15f
                sampleL = mid + widenedSide
                sampleR = mid - widenedSide
            }

            // 3. APPLY FILTERS (SVF)
            for (i in localDeviceFilters.indices) {
                sampleL = localDeviceFilters[i].process(sampleL, 0)
                if (channels == 2) sampleR = localDeviceFilters[i].process(sampleR, 1)
            }

            for (i in localActiveFilters.indices) {
                sampleL = localActiveFilters[i].process(sampleL, 0)
                if (channels == 2) sampleR = localActiveFilters[i].process(sampleR, 1)
            }

            // 4. ACOUSTIC ENVIRONMENTS
            when (localEnvironmentMode) {
                AcousticEnvironmentMode.STUDIO -> Unit
                AcousticEnvironmentMode.VINYL_WARMTH -> {
                    val vinylFrame = applyVinylWarmth(sampleL, sampleR, channels, sampleRate)
                    sampleL = vinylFrame.left
                    sampleR = vinylFrame.right
                }
                AcousticEnvironmentMode.CONCERT_HALL -> {
                    hallProcessor.process(sampleL, sampleR, channels)
                    sampleL = hallProcessor.outL
                    sampleR = hallProcessor.outR
                }
            }

            // 5. PRISM ISOLATION (Linkwitz-Riley)
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

            // 6. AGC / LIMITER (Look-ahead + Exponential Release)
            val delayedL = lookAheadBufferL[(lookAheadIndex - lookAheadDelay + 512) % 512]
            val delayedR = lookAheadBufferR[(lookAheadIndex - lookAheadDelay + 512) % 512]
            lookAheadIndex = (lookAheadIndex + 1) % 512

            val currentPeak = max(abs(sampleL), abs(sampleR))
            if (currentPeak > agcEnvelope) {
                agcEnvelope += agcAttackCoef * (currentPeak - agcEnvelope)
            } else {
                agcEnvelope += agcReleaseCoef * (currentPeak - agcEnvelope)
            }

            val reduction = if (agcEnvelope > 0.85f) 0.85f / agcEnvelope else 1f
            sampleL = delayedL * reduction
            sampleR = delayedR * reduction

            // Soft-clipping saturation function
            val clampL = sampleL.coerceIn(-1.25f, 1.25f)
            sampleL = if (abs(clampL) > 1f) sign(clampL) else clampL - ((clampL * clampL * clampL) / 3f)

            val clampR = sampleR.coerceIn(-1.25f, 1.25f)
            sampleR = if (abs(clampR) > 1f) sign(clampR) else clampR - ((clampR * clampR * clampR) / 3f)

            // 7. REAL-TIME ANALYSIS (Envelope Followers)
            val mono = (sampleL + sampleR) * 0.5f
            lpBass += (if (abs(mono) > lpBass) bassCoefA else bassCoefR) * (abs(mono) - lpBass)
            lpMid += (if (abs(mono) > lpMid) midCoefA else midCoefR) * (abs(mono) - lpMid)
            lpTreble += (if (abs(mono - lpMid) > lpTreble) trebleCoefA else trebleCoefR) * (abs(mono - lpMid) - lpTreble)
            
            val bass = lpBass
            val mid = abs(lpMid - lpBass)
            val treble = lpTreble
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

            // 8. TPDF DITHERING (Final 16-bit Stage)
            ditherSeed1 = ditherSeed1 * 1664525 + 1013904223
            ditherSeed2 = ditherSeed2 * 1664525 + 1013904223
            val rand1 = (ditherSeed1 shr 8 and 0x00FFFFFF) / 16777215f
            val rand2 = (ditherSeed2 shr 8 and 0x00FFFFFF) / 16777215f
            val tpdf = (rand1 + rand2 - 1f) / PCM_FLOAT

            outShortBuffer.put(((sampleL + tpdf) * PCM_MAX_INT).toInt().coerceIn(PCM_MIN_INT, PCM_MAX_INT).toShort())
            if (channels == 2) {
                outShortBuffer.put(((sampleR + tpdf) * PCM_MAX_INT).toInt().coerceIn(PCM_MIN_INT, PCM_MAX_INT).toShort())
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

    @Suppress("DEPRECATION")
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
        lpTreble = 0f
        prevEnergy = 0f
        analysisFrameCounter = 0
        bassAccumulator = 0f
        midAccumulator = 0f
        trebleAccumulator = 0f
        energyAccumulator = 0f
        
        vinylWowPhase = 0f
        vinylWowBufferL.fill(0f)
        vinylWowBufferR.fill(0f)
        vinylWowIndex = 0
        
        lookAheadBufferL.fill(0f)
        lookAheadBufferR.fill(0f)
        lookAheadIndex = 0
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
    
    // REVERB DAMPING: One-pole LPF states per comb filter
    private var combDampL = FloatArray(0)
    private var combDampR = FloatArray(0)
    private val dampingFactor = 0.45f

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
        combDampL = FloatArray(combMs.size)
        combDampR = FloatArray(combMs.size)

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
            combDampL,
            allPassL,
            allPassIndexL
        )
        val tailR = processTank(
            diffuseIn,
            combR,
            combIndexR,
            combDampR,
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
        combDampL.fill(0f)
        combDampR.fill(0f)
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
        combDamp: FloatArray,
        allPass: Array<FloatArray>,
        allPassIndices: IntArray
    ): Float {
        var sum = 0f

        for (i in comb.indices) {
            val buffer = comb[i]
            val idx = combIndices[i]
            val delayed = buffer[idx]
            
            // DAMPING RITUAL: Filter the feedback signal
            combDamp[i] += (delayed - combDamp[i]) * dampingFactor
            
            buffer[idx] = input + combDamp[i] * 0.79f
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
 * 🎨 STATE VARIABLE FILTER (SVF)
 * The Gold Standard for real-time studio equipment.
 * Inherently stable, perfectly coherent, and allows smooth parameter sweeps
 * without the "zipper noise" or "pops" of standard Biquads.
 */
class StateVariableFilter(
    var type: FilterType,
    val sampleRate: Int,
    initialFreq: Float,
    initialGain: Float,
    initialQ: Float
) {
    // Current parameters for smoothing
    private var targetG = 0f
    private var targetK = 0f
    private var targetA = 0f

    private var g = 0f
    private var k = 0f
    private var a = 0f

    // Internal state (integrators) per channel
    private val ic1eq = FloatArray(2)
    private val ic2eq = FloatArray(2)

    init {
        updateParameters(initialFreq, initialGain, initialQ, sampleRate)
        // Instant init
        g = targetG; k = targetK; a = targetA
    }

    fun updateParameters(freq: Float, gainDb: Float, q: Float, sampleRate: Int) {
        val f = freq.coerceIn(20f, (sampleRate / 2f) * 0.9f)
        val qVal = q.coerceIn(0.1f, 10f)
        val gain = 10f.pow(gainDb / 40f)

        targetG = tan(PI.toFloat() * f / sampleRate.toFloat())
        targetK = 1f / qVal
        targetA = gain
    }

    fun process(sample: Float, channel: Int): Float {
        // Smooth parameters
        val smooth = 0.003f
        g += (targetG - g) * smooth
        k += (targetK - k) * smooth
        a += (targetA - a) * smooth

        val a1 = 1f / (1f + g * (g + k))
        val a2 = g * a1
        val a3 = g * a2

        val v3 = sample - ic2eq[channel]
        val v1 = a1 * ic1eq[channel] + a2 * v3
        val v2 = ic2eq[channel] + a2 * ic1eq[channel] + a3 * v3

        ic1eq[channel] = 2f * v1 - ic1eq[channel]
        ic2eq[channel] = 2f * v2 - ic2eq[channel]

        return when (type) {
            FilterType.LOW_SHELF -> {
                sample + (a - 1f) * v2
            }
            FilterType.PEAKING -> {
                sample + k * (a - 1f) * v1
            }
            FilterType.HIGH_SHELF -> {
                val hp = sample - k * v1 - v2
                sample + (a - 1f) * hp
            }
        }
    }

    fun reset() {
        ic1eq.fill(0f)
        ic2eq.fill(0f)
    }
}

internal data class StereoFrame(val left: Float, val right: Float)

internal class PrismIsolator {
    private var sampleRateHz = 48_000f

    // LINKWITZ-RILEY 4TH ORDER (LR4)
    // Created by cascading two Butterworth filters (Q=0.707)
    // Stage 1
    private val lowLp1 = MonoBiquad()
    private val lowLp2 = MonoBiquad()
    
    // Stage 2
    private val midHp1 = MonoBiquad()
    private val midHp2 = MonoBiquad()
    private val midLp1 = MonoBiquad()
    private val midLp2 = MonoBiquad()
    
    // Stage 3
    private val highHp1 = MonoBiquad()
    private val highHp2 = MonoBiquad()

    fun configure(sampleRate: Int) {
        sampleRateHz = sampleRate.toFloat().coerceAtLeast(8_000f)

        // Crossover 1: 150 Hz
        lowLp1.configureLowPass(sampleRateHz, 150f, 0.707f)
        lowLp2.configureLowPass(sampleRateHz, 150f, 0.707f)
        
        midHp1.configureHighPass(sampleRateHz, 150f, 0.707f)
        midHp2.configureHighPass(sampleRateHz, 150f, 0.707f)

        // Crossover 2: 3000 Hz
        midLp1.configureLowPass(sampleRateHz, 3000f, 0.707f)
        midLp2.configureLowPass(sampleRateHz, 3000f, 0.707f)
        
        highHp1.configureHighPass(sampleRateHz, 3000f, 0.707f)
        highHp2.configureHighPass(sampleRateHz, 3000f, 0.707f)
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

        // SPLIT INTO BANDS (PHASE COHERENT LR4)
        val low = lowLp2.process(lowLp1.process(mid))
        
        val midBandRaw = midHp2.process(midHp1.process(mid))
        val vocals = midLp2.process(midLp1.process(midBandRaw))
        
        val highBandRaw = highHp2.process(highHp1.process(midBandRaw))
        // High frequencies from Side channel for instruments
        val instruments = highBandRaw + highHp2.process(highHp1.process(side))

        // Recombine with gains
        // In LR4, the crossover summation is perfectly flat.
        val outMid = vocals * vocalsGain + low * beatsGain
        val outSide = instruments * instrumentsGain

        val outL = (outMid + outSide).coerceIn(-1.2f, 1.2f)
        val outR = if (channels == 2) (outMid - outSide).coerceIn(-1.2f, 1.2f) else outL

        return StereoFrame(outL, outR)
    }

    fun reset() {
        lowLp1.reset(); lowLp2.reset()
        midHp1.reset(); midHp2.reset()
        midLp1.reset(); midLp2.reset()
        highHp1.reset(); highHp2.reset()
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
