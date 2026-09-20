package com.example.tigerplayer.data.model

/**
 * Domain-level view of a [com.example.tigerplayer.data.local.entity.MusicFolderEntity] (issue
 * #50) - a user-selected music directory, either an "include" root scanned via SAF or an
 * "exclude" entry honoured across scan, search and playback.
 */
data class MusicFolder(
    val uriString: String,
    val displayName: String,
    val path: String,
    val isExcluded: Boolean,
    val addedAt: Long
)
