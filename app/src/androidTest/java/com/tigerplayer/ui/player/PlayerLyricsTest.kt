package com.tigerplayer.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.ui.theme.TigerPlayerTheme
import com.tigerplayer.utils.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for issue #159: LRCLIB frequently only returns plain (unsynced) lyrics,
 * with no `[mm:ss.xx]` tags. `LyricsDisplay` must fall back to rendering that raw text instead
 * of silently showing a blank/empty screen. See testing.instructions.md priority coverage gaps
 * and AGENTS.md's `PrismTestTags` convention for centralized test tags.
 */
class PlayerLyricsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nullLyrics_showsNoLyricsFoundPlaceholder() {
        composeRule.setContent {
            TigerPlayerTheme {
                LyricsDisplay(
                    lyrics = Lyrics.None,
                    currentPosition = 0L,
                    textColor = Color.White
                )
            }
        }

        composeRule.onNodeWithTag(LyricsTestTags.NO_LYRICS_FOUND).assertIsDisplayed()
        composeRule.onNodeWithText("NO LYRICS FOUND").assertIsDisplayed()
    }

    @Test
    fun plainUnsyncedLyrics_rendersRawTextInsteadOfBlankScreen() {
        // Real-world shape of an LRCLIB plainLyrics-only response: no [mm:ss.xx] tags at all.
        val plainLyrics = "First line of the song\nSecond line of the song\nThird line"

        composeRule.setContent {
            TigerPlayerTheme {
                LyricsDisplay(
                    lyrics = Lyrics.Plain(plainLyrics),
                    currentPosition = 5_000L,
                    textColor = Color.White
                )
            }
        }

        composeRule.onNodeWithTag(LyricsTestTags.PLAIN_LYRICS_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText(plainLyrics, substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag(LyricsTestTags.SYNCED_LYRICS_LIST).assertCountEquals(0)
        composeRule.onAllNodesWithTag(LyricsTestTags.NO_LYRICS_FOUND).assertCountEquals(0)
    }

    @Test
    fun syncedLyrics_rendersTimestampedListNotRawText() {
        val syncedLyrics = "[00:01.00]First synced line\n[00:05.00]Second synced line"

        composeRule.setContent {
            TigerPlayerTheme {
                LyricsDisplay(
                    lyrics = LrcParser.parse(syncedLyrics),
                    currentPosition = 0L,
                    textColor = Color.White
                )
            }
        }

        composeRule.onNodeWithTag(LyricsTestTags.SYNCED_LYRICS_LIST).assertIsDisplayed()
        composeRule.onNodeWithText("First synced line").assertIsDisplayed()
        composeRule.onAllNodesWithTag(LyricsTestTags.PLAIN_LYRICS_TEXT).assertCountEquals(0)
    }

    @Test
    fun syncedLyrics_tapLine_triggersSeekCallback() {
        val syncedLyrics = "[00:01.00]First synced line\n[00:05.00]Second synced line"
        var soughtPosition: Long? = null

        composeRule.setContent {
            TigerPlayerTheme {
                LyricsDisplay(
                    lyrics = LrcParser.parse(syncedLyrics),
                    currentPosition = 0L,
                    textColor = Color.White,
                    onLineClick = { soughtPosition = it }
                )
            }
        }

        composeRule.onNodeWithTag("${LyricsTestTags.LYRIC_LINE_PREFIX}1").performClick()
        assertEquals(5000L, soughtPosition)
    }

    @Test
    fun syncedLyrics_offsetControls_adjustsOffset() {
        val syncedLyrics = "[00:01.00]First synced line"
        var currentOffset = 0L

        composeRule.setContent {
            TigerPlayerTheme {
                LyricsDisplay(
                    lyrics = LrcParser.parse(syncedLyrics),
                    currentPosition = 0L,
                    textColor = Color.White,
                    manualOffsetMs = currentOffset,
                    showOffsetControls = true,
                    onOffsetAdjust = { currentOffset += it },
                    onOffsetReset = { currentOffset = 0L }
                )
            }
        }

        composeRule.onNodeWithTag(LyricsTestTags.OFFSET_CONTROLS).assertIsDisplayed()
        composeRule.onNodeWithTag(LyricsTestTags.OFFSET_INCREASE).performClick()
        assertEquals(500L, currentOffset)

        composeRule.onNodeWithTag(LyricsTestTags.OFFSET_DECREASE).performClick()
        assertEquals(0L, currentOffset)
    }
}




