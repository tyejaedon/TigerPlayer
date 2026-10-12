package com.tigerplayer.ui.coverscreen

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverScreenQueueViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun track(id: String) = AudioTrack(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        uri = Uri.parse("content://queue/$id"),
        artworkUri = Uri.EMPTY,
        durationMs = 60_000L,
        mimeType = "audio/mpeg"
    )

    @Test
    fun fullQueueRendersEveryItem() {
        val queue = (1..12).map { track(it.toString()) }
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenQueueView(
                    queue = queue,
                    currentTrackId = queue.first().id,
                    currentIndex = 0,
                    shuffleModeEnabled = false,
                    onTrackTapped = {},
                    onRemoveFromQueue = {},
                    onMoveQueueItem = { _, _ -> },
                    onClearQueue = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.QUEUE_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(CoverScreenTestTags.QUEUE_LIST)
            .performScrollToNode(hasTestTag(CoverScreenTestTags.queueItem(queue.last().id, queue.lastIndex)))
        composeRule.onNodeWithTag(CoverScreenTestTags.queueItem(queue.last().id, queue.lastIndex))
            .assertIsDisplayed()
    }

    @Test
    fun swipingLeftRequestsRemovalOnlyAfterConfirmation() {
        val queue = listOf(track("1"), track("2"))
        var removedIndex = -1
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenQueueView(
                    queue = queue,
                    currentTrackId = queue.first().id,
                    currentIndex = 0,
                    shuffleModeEnabled = false,
                    onTrackTapped = {},
                    onRemoveFromQueue = { removedIndex = it },
                    onMoveQueueItem = { _, _ -> },
                    onClearQueue = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.queueItem(queue[1].id, 1))
            .performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(CoverScreenTestTags.QUEUE_REMOVE_DIALOG).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(-1, removedIndex) }

        composeRule.onNodeWithTag(CoverScreenTestTags.QUEUE_REMOVE_CONFIRM).performClick()
        composeRule.runOnIdle { assertEquals(1, removedIndex) }
    }

    @Test
    fun longPressDragReordersQueue() {
        val queue = listOf(track("1"), track("2"), track("3"))
        val movedIndices = mutableListOf<Pair<Int, Int>>()
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenQueueView(
                    queue = queue,
                    currentTrackId = queue.first().id,
                    currentIndex = 0,
                    shuffleModeEnabled = false,
                    onTrackTapped = {},
                    onRemoveFromQueue = {},
                    onMoveQueueItem = { from, to -> movedIndices += from to to },
                    onClearQueue = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.queueDragHandle(1)).performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 150f))
            up()
        }

        composeRule.runOnIdle { assertEquals(listOf(1 to 2), movedIndices) }
    }

    @Test
    fun shuffleModeDisablesQueueReordering() {
        val queue = listOf(track("1"), track("2"), track("3"))
        val movedIndices = mutableListOf<Pair<Int, Int>>()
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenQueueView(
                    queue = queue,
                    currentTrackId = queue.first().id,
                    currentIndex = 0,
                    shuffleModeEnabled = true,
                    onTrackTapped = {},
                    onRemoveFromQueue = {},
                    onMoveQueueItem = { from, to -> movedIndices += from to to },
                    onClearQueue = {}
                )
            }
        }
        composeRule.onNodeWithTag(CoverScreenTestTags.queueDragHandle(1)).performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 150f))
            up()
        }
        composeRule.runOnIdle { assertTrue(movedIndices.isEmpty()) }
    }

    @Test
    fun clearQueueRequiresConfirmation() {
        var cleared = false
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenQueueView(
                    queue = listOf(track("1")),
                    currentTrackId = "1",
                    currentIndex = 0,
                    shuffleModeEnabled = false,
                    onTrackTapped = {},
                    onRemoveFromQueue = {},
                    onMoveQueueItem = { _, _ -> },
                    onClearQueue = { cleared = true }
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.QUEUE_CLEAR).performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.QUEUE_CLEAR_DIALOG).assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(cleared) }
        composeRule.onNodeWithTag(CoverScreenTestTags.QUEUE_CLEAR_CONFIRM).performClick()
        composeRule.runOnIdle { assertTrue(cleared) }
    }
}
