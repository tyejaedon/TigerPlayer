package com.example.tigerplayer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDspEngineSafetyTest {

    @Test
    fun mixDryWet_clampsMixAmountAndOutputRange() {
        val overMix = DspMath.mixDryWet(dry = 0.8f, wet = 1.9f, wetAmount = 2f)
        val underMix = DspMath.mixDryWet(dry = -0.8f, wet = -1.9f, wetAmount = -1f)

        assertEquals(1f, overMix, 0.0001f)
        assertEquals(-0.8f, underMix, 0.0001f)
    }

    @Test
    fun toPcm16_clampsHardOverloadsSafely() {
        assertEquals(32767.toShort(), DspMath.toPcm16(4f))
        assertEquals((-32768).toShort(), DspMath.toPcm16(-4f))
    }

    @Test
    fun toPcm16_keepsNominalSamplesInRange() {
        val values = listOf(-1.1f, -1f, -0.5f, 0f, 0.5f, 1f, 1.1f)
        values.forEach { sample ->
            val pcm = DspMath.toPcm16(sample).toInt()
            assertTrue("PCM output should stay in 16-bit bounds", pcm in -32768..32767)
        }
    }
}

