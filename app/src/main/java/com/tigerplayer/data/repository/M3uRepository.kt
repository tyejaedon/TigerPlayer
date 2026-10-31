package com.tigerplayer.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.M3uExportResult
import com.tigerplayer.data.model.M3uImportResult
import com.tigerplayer.data.model.M3uPathMode
import com.tigerplayer.utils.M3uParser
import com.tigerplayer.utils.SafPathResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating M3U and M3U8 playlist imports and exports (issue #58).
 *
 * Implements:
 * - Parsing M3U/M3U8 via Storage Access Framework (SAF) Uris.
 * - Resolving relative and absolute track paths against the local audio library.
 * - Collecting and reporting unmatched playlist tracks cleanly instead of silently dropping them.
 * - Lossless UTF-8 export to `.m3u8` with both absolute and relative path modes.
 * - Preservation of user and cross-ref order.
 */
@Singleton
class M3uRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val audioRepository: AudioRepository
) {
    companion object {
        private const val TAG = "M3uRepository"
    }

    /**
     * Imports an M3U/M3U8 playlist from a SAF [fileUri].
     *
     * @param fileUri Content URI pointing to the .m3u or .m3u8 file.
     * @param customPlaylistName Optional name override; if null or blank, derived from filename.
     * @return Result containing [M3uImportResult].
     */
    suspend fun importPlaylist(
        fileUri: Uri,
        customPlaylistName: String? = null
    ): Result<M3uImportResult> = withContext(Dispatchers.IO) {
        try {
            val displayName = queryDisplayName(fileUri)
            val inferredName = customPlaylistName?.trim()?.ifEmpty { null }
                ?: displayName?.substringBeforeLast('.')?.trim()?.ifEmpty { null }
                ?: "Imported Playlist"

            val playlistBaseDir = SafPathResolver.resolveParentPath(fileUri)

            val m3uPlaylist = context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                M3uParser.parseStream(inputStream, inferredName)
            } ?: return@withContext Result.failure(IOException("Could not open input stream for $fileUri"))

            // Query current local library tracks for resolution
            val libraryTracks = audioRepository.getCachedLocalTracks().first()

            val matchedTracks = mutableListOf<AudioTrack>()
            val unmatchedEntries = mutableListOf<String>()

            for (entry in m3uPlaylist.entries) {
                val matched = M3uParser.resolveTrack(
                    entryPath = entry.pathOrUri,
                    playlistBaseDir = playlistBaseDir,
                    libraryTracks = libraryTracks
                )
                if (matched != null) {
                    matchedTracks.add(matched)
                } else {
                    unmatchedEntries.add(entry.pathOrUri)
                }
            }

            // Create new playlist in Room
            val newPlaylistId = playlistDao.insertPlaylistSafely(
                PlaylistEntity(
                    playlistId = 0L,
                    name = inferredName,
                    artworkUri = null,
                    createdAt = System.currentTimeMillis()
                )
            )

            // Insert cross references in exact sequential order
            val crossRefs = matchedTracks.mapIndexed { index, track ->
                PlaylistTrackCrossRef(
                    playlistId = newPlaylistId,
                    trackId = track.id,
                    position = index,
                    dateAdded = System.currentTimeMillis()
                )
            }
            if (crossRefs.isNotEmpty()) {
                playlistDao.insertTracksBatch(crossRefs)
            }

            val result = M3uImportResult(
                playlistId = newPlaylistId,
                playlistName = inferredName,
                importedCount = matchedTracks.size,
                unmatchedCount = unmatchedEntries.size,
                unmatchedEntries = unmatchedEntries
            )

            Result.success(result)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to import M3U playlist", e)
            Result.failure(e)
        }
    }

    /**
     * Exports a playlist identified by [playlistId] to a SAF [destinationUri] in `.m3u8` UTF-8 format.
     *
     * @param playlistId ID of the playlist in Room.
     * @param destinationUri Destination content URI chosen by user via SAF.
     * @param pathMode Whether to format track paths as absolute or relative.
     */
    suspend fun exportPlaylist(
        playlistId: Long,
        destinationUri: Uri,
        pathMode: M3uPathMode = M3uPathMode.ABSOLUTE
    ): Result<M3uExportResult> = withContext(Dispatchers.IO) {
        try {
            val playlist = playlistDao.getPlaylistById(playlistId)
                ?: return@withContext Result.failure(IllegalArgumentException("Playlist $playlistId not found"))

            // Obtain playlist tracks in order
            val tracks = audioRepository.getTracksForPlaylist(playlistId).first()

            val playlistBaseDir = SafPathResolver.resolveParentPath(destinationUri)

            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { outputStream ->
                M3uParser.writePlaylist(
                    outputStream = outputStream,
                    tracks = tracks,
                    playlistBaseDir = playlistBaseDir,
                    pathMode = pathMode
                )
            } ?: return@withContext Result.failure(IOException("Could not open output stream for $destinationUri"))

            val result = M3uExportResult(
                playlistId = playlistId,
                playlistName = playlist.name,
                trackCount = tracks.size
            )

            Result.success(result)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to export playlist $playlistId", e)
            Result.failure(e)
        }
    }

    /**
     * Queries the display name of a content URI via SAF.
     */
    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
