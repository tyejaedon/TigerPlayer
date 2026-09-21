package com.tigerplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tigerplayer.data.local.entity.MusicFolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Persists user-selected music directories (issue #50) - both "include" roots scanned via SAF
 * and "exclude" entries honoured across scan, search and playback.
 */
@Dao
abstract class MusicFolderDao {

    @Query("SELECT * FROM music_folders ORDER BY addedAt ASC")
    abstract fun getAll(): Flow<List<MusicFolderEntity>>

    @Query("SELECT * FROM music_folders ORDER BY addedAt ASC")
    abstract suspend fun getAllSync(): List<MusicFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(folder: MusicFolderEntity)

    @Query("DELETE FROM music_folders WHERE uriString = :uriString")
    abstract suspend fun deleteByUri(uriString: String)
}
