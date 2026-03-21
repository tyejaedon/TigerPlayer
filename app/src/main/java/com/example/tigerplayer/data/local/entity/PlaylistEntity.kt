package com.example.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index


@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)


@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("trackId")] // Speeds up queries when searching for a specific track
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val dateAdded: Long = System.currentTimeMillis()
)