package com.tigerplayer.engine

import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Modular computation and geometry engine for audio-reactive waveforms across
 * both the Main Player screen and Cover Screen.
 *
 * Provides:
 * - High-speed zero-allocation sinusoidal curve generation
 * - 3-tier harmonic wave hierarchies (fundamental, octave lead, cosine apex)
 * - Continuous raised-cosine boundary envelopes anchoring curves to the x-axis baseline
 *   at shift 0 (x = 0) and terminal intercept (x = width)
 * - Safe amplitude sample extraction and downsampling
 */
object WaveformComputation {

    /**
     * Calculates sampling points for cover and player micro waveforms from input amplitudes.
     * Guaranteed to return a valid [FloatArray] of size [pointCount], with all values in 0f..1f,
     * immune to NaNs, infinities, or empty/null inputs.
     */
    fun calculateWaveformPoints(
        amplitudes: List<Float>?,
        pointCount: Int = 32
    ): FloatArray {
        val safeCount = pointCount.coerceAtLeast(2)
        val result = FloatArray(safeCount)
        if (amplitudes.isNullOrEmpty()) {
            result.fill(0.2f)
            return result
        }

        val lastIdx = (amplitudes.size - 1).coerceAtLeast(0)
        val divisor = (safeCount - 1).toFloat()
        for (i in 0 until safeCount) {
            val sourceIndex = ((i / divisor) * lastIdx).toInt().coerceIn(0, lastIdx)
            val raw = amplitudes[sourceIndex]
            result[i] = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0.2f
        }
        return result
    }

    /**
     * Modular waveform generator that creates smooth, continuous curved paths containing exclusively
     * positive amplitudes resting directly on top of the slider track with slight overlap for seamless
     * visual continuity.
     *
     * Mathematical details:
     * - Continuous raised sinusoidal wave: 0.5 * (1 + trig(theta)), oscillating smoothly between 0 and maxHeight.
     * - At troughs (trig = -1), the wave gently touches the baseline with a zero horizontal derivative (no flat clipping).
     * - At crests (trig = 1), the wave reaches its apex peak height smoothly.
     * - When [isCosine] is false and [phaseOffset] is 0, generates a standard sine wave.
     * - When [isCosine] is false and [phaseOffset] is PI / 4 radians, generates a phase-shifted wave (45-degree offset).
     * - When [isCosine] is true, generates a cosine wave (equivalent to 90-degree offset).
     * - Anchors smoothly to the baseline x-axis with shift 0 at the start (x = 0) and intercepts the baseline
     *   at the endpoint (x = width) via a raised-cosine boundary envelope W(x), preventing vertical detachment
     *   while preserving dynamic audio-reactivity and harmonic ripples across the slider body.
     * - All waves develop proper continuous curves in all scenarios (including zero audio energy, silence, or paused playback)
     *   without flattening or collapsing into straight lines.
     *
     * @param x Horizontal pixel position.
     * @param width Full horizontal drawing width in pixels.
     * @param effectiveHeight Effective peak height in pixels above the baseline after energy/bass modulation.
     * @param effectiveBaselineY Effective baseline Y coordinate (baselineY + overlapPx).
     * @param safePhase Dynamic animated phase angle in radians.
     * @param phaseOffset Fixed phase offset in radians (e.g. PI / 4 for the second wave).
     * @param isCosine Whether this wave uses cosine (true) or sine (false).
     * @param frequencyMultiplier Harmonic frequency scaling factor.
     * @param waveCycles Number of complete wave cycles across the width (defaults to 2.0f for consistent sinusoidal wavelength).
     * @param turbulence Multi-harmonic ripple factor creating rich, vibey, organic undulations.
     * @param edgeTaper Boundary fraction (0..0.5) over which the wave smoothly anchors to the baseline.
     * @param points Smoothed amplitude data points along the horizontal axis.
     */
    fun calculateWaveformYWithEffectiveHeight(
        x: Float,
        width: Float,
        effectiveHeight: Float,
        effectiveBaselineY: Float,
        safePhase: Float,
        phaseOffset: Float = 0f,
        isCosine: Boolean = false,
        frequencyMultiplier: Float = 1f,
        waveCycles: Float = 2.0f,
        turbulence: Float = 0f,
        edgeTaper: Float = 0.12f,
        points: FloatArray? = null
    ): Float {
        if (width <= 0f || effectiveHeight <= 0f) {
            return effectiveBaselineY
        }
        val normalizedX = (x / width).coerceIn(0f, 1f)
        if (edgeTaper > 0f && (normalizedX <= 0f || normalizedX >= 1f)) {
            return effectiveBaselineY
        }

        val twoPi = (Math.PI * 2.0).toFloat()
        val angle = (safePhase * frequencyMultiplier) + phaseOffset + (normalizedX * waveCycles * twoPi * frequencyMultiplier)
        val rawTrig = if (isCosine) cos(angle.toDouble()).toFloat() else sin(angle.toDouble()).toFloat()

        val baseOscillation = 0.5f * (1f + rawTrig)
        // Multi-harmonic overtone ripples for lively, organic vibration (active only when turbulence > 0)
        val harmonicRipple = if (turbulence > 0f) {
            val ripple2 = sin((angle * 2.0f + safePhase * 0.7f).toDouble()).toFloat() * (0.18f * turbulence)
            val ripple3 = cos((angle * 3.0f - safePhase * 1.1f).toDouble()).toFloat() * (0.09f * turbulence)
            (ripple2 + ripple3) * baseOscillation
        } else {
            0f
        }

        val smoothOscillation = (baseOscillation + harmonicRipple).coerceAtLeast(0f)

        // Smooth boundary envelope W(x): continuous raised-cosine tapering that anchors to the baseline
        // at x = 0 (shift 0) and x = width (intercepting effectiveBaselineY), applied only when edgeTaper > 0f
        val envelope = if (edgeTaper > 0f) {
            val safeEdgeTaper = edgeTaper.coerceIn(0.01f, 0.5f)
            when {
                normalizedX < safeEdgeTaper -> {
                    val t = normalizedX / safeEdgeTaper
                    0.5f * (1f - cos(Math.PI * t.toDouble()).toFloat())
                }
                normalizedX > 1f - safeEdgeTaper -> {
                    val t = (1f - normalizedX) / safeEdgeTaper
                    0.5f * (1f - cos(Math.PI * t.toDouble()).toFloat())
                }
                else -> 1f
            }
        } else {
            1f
        }

        val peakHeight = smoothOscillation * effectiveHeight * envelope
        return effectiveBaselineY - peakHeight
    }

    /**
     * Calculates the waveform Y coordinate at any horizontal pixel position [x].
     *
     * Supports smooth raised-cosine boundary envelope W(x) when [edgeTaper] > 0,
     * and pure continuous unwindowed sinusoidal curvature when [edgeTaper] <= 0.
     */
    fun calculateWaveformY(
        x: Float,
        width: Float,
        maxHeight: Float,
        baselineY: Float,
        overlapPx: Float,
        phase: Float,
        phaseOffset: Float = 0f,
        isCosine: Boolean = false,
        frequencyMultiplier: Float = 1f,
        waveCycles: Float = 2.0f,
        bass: Float = 0f,
        energy: Float = 1f,
        flux: Float = 0f,
        turbulence: Float = 0f,
        edgeTaper: Float = 0.12f,
        points: FloatArray? = null
    ): Float {
        if (width <= 0f || maxHeight <= 0f) {
            return baselineY + overlapPx
        }

        val effectiveBaselineY = baselineY + overlapPx
        val safePhase = if (phase.isFinite()) phase else 0f
        val safeBass = if (bass.isFinite()) bass.coerceIn(0f, 1f) else 0f
        val safeEnergy = if (energy.isFinite()) energy.coerceIn(0.2f, 1.2f) else 0.5f
        val safeFlux = if (flux.isFinite()) flux.coerceIn(0f, 1f) else 0f

        val transientKick = (safeBass * safeBass * 0.10f) + (safeFlux * 0.08f)
        val baseEnergyFactor = (safeBass * 0.5f + ((safeEnergy - 0.2f) / 1.0f).coerceIn(0f, 1f) * 0.5f).coerceIn(0f, 1f)
        val energyModulation = 0.90f + 0.10f * baseEnergyFactor + transientKick
        val effectiveHeight = maxHeight * energyModulation

        return calculateWaveformYWithEffectiveHeight(
            x = x,
            width = width,
            effectiveHeight = effectiveHeight,
            effectiveBaselineY = effectiveBaselineY,
            safePhase = safePhase,
            phaseOffset = phaseOffset,
            isCosine = isCosine,
            frequencyMultiplier = frequencyMultiplier,
            waveCycles = waveCycles,
            turbulence = turbulence,
            edgeTaper = edgeTaper,
            points = points
        )
    }

    /**
     * Populates pre-allocated [Path] and optional [fillPath] with smooth continuous positive waveform curves.
     * Enforces zero heap allocations inside 60/120 FPS draw loops.
     */
    fun populatePositiveWaveformPath(
        path: Path,
        fillPath: Path? = null,
        points: FloatArray,
        width: Float,
        maxHeight: Float,
        baselineY: Float,
        overlapPx: Float,
        phase: Float,
        phaseOffset: Float = 0f,
        isCosine: Boolean = false,
        frequencyMultiplier: Float = 1f,
        waveCycles: Float = 2.0f,
        bass: Float = 0f,
        energy: Float = 1f,
        flux: Float = 0f,
        turbulence: Float = 0f,
        edgeTaper: Float = 0f
    ): Path {
        path.reset()
        fillPath?.reset()
        if (width <= 0f || maxHeight <= 0f) {
            return path
        }

        val effectiveBaselineY = baselineY + overlapPx
        val safePhase = if (phase.isFinite()) phase else 0f
        val safeBass = if (bass.isFinite()) bass.coerceIn(0f, 1f) else 0f
        val safeEnergy = if (energy.isFinite()) energy.coerceIn(0.2f, 1.2f) else 0.5f
        val safeFlux = if (flux.isFinite()) flux.coerceIn(0f, 1f) else 0f

        val transientKick = (safeBass * safeBass * 0.10f) + (safeFlux * 0.08f)
        val baseEnergyFactor = (safeBass * 0.5f + ((safeEnergy - 0.2f) / 1.0f).coerceIn(0f, 1f) * 0.5f).coerceIn(0f, 1f)
        val energyModulation = 0.90f + 0.10f * baseEnergyFactor + transientKick
        val effectiveHeight = maxHeight * energyModulation

        val sampleCount = max(64, points.size)
        val step = width / sampleCount

        val y0 = calculateWaveformYWithEffectiveHeight(
            x = 0f,
            width = width,
            effectiveHeight = effectiveHeight,
            effectiveBaselineY = effectiveBaselineY,
            safePhase = safePhase,
            phaseOffset = phaseOffset,
            isCosine = isCosine,
            frequencyMultiplier = frequencyMultiplier,
            waveCycles = waveCycles,
            turbulence = turbulence,
            edgeTaper = edgeTaper,
            points = points
        )
        path.moveTo(0f, y0)
        fillPath?.let { fill ->
            fill.moveTo(0f, effectiveBaselineY)
            fill.lineTo(0f, y0)
        }

        for (i in 1..sampleCount) {
            val x = if (i == sampleCount) width else i * step
            val y = calculateWaveformYWithEffectiveHeight(
                x = x,
                width = width,
                effectiveHeight = effectiveHeight,
                effectiveBaselineY = effectiveBaselineY,
                safePhase = safePhase,
                phaseOffset = phaseOffset,
                isCosine = isCosine,
                frequencyMultiplier = frequencyMultiplier,
                waveCycles = waveCycles,
                turbulence = turbulence,
                edgeTaper = edgeTaper,
                points = points
            )
            path.lineTo(x, y)
            fillPath?.lineTo(x, y)
        }

        fillPath?.let { fill ->
            fill.lineTo(width, effectiveBaselineY)
            fill.lineTo(0f, effectiveBaselineY)
            fill.close()
        }

        return path
    }
}

// Top-level aliases in com.tigerplayer.engine for convenient direct invocation
fun calculateWaveformYWithEffectiveHeight(
    x: Float,
    width: Float,
    effectiveHeight: Float,
    effectiveBaselineY: Float,
    safePhase: Float,
    phaseOffset: Float = 0f,
    isCosine: Boolean = false,
    frequencyMultiplier: Float = 1f,
    waveCycles: Float = 2.0f,
    turbulence: Float = 0f,
    edgeTaper: Float = 0.12f,
    points: FloatArray? = null
): Float = WaveformComputation.calculateWaveformYWithEffectiveHeight(
    x = x,
    width = width,
    effectiveHeight = effectiveHeight,
    effectiveBaselineY = effectiveBaselineY,
    safePhase = safePhase,
    phaseOffset = phaseOffset,
    isCosine = isCosine,
    frequencyMultiplier = frequencyMultiplier,
    waveCycles = waveCycles,
    turbulence = turbulence,
    edgeTaper = edgeTaper,
    points = points
)

fun calculateWaveformY(
    x: Float,
    width: Float,
    maxHeight: Float,
    baselineY: Float,
    overlapPx: Float,
    phase: Float,
    phaseOffset: Float = 0f,
    isCosine: Boolean = false,
    frequencyMultiplier: Float = 1f,
    waveCycles: Float = 2.0f,
    bass: Float = 0f,
    energy: Float = 1f,
    flux: Float = 0f,
    turbulence: Float = 0f,
    edgeTaper: Float = 0.12f,
    points: FloatArray? = null
): Float = WaveformComputation.calculateWaveformY(
    x = x,
    width = width,
    maxHeight = maxHeight,
    baselineY = baselineY,
    overlapPx = overlapPx,
    phase = phase,
    phaseOffset = phaseOffset,
    isCosine = isCosine,
    frequencyMultiplier = frequencyMultiplier,
    waveCycles = waveCycles,
    bass = bass,
    energy = energy,
    flux = flux,
    turbulence = turbulence,
    edgeTaper = edgeTaper,
    points = points
)

fun populatePositiveWaveformPath(
    path: Path,
    fillPath: Path? = null,
    points: FloatArray,
    width: Float,
    maxHeight: Float,
    baselineY: Float,
    overlapPx: Float,
    phase: Float,
    phaseOffset: Float = 0f,
    isCosine: Boolean = false,
    frequencyMultiplier: Float = 1f,
    waveCycles: Float = 2.0f,
    bass: Float = 0f,
    energy: Float = 1f,
    flux: Float = 0f,
    turbulence: Float = 0f,
    edgeTaper: Float = 0f
): Path = WaveformComputation.populatePositiveWaveformPath(
    path = path,
    fillPath = fillPath,
    points = points,
    width = width,
    maxHeight = maxHeight,
    baselineY = baselineY,
    overlapPx = overlapPx,
    phase = phase,
    phaseOffset = phaseOffset,
    isCosine = isCosine,
    frequencyMultiplier = frequencyMultiplier,
    waveCycles = waveCycles,
    bass = bass,
    energy = energy,
    flux = flux,
    turbulence = turbulence,
    edgeTaper = edgeTaper
)
