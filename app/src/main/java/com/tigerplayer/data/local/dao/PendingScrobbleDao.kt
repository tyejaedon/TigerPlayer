package com.tigerplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tigerplayer.data.local.entity.PendingScrobbleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for durable offline Last.fm scrobble queue.
 */
@Dao
interface PendingScrobbleDao {

    @Insert
    suspend fun insert(scrobble: PendingScrobbleEntity): Long

    @Query(
        "SELECT * FROM pending_scrobbles " +
            "WHERE nextRetryAtMs <= :nowMs " +
            "ORDER BY timestampSec ASC " +
            "LIMIT :limit"
    )
    suspend fun getDueScrobbles(nowMs: Long, limit: Int = 50): List<PendingScrobbleEntity>

    @Query("DELETE FROM pending_scrobbles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query(
        "UPDATE pending_scrobbles " +
            "SET attemptCount = :attemptCount, nextRetryAtMs = :nextRetryAtMs " +
            "WHERE id = :id"
    )
    suspend fun updateRetry(id: Long, attemptCount: Int, nextRetryAtMs: Long)

    @Query("SELECT COUNT(*) FROM pending_scrobbles")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM pending_scrobbles")
    suspend fun clearAll()
}
