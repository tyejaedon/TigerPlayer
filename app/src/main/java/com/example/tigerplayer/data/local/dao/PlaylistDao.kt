package com.example.tigerplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tigerplayer.data.local.entity.PlaylistEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
interface PlaylistDao {

    // --- PLAYLIST CREATION ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    // THE FIX: Add ': Int' to return the number of deleted rows
    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long): Int

    // --- TRACK MANAGEMENT ---
    // THE FIX: Add ': Long' to return the inserted row ID
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackIntoPlaylist(crossRef: PlaylistTrackCrossRef): Long

    // THE FIX: Add ': Int' to return the number of deleted rows
    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Int

    // --- DATA RETRIEVAL (Leave these alone, they are fine) ---
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT COUNT(*) FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int>

    @Query("SELECT trackId FROM playlist_track_cross_ref WHERE playlistId = :playlistId ORDER BY dateAdded ASC")
    fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<String>>
}