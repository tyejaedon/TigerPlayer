package com.tigerplayer.ui.coverscreen

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.dashboard.CurationFeedMeta
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverScreenDiscoveryViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun suggestionsPrioritizeBothFeedsAndSupportPlayAndQueueActions() {
        val daylistTracks = listOf(track("daylist-1"), track("daylist-2"))
        val discoveryTracks = listOf(track("discovery-1"), track("discovery-2"))
        var played: AudioTrack? = null
        var queued: AudioTrack? = null
        val recommendations = buildCoverScreenRecommendations(daylistTracks, discoveryTracks)

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenDiscoveryView(
                    daylistTracks = daylistTracks,
                    discoveryWeeklyTracks = discoveryTracks,
                    daylistMeta = CurationFeedMeta(isLoading = false),
                    discoveryMeta = CurationFeedMeta(isLoading = false),
                    onPlayTrack = { played = it },
                    onQueueTrack = { queued = it },
                    onRefresh = {}
                )
            }
        }

        assertEquals(3, recommendations.size)
        assertEquals("DAY LIST", recommendations[0].source)
        assertEquals("DISCOVERY WEEKLY", recommendations[1].source)

        composeRule.onNodeWithTag(
            CoverScreenTestTags.discoveryRecommendation(daylistTracks.first().id)
        ).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.DISCOVERY_LIST)
            .performScrollToNode(
                hasTestTag(CoverScreenTestTags.discoveryRecommendation(discoveryTracks.first().id))
            )
        composeRule.onNodeWithTag(
            CoverScreenTestTags.discoveryQueue(discoveryTracks.first().id)
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(daylistTracks.first(), played)
            assertEquals(discoveryTracks.first(), queued)
        }
    }

    @Test
    fun emptyFeedsShowRefreshableFallback() {
        var refreshed = false

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenDiscoveryView(
                    daylistTracks = emptyList(),
                    discoveryWeeklyTracks = emptyList(),
                    daylistMeta = CurationFeedMeta(isLoading = false),
                    discoveryMeta = CurationFeedMeta(isLoading = false),
                    onPlayTrack = {},
                    onQueueTrack = {},
                    onRefresh = { refreshed = true }
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.DISCOVERY_EMPTY_STATE).assertIsDisplayed()
        composeRule.onNodeWithTag(CoverScreenTestTags.DISCOVERY_REFRESH).performClick()
        composeRule.runOnIdle { assertTrue(refreshed) }
    }

    @Test
    fun missingFeedIsReportedAlongsideAvailableSuggestions() {
        val daylistTrack = track("daylist-only")

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenDiscoveryView(
                    daylistTracks = listOf(daylistTrack),
                    discoveryWeeklyTracks = emptyList(),
                    daylistMeta = CurationFeedMeta(isLoading = false),
                    discoveryMeta = CurationFeedMeta(isLoading = false),
                    onPlayTrack = {},
                    onQueueTrack = {},
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.DISCOVERY_MISSING_FEED).assertIsDisplayed()
    }

    private fun track(id: String) = AudioTrack(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        uri = Uri.parse("content://tracks/$id"),
        artworkUri = Uri.EMPTY,
        durationMs = 60_000L,
        mimeType = "audio/mpeg"
    )
}
