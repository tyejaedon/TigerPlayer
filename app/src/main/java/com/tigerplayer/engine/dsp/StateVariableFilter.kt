package com.tigerplayer.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tan

/**
 * 🎨 STATE VARIABLE FILTER (SVF)
 * The Gold Standard for real-time studio equipment.
 * Inherently stable, perfectly coherent, and allows smooth parameter sweeps
 * without the "zipper noise" or "pops" of standard Biquads.
 *
 * Implements Cytomic SVF topology by Andrew Simper.
 *
 * @param type Filter topology type ([FilterType]).
 * @param sampleRate Sampling rate in Hz.
 * @param initialFreq Initial center frequency in Hz.
 * @param initialGain Initial gain boost/cut in dB.
 * @param initialQ Initial quality factor.
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

    /**
     * Sets target filter parameters for smooth coefficient interpolation.
     *
     * @param freq Cutoff/Center frequency in Hz.
     * @param gainDb Gain boost/cut in dB.
     * @param q Quality factor Q.
     * @param sampleRate Sample rate in Hz.
     */
    fun updateParameters(freq: Float, gainDb: Float, q: Float, sampleRate: Int) {
        val f = freq.coerceIn(20f, (sampleRate / 2f) * 0.9f)
        val qVal = q.coerceIn(0.1f, 10f)
        val gain = 10f.pow(gainDb / 40f)

        targetG = tan(PI.toFloat() * f / sampleRate.toFloat())
        targetK = 1f / qVal
        targetA = gain
        isTransitioning = true // Flag the filter to start interpolating math
    }

    /**
     * Filters a single audio sample for the specified channel (0=Left, 1=Right).
     *
     * @param sample Input audio sample.
     * @param channel Channel index (0 for Left, 1 for Right).
     * @return Filtered output sample.
     */
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

    /**
     * Clears internal integrator memory buffers.
     */
    fun reset() {
        ic1eq.fill(0f)
        ic2eq.fill(0f)
    }
}
