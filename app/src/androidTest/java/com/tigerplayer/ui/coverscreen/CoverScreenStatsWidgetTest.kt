package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            sessionTopArtists = listOf(ArtistStats("Artist One", 2, 120_000L)),
            sessionTrackPlayCount = 3,
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
        composeRule.onNodeWithText("Session favorites: Artist One · 3 plays").assertIsDisplayed()
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

    @Test
    fun widgetAndCanvasRenderAtCompactAndWideWidths() {
        val stats = CoverScreenStatsSnapshot(
            dailyPlayCounts = listOf(0, 2, 1, 4, 3, 5, 2),
            hasHistory = true
        )
        val widths = listOf(220.dp, 360.dp)

        composeRule.setContent {
            TigerPlayerTheme {
                Column {
                    widths.forEach { width ->
                        CoverScreenStatsWidget(
                            stats = stats,
                            onOpenFullStats = {},
                            modifier = Modifier.width(width)
                        )
                    }
                }
            }
        }

        composeRule.onAllNodesWithTag(CoverScreenTestTags.STATS_WIDGET).assertCountEquals(2)
        composeRule.onAllNodesWithTag(CoverScreenTestTags.STATS_CHART).assertCountEquals(2)
    }

    @Test
    fun historyChartAnimatesOutWhenStatsBecomeUnavailable() {
        var stats by mutableStateOf(
            CoverScreenStatsSnapshot(
                dailyPlayCounts = listOf(0, 1, 2, 3, 4, 5, 6),
                hasHistory = true
            )
        )

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenStatsWidget(stats = stats, onOpenFullStats = {})
            }
        }

        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onAllNodesWithTag(CoverScreenTestTags.STATS_CHART).assertCountEquals(1)
        composeRule.runOnIdle { stats = CoverScreenStatsSnapshot() }
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onAllNodesWithTag(CoverScreenTestTags.STATS_CHART).assertCountEquals(0)
    }
}
