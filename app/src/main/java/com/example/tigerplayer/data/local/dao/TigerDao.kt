package com.example.tigerplayer.data.local.dao

import androidx.room.*
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.local.entity.CachedTrackEntity
import com.example.tigerplayer.data.local.entity.LyricsCacheEntity
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * THE CHRONICLE RECORDS
 * Data classes for aggregating your listening habits.
 */
data class ArtistStats(
    val artistName: String,
    val playCount: Int,
    val imageUrl: String? = null
)

data class TrackStats(
    val trackId: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val playCount: Int
)

@Dao
@JvmSuppressWildcards
interface TigerDao {

    // --- THE CHRONICLES (Playback History) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity): Long

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentTracks(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT SUM(durationListenedMs) FROM playback_history")
    fun getTotalListeningTimeMs(): Flow<Long?>

    @Query("SELECT SUM(durationListenedMs) FROM playback_history WHERE timestamp >= :startTime")
    fun getTotalListeningTimeMs(startTime: Long): Flow<Long?>

    /**
     * THE SUPREME ARTIST
     * Returns the most played artist, accounting for collaborations.
     */
    @Query(
        """
        SELECT 
            trim(CASE 
                WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
                WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
                WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
                WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
                ELSE artist 
            END) as artistName
        FROM playback_history 
        GROUP BY artistName 
        ORDER BY COUNT(*) DESC 
        LIMIT 1
    """
    )
    fun getTopArtist(): Flow<String?>

    /**
     * THE TOP ARTISTS LIST
     * Direct SQL ritual to split featuring artists and rank them.
     */
    @Query(
        """
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
    """
    )
    fun getTopArtists(startTime: Long, limit: Int): Flow<List<ArtistStats>>

    @Query(
        """
        SELECT trackId, title, artist, MAX(imageUrl) as imageUrl, COUNT(*) as playCount 
        FROM playback_history 
        WHERE timestamp >= :startTime 
        GROUP BY trackId 
        ORDER BY playCount DESC 
        LIMIT :limit
    """
    )
    fun getTopTracks(startTime: Long, limit: Int): Flow<List<TrackStats>>

    // --- THE METADATA SIGN (Artist Cache) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistCache(artist: ArtistCacheEntity): Long

    @Query("SELECT * FROM artist_cache WHERE artistName = :name LIMIT 1")
    suspend fun getArtistCache(name: String): ArtistCacheEntity?

    // --- THE VAULT (Local Track Caching) ---

    @Query("SELECT * FROM cached_tracks ORDER BY title ASC")
    suspend fun getCachedTracks(): List<CachedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTracks(tracks: List<CachedTrackEntity>): List<Long>

    @Query("DELETE FROM cached_tracks")
    suspend fun clearTrackCache(): Int

    // Inside your TigerDao interface:

    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun getLyricsCache(trackId: String): LyricsCacheEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertLyricsCache(lyrics: LyricsCacheEntity)

    @Query("UPDATE lyrics_cache SET lastAccessed = :timestamp WHERE trackId = :trackId")
    suspend fun updateLyricsAccessTime(
        trackId: String,
        timestamp: Long = System.currentTimeMillis()
    )

    // The space-saving ritual (2000 rows = ~10MB max)
    @Query("DELETE FROM lyrics_cache WHERE trackId NOT IN (SELECT trackId FROM lyrics_cache ORDER BY lastAccessed DESC LIMIT 2000)")
    suspend fun enforceLyricsCacheLimit()

    /**
     * PURGE RITUAL
     * Deletes playback history older than 90 days to keep the S22 snappy.
     */
    @Query("DELETE FROM lyrics_cache")
    suspend fun clearAllLyrics()


    @Query("DELETE FROM artist_cache") // Adjust table name if yours is different
    suspend fun clearArtistCache()
}