package com.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted definition for a rule-based Smart Playlist (issue #68).
 *
 * Track membership is never materialized on disk; instead [rulesJson], [sortBy],
 * [sortDescending], and [limitCount] are compiled into a live Room `@RawQuery`
 * over `cached_tracks`, `playback_history`, and `artist_cache`.
 */
@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rulesJson: String,
    val sortBy: String = "PLAY_COUNT",
    val sortDescending: Boolean = true,
    val limitCount: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
