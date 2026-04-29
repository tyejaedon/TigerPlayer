package com.example.tigerplayer.utils

import com.example.tigerplayer.engine.dsp.AcousticNode
import com.example.tigerplayer.engine.dsp.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 🎧 REAL BIQUAD COEFFICIENT GENERATOR
 */
data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a0: Double,
    val a1: Double,
    val a2: Double
)

object BiquadDesigner {

    fun design(node: AcousticNode, sampleRate: Float = 44100f): BiquadCoefficients {
        val A = 10.0.pow(node.gainDb / 40.0)
        val w0 = 2.0 * PI * node.frequency / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2 * node.qFactor)

        // 🔥 THE FIX: Changed 'node.type' to 'node.filterType'
        return when (node.filterType) {

            FilterType.PEAKING -> {
                val b0 = 1 + alpha * A
                val b1 = -2 * cosW0
                val b2 = 1 - alpha * A
                val a0 = 1 + alpha / A
                val a1 = -2 * cosW0
                val a2 = 1 - alpha / A
                BiquadCoefficients(b0, b1, b2, a0, a1, a2)
            }

            FilterType.LOW_SHELF -> {
                val sqrtA = sqrt(A)
                val twoSqrtAAlpha = 2 * sqrtA * alpha

                val b0 = A * ((A + 1) - (A - 1) * cosW0 + twoSqrtAAlpha)
                val b1 = 2 * A * ((A - 1) - (A + 1) * cosW0)
                val b2 = A * ((A + 1) - (A - 1) * cosW0 - twoSqrtAAlpha)
                val a0 = (A + 1) + (A - 1) * cosW0 + twoSqrtAAlpha
                val a1 = -2 * ((A - 1) + (A + 1) * cosW0)
                val a2 = (A + 1) + (A - 1) * cosW0 - twoSqrtAAlpha

                BiquadCoefficients(b0, b1, b2, a0, a1, a2)
            }

            FilterType.HIGH_SHELF -> {
                val sqrtA = sqrt(A)
                val twoSqrtAAlpha = 2 * sqrtA * alpha

                val b0 = A * ((A + 1) + (A - 1) * cosW0 + twoSqrtAAlpha)
                val b1 = -2 * A * ((A - 1) + (A + 1) * cosW0)
                val b2 = A * ((A + 1) + (A - 1) * cosW0 - twoSqrtAAlpha)
                val a0 = (A + 1) - (A - 1) * cosW0 + twoSqrtAAlpha
                val a1 = 2 * ((A - 1) - (A + 1) * cosW0)
                val a2 = (A + 1) - (A - 1) * cosW0 - twoSqrtAAlpha

                BiquadCoefficients(b0, b1, b2, a0, a1, a2)
            }
        }
    }

    /**
     * 🎯 TRUE FREQUENCY RESPONSE (|H(e^jw)|)
     */
    fun magnitudeAt(freq: Float, coeff: BiquadCoefficients, sampleRate: Float = 44100f): Float {
        val w = 2 * PI * freq / sampleRate
        val cosW = cos(w)
        val sinW = sin(w)

        val numeratorReal = coeff.b0 + coeff.b1 * cosW + coeff.b2 * cos(2 * w)
        val numeratorImag = coeff.b1 * sinW + coeff.b2 * sin(2 * w)

        val denominatorReal = coeff.a0 + coeff.a1 * cosW + coeff.a2 * cos(2 * w)
        val denominatorImag = coeff.a1 * sinW + coeff.a2 * sin(2 * w)

        val num = sqrt(numeratorReal.pow(2) + numeratorImag.pow(2))
        val den = sqrt(denominatorReal.pow(2) + denominatorImag.pow(2))

        val magnitude = num / den
        return (20 * log10(magnitude)).toFloat()
    }
}