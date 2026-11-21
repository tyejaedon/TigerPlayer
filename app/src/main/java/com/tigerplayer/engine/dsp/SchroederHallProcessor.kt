package com.tigerplayer.engine

import kotlin.math.tanh

/**
 * 🏛️ Schroeder Reverb Processor
 *
 * Implements a 3D concert hall reverberator tank using 4 parallel comb filters with
 * one-pole low-pass damping & soft-clipping feedback, followed by series all-pass diffusion
 * and Haas effect early reflections.
 */
internal class SchroederHallProcessor {
    /** Left channel reverb output sample. */
    var outL: Float = 0f
        private set
    /** Right channel reverb output sample. */
    var outR: Float = 0f
        private set

    private var combL: Array<FloatArray> = emptyArray()
    private var combR: Array<FloatArray> = emptyArray()
    private var combIndexL = IntArray(0)
    private var combIndexR = IntArray(0)
    private var combStoreL = FloatArray(0)
    private var combStoreR = FloatArray(0)

    // REVERB DAMPING: One-pole LPF states per comb filter
    private var combDampL = FloatArray(0)
    private var combDampR = FloatArray(0)
    private val dampingFactor = 0.45f

    private var allPassL: Array<FloatArray> = emptyArray()
    private var allPassR: Array<FloatArray> = emptyArray()
    private var allPassIndexL = IntArray(0)
    private var allPassIndexR = IntArray(0)

    private var haasL = FloatArray(1)
    private var haasR = FloatArray(1)
    private var haasIndexL = 0
    private var haasIndexR = 0

    private val combMs = floatArrayOf(31.1f, 37.1f, 41.1f, 43.7f)
    private val allPassMs = floatArrayOf(5.0f, 1.7f)

    /**
     * Configures comb, all-pass, and Haas delay buffers based on sample rate.
     *
     * @param sampleRate Target audio sampling rate in Hz.
     */
    fun configure(sampleRate: Int) {
        combL = Array(combMs.size) { i -> FloatArray(((sampleRate * combMs[i]) / 1000f).toInt().coerceAtLeast(1)) }
        combR = Array(combMs.size) { i -> FloatArray(((sampleRate * (combMs[i] + 0.8f)) / 1000f).toInt().coerceAtLeast(1)) }
        combIndexL = IntArray(combMs.size)
        combIndexR = IntArray(combMs.size)
        combStoreL = FloatArray(combMs.size)
        combStoreR = FloatArray(combMs.size)
        combDampL = FloatArray(combMs.size)
        combDampR = FloatArray(combMs.size)

        allPassL = Array(allPassMs.size) { i -> FloatArray(((sampleRate * allPassMs[i]) / 1000f).toInt().coerceAtLeast(1)) }
        allPassR = Array(allPassMs.size) { i -> FloatArray(((sampleRate * (allPassMs[i] + 0.3f)) / 1000f).toInt().coerceAtLeast(1)) }
        allPassIndexL = IntArray(allPassMs.size)
        allPassIndexR = IntArray(allPassMs.size)

        haasL = FloatArray((sampleRate * 0.013f).toInt().coerceAtLeast(1))
        haasR = FloatArray((sampleRate * 0.019f).toInt().coerceAtLeast(1))
        haasIndexL = 0
        haasIndexR = 0
    }

    /**
     * Renders Schroeder hall reverb for stereo sample pair.
     * Updates [outL] and [outR] output fields.
     *
     * @param inputL Left channel input sample.
     * @param inputR Right channel input sample.
     * @param channels Channel count (1 for Mono, 2 for Stereo).
     */
    fun process(inputL: Float, inputR: Float, channels: Int) {
        if (combL.isEmpty()) {
            outL = inputL
            outR = if (channels == 2) inputR else inputL
            return
        }

        val stereoR = if (channels == 2) inputR else inputL
        val earlyL = writeDelay(haasL, inputL + stereoR * 0.18f, true)
        val earlyR = writeDelay(haasR, stereoR + inputL * 0.18f, false)
        val diffuseIn = ((inputL + stereoR) * 0.5f + (earlyL + earlyR) * 0.25f) * 0.95f

        val tailL = processTank(
            diffuseIn,
            combL,
            combIndexL,
            combDampL,
            allPassL,
            allPassIndexL
        )
        val tailR = processTank(
            diffuseIn,
            combR,
            combIndexR,
            combDampR,
            allPassR,
            allPassIndexR
        )

        outL = (inputL * 0.70f + earlyL * 0.10f + tailL * 0.20f).coerceIn(-1.0f, 1.0f)
        outR = (stereoR * 0.70f + earlyR * 0.10f + tailR * 0.20f).coerceIn(-1.0f, 1.0f)
    }

    /**
     * Resets delay buffers and internal filter states.
     */
    fun reset() {
        combL.forEach { it.fill(0f) }
        combR.forEach { it.fill(0f) }
        allPassL.forEach { it.fill(0f) }
        allPassR.forEach { it.fill(0f) }
        combIndexL.fill(0)
        combIndexR.fill(0)
        combStoreL.fill(0f)
        combStoreR.fill(0f)
        combDampL.fill(0f)
        combDampR.fill(0f)
        allPassIndexL.fill(0)
        allPassIndexR.fill(0)
        haasL.fill(0f)
        haasR.fill(0f)
        haasIndexL = 0
        haasIndexR = 0
        outL = 0f
        outR = 0f
    }

    /**
     * Pushes sample to Haas early reflection circular delay buffer.
     *
     * @param buffer Circular delay buffer float array.
     * @param input Incoming sample value.
     * @param isLeft True for Left channel buffer; false for Right channel.
     * @return Delayed sample value.
     */
    private fun writeDelay(buffer: FloatArray, input: Float, isLeft: Boolean): Float {
        val index = if (isLeft) haasIndexL else haasIndexR
        val delayed = buffer[index]
        buffer[index] = input

        if (isLeft) {
            haasIndexL = (index + 1) % buffer.size
        } else {
            haasIndexR = (index + 1) % buffer.size
        }

        return delayed
    }

    /**
     * Processes comb filter bank with low-pass damping & soft-clipping feedback, followed by all-pass diffusion.
     *
     * @param input Input sample.
     * @param comb Comb filter delay lines.
     * @param combIndices Comb delay line read/write indices.
     * @param combDamp Comb damping state array.
     * @param allPass All-pass filter delay lines.
     * @param allPassIndices All-pass delay line indices.
     * @return Reverb tail sample value.
     */
    private fun processTank(
        input: Float,
        comb: Array<FloatArray>,
        combIndices: IntArray,
        combDamp: FloatArray,
        allPass: Array<FloatArray>,
        allPassIndices: IntArray
    ): Float {
        var sum = 0f

        for (i in comb.indices) {
            val buffer = comb[i]
            val idx = combIndices[i]
            val delayed = buffer[idx]

            // DAMPING RITUAL: Filter the feedback signal
            combDamp[i] += (delayed - combDamp[i]) * dampingFactor

            // FIX: Apply tanh soft-clipping inside the feedback loop
            val feedback = combDamp[i] * 0.79f
            val safeFeedback = tanh(feedback)

            buffer[idx] = input + safeFeedback
            combIndices[i] = (idx + 1) % buffer.size
            sum += delayed
        }

        var output = sum * (1f / comb.size.coerceAtLeast(1))

        for (i in allPass.indices) {
            val buffer = allPass[i]
            val idx = allPassIndices[i]
            val delayed = buffer[idx]
            buffer[idx] = output + delayed * 0.5f
            output = delayed - output
            allPassIndices[i] = (idx + 1) % buffer.size
        }

        return output
    }
}
