package com.tigerplayer.ui.coverscreen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying day phase resolution across chrono time boundaries in [CoverScreenDiscoveryView].
 */
class CoverScreenDiscoveryViewTest {

    @Test
    fun `hours 5 through 11 resolve to MORNING WAVE`() {
        for (hour in 5..11) {
            val phase = resolveCoverDayPhase(hour)
            assertEquals("MORNING WAVE", phase.label)
            assertEquals("Sunrise Kinetic Arc", phase.headline)
        }
    }

    @Test
    fun `hours 12 through 16 resolve to AFTERNOON FLOW`() {
        for (hour in 12..16) {
            val phase = resolveCoverDayPhase(hour)
            assertEquals("AFTERNOON FLOW", phase.label)
            assertEquals("Midday Signal Drift", phase.headline)
        }
    }

    @Test
    fun `hours 17 through 21 resolve to EVENING GLOW`() {
        for (hour in 17..21) {
            val phase = resolveCoverDayPhase(hour)
            assertEquals("EVENING GLOW", phase.label)
            assertEquals("Twilight Pulse Network", phase.headline)
        }
    }

    @Test
    fun `hours 22 through 23 and 0 through 4 resolve to NIGHT ECHO`() {
        val nightHours = listOf(22, 23, 0, 1, 2, 3, 4)
        for (hour in nightHours) {
            val phase = resolveCoverDayPhase(hour)
            assertEquals("NIGHT ECHO", phase.label)
            assertEquals("Afterdark Resonance Grid", phase.headline)
        }
    }
}
