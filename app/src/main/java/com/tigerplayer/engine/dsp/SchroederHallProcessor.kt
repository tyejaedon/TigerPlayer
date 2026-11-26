package com.tigerplayer.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Pre-allocated acoustic tuning parameters for [SchroederHallProcessor].
 */
internal data class HallTuning(
    val preDelayMs: Float,
    val targetRt60Sec: Float,
    val dampingFactor: Float,
    val highPassCutoffHz: Float,
    val dryMix: Float,
    val earlyMix: Float,
    val tailMix: Float
) {
    companion object {
        val SPEAKER = HallTuning(
            preDelayMs = 16.0f,
            targetRt60Sec = 1.4f,
            dampingFactor = 0.55f,
            highPassCutoffHz = 200.0f,
            dryMix = 0.72f,
            earlyMix = 0.08f,
            tailMix = 0.18f
        )

        val HEADPHONES = HallTuning(
            preDelayMs = 22.0f,
            targetRt60Sec = 2.0f,
            dampingFactor = 0.38f,
            highPassCutoffHz = 100.0f,
            dryMix = 0.70f,
            earlyMix = 0.10f,
            tailMix = 0.20f
        )
    }
}

/**
 * Schroeder Reverb Processor
 *
 * Implements a 3D concert hall reverberator tank using 4 parallel comb filters with
 * one-pole low-pass damping & soft-clipping feedback, followed by series all-pass diffusion,
 * Haas effect early reflections, a sample-rate-scaled pre-delay line, and a one-pole
 * high-pass filter on the reverb send.
 */
internal class SchroederHallProcessor {
    companion object {
        private const val DENORMAL_THRESHOLD = 1e-18f
    }

    /** Left channel reverb output sample. */
    var outL: Float = 0f
        private set
    /** Right channel reverb output sample. */
    var outR: Float = 0f
        private set

    internal var lastTailL: Float = 0f
        private set
    internal var lastTailR: Float = 0f
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

    private var allPassL: Array<FloatArray> = emptyArray()
    private var allPassR: Array<FloatArray> = emptyArray()
    private var allPassIndexL = IntArray(0)
    private var allPassIndexR = IntArray(0)

    private var haasL = FloatArray(1)
    private var haasR = FloatArray(1)
    private var haasIndexL = 0
    private var haasIndexR = 0

    private var preDelayBuffer = FloatArray(1)
    private var preDelayWriteIndex = 0
    private var speakerPreDelaySamples = 1
    private var headphonePreDelaySamples = 1

    private var speakerCombFeedbacksL = FloatArray(0)
    private var speakerCombFeedbacksR = FloatArray(0)
    private var headphoneCombFeedbacksL = FloatArray(0)
    private var headphoneCombFeedbacksR = FloatArray(0)

    private var speakerHpfAlpha = 0.97f
    private var headphoneHpfAlpha = 0.985f
    private var hpfPrevIn = 0f
    private var hpfPrevOut = 0f

    private val combMs = floatArrayOf(31.1f, 37.1f, 41.1f, 43.7f)
    private val allPassMs = floatArrayOf(5.0f, 1.7f)

    /**
     * Configures comb, all-pass, Haas, pre-delay buffers, and preset coefficients based on sample rate.
     *
     * @param sampleRate Target audio sampling rate in Hz.
     */
    fun configure(sampleRate: Int) {
        val safeRate = sampleRate.coerceAtLeast(8000)
        combL = Array(combMs.size) { i -> FloatArray(((safeRate * combMs[i]) / 1000f).toInt().coerceAtLeast(1)) }
        combR = Array(combMs.size) { i -> FloatArray(((safeRate * (combMs[i] + 0.8f)) / 1000f).toInt().coerceAtLeast(1)) }
        combIndexL = IntArray(combMs.size)
        combIndexR = IntArray(combMs.size)
        combStoreL = FloatArray(combMs.size)
        combStoreR = FloatArray(combMs.size)
        combDampL = FloatArray(combMs.size)
        combDampR = FloatArray(combMs.size)

        allPassL = Array(allPassMs.size) { i -> FloatArray(((safeRate * allPassMs[i]) / 1000f).toInt().coerceAtLeast(1)) }
        allPassR = Array(allPassMs.size) { i -> FloatArray(((safeRate * (allPassMs[i] + 0.3f)) / 1000f).toInt().coerceAtLeast(1)) }
        allPassIndexL = IntArray(allPassMs.size)
        allPassIndexR = IntArray(allPassMs.size)

        haasL = FloatArray((safeRate * 0.013f).toInt().coerceAtLeast(1))
        haasR = FloatArray((safeRate * 0.019f).toInt().coerceAtLeast(1))
        haasIndexL = 0
        haasIndexR = 0

        speakerPreDelaySamples = ((safeRate * HallTuning.SPEAKER.preDelayMs) / 1000f).roundToInt().coerceAtLeast(1)
        headphonePreDelaySamples = ((safeRate * HallTuning.HEADPHONES.preDelayMs) / 1000f).roundToInt().coerceAtLeast(1)
        preDelayBuffer = FloatArray(maxOf(speakerPreDelaySamples, headphonePreDelaySamples) + 1)
        preDelayWriteIndex = 0

        speakerCombFeedbacksL = computeCombFeedbacks(combL, safeRate, HallTuning.SPEAKER.targetRt60Sec)
        speakerCombFeedbacksR = computeCombFeedbacks(combR, safeRate, HallTuning.SPEAKER.targetRt60Sec)
        headphoneCombFeedbacksL = computeCombFeedbacks(combL, safeRate, HallTuning.HEADPHONES.targetRt60Sec)
        headphoneCombFeedbacksR = computeCombFeedbacks(combR, safeRate, HallTuning.HEADPHONES.targetRt60Sec)

        speakerHpfAlpha = computeHpfAlpha(HallTuning.SPEAKER.highPassCutoffHz, safeRate)
        headphoneHpfAlpha = computeHpfAlpha(HallTuning.HEADPHONES.highPassCutoffHz, safeRate)
        hpfPrevIn = 0f
        hpfPrevOut = 0f
        lastTailL = 0f
        lastTailR = 0f
    }

    internal fun preDelaySamplesFor(isBuiltInSpeaker: Boolean): Int {
        return if (isBuiltInSpeaker) speakerPreDelaySamples else headphonePreDelaySamples
    }

    /**
     * Renders Schroeder hall reverb for stereo sample pair.
     * Updates [outL] and [outR] output fields.
     *
     * @param inputL Left channel input sample.
     * @param inputR Right channel input sample.
     * @param channels Channel count (1 for Mono, 2 for Stereo).
     * @param isBuiltInSpeaker True when routed solely to built-in device speakers.
     */
    fun process(inputL: Float, inputR: Float, channels: Int, isBuiltInSpeaker: Boolean = false) {
        if (combL.isEmpty()) {
            outL = inputL
            outR = if (channels == 2) inputR else inputL
            lastTailL = 0f
            lastTailR = 0f
            return
        }

        val tuning = if (isBuiltInSpeaker) HallTuning.SPEAKER else HallTuning.HEADPHONES
        val preDelaySamples = if (isBuiltInSpeaker) speakerPreDelaySamples else headphonePreDelaySamples
        val feedbacksL = if (isBuiltInSpeaker) speakerCombFeedbacksL else headphoneCombFeedbacksL
        val feedbacksR = if (isBuiltInSpeaker) speakerCombFeedbacksR else headphoneCombFeedbacksR
        val hpfAlpha = if (isBuiltInSpeaker) speakerHpfAlpha else headphoneHpfAlpha

        val stereoR = if (channels == 2) inputR else inputL
        val earlyL = writeDelay(haasL, inputL + stereoR * 0.18f, true)
        val earlyR = writeDelay(haasR, stereoR + inputL * 0.18f, false)
        val rawSend = ((inputL + stereoR) * 0.5f + (earlyL + earlyR) * 0.25f) * 0.95f

        val hpfOut = flushDenormal(hpfAlpha * (hpfPrevOut + rawSend - hpfPrevIn))
        hpfPrevIn = rawSend
        hpfPrevOut = hpfOut

        val readIndex = (preDelayWriteIndex - preDelaySamples + preDelayBuffer.size) % preDelayBuffer.size
        val diffuseIn = preDelayBuffer[readIndex]
        preDelayBuffer[preDelayWriteIndex] = hpfOut
        preDelayWriteIndex = (preDelayWriteIndex + 1) % preDelayBuffer.size

        val tailL = processTank(
            diffuseIn,
            combL,
            combIndexL,
            combDampL,
            feedbacksL,
            tuning.dampingFactor,
            allPassL,
            allPassIndexL
        )
        val tailR = processTank(
            diffuseIn,
            combR,
            combIndexR,
            combDampR,
            feedbacksR,
            tuning.dampingFactor,
            allPassR,
            allPassIndexR
        )

        lastTailL = tailL
        lastTailR = tailR

        outL = flushDenormal(
            (inputL * tuning.dryMix + earlyL * tuning.earlyMix + tailL * tuning.tailMix).coerceIn(-1.0f, 1.0f)
        )
        outR = flushDenormal(
            (stereoR * tuning.dryMix + earlyR * tuning.earlyMix + tailR * tuning.tailMix).coerceIn(-1.0f, 1.0f)
        )
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
        preDelayBuffer.fill(0f)
        preDelayWriteIndex = 0
        hpfPrevIn = 0f
        hpfPrevOut = 0f
        lastTailL = 0f
        lastTailR = 0f
        outL = 0f
        outR = 0f
    }

    private fun computeCombFeedbacks(
        combs: Array<FloatArray>,
        sampleRate: Int,
        targetRt60Sec: Float
    ): FloatArray {
        return FloatArray(combs.size) { i ->
            val delaySec = combs[i].size.toDouble() / sampleRate.toDouble()
            10.0.pow(-3.0 * delaySec / targetRt60Sec.toDouble()).toFloat().coerceIn(0f, 0.99f)
        }
    }

    private fun computeHpfAlpha(cutoffHz: Float, sampleRate: Int): Float {
        val omega = 2.0 * PI * cutoffHz.toDouble() / sampleRate.toDouble()
        return (1.0 / (1.0 + omega)).toFloat().coerceIn(0f, 0.9999f)
    }

    private fun flushDenormal(value: Float): Float {
        return if (abs(value) < DENORMAL_THRESHOLD) 0f else value
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
        buffer[index] = flushDenormal(input)

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
     * @param combFeedbacks Pre-computed RT60 comb feedback gains.
     * @param dampingFactor One-pole low-pass damping coefficient.
     * @param allPass All-pass filter delay lines.
     * @param allPassIndices All-pass delay line indices.
     * @return Reverb tail sample value.
     */
    private fun processTank(
        input: Float,
        comb: Array<FloatArray>,
        combIndices: IntArray,
        combDamp: FloatArray,
        combFeedbacks: FloatArray,
        dampingFactor: Float,
        allPass: Array<FloatArray>,
        allPassIndices: IntArray
    ): Float {
        var sum = 0f

        for (i in comb.indices) {
            val buffer = comb[i]
            val idx = combIndices[i]
            val delayed = buffer[idx]

            combDamp[i] = flushDenormal(combDamp[i] + (delayed - combDamp[i]) * dampingFactor)

            val feedback = combDamp[i] * combFeedbacks[i]
            val safeFeedback = tanh(feedback)

            buffer[idx] = flushDenormal(input + safeFeedback)
            combIndices[i] = (idx + 1) % buffer.size
            sum += delayed
        }

        var output = sum * (1f / comb.size.coerceAtLeast(1))

        for (i in allPass.indices) {
            val buffer = allPass[i]
            val idx = allPassIndices[i]
            val delayed = buffer[idx]
            buffer[idx] = flushDenormal(output + delayed * 0.5f)
            output = flushDenormal(delayed - output)
            allPassIndices[i] = (idx + 1) % buffer.size
        }

        return output
    }
}
