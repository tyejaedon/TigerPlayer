package com.tigerplayer.ui.coverscreen

import com.tigerplayer.data.model.AudioTrack
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverScreenDiscoveryRecommendationsTest {

    @Test
    fun recommendationsPrioritizeEachFeedAndNeverExceedThreeItems() {
        val daylist = listOf(track("day-1"), track("day-2"), track("day-3"))
        val discovery = listOf(track("weekly-1"), track("weekly-2"))

        val recommendations = buildCoverScreenRecommendations(daylist, discovery)

        assertEquals(3, recommendations.size)
        assertEquals("DAY LIST", recommendations[0].source)
        assertEquals(daylist[0], recommendations[0].track)
        assertEquals("DISCOVERY WEEKLY", recommendations[1].source)
        assertEquals(discovery[0], recommendations[1].track)
        assertEquals(daylist[1], recommendations[2].track)
    }

    private fun track(id: String): AudioTrack = mockk {
        every { this@mockk.id } returns id
    }
}
