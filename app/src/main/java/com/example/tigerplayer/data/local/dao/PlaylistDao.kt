package com.example.tigerplayer.data.local.dao

import androidx.room.*
import com.example.tigerplayer.data.local.entity.PlaylistEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.example.tigerplayer.data.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards // 🔥 THE FIX: Prevents Continuation<? super T> crashes in Room KSP
abstract class PlaylistDao {

    // --- PLAYLIST CREATION ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    abstract suspend fun deletePlaylist(playlistId: Long): Int

    @Query("UPDATE playlists SET name = :newName WHERE playlistId = :playlistId")
    abstract suspend fun renamePlaylist(playlistId: Long, newName: String): Int

    @Query("UPDATE playlists SET artworkUri = :artworkUri WHERE playlistId = :playlistId")
    abstract suspend fun updatePlaylistArtwork(playlistId: Long, artworkUri: String?): Int

    // --- TRACK MANAGEMENT ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTrackIntoPlaylist(crossRef: PlaylistTrackCrossRef): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTracks(crossRefs: List<PlaylistTrackCrossRef>): List<Long>

    @Query("""
        DELETE FROM playlist_track_cross_ref 
        WHERE playlistId = :playlistId AND trackId = :trackId
    """)
    abstract suspend fun removeTrackFromPlaylist(
        playlistId: Long,
        trackId: String
    ): Int

    @Query("""
        UPDATE playlist_track_cross_ref 
        SET position = :position 
        WHERE playlistId = :playlistId AND trackId = :trackId
    """)
    abstract suspend fun updateTrackPosition(
        playlistId: Long,
        trackId: String,
        position: Int
    ): Int

    // --- DATA RETRIEVAL ---

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    abstract fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("""
        SELECT 
            p.playlistId AS id,
            p.name,
            p.createdAt,
            p.artworkUri,
            COUNT(c.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_track_cross_ref c 
            ON p.playlistId = c.playlistId
        GROUP BY p.playlistId
        ORDER BY p.createdAt DESC
    """)
    abstract fun getPlaylistsWithCount(): Flow<List<Playlist>>

    @Query("""
        SELECT COUNT(*) 
        FROM playlist_track_cross_ref 
        WHERE playlistId = :playlistId
    """)
    abstract fun getTrackCountForPlaylist(playlistId: Long): Flow<Int>

    @Query("""
        SELECT trackId 
        FROM playlist_track_cross_ref 
        WHERE playlistId = :playlistId 
        ORDER BY position ASC, dateAdded ASC
    """)
    abstract fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<String>>
}