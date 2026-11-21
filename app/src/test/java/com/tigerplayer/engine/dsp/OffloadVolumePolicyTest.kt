package com.tigerplayer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for issue #53.
 *
 * The offload transition fades the volume to zero before reconfiguring the audio sink. Previously
 * the fade-back target was obtained by reading `player.volume` back afterwards, which returned the
 * faded-out `0f` whenever nothing had reassigned it - leaving playback permanently silent.
 */
class OffloadVolumePolicyTest {

    @Test
    fun `bit-perfect preserves the pre-fade volume`() {
        assertEquals(0.3f, OffloadVolumePolicy.targetVolume(true, null, 0.3f), EPSILON)
        assertEquals(0.3f, OffloadVolumePolicy.targetVolume(true, -12f, 0.3f), EPSILON)
    }

    @Test
    fun `dsp mode with no profile restores the pre-fade volume`() {
        assertEquals(0.42f, OffloadVolumePolicy.targetVolume(false, null, 0.42f), EPSILON)
    }

    @Test
    fun `dsp mode with no profile preserves an intentional mute`() {
        assertEquals(0f, OffloadVolumePolicy.targetVolume(false, null, 0f), EPSILON)
    }

    @Test
    fun `a non-finite pre-fade reading falls back to unity rather than silence`() {
        assertEquals(1.0f, OffloadVolumePolicy.targetVolume(false, null, Float.NaN), EPSILON)
        assertEquals(1.0f, OffloadVolumePolicy.targetVolume(false, null, Float.NEGATIVE_INFINITY), EPSILON)
    }

    @Test
    fun `a preamp of zero decibels is unity gain`() {
        assertEquals(1.0f, OffloadVolumePolicy.preampVolume(0f), EPSILON)
    }

    @Test
    fun `a negative preamp attenuates by the expected linear factor`() {
        // -6 dB is a little over half amplitude.
        assertEquals(0.501f, OffloadVolumePolicy.preampVolume(-6f), 0.001f)
        // -20 dB is exactly one tenth.
        assertEquals(0.1f, OffloadVolumePolicy.preampVolume(-20f), 0.001f)
    }

    @Test
    fun `a positive preamp is clamped to unity to avoid clipping`() {
        // Boosting the preamp would push the summed signal into clipping; guidance is preamp <= 0.
        assertEquals(1.0f, OffloadVolumePolicy.preampVolume(6f), EPSILON)
        assertEquals(1.0f, OffloadVolumePolicy.preampVolume(60f), EPSILON)
    }

    @Test
    fun `an extreme attenuation is floored rather than silenced`() {
        assertEquals(OffloadVolumePolicy.MIN_DSP_VOLUME, OffloadVolumePolicy.preampVolume(-200f), EPSILON)
    }

    @Test
    fun `a malformed preamp does not produce NaN`() {
        // AutoEq parsing can yield NaN; a NaN reaching the sink produces loud artifacts.
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { bad ->
            val result = OffloadVolumePolicy.preampVolume(bad)
            assertTrue("preampVolume($bad) = $result was not finite", result.isFinite())
        }
    }

    @Test
    fun `every output is a finite volume inside the valid range`() {
        val preamps = listOf(null, Float.NaN, -200f, -24f, -6f, 0f, 12f)
        val preFades = listOf(0f, 0.05f, 0.5f, 1f, Float.NaN)

        for (bitPerfect in listOf(true, false)) {
            for (preamp in preamps) {
                for (preFade in preFades) {
                    val volume = OffloadVolumePolicy.targetVolume(bitPerfect, preamp, preFade)
                    assertTrue("not finite for ($bitPerfect, $preamp, $preFade)", volume.isFinite())
                    assertTrue("out of range: $volume", volume in 0f..1f)
                    assertTrue("out of range: $volume", volume >= 0f)
                }
            }
        }
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
