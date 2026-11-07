package com.tigerplayer.engine

import com.tigerplayer.data.local.ReplayGainMode
import kotlin.math.min
import kotlin.math.pow

/**
 * Calculates effective ReplayGain linear volume normalization multipliers per issue #56.
 */
object ReplayGainCalculator {

    private const val MIN_LINEAR_GAIN = 0.0001f
    private const val MAX_LINEAR_GAIN = 10.0f

    /**
     * Calculates the effective linear gain multiplier to apply to the audio stream.
     *
     * @param mode Selected ReplayGain mode (OFF, TRACK, ALBUM, SMART)
     * @param preampDb Additional user pre-amplifier gain in dB (e.g. -15 dB to +15 dB)
     * @param preventClipping Whether to clamp gain so that signal * peak <= 1.0 (0 dBFS)
     * @param trackGainDb ReplayGain track gain in dB from tags, or null
     * @param albumGainDb ReplayGain album gain in dB from tags, or null
     * @param trackPeak ReplayGain track peak (1.0 = full scale), or null
     * @param albumPeak ReplayGain album peak (1.0 = full scale), or null
     * @param isSequentialAlbumPlay Whether the playback queue is playing an album in track order
     * @param isBitPerfect Whether bit-perfect / offload DSP bypass is active
     * @return Linear amplitude multiplier (1.0 = unity gain)
     */
    fun calculateEffectiveLinearGain(
        mode: ReplayGainMode,
        preampDb: Float = 0f,
        preventClipping: Boolean = true,
        trackGainDb: Double? = null,
        albumGainDb: Double? = null,
        trackPeak: Double? = null,
        albumPeak: Double? = null,
        isSequentialAlbumPlay: Boolean = false,
        isBitPerfect: Boolean = false
    ): Float {
        if (isBitPerfect || mode == ReplayGainMode.OFF) {
            return 1.0f
        }

        val (targetGainDb, targetPeak) = when (mode) {
            ReplayGainMode.OFF -> 0.0 to null
            ReplayGainMode.TRACK -> (trackGainDb ?: albumGainDb ?: 0.0) to (trackPeak ?: albumPeak)
            ReplayGainMode.ALBUM -> (albumGainDb ?: trackGainDb ?: 0.0) to (albumPeak ?: trackPeak)
            ReplayGainMode.SMART -> {
                if (isSequentialAlbumPlay && albumGainDb != null) {
                    albumGainDb to (albumPeak ?: trackPeak)
                } else {
                    (trackGainDb ?: albumGainDb ?: 0.0) to (trackPeak ?: albumPeak)
                }
            }
        }

        // Sum ReplayGain with the user pre-amp
        val totalGainDb = targetGainDb + preampDb
        val rawLinearGain = 10.0.pow(totalGainDb / 20.0)

        // Clipping protection: if enabled and peak > 0, limit gain so gain * peak <= 1.0
        val safeLinearGain = if (preventClipping && targetPeak != null && targetPeak > 0.0) {
            min(rawLinearGain, 1.0 / targetPeak)
        } else {
            rawLinearGain
        }

        return safeLinearGain.toFloat().coerceIn(MIN_LINEAR_GAIN, MAX_LINEAR_GAIN)
    }
}
