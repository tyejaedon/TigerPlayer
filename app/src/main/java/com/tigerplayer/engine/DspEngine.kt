package com.tigerplayer.engine

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.tigerplayer.data.local.ReplayGainMode
import com.tigerplayer.utils.BiquadDesigner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*



/**
 * 🎛️ High-Performance Adaptive DSP Engine
 *
 * AndroidX Media3 [AudioProcessor] pipeline executing real-time studio-grade audio processing:
 * 1. Headphone Mid/Side stereo widening.
 * 2. Parametric Equalizer via Cytotoxic State Variable Filters (SVF).
 * 3. Acoustic Environment modeling (Studio Bypass, Vinyl Warmth with Wow/LFO, Concert Hall Schroeder Reverb).
 * 4. Prism 3-stem isolator (Linkwitz-Riley 4th order crossovers separating Vocals, Beats, and Instruments).
 * 5. Sample-rate aware look-ahead peak limiter and AGC prevent clipping.
 * 6. Real-time 6-band bandpass/FFT spectral analysis emitting [AudioReactiveFrame] via StateFlow.
 *
 * @param context Application context used for querying hardware audio output routes.
 */
@UnstableApi
@Singleton
class AdaptiveDspEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioProcessor {

    companion object {
        // AUDIOPHILE COMPATIBILITY: Increased buffer size for Samsung HAL stability
        // during high-CPU visualizer operations.
        private const val LOOK_AHEAD_BUFFER_SIZE = 1024
        private const val TRUE_PEAK_THRESHOLD = 1.0f
        @Deprecated("Replaced with transparent true-peak ceiling at 1.0f (0.0 dBFS)", ReplaceWith("TRUE_PEAK_THRESHOLD"))
        private const val LIMITER_THRESHOLD = 1.0f
        private const val LIMITER_ATTACK_SEC = 0.0008f // ~0.8ms look-ahead settling attack
        private const val LIMITER_RELEASE_SEC = 0.050f // 50ms fast recovery release (no pumping)
    }

    private var isActive = true
    private var inputEnded = false
    @Volatile private var autoMakeupGain = 1.0f
    @Volatile private var replayGainLinear = 1.0f
    @Volatile private var replayGainMode = ReplayGainMode.SMART
    @Volatile private var replayGainPreampDb = 0.0f
    @Volatile private var replayGainPreventClipping = true
    @Volatile private var replayGainTrackDb: Double? = null
    @Volatile private var replayGainAlbumDb: Double? = null
    @Volatile private var replayGainTrackPeak: Double? = null
    @Volatile private var replayGainAlbumPeak: Double? = null
    @Volatile private var isSequentialAlbumPlay = false
    @Volatile private var isBitPerfect = false
    @Volatile private var isLosslessStream = false
    @Volatile private var hasPositiveEqBoost = false
    @Volatile private var compositePeakDb = 0.0f

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
    /** StateFlow emitting real-time audio spectral dynamics for visualizer consumption. */
    val audioReactiveFrame: StateFlow<AudioReactiveFrame> = _audioReactiveFrame.asStateFlow()

    private var vinylNoiseSeed = 0x13579BDF
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
    private val rawBandEnergies = FloatArray(6)
    private val normalizedBandEnergies = FloatArray(6)
    private var analysisFrameCounter = 0
    private var energyAccumulator = 0f
    private var analysisCostMicrosSmoothed = 0f

    /**
     * Clamps float value [v] into range [0.0, 1.0].
     *
     * @param v Raw float value.
     * @return Clamped float in [0.0, 1.0].
     */
    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    /**
     * Generates white noise sample using a fast linear congruential pseudo-random generator.
     *
     * @return Pseudo-random float sample in range [-1.0, 1.0].
     */
    private fun nextNoise(): Float {
        vinylNoiseSeed = vinylNoiseSeed * 1664525 + 1013904223
        val normalized = ((vinylNoiseSeed ushr 8) and 0x00FFFFFF) / 16777215f
        return normalized * 2f - 1f
    }

    /**
     * Applies analog vinyl record characteristics (0.5Hz Pitch Wow LFO, soft polynomial saturation,
     * low-level vinyl hiss, and RIAA warmth low-shelf filter).
     *
     * @param sampleL Left channel input sample.
     * @param sampleR Right channel input sample.
     * @param channels Channel count (1 for Mono, 2 for Stereo).
     * @param sampleRate Current sampling rate in Hz.
     * @return [StereoFrame] containing processed warm vinyl stereo samples.
     */
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

    /**
     * Inspects active output audio routes (built-in speaker, Bluetooth A2DP, wired headphones,
     * USB DACs, and Samsung Galaxy Buds) and dynamically configures device compensation filters.
     */
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

        val sampleRate = getSampleRate()
        updateLimiterCoefficients(sampleRate)

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

    /**
     * Recalculates sample-rate aware attack and release coefficients for true-peak look-ahead limiter.
     */
    private fun updateLimiterCoefficients(sampleRate: Int) {
        val rate = sampleRate.coerceAtLeast(8000)
        agcAttackCoef = 1f - exp(-1f / (rate * LIMITER_ATTACK_SEC))
        agcReleaseCoef = 1f - exp(-1f / (rate * LIMITER_RELEASE_SEC))
    }

    /**
     * Determines whether digital overs exceeding 0 dBFS (1.0f) are physically imminent due to positive
     * EQ boosts, reverb tail summing, stem boosting, or positive ReplayGain scaling.
     */
    private fun isDigitalOverImminent(): Boolean {
        return hasPositiveEqBoost ||
            acousticEnvironmentMode == AcousticEnvironmentMode.CONCERT_HALL ||
            (prismMode == PrismMode.ISOLATION && (prismTargetMix.vocals > 1.0f || prismTargetMix.beats > 1.0f || prismTargetMix.instruments > 1.0f)) ||
            replayGainLinear > 1.001f
    }

    /**
     * Enables or disables lossless stream mode (FLAC, ALAC, WAV, AIFF, and 24-bit/32-bit Float PCM).
     * When active and EQ bands have zero or negative boost, dynamic AGC gain reduction is completely bypassed.
     */
    fun setIsLosslessStream(isLossless: Boolean) {
        this.isLosslessStream = isLossless
    }

    /** Returns whether the current stream is flagged as lossless audio. */
    fun isLosslessStream(): Boolean = isLosslessStream

    /** Updates bit-perfect DSP bypass state. */
    fun setBitPerfect(enabled: Boolean) {
        this.isBitPerfect = enabled
    }

    /** Returns whether bit-perfect DSP bypass is active. */
    fun isBitPerfect(): Boolean = isBitPerfect

    /** Returns the current auto-makeup headroom gain multiplier [0.0, 1.0]. */
    fun getAutoMakeupGain(): Float = autoMakeupGain

    /** Returns the maximum composite peak boost in dB calculated across active EQ bands. */
    fun getCompositePeakDb(): Float = compositePeakDb

    /**
     * Updates active parametric Equalizer bands and recalculates auto-makeup gain attenuation.
     *
     * Calculates the true maximum positive peak of the composite filter response curve across
     * the audible spectrum (20 Hz - 20 kHz) using [BiquadDesigner.calculateCompositePeakGainDb]
     * rather than naively summing band gains algebraically. This prevents drastic loudness drops
     * on non-overlapping bands (Fletcher-Munson illusion) while accurately accounting for compounding
     * on overlapping bands.
     *
     * @param nodes List of [AcousticNode] defining filter type, frequency, gain, and Q factor per band.
     */
    fun updateAcousticNodes(nodes: List<AcousticNode>) {
        pendingNodes = nodes
        val sampleRate = getSampleRate()

        // Calculate true peak composite gain across spectrum rather than linear algebraic sum
        val peakDb = BiquadDesigner.calculateCompositePeakGainDb(nodes, sampleRate.toFloat())
        compositePeakDb = peakDb
        hasPositiveEqBoost = peakDb > 0.001f
        autoMakeupGain = if (peakDb > 0.001f) {
            10f.pow(-peakDb / 20f)
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

    /**
     * Sets acoustic environment processing mode.
     *
     * @param mode Selected [AcousticEnvironmentMode] (STUDIO, VINYL_WARMTH, or CONCERT_HALL).
     */
    fun setAcousticEnvironmentMode(mode: AcousticEnvironmentMode) {
        acousticEnvironmentMode = mode
    }


    /**
     * Gets current processing sample rate in Hz.
     *
     * @return Sample rate in Hz (defaults to 48000 if unset).
     */
    fun getSampleRate(): Int = if (inputAudioFormat.sampleRate != AudioFormat.NOT_SET.sampleRate) inputAudioFormat.sampleRate else 48000

    /**
     * Sets Prism stem isolator state.
     *
     * @param mode Target [PrismMode] (BYPASS or ISOLATION).
     */
    fun setPrismMode(mode: PrismMode) {
        prismMode = mode
    }

    /**
     * Updates target stem mix multipliers for Prism isolation.
     *
     * @param vocals Vocal stem level [0.0, 1.0].
     * @param beats Beat stem level [0.0, 1.0].
     * @param instruments Instrument stem level [0.0, 1.0].
     */
    fun updatePrismMix(vocals: Float, beats: Float, instruments: Float) {
        prismTargetMix = PrismMixLevels(
            vocals = vocals.coerceIn(0f, 1f),
            beats = beats.coerceIn(0f, 1f),
            instruments = instruments.coerceIn(0f, 1f)
        )
    }

    /**
     * Updates ReplayGain engine configuration.
     *
     * @param mode Active [ReplayGainMode].
     * @param preampDb Manual preamp offset in dB.
     * @param preventClipping True to enable automatic peak attenuation preventing overload.
     * @param isBitPerfect True to bypass ReplayGain scaling for bit-perfect output.
     */
    fun setReplayGainConfig(
        mode: ReplayGainMode,
        preampDb: Float,
        preventClipping: Boolean,
        isBitPerfect: Boolean
    ) {
        this.replayGainMode = mode
        this.replayGainPreampDb = preampDb
        this.replayGainPreventClipping = preventClipping
        this.isBitPerfect = isBitPerfect
        recalculateReplayGain()
    }

    /**
     * Updates track and album ReplayGain metadata for current media item.
     *
     * @param trackGainDb Track gain adjustment in dB.
     * @param albumGainDb Album gain adjustment in dB.
     * @param trackPeak Track peak amplitude factor.
     * @param albumPeak Album peak amplitude factor.
     * @param isSequentialAlbumPlay True if playing sequentially within the same album.
     */
    fun setReplayGainTrackData(
        trackGainDb: Double?,
        albumGainDb: Double?,
        trackPeak: Double?,
        albumPeak: Double?,
        isSequentialAlbumPlay: Boolean
    ) {
        this.replayGainTrackDb = trackGainDb
        this.replayGainAlbumDb = albumGainDb
        this.replayGainTrackPeak = trackPeak
        this.replayGainAlbumPeak = albumPeak
        this.isSequentialAlbumPlay = isSequentialAlbumPlay
        recalculateReplayGain()
    }

    /**
     * Recalculates effective linear gain based on current ReplayGain config and track metadata.
     */
    private fun recalculateReplayGain() {
        replayGainLinear = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = replayGainMode,
            preampDb = replayGainPreampDb,
            preventClipping = replayGainPreventClipping,
            trackGainDb = replayGainTrackDb,
            albumGainDb = replayGainAlbumDb,
            trackPeak = replayGainTrackPeak,
            albumPeak = replayGainAlbumPeak,
            isSequentialAlbumPlay = isSequentialAlbumPlay,
            isBitPerfect = isBitPerfect
        )
    }

    /**
     * Sets spectral analysis backend mode for real-time audio visualization.
     *
     * @param mode Target [SpectralAnalysisMode] (BANDPASS filterbank or FFT).
     */
    fun setSpectralAnalysisMode(mode: SpectralAnalysisMode) {
        spectralAnalysisMode = mode
    }

    /**
     * Configures 6-band bandpass biquad filterbank and FFT spectral analyzer for given sample rate.
     *
     * @param sampleRate Sampling rate in Hz.
     */
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

    /**
     * Configures audio processor format. Supports 16-bit PCM, 24-bit PCM, and 32-bit Float PCM.
     * Always configures output format to 16-bit PCM for downstream Media3 processor compatibility.
     *
     * @param inputAudioFormat Incoming audio format.
     * @return Negotiated output [AudioFormat] (16-bit PCM).
     */
    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        // AUDIOPHILE UPGRADE: Support 16-bit, 24-bit (via 32-bit float), and Float PCM
        val isSupported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT

        if (!isSupported) {
            isActive = false
            return inputAudioFormat
        }

        if (inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        ) {
            isLosslessStream = true
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
        updateLimiterCoefficients(inputAudioFormat.sampleRate)
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

    /**
     * Indicates whether the processor is configured and active.
     *
     * @return True if active; false otherwise.
     */
    override fun isActive(): Boolean = isActive

    /**
     * Reallocates or clears internal direct ByteBuffer for output frame storage.
     *
     * @param count Capacity requirement in bytes.
     * @return Prepared [ByteBuffer].
     */
    private fun replaceOutputBuffer(count: Int): ByteBuffer {
        if (buffer.capacity() < count) {
            buffer = ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    /**
     * Main real-time audio processing callback. Decodes 16-bit, 24-bit, or Float PCM input frames,
     * processes stereo widening, SVF EQ, environmental modeling, stem isolation, look-ahead limiting,
     * and spectral analysis, writing 16-bit PCM frames to the output buffer.
     *
     * @param inputBuffer Incoming raw audio PCM sample byte buffer.
     */
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

                else -> {
                    sampleL = 0f
                    sampleR = 0f
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

            // FIX: Apply auto-makeup gain and ReplayGain preamp before active EQ to prevent SVF internal clipping
            val totalInputGain = autoMakeupGain * replayGainLinear
            var eqSampleL = sampleL * totalInputGain
            var eqSampleR = sampleR * totalInputGain

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

            // 5. TRUE-PEAK LIMITER & AGC (sample-rate aware look-ahead + smooth release)
            val delayedIndex = (lookAheadIndex - lookAheadDelaySamples + LOOK_AHEAD_BUFFER_SIZE) % LOOK_AHEAD_BUFFER_SIZE
            val delayedL = lookAheadBufferL[delayedIndex]
            val delayedR = lookAheadBufferR[delayedIndex]

            lookAheadBufferL[lookAheadIndex] = sampleL
            lookAheadBufferR[lookAheadIndex] = sampleR
            lookAheadIndex = (lookAheadIndex + 1) % LOOK_AHEAD_BUFFER_SIZE

            val shouldBypassLimiter = (isLosslessStream && !isDigitalOverImminent()) || isBitPerfect

            val reduction = if (shouldBypassLimiter) {
                1.0f
            } else {
                val currentPeak = max(abs(sampleL), abs(sampleR))
                if (currentPeak > agcEnvelope) {
                    agcEnvelope += agcAttackCoef * (currentPeak - agcEnvelope)
                } else {
                    agcEnvelope += agcReleaseCoef * (currentPeak - agcEnvelope)
                }
                if (agcEnvelope > TRUE_PEAK_THRESHOLD) TRUE_PEAK_THRESHOLD / agcEnvelope else 1.0f
            }

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

                var rawSum = 0f
                if (localSpectralMode == SpectralAnalysisMode.BANDPASS) {
                    for (index in 0 until 6) {
                        val raw = spectralBandAccumulators[index] * inv
                        rawBandEnergies[index] = raw
                        rawSum += raw
                    }
                } else {
                    val fftBands = fftSpectralAnalyzer.getLatestBandEnergies()
                    for (index in 0 until 6) {
                        val raw = fftBands[index]
                        rawBandEnergies[index] = raw
                        rawSum += raw
                    }
                }

                val energyGate = clamp01(energyAvg)
                var bandSum = 0f
                for (index in 0 until 6) {
                    val raw = rawBandEnergies[index]
                    val targetNorm = (raw * 14f).coerceAtLeast(0f)
                    val currentNorm = spectralBandNormalization[index]
                    val coeff = if (targetNorm > currentNorm) 0.16f else 0.04f
                    spectralBandNormalization[index] = currentNorm + (targetNorm - currentNorm) * coeff

                    val ratio = if (rawSum > 1e-6f) raw / rawSum else 0f
                    val normalized = (ratio * 6f * energyGate).coerceIn(0f, 1f)
                    val adaptive = (raw / (spectralBandNormalization[index] + 1e-5f)).coerceIn(0f, 1f)
                    val value = (normalized * 0.55f + adaptive * 0.45f).coerceIn(0f, 1f)
                    normalizedBandEnergies[index] = value
                    bandSum += value
                }

                val bassAvg = (normalizedBandEnergies[0] * 0.72f + normalizedBandEnergies[1] * 0.28f).coerceIn(0f, 1f)
                val midAvg = (normalizedBandEnergies[2] * 0.56f + normalizedBandEnergies[3] * 0.44f).coerceIn(0f, 1f)
                val trebleAvg = (normalizedBandEnergies[4] * 0.58f + normalizedBandEnergies[5] * 0.42f).coerceIn(0f, 1f)

                val weightedCentroid =
                    normalizedBandEnergies[0] * 0.05f +
                    normalizedBandEnergies[1] * 0.18f +
                    normalizedBandEnergies[2] * 0.38f +
                    normalizedBandEnergies[3] * 0.58f +
                    normalizedBandEnergies[4] * 0.78f +
                    normalizedBandEnergies[5] * 0.95f
                val centroid = if (bandSum > 1e-5f) weightedCentroid / bandSum else 0.5f
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
                    spectralBands = normalizedBandEnergies.toList(),
                    analysisMode = localSpectralMode,
                    analysisCostMicros = analysisCostMicrosSmoothed.coerceAtLeast(0f)
                )

                analysisFrameCounter = 0
                spectralBandAccumulators.fill(0f)
                energyAccumulator = 0f
            }

            // 7. OUTPUT STAGE: convert float domain back to PCM_16BIT for sink compatibility.
            outBuffer.putShort((sampleL.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
            if (channels == 2) {
                outBuffer.putShort((sampleR.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
            }
        }

        // inputBuffer position is already advanced by framesToProcess * inputFrameSize.
        // We only skip partial frames if they exist.
        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        outBuffer.flip()
    }

    /**
     * Signals end of audio input stream.
     */
    override fun queueEndOfStream() {
        inputEnded = true
    }

    /**
     * Retrieves processed audio output buffer.
     *
     * @return [ByteBuffer] containing processed PCM_16BIT sample data.
     */
    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    /**
     * Checks whether processing has completed for the current stream.
     *
     * @return True if input stream ended and output buffer consumed.
     */
    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    /**
     * Flushes internal processor state (filters, look-ahead buffers, reverb, and AGC envelope).
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        activeFilters.forEach { it.reset() }
        deviceCompensationFilters.forEach { it.reset() }
        agcEnvelope = 0f // FIXED: Clear AGC volume reduction upon seeking/re-buffering
        lookAheadBufferL.fill(0f)
        lookAheadBufferR.fill(0f)
        lookAheadIndex = 0
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

    /**
     * Resets processor and clears output memory buffer.
     */
    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
    }
}
