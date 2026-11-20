package com.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Queued scrobble awaiting delivery to Last.fm.
 * Persisted in Room so scrobbles survive offline periods and process restarts.
 */
@Entity(
    tableName = "pending_scrobbles",
    indices = [Index(value = ["nextRetryAtMs"])]
)
data class PendingScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val artist: String,
    val track: String,
    val album: String? = null,
    val durationSec: Int,
    val timestampSec: Long,
    val attemptCount: Int = 0,
    val nextRetryAtMs: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis()
)
