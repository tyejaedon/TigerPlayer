package com.example.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * THE VAULT RECORD
 * Represents a local high-fidelity track cached in the Room database
 * to prevent redundant MediaStore scans.
 */
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
    val trackNumber: Int,
    val year: String? = null,

    // --- THE FIX: The Absolute Path ---
    // Critical for the LyricsRepository to locate .lrc files in the same folder
    val path: String?
)