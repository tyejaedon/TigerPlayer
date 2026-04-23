package com.example.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "playlists")
data class PlaylistEntity(
    /**
     * THE ID ANCHOR
     * - Default (0): Triggers Room's Auto-Increment for standard user playlists.
     * - Manual (-1): Used for LIKED_SONGS_ID to create a singleton "Liked Songs" vault.
     */
    @PrimaryKey(autoGenerate = true)
    val playlistId: Long = 0,

    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("trackId")] // Critical for performance when checking if a track is in a playlist
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val dateAdded: Long = System.currentTimeMillis()
)