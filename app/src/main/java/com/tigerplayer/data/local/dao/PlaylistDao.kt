package com.tigerplayer.data.local.dao

import androidx.room.*
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.tigerplayer.data.model.Playlist
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

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId LIMIT 1")
    abstract suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("""
        SELECT * FROM playlists 
        WHERE LOWER(TRIM(name)) = LOWER(TRIM(:likedName)) AND playlistId != :singletonId
    """)
    abstract suspend fun findLegacyLikedPlaylists(
        likedName: String = PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME,
        singletonId: Long = PlaylistEntity.LIKED_SONGS_PLAYLIST_ID
    ): List<PlaylistEntity>

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    abstract suspend fun deleteCrossRefsForPlaylist(playlistId: Long): Int

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

    /**
     * Ensures that exactly one playlist named "Liked Songs" exists as a singleton (ID = -1L).
     * If legacy or duplicate playlists matching "Liked Songs" (case-insensitive) exist from previous
     * versions, their tracks are merged into the singleton liked playlist and the duplicate playlists are removed.
     * Returns the list of track IDs present in the singleton liked playlist.
     */
    @Transaction
    open suspend fun ensureSingletonLikedPlaylist(
        singletonId: Long = PlaylistEntity.LIKED_SONGS_PLAYLIST_ID,
        likedName: String = PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME
    ): List<String> {
        // 1. Ensure the singleton playlist entity exists
        val existingSingleton = getPlaylistById(singletonId)
        if (existingSingleton == null) {
            insertPlaylist(
                PlaylistEntity(
                    playlistId = singletonId,
                    name = likedName,
                    artworkUri = null,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else if (existingSingleton.name != likedName) {
            renamePlaylist(singletonId, likedName)
        }

        // 2. Query any legacy/duplicate playlists matching "Liked Songs"
        val legacyPlaylists = findLegacyLikedPlaylists(likedName, singletonId)
        val singletonRefs = getRawCrossRefs(singletonId)
        val allMergedTrackIds = singletonRefs.map { it.trackId }.toMutableSet()
        var nextPosition = singletonRefs.size

        if (legacyPlaylists.isNotEmpty()) {
            for (legacy in legacyPlaylists) {
                val legacyRefs = getRawCrossRefs(legacy.playlistId)
                val newRefs = mutableListOf<PlaylistTrackCrossRef>()

                for (ref in legacyRefs) {
                    if (ref.trackId !in allMergedTrackIds) {
                        allMergedTrackIds.add(ref.trackId)
                        newRefs.add(
                            PlaylistTrackCrossRef(
                                playlistId = singletonId,
                                trackId = ref.trackId,
                                dateAdded = ref.dateAdded,
                                position = nextPosition++
                            )
                        )
                    }
                }

                if (newRefs.isNotEmpty()) {
                    insertTracksBatch(newRefs)
                }

                // Delete legacy cross references and the duplicate playlist entity
                deleteCrossRefsForPlaylist(legacy.playlistId)
                deletePlaylist(legacy.playlistId)
            }
        }

        return allMergedTrackIds.toList()
    }

    /**
     * Safely inserts a playlist, ensuring that requests to create a playlist named "Liked Songs"
     * are redirected to the singleton liked playlist instead of creating duplicates.
     */
    @Transaction
    open suspend fun insertPlaylistSafely(playlist: PlaylistEntity): Long {
        if (playlist.name.trim().equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)) {
            ensureSingletonLikedPlaylist()
            return PlaylistEntity.LIKED_SONGS_PLAYLIST_ID
        }
        return insertPlaylist(playlist)
    }

    /**
     * Safely renames a playlist. If a user attempts to rename a playlist to "Liked Songs",
     * it merges the playlist into the singleton liked playlist to prevent duplicate names.
     */
    @Transaction
    open suspend fun renamePlaylistSafely(playlistId: Long, newName: String): Int {
        val trimmed = newName.trim()
        if (trimmed.equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)) {
            if (playlistId != PlaylistEntity.LIKED_SONGS_PLAYLIST_ID) {
                renamePlaylist(playlistId, trimmed)
                ensureSingletonLikedPlaylist()
                return 1
            }
        }
        return renamePlaylist(playlistId, trimmed)
    }



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

    // --- BACKUP & RESTORE (one-shot reads / destructive writes, used only by BackupManager) ---

    /** One-shot snapshot of every playlist, for export. Never observed by the UI. */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    abstract suspend fun getAllPlaylistsSync(): List<PlaylistEntity>

    /** One-shot snapshot of a playlist's tracks in order, for export. */
    @Query("SELECT * FROM playlist_track_cross_ref WHERE playlistId = :playlistId ORDER BY position ASC")
    abstract suspend fun getCrossRefsForPlaylistSync(playlistId: Long): List<PlaylistTrackCrossRef>

    @Query("DELETE FROM playlists")
    protected abstract suspend fun clearAllPlaylists(): Int

    @Query("DELETE FROM playlist_track_cross_ref")
    protected abstract suspend fun clearAllCrossRefs(): Int

    /** Wipes every playlist and cross-ref row. Only ever called for a "replace" restore. */
    @Transaction
    open suspend fun replaceAllPlaylists() {
        clearAllCrossRefs()
        clearAllPlaylists()
    }
}
