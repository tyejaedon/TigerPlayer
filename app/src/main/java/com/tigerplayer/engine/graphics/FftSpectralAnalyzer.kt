package com.tigerplayer.engine

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * ⚡ Real-Time 1024-Point FFT Spectral Analyzer
 *
 * Uses [FloatFFT_1D] with a Hann window function to calculate logarithmic magnitude spectrums
 * across 6 target frequency bands (60 Hz, 250 Hz, 1 kHz, 2.5 kHz, 6 kHz, 12 kHz).
 *
 * @param windowSize Window size length (1024 samples).
 */
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

    /**
     * Configures FFT analyzer frequency bin ranges for target center frequencies.
     *
     * @param sampleRate Sampling rate in Hz.
     * @param bandCentersHz Array of 6 target center frequencies in Hz.
     */
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

    /**
     * Ingests single audio sample into circular frame buffer. Performs FFT when frame is full.
     *
     * @param sample Input mono audio sample.
     */
    fun pushSample(sample: Float) {
        frameBuffer[writeIndex] = sample
        writeIndex += 1
        if (writeIndex >= windowSize) {
            computeBands()
            writeIndex = 0
        }
    }

    /**
     * Retrieves copy of current 6-band logarithmic energy array.
     *
     * @return 6-element [FloatArray].
     */
    fun getLatestBandEnergies(): FloatArray = latestBandEnergies.copyOf()

    /**
     * Resets frame buffers and clears FFT output energies.
     */
    fun reset() {
        writeIndex = 0
        frameBuffer.fill(0f)
        fftBuffer.fill(0f)
        latestBandEnergies.fill(0f)
    }

    /**
     * Applies Hann windowing, executes forward FFT, computes bin magnitudes, and aggregates
     * band energies into logarithmic scales.
     */
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
