package com.example.tigerplayer.utils

import com.example.tigerplayer.engine.FilterType
import kotlin.math.*

/**
 * 📐 HIGH-PRECISION BIQUAD FILTER DESIGNER
 * Implements Robert Bristow-Johnson's Audio EQ Cookbook equations with strict
 * bounding guards to prevent NaN/Infinity leaks on both visualizer and DSP.
 */
object BiquadDesigner {

    class Coefficients(
        val b0: Double, val b1: Double, val b2: Double,
        val a0: Double, val a1: Double, val a2: Double
    )

    fun design(
        type: FilterType,
        freq: Float,
        gainDb: Float,
        q: Float,
        sampleRate: Float
    ): Coefficients {
        // Coerce inputs to safe mathematical bounds (avoiding Nyquist limit and negative parameters)
        val f0 = freq.coerceIn(20f, (sampleRate / 2f) - 100f).toDouble()
        val g = gainDb.coerceIn(-24f, 24f).toDouble()
        val qVal = q.coerceIn(0.1f, 10f).toDouble()
        val sr = sampleRate.toDouble()

        val omega = 2.0 * PI * f0 / sr
        val cosOmega = cos(omega)
        val sinOmega = sin(omega)
        val alpha = sinOmega / (2.0 * qVal)
        val a = 10.0.pow(g / 40.0)

        return when (type) {
            FilterType.LOW_SHELF -> {
                val beta = sqrt(a) / qVal
                val b0 = a * ((a + 1.0) - (a - 1.0) * cosOmega + beta * sinOmega)
                val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosOmega)
                val b2 = a * ((a + 1.0) - (a - 1.0) * cosOmega - beta * sinOmega)
                val a0 = (a + 1.0) + (a - 1.0) * cosOmega + beta * sinOmega
                val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosOmega)
                val a2 = (a + 1.0) + (a - 1.0) * cosOmega - beta * sinOmega
                Coefficients(b0, b1, b2, a0, a1, a2)
            }
            FilterType.HIGH_SHELF -> {
                val beta = sqrt(a) / qVal
                val b0 = a * ((a + 1.0) + (a - 1.0) * cosOmega + beta * sinOmega)
                val b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosOmega)
                val b2 = a * ((a + 1.0) + (a - 1.0) * cosOmega - beta * sinOmega)
                val a0 = (a + 1.0) - (a - 1.0) * cosOmega + beta * sinOmega
                val a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosOmega)
                val a2 = (a + 1.0) - (a - 1.0) * cosOmega - beta * sinOmega
                Coefficients(b0, b1, b2, a0, a1, a2)
            }
            FilterType.PEAKING -> {
                val b0 = 1.0 + alpha * a
                val b1 = -2.0 * cosOmega
                val b2 = 1.0 - alpha * a
                val a0 = 1.0 + alpha / a
                val a1 = -2.0 * cosOmega
                val a2 = 1.0 - alpha / a
                Coefficients(b0, b1, b2, a0, a1, a2)
            }
        }
    }

    /**
     * Computes the magnitude response (in dB) of a biquad filter at any given frequency.
     */
    fun magnitudeAt(freq: Float, coeffs: Coefficients, sampleRate: Float): Float {
        val f = freq.coerceIn(20f, (sampleRate / 2f) - 100f).toDouble()
        val phi = 2.0 * PI * f / sampleRate.toDouble()
        val cos1 = cos(phi)
        val cos2 = cos(2.0 * phi)
        val sin1 = sin(phi)
        val sin2 = sin(2.0 * phi)

        val numReal = coeffs.b0 + coeffs.b1 * cos1 + coeffs.b2 * cos2
        val numImag = -(coeffs.b1 * sin1 + coeffs.b2 * sin2)
        val denReal = coeffs.a0 + coeffs.a1 * cos1 + coeffs.a2 * cos2
        val denImag = -(coeffs.a1 * sin1 + coeffs.a2 * sin2)

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        if (denMagSq < 1e-15) return 0f // Protect from division by zero

        val magnitude = sqrt(numMagSq / denMagSq)
        val magnitudeDb = 20.0 * log10(magnitude.coerceAtLeast(1e-10))
        return magnitudeDb.toFloat()
    }
}