package com.tigerplayer.data.model

import android.net.Uri

/**
 * Result models and configurations for M3U / M3U8 import and export (issue #58).
 */

/**
 * An individual entry parsed from an M3U/M3U8 file.
 */
data class M3uEntry(
    val pathOrUri: String,
    val durationSeconds: Long = -1L,
    val title: String? = null
)

/**
 * Parsed M3U playlist file content.
 */
data class M3uPlaylist(
    val name: String,
    val entries: List<M3uEntry>
)

/**
 * Path style to use when exporting an M3U8 playlist.
 */
enum class M3uPathMode {
    ABSOLUTE,
    RELATIVE
}

/**
 * Summary returned after importing an M3U/M3U8 playlist.
 *
 * @property playlistId ID of the newly imported playlist in Room.
 * @property playlistName Name given to the imported playlist.
 * @property importedCount Number of entries successfully matched to local library tracks.
 * @property unmatchedCount Number of entries in the M3U file that could not be matched.
 * @property unmatchedEntries Raw paths/URIs that could not be resolved.
 */
data class M3uImportResult(
    val playlistId: Long,
    val playlistName: String,
    val importedCount: Int,
    val unmatchedCount: Int,
    val unmatchedEntries: List<String> = emptyList()
)

/**
 * Summary returned after exporting an M3U8 playlist.
 *
 * @property playlistId ID of the exported playlist.
 * @property playlistName Name of the exported playlist.
 * @property trackCount Number of tracks exported.
 */
data class M3uExportResult(
    val playlistId: Long,
    val playlistName: String,
    val trackCount: Int
)
