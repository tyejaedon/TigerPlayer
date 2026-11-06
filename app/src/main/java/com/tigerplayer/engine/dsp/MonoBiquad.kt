package com.tigerplayer.engine

import kotlin.math.cos
import kotlin.math.sin

/**
 * Direct Form I Mono Biquad Filter.
 * Used for audio crossover networks and bandpass spectral analysis.
 */
internal class MonoBiquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    /**
     * Configures low-pass biquad coefficients.
     *
     * @param sampleRate Sampling rate in Hz.
     * @param cutoffHz Cutoff frequency in Hz.
     * @param q Quality factor Q.
     */
    fun configureLowPass(sampleRate: Float, cutoffHz: Float, q: Float) {
        configure(sampleRate, cutoffHz, q, highPass = false)
    }

    /**
     * Configures high-pass biquad coefficients.
     *
     * @param sampleRate Sampling rate in Hz.
     * @param cutoffHz Cutoff frequency in Hz.
     * @param q Quality factor Q.
     */
    fun configureHighPass(sampleRate: Float, cutoffHz: Float, q: Float) {
        configure(sampleRate, cutoffHz, q, highPass = true)
    }

    /**
     * Configures band-pass biquad coefficients.
     *
     * @param sampleRate Sampling rate in Hz.
     * @param centerHz Center frequency in Hz.
     * @param q Quality factor Q.
     */
    fun configureBandPass(sampleRate: Float, centerHz: Float, q: Float) {
        val omega = (2.0 * Math.PI * (centerHz.coerceAtLeast(20f) / sampleRate)).toFloat()
        val sinW = sin(omega)
        val cosW = cos(omega)
        val alpha = sinW / (2f * q.coerceAtLeast(0.1f))

        val rawA0 = 1f + alpha
        val rawA1 = -2f * cosW

        val rawB0 = alpha
        val rawB1 = 0f
        val rawB2 = -alpha

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = (-2f * cosW) / rawA0
    }

    /**
     * Computes low-pass or high-pass biquad coefficients.
     */
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
        a2 = (1f - alpha) / rawA0
    }

    /**
     * Filters single mono sample.
     *
     * @param input Input audio sample.
     * @return Filtered output sample.
     */
    fun process(input: Float): Float {
        val out = b0 * input + z1
        z1 = b1 * input - a1 * out + z2
        z2 = b2 * input - a2 * out
        return out
    }

    /**
     * Clears internal z-plane delay memory registers.
     */
    fun reset() {
        z1 = 0f
        z2 = 0f
    }
}
