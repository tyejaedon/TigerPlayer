package com.example.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.tigerplayer.data.local.MediaSource

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String?,
    val durationListenedMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val source: MediaSource
)
