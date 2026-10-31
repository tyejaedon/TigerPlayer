package com.tigerplayer.utils

import com.tigerplayer.engine.AcousticNode
import com.tigerplayer.engine.FilterType
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
        // Coerce inputs to safe mathematical bounds
        val sr = sampleRate.toDouble().coerceAtLeast(8000.0)
        val f0 = freq.toDouble().coerceIn(20.0, (sr / 2.0) * 0.95)
        val g = gainDb.toDouble().coerceIn(-24.0, 24.0)
        val qVal = q.toDouble().coerceIn(0.1, 10.0)

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
     * Optimized for high-performance UI rendering.
     */
    fun magnitudeAt(freq: Float, coeffs: Coefficients, sampleRate: Float): Float {
        val f = freq.toDouble().coerceIn(20.0, (sampleRate.toDouble() / 2.0) * 0.95)
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

        if (denMagSq < 1e-18) return 0f

        val magnitude = sqrt(numMagSq / denMagSq)
        val magnitudeDb = 20.0 * log10(magnitude.coerceAtLeast(1e-12))
        return magnitudeDb.toFloat()
    }

    /**
     * Calculates the true maximum positive peak of the composite filter response curve (in dB)
     * across the audible frequency spectrum (20 Hz - 20 kHz) for a list of acoustic nodes.
     *
     * Resolves the Fletcher-Munson equal-loudness issue where naive linear decibel summation
     * over-attenuates non-overlapping frequency bands. Also accurately models cumulative
     * compounding of overlapping filters and cancellation between opposing boost/cut nodes.
     *
     * @param nodes List of [AcousticNode] filters.
     * @param sampleRate Sampling rate in Hz.
     * @return Maximum positive composite gain in dB (>= 0f). Returns 0f if all points are <= 0 dB.
     */
    fun calculateCompositePeakGainDb(nodes: List<AcousticNode>, sampleRate: Float): Float {
        if (nodes.isEmpty()) return 0f
        // Fast path: if no bands have positive boost, composite gain cannot exceed 0 dB
        if (nodes.none { it.gainDb > 0.001f }) return 0f

        val sr = sampleRate.coerceAtLeast(8000f)
        val maxNyquistFreq = (sr / 2f) * 0.95f
        val maxEvalFreq = min(20000f, maxNyquistFreq)
        val minEvalFreq = 20f

        val activeNodes = nodes.filter { abs(it.gainDb) > 0.001f }
        if (activeNodes.isEmpty() || activeNodes.none { it.gainDb > 0.001f }) return 0f

        val coeffs = activeNodes.map { node ->
            design(node.filterType, node.frequency, node.gainDb, node.qFactor, sr)
        }

        fun compositeAt(freq: Float): Float {
            var sumDb = 0f
            for (i in coeffs.indices) {
                sumDb += magnitudeAt(freq, coeffs[i], sr)
            }
            return sumDb
        }

        var maxGainDb = 0f

        // 1. Evaluate at the center frequency and immediate sidebands of each active node
        for (node in activeNodes) {
            val f0 = node.frequency.coerceIn(minEvalFreq, maxEvalFreq)
            val candidates = floatArrayOf(
                f0,
                (f0 * 0.95f).coerceIn(minEvalFreq, maxEvalFreq),
                (f0 * 1.05f).coerceIn(minEvalFreq, maxEvalFreq),
                (f0 * 0.90f).coerceIn(minEvalFreq, maxEvalFreq),
                (f0 * 1.10f).coerceIn(minEvalFreq, maxEvalFreq)
            )
            for (f in candidates) {
                val gain = compositeAt(f)
                if (gain > maxGainDb) {
                    maxGainDb = gain
                }
            }
        }

        // 2. Evaluate geometric midpoints between active nodes to catch inter-band compounding
        for (i in activeNodes.indices) {
            for (j in i + 1 until activeNodes.size) {
                val fMid = sqrt(activeNodes[i].frequency * activeNodes[j].frequency)
                    .coerceIn(minEvalFreq, maxEvalFreq)
                val gain = compositeAt(fMid)
                if (gain > maxGainDb) {
                    maxGainDb = gain
                }
            }
        }

        // 3. Dense logarithmic grid sweep across audible spectrum (150 points)
        val numPoints = 150
        val minLog = log10(minEvalFreq.toDouble())
        val maxLog = log10(maxEvalFreq.toDouble())
        val stepLog = (maxLog - minLog) / numPoints

        var bestFreq = minEvalFreq
        for (i in 0..numPoints) {
            val f = 10.0.pow(minLog + i * stepLog).toFloat()
            val gain = compositeAt(f)
            if (gain > maxGainDb) {
                maxGainDb = gain
                bestFreq = f
            }
        }

        // 4. Local golden-section search refinement around the peak candidate to achieve sub-0.01 dB precision
        if (maxGainDb > 0f) {
            var low = (bestFreq * 0.85f).coerceAtLeast(minEvalFreq)
            var high = (bestFreq * 1.15f).coerceAtMost(maxEvalFreq)
            val invPhi = 0.618033988749895f
            var c = high - (high - low) * invPhi
            var d = low + (high - low) * invPhi
            var fc = compositeAt(c)
            var fd = compositeAt(d)

            for (iter in 0 until 8) {
                if (fc > fd) {
                    high = d
                    d = c
                    fd = fc
                    c = high - (high - low) * invPhi
                    fc = compositeAt(c)
                } else {
                    low = c
                    c = d
                    fc = fd
                    d = low + (high - low) * invPhi
                    fd = compositeAt(d)
                }
            }
            val refinedGain = max(fc, fd)
            if (refinedGain > maxGainDb) {
                maxGainDb = refinedGain
            }
        }

        return max(0f, maxGainDb)
    }
}