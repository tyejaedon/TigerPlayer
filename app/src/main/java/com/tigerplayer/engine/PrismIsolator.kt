package com.tigerplayer.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * 💎 Prism 3-Stem Real-Time Isolator
 *
 * Implements 3-way Mid/Side audio separation using 4th-order Linkwitz-Riley crossover networks (LR4):
 * - Vocals Stem: Mid channel mid-band (160 Hz – 3500 Hz).
 * - Beats Stem: Low-frequency bass (below 160 Hz).
 * - Instruments Stem: Stereo side channels and high-frequency content (above 3500 Hz).
 */
internal class PrismIsolator {
    private var sampleRateHz = 48_000f

    // Mid channel LR4 crossovers.
    private val lowMidLp1 = MonoBiquad()
    private val lowMidLp2 = MonoBiquad()
    private val midMidHp1 = MonoBiquad()
    private val midMidHp2 = MonoBiquad()
    private val midMidLp1 = MonoBiquad()
    private val midMidLp2 = MonoBiquad()
    private val highMidHp1 = MonoBiquad()
    private val highMidHp2 = MonoBiquad()

    // Side channel LR4 crossovers.
    private val lowSideLp1 = MonoBiquad()
    private val lowSideLp2 = MonoBiquad()
    private val midSideHp1 = MonoBiquad()
    private val midSideHp2 = MonoBiquad()
    private val midSideLp1 = MonoBiquad()
    private val midSideLp2 = MonoBiquad()
    private val highSideHp1 = MonoBiquad()
    private val highSideHp2 = MonoBiquad()

    private var normalizationAttackCoef = 0.05f
    private var normalizationReleaseCoef = 0.006f
    private var limiterAttackCoef = 0.12f
    private var limiterReleaseCoef = 0.01f
    private var limiterEnvelopeAttackCoef = 0.28f
    private var limiterEnvelopeReleaseCoef = 0.02f

    private var outputNormalization = 1f
    private var limiterEnvelope = 0f
    private var limiterGain = 1f

    /**
     * Clamps gain float into range [0.0, 1.0].
     *
     * @param value Raw gain value.
     * @return Clamped gain in [0.0, 1.0].
     */
    private fun sanitizeGain(value: Float): Float = value.coerceIn(0f, 1f)

    /**
     * Soft polynomial saturator preventing hard digital clipping on large audio peaks.
     *
     * @param sample Raw audio sample.
     * @return Saturated sample value.
     */
    private fun softSaturate(sample: Float): Float {
        // FIX: Prevent foldback distortion for massive peaks without hard-clipping early
        val absSample = abs(sample)
        return if (absSample <= 1.2f) {
            sample - (sample * sample * sample) * 0.22f
        } else {
            sign(sample) * (1.2f - (1.2f * 1.2f * 1.2f) * 0.22f)
        }
    }

    /**
     * Configures Linkwitz-Riley crossovers for given sample rate.
     *
     * @param sampleRate Sampling rate in Hz.
     */
    fun configure(sampleRate: Int) {
        sampleRateHz = sampleRate.toFloat().coerceAtLeast(8_000f)

        // 3-way split tuned for practical stem separation without harsh artifacts.
        val lowCutoffHz = 160f
        val highCutoffHz = 3_500f

        lowMidLp1.configureLowPass(sampleRateHz, lowCutoffHz, 0.707f)
        lowMidLp2.configureLowPass(sampleRateHz, lowCutoffHz, 0.707f)
        midMidHp1.configureHighPass(sampleRateHz, lowCutoffHz, 0.707f)
        midMidHp2.configureHighPass(sampleRateHz, lowCutoffHz, 0.707f)
        midMidLp1.configureLowPass(sampleRateHz, highCutoffHz, 0.707f)
        midMidLp2.configureLowPass(sampleRateHz, highCutoffHz, 0.707f)
        highMidHp1.configureHighPass(sampleRateHz, highCutoffHz, 0.707f)
        highMidHp2.configureHighPass(sampleRateHz, highCutoffHz, 0.707f)

        lowSideLp1.configureLowPass(sampleRateHz, lowCutoffHz, 0.707f)
        lowSideLp2.configureLowPass(sampleRateHz, lowCutoffHz, 0.707f)
        midSideHp1.configureHighPass(sampleRateHz, lowCutoffHz, 0.707f)
        midSideHp2.configureHighPass(sampleRateHz, lowCutoffHz, 0.707f)
        midSideLp1.configureLowPass(sampleRateHz, highCutoffHz, 0.707f)
        midSideLp2.configureLowPass(sampleRateHz, highCutoffHz, 0.707f)
        highSideHp1.configureHighPass(sampleRateHz, highCutoffHz, 0.707f)
        highSideHp2.configureHighPass(sampleRateHz, highCutoffHz, 0.707f)

        normalizationAttackCoef = 1f - exp(-1f / (sampleRateHz * 0.004f))
        normalizationReleaseCoef = 1f - exp(-1f / (sampleRateHz * 0.060f))
        limiterAttackCoef = 1f - exp(-1f / (sampleRateHz * 0.002f))
        limiterReleaseCoef = 1f - exp(-1f / (sampleRateHz * 0.090f))
        limiterEnvelopeAttackCoef = 1f - exp(-1f / (sampleRateHz * 0.0015f))
        limiterEnvelopeReleaseCoef = 1f - exp(-1f / (sampleRateHz * 0.065f))
    }

    /**
     * Decomposes stereo input into Mid/Side bands, applies stem gain multipliers, and normalizes output.
     *
     * @param left Left channel sample.
     * @param right Right channel sample.
     * @param channels Channel count (1 for Mono, 2 for Stereo).
     * @param vocalsGain Vocal gain multiplier [0.0, 1.0].
     * @param beatsGain Beat gain multiplier [0.0, 1.0].
     * @param instrumentsGain Instrument gain multiplier [0.0, 1.0].
     * @return [StereoFrame] with isolated/remixed stereo samples.
     */
    fun process(
        left: Float,
        right: Float,
        channels: Int,
        vocalsGain: Float,
        beatsGain: Float,
        instrumentsGain: Float
    ): StereoFrame {
        val safeVocals = sanitizeGain(vocalsGain)
        val safeBeats = sanitizeGain(beatsGain)
        val safeInstruments = sanitizeGain(instrumentsGain)

        val stereoRight = if (channels == 2) right else left

        // Mid/Side decomposition.
        val mid = (left + stereoRight) * 0.5f
        val side = if (channels == 2) (left - stereoRight) * 0.5f else 0f

        val midLow = lowMidLp2.process(lowMidLp1.process(mid))
        val midBandRaw = midMidHp2.process(midMidHp1.process(mid))
        val midMid = midMidLp2.process(midMidLp1.process(midBandRaw))
        val midHigh = highMidHp2.process(highMidHp1.process(midBandRaw))

        val sideLow = lowSideLp2.process(lowSideLp1.process(side))
        val sideBandRaw = midSideHp2.process(midSideHp1.process(side))
        val sideMid = midSideLp2.process(midSideLp1.process(sideBandRaw))
        val sideHigh = highSideHp2.process(highSideHp1.process(sideBandRaw))

        val vocalsStem = midMid * 0.92f + midHigh * 0.08f
        val beatsStem = midLow + sideLow * 0.12f
        val instrumentsStem = sideMid * 0.72f + sideHigh * 1.05f + midHigh * 0.08f

        val outMid = vocalsStem * safeVocals + beatsStem * safeBeats
        val outSide = instrumentsStem * safeInstruments

        var outL = outMid + outSide
        var outR = if (channels == 2) outMid - outSide else outL

        val squaredSum =
            safeVocals * safeVocals + safeBeats * safeBeats + safeInstruments * safeInstruments
        val targetNormalization = if (squaredSum <= 1e-5f) {
            0f
        } else {
            (1f / sqrt(squaredSum)).coerceIn(0.52f, 1.05f)
        }

        val normCoef = if (targetNormalization < outputNormalization) {
            normalizationAttackCoef
        } else {
            normalizationReleaseCoef
        }
        outputNormalization += (targetNormalization - outputNormalization) * normCoef

        outL *= outputNormalization
        outR *= outputNormalization

        val peak = max(abs(outL), abs(outR))
        val envCoef = if (peak > limiterEnvelope) limiterEnvelopeAttackCoef else limiterEnvelopeReleaseCoef
        limiterEnvelope += (peak - limiterEnvelope) * envCoef

        val targetLimiterGain = if (limiterEnvelope > 0.93f) {
            0.93f / limiterEnvelope
        } else {
            1f
        }
        val limiterCoef = if (targetLimiterGain < limiterGain) limiterAttackCoef else limiterReleaseCoef
        limiterGain += (targetLimiterGain - limiterGain) * limiterCoef

        outL = softSaturate(outL * limiterGain)
        outR = softSaturate(outR * limiterGain)

        return StereoFrame(outL, outR)
    }

    /**
     * Resets crossover biquad states and limiter envelope memory.
     */
    fun reset() {
        lowMidLp1.reset(); lowMidLp2.reset()
        midMidHp1.reset(); midMidHp2.reset()
        midMidLp1.reset(); midMidLp2.reset()
        highMidHp1.reset(); highMidHp2.reset()

        lowSideLp1.reset(); lowSideLp2.reset()
        midSideHp1.reset(); midSideHp2.reset()
        midSideLp1.reset(); midSideLp2.reset()
        highSideHp1.reset(); highSideHp2.reset()

        outputNormalization = 1f
        limiterEnvelope = 0f
        limiterGain = 1f
    }
}
