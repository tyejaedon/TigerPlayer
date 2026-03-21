package com.example.tigerplayer.data.model

import android.net.Uri

data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Unknown Album",
    val trackNumber: Int,
    val uri: Uri,
    val durationMs: Long,
    val artworkUri: Uri?,
    val mimeType: String = "audio/mpeg",
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val isLocal: Boolean = true,
    val isRemote: Boolean = false,
    val serverPath: String? = null // Store the raw Subsonic ID here
)

data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)