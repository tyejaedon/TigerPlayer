package com.example.tigerplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tigerplayer.data.local.entity.PlaylistEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.example.tigerplayer.data.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
interface PlaylistDao {

    // --- PLAYLIST CREATION ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long): Int

    // --- TRACK MANAGEMENT ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackIntoPlaylist(crossRef: PlaylistTrackCrossRef): Long

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Int

    // --- DATA RETRIEVAL ---
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    /**
     * THE DYNAMIC COUNT:
     * This query joins the playlist metadata with the track cross-references
     * to provide a real-time count of songs without manual increments.
     */
    @Query("""
        SELECT p.playlistId AS id, p.name, p.createdAt, COUNT(c.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_track_cross_ref c ON p.playlistId = c.playlistId
        GROUP BY p.playlistId
        ORDER BY p.createdAt DESC
    """)
    fun getPlaylistsWithCount(): Flow<List<Playlist>>

    @Query("SELECT COUNT(*) FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int>

    @Query("SELECT trackId FROM playlist_track_cross_ref WHERE playlistId = :playlistId ORDER BY dateAdded ASC")
    fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<String>>
}