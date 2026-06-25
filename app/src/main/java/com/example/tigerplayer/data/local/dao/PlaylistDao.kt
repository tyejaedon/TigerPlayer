package com.example.tigerplayer.data.local.dao

import androidx.room.*
import com.example.tigerplayer.data.local.entity.PlaylistEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.example.tigerplayer.data.model.Playlist
import kotlinx.coroutines.flow.Flow

/**
 * THE PLAYLIST ORCHESTRATOR
 * Handles relational mapping between archives and custom sequences.
 * Uses @JvmSuppressWildcards to ensure compatibility with modern Room KSP generators.
 */
@Dao
@JvmSuppressWildcards
abstract class PlaylistDao {

    // --- PLAYLIST CORE OPS ---


    @Transaction
    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    abstract suspend fun deletePlaylist(playlistId: Long): Int

    @Query("UPDATE playlists SET name = :newName WHERE playlistId = :playlistId")
    abstract suspend fun renamePlaylist(playlistId: Long, newName: String): Int

    @Query("UPDATE playlists SET artworkUri = :artworkUri WHERE playlistId = :playlistId")
    abstract suspend fun updatePlaylistArtwork(playlistId: Long, artworkUri: String?): Int

    // --- RELATIONAL TRACK OPS ---

    /**
     * Inserts a track and automatically calculates the next position if not provided.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTrackCrossRef(crossRef: PlaylistTrackCrossRef): Long

    @Transaction
    open suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        val currentCount = getTrackCountImmediate(playlistId)
        val crossRef = PlaylistTrackCrossRef(
            playlistId = playlistId,
            trackId = trackId,
            position = currentCount, // Append to the end
            dateAdded = System.currentTimeMillis()
        )
        insertTrackCrossRef(crossRef)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTracksBatch(crossRefs: List<PlaylistTrackCrossRef>): List<Long>

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    protected abstract suspend fun internalRemoveTrack(playlistId: Long, trackId: String): Int

    /**
     * Removes a track and preserves the sequence integrity of the remaining archives.
     */
    @Transaction
    open suspend fun removeTrackAndReorder(playlistId: Long, trackId: String) {
        internalRemoveTrack(playlistId, trackId)
        // Cleanup: Shift all subsequent tracks down to fill the gap
        // This ensures the 'position' column remains a clean 0..N sequence
        reorderPlaylistPositions(playlistId)
    }

    @Query("""
        SELECT 
            p.playlistId AS id,
            p.name,
            p.createdAt,
            p.artworkUri,
            COUNT(c.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_track_cross_ref c ON p.playlistId = c.playlistId
        GROUP BY p.playlistId
        ORDER BY p.createdAt DESC
    """)
    abstract fun getPlaylistsWithCount(): Flow<List<Playlist>>

    /**
     * CREATE NEW PLAYLIST
     * TigerDao was missing the ability to actually record a new PlaylistEntity.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long



    @Query("""
        UPDATE playlist_track_cross_ref 
        SET position = :newPosition 
        WHERE playlistId = :playlistId AND trackId = :trackId
    """)
    abstract suspend fun updateTrackPosition(playlistId: Long, trackId: String, newPosition: Int): Int

    // --- ANALYTICS & RETRIEVAL ---

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    abstract fun getAllPlaylistsFlow(): Flow<List<PlaylistEntity>>



    @Query("SELECT COUNT(*) FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    abstract fun getTrackCountForPlaylist(playlistId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    protected abstract suspend fun getTrackCountImmediate(playlistId: Long): Int

    @Query("""
        SELECT trackId 
        FROM playlist_track_cross_ref 
        WHERE playlistId = :playlistId 
        ORDER BY position ASC, dateAdded ASC
    """)
    abstract fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<String>>

    // --- PRIVATE ARCHIVE RECOVERY ---

    @Query("""
        SELECT * FROM playlist_track_cross_ref 
        WHERE playlistId = :playlistId 
        ORDER BY position ASC
    """)
    protected abstract suspend fun getRawCrossRefs(playlistId: Long): List<PlaylistTrackCrossRef>

    @Transaction
    open suspend fun reorderPlaylistPositions(playlistId: Long) {
        val tracks = getRawCrossRefs(playlistId)
        tracks.forEachIndexed { index, ref ->
            if (ref.position != index) {
                updateTrackPosition(playlistId, ref.trackId, index)
            }
        }
    }
}