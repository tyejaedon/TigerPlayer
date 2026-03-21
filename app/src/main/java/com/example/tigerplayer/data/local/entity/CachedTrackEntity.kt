package com.example.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uriString: String,
    val artworkUriString: String,
    val durationMs: Long,
    val mimeType: String,
    val bitrate: Int,
    val sampleRate: Int,
    val trackNumber: Int
)