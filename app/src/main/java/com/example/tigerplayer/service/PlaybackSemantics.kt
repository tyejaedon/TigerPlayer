package com.example.tigerplayer.service

import androidx.media3.common.Player

internal object PlaybackSemantics {

    fun nextRepeatMode(currentMode: Int): Int {
        return when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggledShuffle(currentEnabled: Boolean): Boolean = !currentEnabled

    fun isValidQueueIndex(index: Int, itemCount: Int): Boolean {
        return index in 0 until itemCount
    }

    fun canMoveQueueItem(fromIndex: Int, toIndex: Int, itemCount: Int): Boolean {
        return isValidQueueIndex(fromIndex, itemCount) && isValidQueueIndex(toIndex, itemCount)
    }
}

