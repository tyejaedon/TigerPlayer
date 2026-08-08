package com.example.tigerplayer.ui.player

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.TigerPlayerTheme
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueueScreenDragDropTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createTrack(id: String, title: String): AudioTrack = AudioTrack(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        uri = Uri.EMPTY,
        artworkUri = Uri.EMPTY,
        durationMs = 0L,
        mimeType = "audio/mpeg",
        isLocal = true,
        isRemote = false,
        bitrate = 0,
        sampleRate = 0,
        trackNumber = 0,
        serverPath = null,
        year = null,
        dateAdded = 0L,
        isLiked = false,
        path = null
    )

    @Test
    fun dragAndDrop_triggers_onMoveItem_with_correct_indices() {
        val onMoveItemMock = mockk<(Int, Int) -> Unit>(relaxed = true)
        
        val tracks = listOf(
            createTrack("1", "Song 1"),
            createTrack("2", "Song 2"),
            createTrack("3", "Song 3")
        )

        composeRule.setContent {
            TigerPlayerTheme {
                QueueDisplay(
                    queue = tracks,
                    currentQueueIndex = 0,
                    isPlaying = false,
                    shuffleModeEnabled = false,
                    repeatMode = 0,
                    dynamicTextColor = Color.White,
                    accentColor = Color.Cyan,
                    onTrackClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = onMoveItemMock
                )
            }
        }

        // Target the second item's drag handle (index 1)
        val handle = composeRule.onNodeWithTag("drag_handle_1")
        
        // Perform long press and drag down to index 2
        handle.performTouchInput {
            down(center)
            advanceEventTime(1500) // Long press
            moveBy(androidx.compose.ui.geometry.Offset(0f, 500f)) // Drag down
            up()
        }

        // Verify drag dispatches the exact from/to indexes.
        verify(exactly = 1) { onMoveItemMock(1, 2) }
        confirmVerified(onMoveItemMock)
    }

    @Test
    fun dragAndDrop_reordered_queue_persists_after_recomposition() {
        val initialTracks = listOf(
            createTrack("1", "Song 1"),
            createTrack("2", "Song 2"),
            createTrack("3", "Song 3")
        )
        val forceRecompose = mutableIntStateOf(0)
        val reorderCalls = mutableListOf<Pair<Int, Int>>()

        composeRule.setContent {
            TigerPlayerTheme {
                var queueState by remember { mutableStateOf(initialTracks) }
                // Read this state so tests can force a recomposition after reorder.
                forceRecompose.intValue

                QueueDisplay(
                    queue = queueState,
                    currentQueueIndex = 0,
                    isPlaying = false,
                    shuffleModeEnabled = false,
                    repeatMode = 0,
                    dynamicTextColor = Color.White,
                    accentColor = Color.Cyan,
                    onTrackClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { from, to ->
                        reorderCalls += Pair(from, to)
                        queueState = queueState.toMutableList().apply {
                            add(to, removeAt(from))
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithTag("drag_handle_1").performTouchInput {
            down(center)
            advanceEventTime(1500)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 500f))
            up()
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(listOf(1 to 2), reorderCalls)
        }

        // Song 2 should now be rendered at index 2 and remain there on recomposition.
        composeRule.onNodeWithTag("queue_row_2_2").assertExists()

        composeRule.runOnIdle {
            forceRecompose.intValue += 1
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("queue_row_2_2").assertExists()
    }

    @Test
    fun dragAndDrop_does_not_reorder_when_shuffle_enabled() {
        val onMoveItemMock = mockk<(Int, Int) -> Unit>(relaxed = true)

        val tracks = listOf(
            createTrack("1", "Song 1"),
            createTrack("2", "Song 2"),
            createTrack("3", "Song 3")
        )

        composeRule.setContent {
            TigerPlayerTheme {
                QueueDisplay(
                    queue = tracks,
                    currentQueueIndex = 0,
                    isPlaying = false,
                    shuffleModeEnabled = true,
                    repeatMode = 0,
                    dynamicTextColor = Color.White,
                    accentColor = Color.Cyan,
                    onTrackClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = onMoveItemMock
                )
            }
        }

        composeRule.onNodeWithTag("drag_handle_1").performTouchInput {
            down(center)
            advanceEventTime(1500)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 500f))
            up()
        }

        verify(exactly = 0) { onMoveItemMock(any(), any()) }
    }
}
