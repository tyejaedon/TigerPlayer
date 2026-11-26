package com.tigerplayer.engine

/**
 * 📊 Real-Time Audio Reactive Spectral Analysis Frame
 *
 * Encapsulates energy, frequency, and spectral dynamics extracted from real-time PCM audio playback.
 * Used by fluid visualizers and UI animations to drive audio-reactive rendering.
 *
 * @property bass Low-frequency energy level [0.0, 1.0] (60 Hz – 250 Hz range).
 * @property mid Mid-frequency energy level [0.0, 1.0] (1 kHz – 2.5 kHz range).
 * @property treble High-frequency energy level [0.0, 1.0] (6 kHz – 12 kHz range).
 * @property energy Overall RMS signal energy level [0.0, 1.0].
 * @property centroid Normalized spectral center of mass / brightness [0.0, 1.0].
 * @property flux Spectral flux measuring frame-to-frame transient energy changes [0.0, 1.0].
 * @property spectralBands 6-element normalized band energy array covering sub-bass to air frequencies.
 * @property analysisMode Active analysis engine mode ([SpectralAnalysisMode.BANDPASS] or [SpectralAnalysisMode.FFT]).
 * @property analysisCostMicros Execution time cost of analysis block in microseconds.
 */
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

/** Acoustic environment processing modes. */
enum class AcousticEnvironmentMode { STUDIO, VINYL_WARMTH, CONCERT_HALL }

/** Prism stem isolation modes. */
enum class PrismMode { BYPASS, ISOLATION }

/** Real-time spectral analysis engine modes. */
enum class SpectralAnalysisMode { BANDPASS, FFT }

/**
 * Stem mix target gains for Prism isolator.
 *
 * @property vocals Vocal stem multiplier [0.0, 1.0].
 * @property beats Beat stem multiplier [0.0, 1.0].
 * @property instruments Instrument stem multiplier [0.0, 1.0].
 */
data class PrismMixLevels(
    val vocals: Float = 1f,
    val beats: Float = 1f,
    val instruments: Float = 1f
)

/** Parametric filter topology types for State Variable Filter. */
enum class FilterType { LOW_SHELF, PEAKING, HIGH_SHELF }

/**
 * Parametric Equalizer node specification.
 *
 * @property id Unique identifier.
 * @property label Display title / frequency tag.
 * @property filterType Filter topology ([FilterType]).
 * @property frequency Center/Cutoff frequency in Hz.
 * @property gainDb Boost or cut gain in decibels.
 * @property qFactor Resonance/bandwidth Quality factor.
 */
data class AcousticNode(
    val id: String,
    val label: String,
    val filterType: FilterType,
    val frequency: Float,
    val gainDb: Float,
    val qFactor: Float
)

/** Internal stereo sample container holding left and right channel float samples. */
internal data class StereoFrame(val left: Float, val right: Float)

/**
 * Pure PCM encoding classification for direct hi-res output negotiation (issue #291).
 * Decoupled from Android framework constants so policy logic remains JVM-testable.
 */
enum class DirectPcmEncoding(val bitDepth: Int, val isHighResolution: Boolean) {
    PCM_16BIT(bitDepth = 16, isHighResolution = false),
    PCM_24BIT(bitDepth = 24, isHighResolution = true),
    PCM_32BIT(bitDepth = 32, isHighResolution = true),
    PCM_FLOAT(bitDepth = 32, isHighResolution = true),
    OTHER(bitDepth = 0, isHighResolution = false)
}

/**
 * Pure policy output describing whether to request direct PCM routing and float output.
 */
data class DirectOutputDecision(
    val requestDirectPcm: Boolean,
    val enableFloatOutput: Boolean,
    val negotiatedSampleRateHz: Int,
    val negotiatedEncoding: DirectPcmEncoding
)

/** Processing mode reported in [SignalPathState] for Signal Path telemetry (issue #282). */
enum class SignalPathDspMode {
    /** Processor is bypassed; untouched PCM / hardware offload path. */
    DIRECT_BIT_PERFECT_BYPASS,
    /** 32-bit floating-point DSP chain is actively processing audio. */
    FLOAT_32_PEQ_ACTIVE
}

/**
 * Live audio signal-path telemetry snapshot exposed by [AdaptiveDspEngine] (issue #282).
 * Consumed by Signal Path Badge UI (#70); updated only on format configuration,
 * DSP setting changes, or output route changes (never per-frame in `queueInput`).
 */
data class SignalPathState(
    val inputCodec: String = "",
    val inputBitDepth: Int = 0,
    val inputSampleRateHz: Int = 0,
    val inputChannelCount: Int = 0,
    val isLosslessInput: Boolean = false,
    val dspMode: SignalPathDspMode = SignalPathDspMode.DIRECT_BIT_PERFECT_BYPASS,
    val activeEqBandCount: Int = 0,
    val isBitPerfectBypass: Boolean = true,
    val outputBitDepth: Int = 0,
    val outputSampleRateHz: Int = 0,
    val outputEncodingLabel: String = "",
    val outputRouteLabel: String = "",
    val isDirectOutput: Boolean = false,
    val isOffloadActive: Boolean = false
) {
    /** Human-readable description of the active DSP processing mode. */
    fun dspDescription(): String = when (dspMode) {
        SignalPathDspMode.DIRECT_BIT_PERFECT_BYPASS -> "Direct Bit-Perfect Bypass"
        SignalPathDspMode.FLOAT_32_PEQ_ACTIVE -> "32-bit Float PEQ Active ($activeEqBandCount bands)"
    }
}
