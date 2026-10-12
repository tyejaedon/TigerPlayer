package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tigerplayer.data.local.dao.ArtistStats
import com.tigerplayer.engine.CoverScreenStatsSnapshot
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverScreenStatsWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun widgetDisplaysDailyStatsAndOpensFullSonicFootprint() {
        var openedFullStats = false
        val stats = CoverScreenStatsSnapshot(
            topArtists = listOf(
                ArtistStats("Artist One", 5, 300_000L),
                ArtistStats("Artist Two", 3, 180_000L),
                ArtistStats("Artist Three", 2, 120_000L)
            ),
            listeningTimeTodayMs = 3_900_000L,
            tracksPlayedToday = 5,
            dailyPlayCounts = listOf(0, 1, 3, 0, 2, 4, 5),
            hasHistory = true
        )

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenStatsWidget(
                    stats = stats,
                    onOpenFullStats = { openedFullStats = true }
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.STATS_WIDGET)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("1h 5m · 5 tracks today").assertIsDisplayed()
        composeRule.onNodeWithText("Top artists: Artist One, Artist Two, Artist Three")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CoverScreenTestTags.STATS_CHART).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(openedFullStats) }
    }

    @Test
    fun widgetShowsFallbackBeforeStatisticsExist() {
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenStatsWidget(
                    stats = CoverScreenStatsSnapshot(),
                    onOpenFullStats = {}
                )
            }
        }

        composeRule.onNodeWithText("Listening stats appear after you play music").assertIsDisplayed()
    }
}
