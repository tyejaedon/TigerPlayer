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
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.*

data class AudioReactiveFrame(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val energy: Float = 0f,
    val centroid: Float = 0.5f,
    val flux: Float = 0f,
    val spectralBands: List<Float> = List(6) { 0f },
    val analysisMode: SpectralAnalysisMode = SpectralAnalysisMode.FFT,
    val analysisCostMicros: Float = 0f
)

@UnstableApi
@Singleton
class AdaptiveDspEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioProcessor {

    companion object {
        // AUDIOPHILE COMPATIBILITY: Increased buffer size for Samsung HAL stability 
        // during high-CPU visualizer operations.
        private const val LOOK_AHEAD_BUFFER_SIZE = 1024
        private const val LIMITER_THRESHOLD = 0.82f
    }

    private var isActive = true
    private var inputEnded = false
    @Volatile private var autoMakeupGain = 1.0f

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

    @Volatile private var pendingNodes: List<AcousticNode> = emptyList()
    @Volatile private var acousticEnvironmentMode: AcousticEnvironmentMode = AcousticEnvironmentMode.STUDIO
    @Volatile private var prismMode: PrismMode = PrismMode.BYPASS
    @Volatile private var prismTargetMix = PrismMixLevels()
    @Volatile private var spectralAnalysisMode: SpectralAnalysisMode = SpectralAnalysisMode.FFT

    // LOOK-AHEAD BUFFER: delay is sample-rate aware, buffer remains fixed-size for perf.
    private val lookAheadBufferL = FloatArray(LOOK_AHEAD_BUFFER_SIZE)
    private val lookAheadBufferR = FloatArray(LOOK_AHEAD_BUFFER_SIZE)
    private var lookAheadIndex = 0
    private var lookAheadDelaySamples = 240

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

    // Real-time 6-band analyzer (band-pass filter bank).
    private val spectralBandCenters = floatArrayOf(60f, 250f, 1_000f, 2_500f, 6_000f, 12_000f)
    private val spectralBandQ = floatArrayOf(1.1f, 1.2f, 1.35f, 1.45f, 1.6f, 1.8f)
    private val spectralBandFilters = Array(6) { MonoBiquad() }
    private val spectralBandAccumulators = FloatArray(6)
    private val spectralBandNormalization = FloatArray(6) { 0.04f }
    private val fftSpectralAnalyzer = FftSpectralAnalyzer(windowSize = 1024)

    private var prevEnergy = 0f
    private var analysisFrameCounter = 0
    private var energyAccumulator = 0f
    private var analysisCostMicrosSmoothed = 0f

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
        val drive = 1.08f
        val drivenL = wowL * drive
        val drivenR = wowR * drive

        val softL = if (abs(drivenL) <= 1.25f) {
            drivenL - ((drivenL * drivenL * drivenL) / 4.5f)
        } else {
            sign(drivenL) * 0.83f // Smoothly flattens at the peak of the curve
        }

        val softR = if (abs(drivenR) <= 1.25f) {
            drivenR - ((drivenR * drivenR * drivenR) / 4.5f)
        } else {
            sign(drivenR) * 0.83f
        }

        // 3. NOISE & HISS
        val rawNoise = nextNoise() * 0.0012f
        vinylNoiseDc += (rawNoise - vinylNoiseDc) * 0.004f
        val hiss = rawNoise - vinylNoiseDc

        // 4. RIAA / WARMTH SHELF
        if (vinylShelf == null || vinylShelf?.sampleRate != sampleRate) {
            vinylShelf = StateVariableFilter(FilterType.LOW_SHELF, sampleRate, 350f, 2.5f, 0.5f)
        }
        
        var outL = (wowL * 0.86f + softL * 0.12f + hiss).coerceIn(-1.02f, 1.02f)
        var outR = (wowR * 0.86f + softR * 0.12f + hiss).coerceIn(-1.02f, 1.02f)
        
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
        
        // AUDIOPHILE COMPATIBILITY: Detect Samsung-specific outputs (Galaxy Buds)
        // to avoid phase-cancellation conflicts with Samsung's native 360 Audio.
        val isSamsungOutput = devices.any { 
            it.productName?.contains("Buds", ignoreCase = true) == true || 
            it.productName?.contains("Samsung", ignoreCase = true) == true 
        }

        isHeadphones = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        } || (isBluetooth && isSamsungOutput)

        val sampleRate = if (inputAudioFormat.sampleRate != AudioFormat.NOT_SET.sampleRate) inputAudioFormat.sampleRate else 48000
        agcAttackCoef = 1f - exp(-1f / (sampleRate * 0.005f)) // 5ms attack
        agcReleaseCoef = 1f - exp(-1f / (sampleRate * 0.250f)) // 250ms release

        val newDeviceFilters = mutableListOf<StateVariableFilter>()
        if (isBuiltIn) {
            newDeviceFilters.add(StateVariableFilter(FilterType.LOW_SHELF, sampleRate, 150f, -6f, 0.707f))
            newDeviceFilters.add(StateVariableFilter(FilterType.PEAKING, sampleRate, 2500f, 3f, 1.0f))
        } else if (isBluetooth) {
            // If it's a Samsung device, we use a lighter touch to coordinate with their native tuning.
            val shelfGain = if (isSamsungOutput) 1.2f else 2.5f
            newDeviceFilters.add(StateVariableFilter(FilterType.HIGH_SHELF, sampleRate, 12000f, shelfGain, 0.707f))
        }

        deviceCompensationFilters = newDeviceFilters
    }

    fun updateAcousticNodes(nodes: List<AcousticNode>) {
        pendingNodes = nodes
        val sampleRate = if (inputAudioFormat.sampleRate != AudioFormat.NOT_SET.sampleRate) inputAudioFormat.sampleRate else 48000

        // FIX: Calculate total positive gain and attenuate the input by that amount
        val maxBoostDb = nodes.filter { it.gainDb > 0f }.sumOf { it.gainDb.toDouble() }.toFloat()
        autoMakeupGain = if (maxBoostDb > 0f) {
            10f.pow(-maxBoostDb / 20f)
        } else {
            1.0f
        }

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

    fun setSpectralAnalysisMode(mode: SpectralAnalysisMode) {
        spectralAnalysisMode = mode
    }

    private fun configureSpectralAnalyzer(sampleRate: Int) {
        val safeRate = sampleRate.coerceAtLeast(8_000)
        spectralBandFilters.indices.forEach { index ->
            spectralBandFilters[index].configureBandPass(
                sampleRate = safeRate.toFloat(),
                centerHz = spectralBandCenters[index],
                q = spectralBandQ[index]
            )
        }
        fftSpectralAnalyzer.configure(sampleRate = safeRate, bandCentersHz = spectralBandCenters)
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        // AUDIOPHILE UPGRADE: Support 16-bit, 24-bit (via 32-bit float), and Float PCM
        val isSupported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT

        if (!isSupported) {
            isActive = false
            return inputAudioFormat
        }

        val isNewSampleRate = this.inputAudioFormat.sampleRate != inputAudioFormat.sampleRate
        this.inputAudioFormat = inputAudioFormat
        // Keep internal math in float, but emit PCM_16BIT so downstream Media3 processors
        // (notably silence skipping) can always configure successfully.
        this.outputAudioFormat = AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_16BIT
        )

        detectAndApplyDeviceProfile()
        lookAheadDelaySamples = (inputAudioFormat.sampleRate * 0.0045f)
            .roundToInt()
            .coerceIn(24, LOOK_AHEAD_BUFFER_SIZE - 1)
        hallProcessor.configure(inputAudioFormat.sampleRate.coerceAtLeast(8000))
        prismIsolator.configure(inputAudioFormat.sampleRate.coerceAtLeast(8000))
        configureSpectralAnalyzer(inputAudioFormat.sampleRate)

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
        val inputEncoding = inputAudioFormat.encoding
        
        val inputFrameSize = when (inputEncoding) {
            C.ENCODING_PCM_16BIT -> channels * 2
            C.ENCODING_PCM_24BIT -> channels * 3
            C.ENCODING_PCM_FLOAT -> channels * 4
            else -> channels * 2
        }
        
        val framesToProcess = remaining / inputFrameSize
        if (framesToProcess == 0) return

        // Output is always PCM_16BIT (2 bytes per sample)
        val requiredOutputBytes = framesToProcess * channels * 2
        val outBuffer = replaceOutputBuffer(requiredOutputBytes)

        val localDeviceFilters = deviceCompensationFilters
        val localActiveFilters = activeFilters
        val localEnvironmentMode = acousticEnvironmentMode
        val localPrismMode = prismMode
        val localPrismTargetMix = prismTargetMix
        val localSpectralMode = spectralAnalysisMode
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        val prismMixSmoothing = (36f / sampleRate).coerceIn(0.0008f, 0.05f)

        repeat(framesToProcess) {
            var sampleL = 0f
            var sampleR = 0f

            when (inputEncoding) {
                C.ENCODING_PCM_16BIT -> {
                    sampleL = inputBuffer.getShort().toFloat() / 32768f
                    sampleR = if (channels == 2) inputBuffer.getShort().toFloat() / 32768f else sampleL
                }
                C.ENCODING_PCM_24BIT -> {
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt() and 0xFF
                    val b3 = inputBuffer.get().toInt()
                    val valL = (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                    sampleL = valL.toFloat() / 2147483648f
                    
                    if (channels == 2) {
                        val rb1 = inputBuffer.get().toInt() and 0xFF
                        val rb2 = inputBuffer.get().toInt() and 0xFF
                        val rb3 = inputBuffer.get().toInt()
                        val valR = (rb1 shl 8) or (rb2 shl 16) or (rb3 shl 24)
                        sampleR = valR.toFloat() / 2147483648f
                    } else {
                        sampleR = sampleL
                    }
                }
                C.ENCODING_PCM_FLOAT -> {
                    sampleL = inputBuffer.getFloat()
                    sampleR = if (channels == 2) inputBuffer.getFloat() else sampleL
                }
            }

            // 1. HEADPHONE WIDENING (Optimized for transparency)
            if (isHeadphones && channels == 2) {
                val mid = (sampleL + sampleR) * 0.5f
                val side = (sampleL - sampleR) * 0.5f
                val widenedSide = side * 1.02f
                sampleL = mid + widenedSide
                sampleR = mid - widenedSide
            }

            // 2. APPLY FILTERS (SVF)
            for (i in localDeviceFilters.indices) {
                sampleL = localDeviceFilters[i].process(sampleL, 0)
                if (channels == 2) sampleR = localDeviceFilters[i].process(sampleR, 1)
            }

            // FIX: Apply auto-makeup gain before active EQ to prevent SVF internal clipping
            var eqSampleL = sampleL * autoMakeupGain
            var eqSampleR = sampleR * autoMakeupGain

            for (i in localActiveFilters.indices) {
                eqSampleL = localActiveFilters[i].process(eqSampleL, 0)
                if (channels == 2) eqSampleR = localActiveFilters[i].process(eqSampleR, 1)
            }

            sampleL = eqSampleL
            sampleR = eqSampleR

            // 3. ACOUSTIC ENVIRONMENTS
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

            // 4. PRISM ISOLATION (Linkwitz-Riley)
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

            // 5. AGC / LIMITER (sample-rate aware look-ahead + smooth release)
            val delayedIndex = (lookAheadIndex - lookAheadDelaySamples + LOOK_AHEAD_BUFFER_SIZE) % LOOK_AHEAD_BUFFER_SIZE
            val delayedL = lookAheadBufferL[delayedIndex]
            val delayedR = lookAheadBufferR[delayedIndex]

            lookAheadBufferL[lookAheadIndex] = sampleL
            lookAheadBufferR[lookAheadIndex] = sampleR
            lookAheadIndex = (lookAheadIndex + 1) % LOOK_AHEAD_BUFFER_SIZE

            val currentPeak = max(abs(sampleL), abs(sampleR))
            if (currentPeak > agcEnvelope) {
                agcEnvelope += agcAttackCoef * (currentPeak - agcEnvelope)
            } else {
                agcEnvelope += agcReleaseCoef * (currentPeak - agcEnvelope)
            }

            val reduction = if (agcEnvelope > LIMITER_THRESHOLD) LIMITER_THRESHOLD / agcEnvelope else 1f
            
            // AUDIOPHILE LIMITER: Use the look-ahead signal and apply gain reduction
            sampleL = (delayedL * reduction).coerceIn(-1.0f, 1.0f)
            sampleR = (delayedR * reduction).coerceIn(-1.0f, 1.0f)

            // 6. REAL-TIME ANALYSIS
            val mono = (sampleL + sampleR) * 0.5f
            val energy = abs(mono)

            if (localSpectralMode == SpectralAnalysisMode.BANDPASS) {
                for (index in spectralBandFilters.indices) {
                    val filtered = spectralBandFilters[index].process(mono)
                    spectralBandAccumulators[index] += abs(filtered)
                }
            } else {
                fftSpectralAnalyzer.pushSample(mono)
            }

            energyAccumulator += energy
            analysisFrameCounter += 1

            if (analysisFrameCounter >= 1024) {
                val analysisStartNs = System.nanoTime()
                val inv = 1f / analysisFrameCounter.toFloat()
                val energyAvg = (energyAccumulator * inv * 3.2f)

                val rawBands = FloatArray(spectralBandFilters.size)
                var rawSum = 0f
                if (localSpectralMode == SpectralAnalysisMode.BANDPASS) {
                    for (index in rawBands.indices) {
                        val raw = spectralBandAccumulators[index] * inv
                        rawBands[index] = raw
                        rawSum += raw
                    }
                } else {
                    val fftBands = fftSpectralAnalyzer.getLatestBandEnergies()
                    for (index in rawBands.indices) {
                        val raw = fftBands[index]
                        rawBands[index] = raw
                        rawSum += raw
                    }
                }

                val energyGate = clamp01(energyAvg)
                val normalizedBands = MutableList(rawBands.size) { 0f }
                for (index in rawBands.indices) {
                    val targetNorm = (rawBands[index] * 14f).coerceAtLeast(0f)
                    val currentNorm = spectralBandNormalization[index]
                    val coeff = if (targetNorm > currentNorm) 0.16f else 0.04f
                    spectralBandNormalization[index] = currentNorm + (targetNorm - currentNorm) * coeff

                    val ratio = if (rawSum > 1e-6f) rawBands[index] / rawSum else 0f
                    val normalized = (ratio * 6f * energyGate).coerceIn(0f, 1f)
                    val adaptive = (rawBands[index] / (spectralBandNormalization[index] + 1e-5f)).coerceIn(0f, 1f)
                    normalizedBands[index] = (normalized * 0.55f + adaptive * 0.45f).coerceIn(0f, 1f)
                }

                val bassAvg = (normalizedBands[0] * 0.72f + normalizedBands[1] * 0.28f).coerceIn(0f, 1f)
                val midAvg = (normalizedBands[2] * 0.56f + normalizedBands[3] * 0.44f).coerceIn(0f, 1f)
                val trebleAvg = (normalizedBands[4] * 0.58f + normalizedBands[5] * 0.42f).coerceIn(0f, 1f)

                val weightedCentroid =
                    normalizedBands[0] * 0.05f +
                    normalizedBands[1] * 0.18f +
                    normalizedBands[2] * 0.38f +
                    normalizedBands[3] * 0.58f +
                    normalizedBands[4] * 0.78f +
                    normalizedBands[5] * 0.95f
                val centroidDenominator = normalizedBands.sum() + 1e-5f
                val centroid = weightedCentroid / centroidDenominator
                val flux = max(0f, energyAvg - prevEnergy) * 2.2f
                prevEnergy += (energyAvg - prevEnergy) * 0.2f

                val elapsedMicros = (System.nanoTime() - analysisStartNs) / 1_000f
                val profileSmoothing = if (elapsedMicros > analysisCostMicrosSmoothed) 0.24f else 0.08f
                analysisCostMicrosSmoothed +=
                    (elapsedMicros - analysisCostMicrosSmoothed) * profileSmoothing

                _audioReactiveFrame.value = AudioReactiveFrame(
                    bass = clamp01(bassAvg),
                    mid = clamp01(midAvg),
                    treble = clamp01(trebleAvg),
                    energy = clamp01(energyAvg),
                    centroid = clamp01(centroid),
                    flux = clamp01(flux),
                    spectralBands = normalizedBands,
                    analysisMode = localSpectralMode,
                    analysisCostMicros = analysisCostMicrosSmoothed.coerceAtLeast(0f)
                )

                analysisFrameCounter = 0
                spectralBandAccumulators.fill(0f)
                energyAccumulator = 0f
            }

            // 7. OUTPUT STAGE: convert float domain back to PCM_16BIT for sink compatibility.
            outBuffer.putShort((sampleL.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
            if (channels == 2) {
                outBuffer.putShort((sampleR.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
            }
        }

        // inputBuffer position is already advanced by framesToProcess * inputFrameSize.
        // We only skip partial frames if they exist.
        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        outBuffer.flip()
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
        prevEnergy = 0f
        analysisFrameCounter = 0
        spectralBandFilters.forEach { it.reset() }
        spectralBandAccumulators.fill(0f)
        spectralBandNormalization.fill(0.04f)
        fftSpectralAnalyzer.reset()
        energyAccumulator = 0f
        analysisCostMicrosSmoothed = 0f

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

        outL = (inputL * 0.70f + earlyL * 0.10f + tailL * 0.20f).coerceIn(-1.0f, 1.0f)
        outR = (stereoR * 0.70f + earlyR * 0.10f + tailR * 0.20f).coerceIn(-1.0f, 1.0f)
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

            // FIX: Apply tanh soft-clipping inside the feedback loop
            val feedback = combDamp[i] * 0.79f
            val safeFeedback = tanh(feedback)

            buffer[idx] = input + safeFeedback
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

enum class SpectralAnalysisMode { BANDPASS, FFT }

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

    // Cached coefficients to avoid per-sample division overhead
    private var cachedA1 = 0f
    private var cachedA2 = 0f
    private var cachedA3 = 0f
    private var isTransitioning = true

    // Internal state (integrators) per channel
    private val ic1eq = FloatArray(2)
    private val ic2eq = FloatArray(2)

    init {
        updateParameters(initialFreq, initialGain, initialQ, sampleRate)
        // Instant init
        g = targetG
        k = targetK
        a = targetA

        // Calculate initial coefficients immediately to prevent first-sample artifacts
        cachedA1 = 1f / (1f + g * (g + k))
        cachedA2 = g * cachedA1
        cachedA3 = g * cachedA2
        isTransitioning = false
    }

    fun updateParameters(freq: Float, gainDb: Float, q: Float, sampleRate: Int) {
        val f = freq.coerceIn(20f, (sampleRate / 2f) * 0.9f)
        val qVal = q.coerceIn(0.1f, 10f)
        val gain = 10f.pow(gainDb / 40f)

        targetG = tan(PI.toFloat() * f / sampleRate.toFloat())
        targetK = 1f / qVal
        targetA = gain
        isTransitioning = true // Flag the filter to start interpolating math
    }

    fun process(sample: Float, channel: Int): Float {
        // Only calculate heavy math (like divisions) if parameters are changing
        if (isTransitioning) {
            val smooth = 0.003f
            g += (targetG - g) * smooth
            k += (targetK - k) * smooth
            a += (targetA - a) * smooth

            // Lock parameters when close enough to bypass math overhead completely
            if (abs(targetG - g) < 0.0001f && abs(targetK - k) < 0.0001f) {
                g = targetG
                k = targetK
                a = targetA
                isTransitioning = false
            }

            cachedA1 = 1f / (1f + g * (g + k))
            cachedA2 = g * cachedA1
            cachedA3 = g * cachedA2
        }

        val v3 = sample - ic2eq[channel]
        val v1 = cachedA1 * ic1eq[channel] + cachedA2 * v3
        val v2 = ic2eq[channel] + cachedA2 * ic1eq[channel] + cachedA3 * v3

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

    // Mid channel LR4 crossovers.
    private val lowMidLp1 = MonoBiquad()
    private val lowMidLp2 = MonoBiquad()
    private val midMidHp1 = MonoBiquad()
    private val midMidHp2 = MonoBiquad()
    private val midMidLp1 = MonoBiquad()
    private val midMidLp2 = MonoBiquad()
    private val highMidHp1 = MonoBiquad()
    private val highMidHp2 = MonoBiquad()

    // Side channel LR4 crossovers.
    private val lowSideLp1 = MonoBiquad()
    private val lowSideLp2 = MonoBiquad()
    private val midSideHp1 = MonoBiquad()
    private val midSideHp2 = MonoBiquad()
    private val midSideLp1 = MonoBiquad()
    private val midSideLp2 = MonoBiquad()
    private val highSideHp1 = MonoBiquad()
    private val highSideHp2 = MonoBiquad()

    private var normalizationAttackCoef = 0.05f
    private var normalizationReleaseCoef = 0.006f
    private var limiterAttackCoef = 0.12f
    private var limiterReleaseCoef = 0.01f
    private var limiterEnvelopeAttackCoef = 0.28f
    private var limiterEnvelopeReleaseCoef = 0.02f

    private var outputNormalization = 1f
    private var limiterEnvelope = 0f
    private var limiterGain = 1f

    private fun sanitizeGain(value: Float): Float = value.coerceIn(0f, 1f)

    private fun softSaturate(sample: Float): Float {
        // FIX: Prevent foldback distortion for massive peaks without hard-clipping early
        val absSample = abs(sample)
        return if (absSample <= 1.2f) {
            sample - (sample * sample * sample) * 0.22f
        } else {
            sign(sample) * (1.2f - (1.2f * 1.2f * 1.2f) * 0.22f)
        }
    }

    fun configure(sampleRate: Int) {
        sampleRateHz = sampleRate.toFloat().coerceAtLeast(8_000f)

        // 3-way split tuned for practical stem separation without harsh artifacts.
        val lowCutoffHz = 160f
        val highCutoffHz = 3_500f

        lowMidLp1.configureLowPass(sampleRateHz, lowCutoffHz, 0.707f)
        lowMidLp2.configureLowPass(sampleRateHz, lowCutoffHz, 0.707f)
        midMidHp1.configureHighPass(sampleRateHz, lowCutoffHz, 0.707f)
        midMidHp2.configureHighPass(sampleRateHz, lowCutoffHz, 0.707f)
        midMidLp1.configureLowPass(sampleRateHz, highCutoffHz, 0.707f)
        midMidLp2.configureLowPass(sampleRateHz, highCutoffHz, 0.707f)
        highMidHp1.configureHighPass(sampleRateHz, highCutoffHz, 0.707f)
        highMidHp2.configureHighPass(sampleRateHz, highCutoffHz, 0.707f)

        lowSideLp1.configureLowPass(sampleRateHz, lowCutoffHz, 0.707f)
        lowSideLp2.configureLowPass(sampleRateHz, lowCutoffHz, 0.707f)
        midSideHp1.configureHighPass(sampleRateHz, lowCutoffHz, 0.707f)
        midSideHp2.configureHighPass(sampleRateHz, lowCutoffHz, 0.707f)
        midSideLp1.configureLowPass(sampleRateHz, highCutoffHz, 0.707f)
        midSideLp2.configureLowPass(sampleRateHz, highCutoffHz, 0.707f)
        highSideHp1.configureHighPass(sampleRateHz, highCutoffHz, 0.707f)
        highSideHp2.configureHighPass(sampleRateHz, highCutoffHz, 0.707f)

        normalizationAttackCoef = 1f - exp(-1f / (sampleRateHz * 0.004f))
        normalizationReleaseCoef = 1f - exp(-1f / (sampleRateHz * 0.060f))
        limiterAttackCoef = 1f - exp(-1f / (sampleRateHz * 0.002f))
        limiterReleaseCoef = 1f - exp(-1f / (sampleRateHz * 0.090f))
        limiterEnvelopeAttackCoef = 1f - exp(-1f / (sampleRateHz * 0.0015f))
        limiterEnvelopeReleaseCoef = 1f - exp(-1f / (sampleRateHz * 0.065f))
    }

    fun process(
        left: Float,
        right: Float,
        channels: Int,
        vocalsGain: Float,
        beatsGain: Float,
        instrumentsGain: Float
    ): StereoFrame {
        val safeVocals = sanitizeGain(vocalsGain)
        val safeBeats = sanitizeGain(beatsGain)
        val safeInstruments = sanitizeGain(instrumentsGain)

        val stereoRight = if (channels == 2) right else left

        // Mid/Side decomposition.
        val mid = (left + stereoRight) * 0.5f
        val side = if (channels == 2) (left - stereoRight) * 0.5f else 0f

        val midLow = lowMidLp2.process(lowMidLp1.process(mid))
        val midBandRaw = midMidHp2.process(midMidHp1.process(mid))
        val midMid = midMidLp2.process(midMidLp1.process(midBandRaw))
        val midHigh = highMidHp2.process(highMidHp1.process(midBandRaw))

        val sideLow = lowSideLp2.process(lowSideLp1.process(side))
        val sideBandRaw = midSideHp2.process(midSideHp1.process(side))
        val sideMid = midSideLp2.process(midSideLp1.process(sideBandRaw))
        val sideHigh = highSideHp2.process(highSideHp1.process(sideBandRaw))

        val vocalsStem = midMid * 0.92f + midHigh * 0.08f
        val beatsStem = midLow + sideLow * 0.12f
        val instrumentsStem = sideMid * 0.72f + sideHigh * 1.05f + midHigh * 0.08f

        val outMid = vocalsStem * safeVocals + beatsStem * safeBeats
        val outSide = instrumentsStem * safeInstruments

        var outL = outMid + outSide
        var outR = if (channels == 2) outMid - outSide else outL

        val squaredSum =
            safeVocals * safeVocals + safeBeats * safeBeats + safeInstruments * safeInstruments
        val targetNormalization = if (squaredSum <= 1e-5f) {
            0f
        } else {
            (1f / sqrt(squaredSum)).coerceIn(0.52f, 1.05f)
        }

        val normCoef = if (targetNormalization < outputNormalization) {
            normalizationAttackCoef
        } else {
            normalizationReleaseCoef
        }
        outputNormalization += (targetNormalization - outputNormalization) * normCoef

        outL *= outputNormalization
        outR *= outputNormalization

        val peak = max(abs(outL), abs(outR))
        val envCoef = if (peak > limiterEnvelope) limiterEnvelopeAttackCoef else limiterEnvelopeReleaseCoef
        limiterEnvelope += (peak - limiterEnvelope) * envCoef

        val targetLimiterGain = if (limiterEnvelope > 0.93f) {
            0.93f / limiterEnvelope
        } else {
            1f
        }
        val limiterCoef = if (targetLimiterGain < limiterGain) limiterAttackCoef else limiterReleaseCoef
        limiterGain += (targetLimiterGain - limiterGain) * limiterCoef

        outL = softSaturate(outL * limiterGain)
        outR = softSaturate(outR * limiterGain)

        return StereoFrame(outL, outR)
    }

    fun reset() {
        lowMidLp1.reset(); lowMidLp2.reset()
        midMidHp1.reset(); midMidHp2.reset()
        midMidLp1.reset(); midMidLp2.reset()
        highMidHp1.reset(); highMidHp2.reset()

        lowSideLp1.reset(); lowSideLp2.reset()
        midSideHp1.reset(); midSideHp2.reset()
        midSideLp1.reset(); midSideLp2.reset()
        highSideHp1.reset(); highSideHp2.reset()

        outputNormalization = 1f
        limiterEnvelope = 0f
        limiterGain = 1f
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

    fun configureBandPass(sampleRate: Float, centerHz: Float, q: Float) {
        val omega = (2.0 * Math.PI * (centerHz.coerceAtLeast(20f) / sampleRate)).toFloat()
        val sinW = sin(omega)
        val cosW = cos(omega)
        val alpha = sinW / (2f * q.coerceAtLeast(0.1f))

        val rawA0 = 1f + alpha
        val rawA1 = -2f * cosW
        val rawA2 = 1f - alpha

        val rawB0 = alpha
        val rawB1 = 0f
        val rawB2 = -alpha

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
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

internal class FftSpectralAnalyzer(
    private val windowSize: Int
) {
    private var sampleRate: Int = 48_000
    private var bandCentersHz = floatArrayOf(60f, 250f, 1_000f, 2_500f, 6_000f, 12_000f)

    private var writeIndex = 0
    private val frameBuffer = FloatArray(windowSize)
    private val window = FloatArray(windowSize) { index ->
        (0.5f - 0.5f * cos((2f * PI.toFloat() * index) / (windowSize - 1).coerceAtLeast(1))).coerceIn(0f, 1f)
    }
    private val fftBuffer = FloatArray(windowSize)
    private val fft = FloatFFT_1D(windowSize.toLong())
    private val latestBandEnergies = FloatArray(6)
    private val bandStartBins = IntArray(6)
    private val bandEndBins = IntArray(6)

    fun configure(sampleRate: Int, bandCentersHz: FloatArray) {
        this.sampleRate = sampleRate.coerceAtLeast(8_000)
        this.bandCentersHz = bandCentersHz

        val edges = FloatArray(7)
        edges[0] = 20f
        for (index in 1 until edges.lastIndex) {
            val left = this.bandCentersHz[index - 1]
            val right = this.bandCentersHz[index]
            edges[index] = sqrt(left * right)
        }
        val nyquist = this.sampleRate * 0.5f
        edges[edges.lastIndex] = (nyquist * 0.95f).coerceAtLeast(edges[edges.lastIndex - 1] + 1f)

        val half = windowSize / 2
        for (index in bandStartBins.indices) {
            val start = ((edges[index] / nyquist) * half).toInt().coerceIn(1, half - 1)
            val end = ((edges[index + 1] / nyquist) * half).toInt().coerceIn(start, half)
            bandStartBins[index] = start
            bandEndBins[index] = end
        }

        reset()
    }

    fun pushSample(sample: Float) {
        frameBuffer[writeIndex] = sample
        writeIndex += 1
        if (writeIndex >= windowSize) {
            computeBands()
            writeIndex = 0
        }
    }

    fun getLatestBandEnergies(): FloatArray = latestBandEnergies.copyOf()

    fun reset() {
        writeIndex = 0
        frameBuffer.fill(0f)
        fftBuffer.fill(0f)
        latestBandEnergies.fill(0f)
    }

    private fun computeBands() {
        for (index in frameBuffer.indices) {
            fftBuffer[index] = frameBuffer[index] * window[index]
        }

        fft.realForward(fftBuffer)

        val half = windowSize / 2
        val magnitudes = FloatArray(half + 1)
        magnitudes[0] = abs(fftBuffer[0])
        for (bin in 1 until half) {
            val real = fftBuffer[2 * bin]
            val imag = fftBuffer[2 * bin + 1]
            magnitudes[bin] = sqrt(real * real + imag * imag)
        }
        magnitudes[half] = abs(fftBuffer[1])

        for (band in latestBandEnergies.indices) {
            val start = bandStartBins[band]
            val end = bandEndBins[band]
            var weightedSum = 0f
            var weight = 0f
            for (bin in start..end) {
                val magnitude = magnitudes[bin]
                val w = 0.7f + (bin.toFloat() / half.toFloat()) * 0.6f
                weightedSum += magnitude * w
                weight += w
            }
            val average = if (weight > 1e-6f) weightedSum / weight else 0f
            latestBandEnergies[band] = ln(1f + average * 4f).coerceAtLeast(0f)
        }
    }
}

