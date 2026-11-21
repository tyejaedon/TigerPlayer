package com.tigerplayer.engine

import com.tigerplayer.data.local.CrossfeedPreset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Linear-phase stereophonic-to-binaural Crossfeed processor modeled on the Bauer / Linkwitz
 * headphone acoustic transfer function.
 *
 * Uses a Type-I symmetric linear-phase FIR low-pass filter (-6 dB/octave roll-off above ~700 Hz)
 * on the stereo difference signal (`L - R`), delayed by a sample-rate-aware Interaural Time
 * Delay (~260-300 us) via fractional linear interpolation, and recombined symmetrically around a
 * group-delay-aligned direct path:
 *
 * ```
 * sideLp[n] = (h_lp * (L - R))[n]
 * cross[n]  = gain * sideLp[n - delaySamples]
 * L_out[n]  = L[n - groupDelay] - cross[n]
 * R_out[n]  = R[n - groupDelay] + cross[n]
 * ```
 *
 * Because center/mono content (`L == R`) yields `L - R == 0`, center vocals experience 0.00 dB
 * flat magnitude response, zero phase distortion, and zero comb filtering, while hard-panned
 * sources crossfeed naturally to the opposite ear below 700 Hz.
 *
 * All buffers and filter coefficients are pre-allocated with zero heap allocations in [configure]
 * and [process].
 */
internal class BinauralCrossfeedProcessor {

    companion object {
        private const val MIN_FIR_HALF_LENGTH = 16
        private const val MAX_FIR_HALF_LENGTH = 192
        private const val MAX_FIR_TAPS = MAX_FIR_HALF_LENGTH * 2 + 1
        private const val HISTORY_BUFFER_SIZE = 512
        private const val HISTORY_MASK = HISTORY_BUFFER_SIZE - 1
        private const val ITD_BUFFER_SIZE = 256
        private const val ITD_MASK = ITD_BUFFER_SIZE - 1
        private const val DEFAULT_CUTOFF_HZ = 700f
        private const val GAIN_SMOOTHING_SEC = 0.008f
        private const val QUADRATURE_BINS = 1024
        private const val TUKEY_ALPHA = 0.4
    }

    /** Left channel crossfeed output sample. */
    var outL: Float = 0f
        private set

    /** Right channel crossfeed output sample. */
    var outR: Float = 0f
        private set

    private val firCoeffs = FloatArray(MAX_FIR_TAPS)
    private val designScratch = DoubleArray(MAX_FIR_HALF_LENGTH + 1)
    private val sideHistory = FloatArray(HISTORY_BUFFER_SIZE)
    private val directDelayL = FloatArray(HISTORY_BUFFER_SIZE)
    private val directDelayR = FloatArray(HISTORY_BUFFER_SIZE)
    private val crossDelayBuffer = FloatArray(ITD_BUFFER_SIZE)

    private var sampleRate = 48_000
    private var configuredSampleRate = -1
    private var configuredCutoffHz = -1f
    private var firHalfLength = 48
    private var historyIndex = 0
    private var itdIndex = 0
    private var currentFeedGain = 0f
    private var currentDelaySamples = 0f
    private var smoothingCoef = 0.0026f
    private var isStatePrimed = false
    private var wasBypassed = true

    /**
     * Configures the linear-phase FIR low-pass kernel and smoothing time constant for [sampleRate].
     * Reuses pre-allocated arrays with zero allocations.
     */
    fun configure(sampleRate: Int, cutoffHz: Float = DEFAULT_CUTOFF_HZ) {
        val safeRate = sampleRate.coerceAtLeast(8_000)
        val safeCutoff = cutoffHz.coerceIn(100f, safeRate * 0.45f)
        this.sampleRate = safeRate
        smoothingCoef = 1f - exp(-1f / (safeRate * GAIN_SMOOTHING_SEC))
        firHalfLength = (safeRate * 0.001f).roundToInt().coerceIn(MIN_FIR_HALF_LENGTH, MAX_FIR_HALF_LENGTH)

        if (configuredSampleRate != safeRate || configuredCutoffHz != safeCutoff) {
            designLinearPhaseLowPassKernel(safeRate, safeCutoff, firHalfLength)
            configuredSampleRate = safeRate
            configuredCutoffHz = safeCutoff
        }
        reset()
    }

    /** Returns the symmetric FIR constant group delay in samples. */
    fun getGroupDelaySamples(): Int = firHalfLength

    /** Clears all delay lines and smoothers while preserving the configured FIR kernel. */
    fun reset() {
        sideHistory.fill(0f)
        directDelayL.fill(0f)
        directDelayR.fill(0f)
        crossDelayBuffer.fill(0f)
        historyIndex = 0
        itdIndex = 0
        currentFeedGain = 0f
        currentDelaySamples = 0f
        isStatePrimed = false
        wasBypassed = true
        outL = 0f
        outR = 0f
    }

    /**
     * Processes one stereo (or mono) frame in 32-bit float PCM and stores results in [outL] and [outR].
     * Zero allocations; zero-latency bypass when [preset] is [CrossfeedPreset.OFF], [bypassForSpeaker]
     * is true, [isBitPerfect] is true, or [channels] != 2.
     */
    fun process(
        inputL: Float,
        inputR: Float,
        channels: Int,
        preset: CrossfeedPreset,
        bypassForSpeaker: Boolean,
        isBitPerfect: Boolean
    ) {
        val shouldEnable = channels == 2 && preset != CrossfeedPreset.OFF && !bypassForSpeaker && !isBitPerfect
        val targetGain = if (shouldEnable) preset.feedGain else 0f
        val maxDelay = (ITD_BUFFER_SIZE - 2).toFloat()
        val targetDelay = if (shouldEnable) {
            (sampleRate * preset.delayMicros * 1e-6f).coerceIn(0f, maxDelay)
        } else {
            currentDelaySamples
        }

        if (!isStatePrimed) {
            currentFeedGain = targetGain
            currentDelaySamples = targetDelay
            wasBypassed = !shouldEnable
            isStatePrimed = true
        }

        if (!shouldEnable && currentFeedGain <= 1e-5f) {
            currentFeedGain = 0f
            wasBypassed = true
            outL = inputL
            outR = if (channels == 2) inputR else inputL
            return
        }

        if (wasBypassed) {
            sideHistory.fill(0f)
            directDelayL.fill(0f)
            directDelayR.fill(0f)
            crossDelayBuffer.fill(0f)
            historyIndex = 0
            itdIndex = 0
            currentDelaySamples = targetDelay
            wasBypassed = false
        }

        currentFeedGain += (targetGain - currentFeedGain) * smoothingCoef
        if (shouldEnable) {
            currentDelaySamples += (targetDelay - currentDelaySamples) * smoothingCoef
        }

        directDelayL[historyIndex] = inputL
        directDelayR[historyIndex] = inputR
        sideHistory[historyIndex] = inputL - inputR

        val centerIdx = (historyIndex - firHalfLength) and HISTORY_MASK
        var sideLp = firCoeffs[firHalfLength] * sideHistory[centerIdx]
        for (k in 1..firHalfLength) {
            val idxBefore = (centerIdx - k) and HISTORY_MASK
            val idxAfter = (centerIdx + k) and HISTORY_MASK
            sideLp += firCoeffs[firHalfLength + k] * (sideHistory[idxBefore] + sideHistory[idxAfter])
        }

        crossDelayBuffer[itdIndex] = sideLp
        val delayInt = currentDelaySamples.toInt()
        val delayFrac = currentDelaySamples - delayInt.toFloat()
        val itdIdx0 = (itdIndex - delayInt) and ITD_MASK
        val itdIdx1 = (itdIdx0 - 1) and ITD_MASK
        val delayedSideLp = crossDelayBuffer[itdIdx0] * (1f - delayFrac) + crossDelayBuffer[itdIdx1] * delayFrac
        itdIndex = (itdIndex + 1) and ITD_MASK

        val alignedL = directDelayL[centerIdx]
        val alignedR = directDelayR[centerIdx]
        val crossTerm = currentFeedGain * delayedSideLp

        outL = alignedL - crossTerm
        outR = alignedR + crossTerm

        historyIndex = (historyIndex + 1) and HISTORY_MASK
    }

    private fun designLinearPhaseLowPassKernel(sampleRate: Int, cutoffHz: Float, halfLength: Int) {
        for (k in 0..halfLength) {
            designScratch[k] = 0.0
        }

        val invCutoffSq = 1.0 / (cutoffHz.toDouble() * cutoffHz.toDouble())
        val twoPi = 2.0 * PI

        for (m in 0 until QUADRATURE_BINS) {
            val w = PI * (m + 0.5) / QUADRATURE_BINS
            val f = (w / twoPi) * sampleRate.toDouble()
            val mag = 1.0 / sqrt(1.0 + f * f * invCutoffSq)

            val cosW = cos(w)
            var cPrev2 = 1.0
            var cPrev1 = cosW
            designScratch[0] += mag
            if (halfLength >= 1) {
                designScratch[1] += mag * cPrev1
            }
            for (k in 2..halfLength) {
                val cCurr = 2.0 * cosW * cPrev1 - cPrev2
                designScratch[k] += mag * cCurr
                cPrev2 = cPrev1
                cPrev1 = cCurr
            }
        }

        val numTaps = halfLength * 2 + 1
        val denom = (numTaps - 1).toDouble()
        val taperThreshold = 1.0 - TUKEY_ALPHA * 0.5
        var sum = 0.0

        for (k in 0..halfLength) {
            val r = (halfLength + k).toDouble() / denom
            val win = if (r > taperThreshold) {
                0.5 * (1.0 + cos(PI * (2.0 * (1.0 - r) / TUKEY_ALPHA - 1.0)))
            } else {
                1.0
            }
            val coeff = ((designScratch[k] / QUADRATURE_BINS) * win).toFloat()
            firCoeffs[halfLength + k] = coeff
            firCoeffs[halfLength - k] = coeff
            sum += if (k == 0) coeff.toDouble() else 2.0 * coeff.toDouble()
        }

        val norm = if (sum > 1e-12) (1.0 / sum).toFloat() else 1.0f
        for (i in 0 until numTaps) {
            firCoeffs[i] *= norm
        }
    }
}
