package com.example.tigerplayer.service

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSemanticsTest {

    @Test
    fun repeat_cycle_is_off_to_all_to_one_to_off() {
        val fromOff = PlaybackSemantics.nextRepeatMode(Player.REPEAT_MODE_OFF)
        val fromAll = PlaybackSemantics.nextRepeatMode(fromOff)
        val fromOne = PlaybackSemantics.nextRepeatMode(fromAll)

        assertEquals(Player.REPEAT_MODE_ALL, fromOff)
        assertEquals(Player.REPEAT_MODE_ONE, fromAll)
        assertEquals(Player.REPEAT_MODE_OFF, fromOne)
    }

    @Test
    fun repeat_cycle_falls_back_to_off_for_unknown_values() {
        val next = PlaybackSemantics.nextRepeatMode(Int.MAX_VALUE)

        assertEquals(Player.REPEAT_MODE_OFF, next)
    }

    @Test
    fun shuffle_toggle_round_trips_without_touching_repeat_inputs() {
        val enabled = PlaybackSemantics.toggledShuffle(currentEnabled = false)
        val disabled = PlaybackSemantics.toggledShuffle(currentEnabled = enabled)

        assertTrue(enabled)
        assertFalse(disabled)
    }

    @Test
    fun queue_index_validation_handles_bounds() {
        assertTrue(PlaybackSemantics.isValidQueueIndex(index = 0, itemCount = 3))
        assertTrue(PlaybackSemantics.isValidQueueIndex(index = 2, itemCount = 3))
        assertFalse(PlaybackSemantics.isValidQueueIndex(index = -1, itemCount = 3))
        assertFalse(PlaybackSemantics.isValidQueueIndex(index = 3, itemCount = 3))
        assertFalse(PlaybackSemantics.isValidQueueIndex(index = 0, itemCount = 0))
    }

    @Test
    fun queue_move_validation_rejects_out_of_bounds_indexes() {
        assertTrue(PlaybackSemantics.canMoveQueueItem(fromIndex = 0, toIndex = 2, itemCount = 3))
        assertTrue(PlaybackSemantics.canMoveQueueItem(fromIndex = 1, toIndex = 1, itemCount = 3))
        assertFalse(PlaybackSemantics.canMoveQueueItem(fromIndex = -1, toIndex = 1, itemCount = 3))
        assertFalse(PlaybackSemantics.canMoveQueueItem(fromIndex = 1, toIndex = 3, itemCount = 3))
        assertFalse(PlaybackSemantics.canMoveQueueItem(fromIndex = 0, toIndex = 0, itemCount = 0))
    }
}

