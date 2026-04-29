package com.example.tigerplayer.data.model

import android.net.Uri

data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val artworkUri: Uri,
    val durationMs: Long,
    val mimeType: String,
    val isLocal: Boolean = false,
    val isRemote: Boolean = false,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val trackNumber: Int = 0,
    val serverPath: String? = null,
    val year: String? = null,
    var isLiked: Boolean = false,

    // --- THE MISSING LINK ---
    // This must exist for track.path to work in the LyricsRepository!
    val path: String? = null
)

/**
 * THE PLAYLIST MANIFEST:
 * Refactored to support dynamic counts from the database,
 * and added artworkUri to support custom covers set by the user.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val artworkUri: String? = null, // Added to support the new edit UI
    val createdAt: Long = System.currentTimeMillis()
)