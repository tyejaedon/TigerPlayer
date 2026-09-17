package com.example.tigerplayer.engine

import kotlin.math.pow

/**
 * Decides the terminal player volume for an audio-offload / DSP transition (issue #53).
 *
 * Extracted from `AudioPlayerService` so the decision is unit testable: the service itself needs a
 * real `ExoPlayer` and cannot run on the JVM.
 *
 * The transition fades the volume to zero before reconfiguring the audio sink, so *something* must
 * define what to fade back up to. Reading `player.volume` back after the reconfiguration is the bug
 * this replaces: in DSP mode with no PEQ profile loaded, nothing reassigned the volume, so the
 * read returned the faded-out `0f` and playback continued permanently silent.
 */
object OffloadVolumePolicy {

    /**
     * Floor for DSP mode. A profile with a very negative preamp would otherwise be inaudible and
     * indistinguishable from the silent-playback failure described above.
     */
    const val MIN_DSP_VOLUME = 0.05f

    /** Unity gain. Bit-perfect explicitly means "no attenuation applied by us". */
    const val BIT_PERFECT_VOLUME = 1.0f

    /**
     * Linear gain for a PEQ preamp expressed in dB.
     *
     * Preamp is clamped to `<= 0 dB`: a positive preamp would push the summed signal into clipping,
     * and clipping protection is not implemented.
     */
    fun preampVolume(preampDb: Float): Float {
        if (!preampDb.isFinite()) return MIN_DSP_VOLUME
        val safeDb = preampDb.coerceAtMost(0f)
        val linear = 10.0.pow(safeDb / 20.0).toFloat()
        // pow can still underflow to 0 for extreme input; treat any non-finite result as the floor.
        return if (linear.isFinite()) linear.coerceIn(MIN_DSP_VOLUME, BIT_PERFECT_VOLUME) else MIN_DSP_VOLUME
    }

    /**
     * The volume the transition must end on.
     *
     * @param bitPerfect whether offload / bit-perfect routing is being enabled.
     * @param profilePreampDb preamp of the active PEQ profile, or `null` when none is loaded.
     * @param preFadeVolume the volume captured **before** any mutation. It is the restore target for
     *   bit-perfect mode, where the in-app PEQ preamp is bypassed.
     */
    fun targetVolume(
        bitPerfect: Boolean,
        profilePreampDb: Float?,
        preFadeVolume: Float
    ): Float {
        if (bitPerfect) return restorePreFadeVolume(preFadeVolume)
        if (profilePreampDb != null) return preampVolume(profilePreampDb)
        return restorePreFadeVolume(preFadeVolume)
    }

    private fun restorePreFadeVolume(preFadeVolume: Float): Float {
        // Zero is a valid user mute. Only an invalid captured value needs a non-silent fallback.
        if (!preFadeVolume.isFinite()) return BIT_PERFECT_VOLUME
        return preFadeVolume.coerceIn(0f, BIT_PERFECT_VOLUME)
    }
}
