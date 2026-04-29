package com.example.tigerplayer.data.local.dao

import androidx.room.*
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.local.entity.CachedTrackEntity
import com.example.tigerplayer.data.local.entity.LyricsCacheEntity
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.example.tigerplayer.data.local.entity.WaveformCacheEntity
import kotlinx.coroutines.flow.Flow

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
abstract class TigerDao {

    // --- 1. THE CHRONICLES (Playback History) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHistory(history: PlaybackHistoryEntity): Long

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT 50")
    abstract fun getRecentTracks(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT SUM(durationListenedMs) FROM playback_history")
    abstract fun getTotalListeningTimeMs(): Flow<Long?>

    @Query("SELECT SUM(durationListenedMs) FROM playback_history WHERE timestamp >= :startTime")
    abstract fun getTotalListeningTimeMs(startTime: Long): Flow<Long?>

    @Query("""
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
    """)
    abstract fun getTopArtist(): Flow<String?>

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
    abstract fun getTopArtists(startTime: Long, limit: Int): Flow<List<ArtistStats>>

    @Query("""
        SELECT trackId, title, artist, MAX(imageUrl) as imageUrl, COUNT(*) as playCount 
        FROM playback_history 
        WHERE timestamp >= :startTime 
        GROUP BY trackId 
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    abstract fun getTopTracks(startTime: Long, limit: Int): Flow<List<TrackStats>>

    // --- 2. THE METADATA SIGN (Artist Cache) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertArtistCache(artist: ArtistCacheEntity): Long

    @Query("SELECT * FROM artist_cache WHERE artistName = :name LIMIT 1")
    abstract suspend fun getArtistCache(name: String): ArtistCacheEntity?

    @Query("DELETE FROM artist_cache")
    abstract suspend fun clearArtistCache(): Int

    // --- 3. THE VAULT (Local Track Caching) ---

    @Query("SELECT * FROM cached_tracks ORDER BY title ASC")
    abstract suspend fun getCachedTracks(): List<CachedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCachedTracks(tracks: List<CachedTrackEntity>): List<Long>

    @Query("DELETE FROM cached_tracks")
    abstract suspend fun clearTrackCache(): Int

    @Query("UPDATE cached_tracks SET isLiked = :isLiked WHERE id = :trackId")
    abstract suspend fun updateTrackLikeStatus(trackId: String, isLiked: Boolean): Int

    // 🔥 NEW: Save High-Res Artwork Permanently
    @Query("UPDATE cached_tracks SET artworkUriString = :newUri WHERE id = :trackId")
    abstract suspend fun updateTrackArtworkUri(trackId: String, newUri: String): Int

    // --- 4. THE LYRIC ARCHIVE ---

    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    abstract suspend fun getLyricsCache(trackId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLyricsCache(lyrics: LyricsCacheEntity): Long

    @Query("UPDATE lyrics_cache SET lastAccessed = :timestamp WHERE trackId = :trackId")
    abstract suspend fun updateLyricsAccessTime(
        trackId: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    @Query("DELETE FROM lyrics_cache WHERE trackId NOT IN (SELECT trackId FROM lyrics_cache ORDER BY lastAccessed DESC LIMIT 2000)")
    abstract suspend fun enforceLyricsCacheLimit(): Int

    @Query("DELETE FROM lyrics_cache")
    abstract suspend fun clearAllLyrics(): Int

    // --- THE WAVEFORM CACHE ---

    @Query("SELECT * FROM waveform_cache WHERE trackId = :trackId LIMIT 1")
    abstract suspend fun getWaveformCache(trackId: String): WaveformCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWaveformCache(waveform: WaveformCacheEntity)

    // --- 5. THE TEMPORAL DATA (Minutes & Play Counts) ---

    @Query("""
        SELECT COUNT(*) 
        FROM playback_history 
        WHERE trim(CASE 
            WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
            WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
            WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
            WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
            ELSE artist 
        END) = :artistName
    """)
    abstract suspend fun getArtistPlayCount(artistName: String): Int

    @Query("""
        SELECT COALESCE(SUM(durationListenedMs), 0) / 60000 
        FROM playback_history 
        WHERE trim(CASE 
            WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
            WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
            WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
            WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
            ELSE artist 
        END) = :artistName
    """)
    abstract suspend fun getArtistMinutesListened(artistName: String): Int

    // --- 6. GRIMOIRE MANAGEMENT (Playlists) ---

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    abstract suspend fun deletePlaylist(playlistId: Long): Int

    @Query("UPDATE playlists SET name = :newName WHERE playlistId = :playlistId")
    abstract suspend fun renamePlaylist(playlistId: Long, newName: String): Int

    @Query("UPDATE playlists SET artworkUri = :artworkUri WHERE playlistId = :playlistId")
    abstract suspend fun updatePlaylistArtwork(playlistId: Long, artworkUri: String): Int

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    abstract suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPlaylistTrackCrossRefs(crossRefs: List<PlaylistTrackCrossRef>): List<Long>

    @Query("UPDATE playlist_track_cross_ref SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    abstract suspend fun updatePlaylistTrackPosition(playlistId: Long, trackId: String, position: Int): Int

    // --- 7. GLOBAL PURGE ---

    @Query("DELETE FROM playback_history WHERE timestamp <= :cutoffTime")
    abstract suspend fun purgeOldHistory(cutoffTime: Long): Int

    @Query("""
        SELECT artworkUriString FROM cached_tracks 
        WHERE artist = :artistName 
        AND artworkUriString IS NOT NULL 
        AND artworkUriString != '' 
        LIMIT 1
    """)
    abstract suspend fun getLocalArtworkForArtist(artistName: String): String?
}