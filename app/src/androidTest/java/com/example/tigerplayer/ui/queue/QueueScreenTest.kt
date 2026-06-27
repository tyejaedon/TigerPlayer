package com.example.tigerplayer.ui.queue

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tigerplayer.data.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun drag_drop_reorder_calls_absolute_indexes_and_persists_host_queue() {
        val queueState = mutableStateOf(sampleQueue())
        var moveCall: Pair<Int, Int>? = null

        composeRule.setContent {
            MaterialTheme {
                QueueScreenContent(
                    queue = queueState.value,
                    currentTrackId = queueState.value.first().id,
                    isPlaying = true,
                    onPlayQueueItem = {},
                    onRemoveFromQueue = {},
                    onMoveQueueItem = { from, to ->
                        moveCall = from to to
                        queueState.value = queueState.value.moved(from, to)
                    }
                )
            }
        }

        dragHandleDownOneSlot(index = 0)

        composeRule.waitForIdle()

        assertEquals(1 to 2, moveCall)
        assertEquals(
            listOf("now", "upcoming_b", "upcoming_a", "upcoming_c"),
            queueState.value.map { it.id }
        )
    }

    @Test
    fun drag_drop_local_order_survives_until_host_queue_sync() {
        val initialQueue = sampleQueue()
        val queueState = mutableStateOf(initialQueue)
        var moveCall: Pair<Int, Int>? = null

        composeRule.setContent {
            MaterialTheme {
                QueueScreenContent(
                    queue = queueState.value,
                    currentTrackId = queueState.value.first().id,
                    isPlaying = true,
                    onPlayQueueItem = {},
                    onRemoveFromQueue = {},
                    onMoveQueueItem = { from, to ->
                        moveCall = from to to
                    }
                )
            }
        }

        dragHandleDownOneSlot(index = 0)

        composeRule.waitForIdle()

        assertEquals(1 to 2, moveCall)
        composeRule
            .onNodeWithTag(QueueTestTags.row(0), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("Track Two")))

        composeRule.runOnUiThread {
            queueState.value = initialQueue.moved(from = 1, to = 2)
        }

        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(QueueTestTags.row(0), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("Track Two")))
    }

    private fun dragHandleDownOneSlot(index: Int) {
        val dragDistancePx = with(composeRule.density) {
            (76.dp.toPx() * 0.72f) + 12f
        }
        composeRule
            .onNodeWithTag(QueueTestTags.dragHandle(index), useUnmergedTree = true)
            .performTouchInput {
                down(center)
                advanceEventTime(750L)
                moveBy(Offset(0f, dragDistancePx / 2f))
                moveBy(Offset(0f, dragDistancePx / 2f))
                up()
            }
    }

    private fun sampleQueue(): List<AudioTrack> {
        return listOf(
            track(id = "now", title = "Now Playing"),
            track(id = "upcoming_a", title = "Track One"),
            track(id = "upcoming_b", title = "Track Two"),
            track(id = "upcoming_c", title = "Track Three")
        )
    }

    private fun track(id: String, title: String): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = "Tiger Artist",
            album = "Tiger Album",
            uri = Uri.parse("content://tigerplayer/$id"),
            artworkUri = Uri.parse("content://tigerplayer/art/$id"),
            durationMs = 180_000L,
            mimeType = "audio/mpeg",
            isLocal = true,
            path = "/music/$id.mp3"
        )
    }

    private fun List<AudioTrack>.moved(from: Int, to: Int): List<AudioTrack> {
        val mutable = toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        return mutable.toList()
    }
}

