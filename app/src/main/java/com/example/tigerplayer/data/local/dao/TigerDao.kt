package com.example.tigerplayer.data.local.dao

import androidx.room.*
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.local.entity.CachedTrackEntity
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

data class ArtistStats(
    val artistName: String,
    val playCount: Int,
    val imageUrl: String? = null // Room will look for this in the MAX(imageUrl)
)

data class TrackStats(
    val trackId: String,
    val title: String,
    val artist: String,
    val imageUrl: String?, // Now matched with your Query
    val playCount: Int
)

@Dao
@JvmSuppressWildcards
interface TigerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity): Long

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentTracks(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT SUM(durationListenedMs) FROM playback_history")
    fun getTotalListeningTimeMs(): Flow<Long?>

    @Query("SELECT SUM(durationListenedMs) FROM playback_history WHERE timestamp >= :startTime")
    fun getTotalListeningTimeMs(startTime: Long): Flow<Long?>


    @Query("SELECT artist FROM playback_history GROUP BY artist ORDER BY COUNT(*) DESC LIMIT 1")
    fun getTopArtist(): Flow<String?>



    @Query("""
        SELECT 
            trim(CASE 
                WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
                WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
                WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
                WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
                ELSE artist 
            END) as artistName, 
            COUNT(*) as playCount,
            MAX(imageUrl) as imageUrl
        FROM playback_history 
        WHERE timestamp >= :startTime 
        GROUP BY artistName 
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    fun getTopArtists(startTime: Long, limit: Int): Flow<List<ArtistStats>>

    @Query("""
        SELECT trackId, title, artist, MAX(imageUrl) as imageUrl, COUNT(*) as playCount 
        FROM playback_history 
        WHERE timestamp >= :startTime 
        GROUP BY trackId 
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    fun getTopTracks(startTime: Long, limit: Int): Flow<List<TrackStats>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistCache(artist: ArtistCacheEntity): Long

    @Transaction
    @Query("SELECT * FROM artist_cache WHERE artistName = :name LIMIT 1")
    suspend fun getArtistCache(name: String): ArtistCacheEntity?

    @Query("SELECT * FROM cached_tracks ORDER BY title ASC")
    suspend fun getCachedTracks(): List<CachedTrackEntity>

    // ✅ FIX: Explicit return types to satisfy KSP
    @Query("DELETE FROM cached_tracks")
    suspend fun clearTrackCache(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTracks(tracks: List<CachedTrackEntity>): List<Long>

}
