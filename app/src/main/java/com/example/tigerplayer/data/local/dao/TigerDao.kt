package com.example.tigerplayer.data.local.dao

import androidx.room.*
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.local.entity.CachedTrackEntity
import com.example.tigerplayer.data.local.entity.LyricsCacheEntity
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.example.tigerplayer.data.local.entity.WaveformCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * 📊 ANALYTICAL DATA MODELS
 */
data class ArtistStats(
    val artistName: String,
    val playCount: Int,
    val totalListeningMs: Long,
    val imageUrl: String? = null
)

data class TrackStats(
    val trackId: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val playCount: Int
)

data class SonicFootprintStats(
    val acoustic: Int,
    val electronic: Int,
    val bassHeavy: Int,
    val vocal: Int,
    val atmospheric: Int,
    val total: Int
)

/**
 * 🐅 TIGER DAO: THE ARCHIVE ENGINE
 * Optimized for high-frequency audio processing and real-time statistics.
 */
@Dao
@JvmSuppressWildcards
abstract class TigerDao {

    // ==========================================
    // --- 1. THE CHRONICLES (Playback History) ---
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHistory(history: PlaybackHistoryEntity): Long

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT 60")
    abstract fun getRecentTracks(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC")
    abstract fun getPlaybackHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE timestamp >= :sinceMillis ORDER BY timestamp DESC")
    abstract fun getPlaybackHistorySince(sinceMillis: Long): Flow<List<PlaybackHistoryEntity>>

    @Query(
        """
        SELECT COUNT(DISTINCT lower(trim(artist)))
        FROM playback_history
        WHERE trim(artist) != ''
          AND lower(trim(artist)) NOT IN ('unknown', 'unknown artist', '<unknown>', 'various artists')
    """
    )
    abstract fun getUniqueArtistCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT trackId) FROM playback_history")
    abstract fun getUniqueTrackCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationListenedMs), 0) FROM playback_history")
    abstract fun getTotalListeningTimeMs(): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationListenedMs), 0) FROM playback_history WHERE timestamp >= :startTime")
    abstract fun getTotalListeningTimeMs(startTime: Long): Flow<Long>

    @Query(
        """
        SELECT
            COALESCE(SUM(
                CASE WHEN (
                    LOWER(title || ' ' || artist || ' ' || album) LIKE '%acoustic%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%unplugged%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%folk%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%guitar%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%singer songwriter%'
                ) THEN 1 ELSE 0 END
            ), 0) AS acoustic,
            COALESCE(SUM(
                CASE WHEN (
                    LOWER(title || ' ' || artist || ' ' || album) LIKE '%electro%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%edm%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%house%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%techno%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%synth%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%trance%'
                ) THEN 1 ELSE 0 END
            ), 0) AS electronic,
            COALESCE(SUM(
                CASE WHEN (
                    LOWER(title || ' ' || artist || ' ' || album) LIKE '%bass%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%trap%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%drill%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%808%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%dubstep%'
                ) THEN 1 ELSE 0 END
            ), 0) AS bassHeavy,
            COALESCE(SUM(
                CASE WHEN (
                    LOWER(title || ' ' || artist || ' ' || album) LIKE '%vocal%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%choir%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%ballad%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%aria%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%soul%'
                ) THEN 1 ELSE 0 END
            ), 0) AS vocal,
            COALESCE(SUM(
                CASE WHEN (
                    LOWER(title || ' ' || artist || ' ' || album) LIKE '%ambient%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%chill%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%cinematic%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%space%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%dream%'
                    OR LOWER(title || ' ' || artist || ' ' || album) LIKE '%lofi%'
                ) THEN 1 ELSE 0 END
            ), 0) AS atmospheric,
            COUNT(*) AS total
        FROM playback_history
        WHERE timestamp >= :startTime
    """
    )
    abstract fun getSonicFootprintStats(startTime: Long): Flow<SonicFootprintStats>

    /**
     * IDENTIFY TOP ARTIST
     * Performs a single-pass string cleaning to identify the most played artist.
     * Upgraded with CTE for better performance on large histories.
     */
    @Query(
        """
        WITH NormalizedHistory AS (
            SELECT 
                trim(CASE 
                    WHEN instr(lower(artist), ' featuring ') > 0 THEN substr(artist, 1, instr(lower(artist), ' featuring ') - 1)
                    WHEN instr(lower(artist), ' feat. ') > 0 THEN substr(artist, 1, instr(lower(artist), ' feat. ') - 1)
                    WHEN instr(lower(artist), ' feat.') > 0 THEN substr(artist, 1, instr(lower(artist), ' feat.') - 1)
                    WHEN instr(lower(artist), ' ft. ') > 0 THEN substr(artist, 1, instr(lower(artist), ' ft. ') - 1)
                    WHEN instr(lower(artist), ' ft.') > 0 THEN substr(artist, 1, instr(lower(artist), ' ft.') - 1)
                    WHEN instr(lower(artist), ' & ') > 0 THEN substr(artist, 1, instr(lower(artist), ' & ') - 1)
                    WHEN instr(artist, ',') > 0 THEN substr(artist, 1, instr(artist, ',') - 1)
                    WHEN instr(artist, '/') > 0 THEN substr(artist, 1, instr(artist, '/') - 1)
                    WHEN instr(artist, ';') > 0 THEN substr(artist, 1, instr(artist, ';') - 1)
                    ELSE artist 
                END) as artistName,
                durationListenedMs
            FROM playback_history 
            WHERE timestamp >= :startTime
        ),
        FilteredHistory AS (
            SELECT artistName, durationListenedMs
            FROM NormalizedHistory
            WHERE trim(artistName) != ''
              AND lower(trim(artistName)) NOT IN ('unknown', 'unknown artist', '<unknown>', 'various artists')
        )
        SELECT artistName
        FROM FilteredHistory
        GROUP BY artistName 
        ORDER BY SUM(durationListenedMs) DESC, COUNT(*) DESC
        LIMIT 1
    """
    )
    abstract fun getTopArtist(startTime: Long = 0L): Flow<String?>

    /**
     * TOP PERFORMERS (WITH CTE OPTIMIZATION)
     * Cleans strings in a temporary view before grouping to save CPU cycles.
     * Fuels the high-density Constellation Galaxy UI.
     */
    @Query(
        """
        WITH NormalizedHistory AS (
            SELECT 
                trim(CASE 
                    WHEN instr(lower(artist), ' featuring ') > 0 THEN substr(artist, 1, instr(lower(artist), ' featuring ') - 1)
                    WHEN instr(lower(artist), ' feat. ') > 0 THEN substr(artist, 1, instr(lower(artist), ' feat. ') - 1)
                    WHEN instr(lower(artist), ' feat.') > 0 THEN substr(artist, 1, instr(lower(artist), ' feat.') - 1)
                    WHEN instr(lower(artist), ' ft. ') > 0 THEN substr(artist, 1, instr(lower(artist), ' ft. ') - 1)
                    WHEN instr(lower(artist), ' ft.') > 0 THEN substr(artist, 1, instr(lower(artist), ' ft.') - 1)
                    WHEN instr(lower(artist), ' & ') > 0 THEN substr(artist, 1, instr(lower(artist), ' & ') - 1)
                    WHEN instr(artist, ',') > 0 THEN substr(artist, 1, instr(artist, ',') - 1)
                    WHEN instr(artist, '/') > 0 THEN substr(artist, 1, instr(artist, '/') - 1)
                    WHEN instr(artist, ';') > 0 THEN substr(artist, 1, instr(artist, ';') - 1)
                    ELSE artist 
                END) as artistName,
                durationListenedMs
            FROM playback_history
            WHERE timestamp >= :startTime
        ),
        FilteredHistory AS (
            SELECT artistName, durationListenedMs
            FROM NormalizedHistory
            WHERE trim(artistName) != ''
              AND lower(trim(artistName)) NOT IN ('unknown', 'unknown artist', '<unknown>', 'various artists')
        )
        SELECT h.artistName, COUNT(*) as playCount, COALESCE(SUM(h.durationListenedMs), 0) as totalListeningMs, ac.imageUrl as imageUrl
        FROM FilteredHistory h
        LEFT JOIN artist_cache ac ON ac.artistName = LOWER(TRIM(h.artistName))
        GROUP BY h.artistName
        ORDER BY totalListeningMs DESC, playCount DESC
        LIMIT :limit
    """
    )
    abstract fun getTopArtists(startTime: Long, limit: Int): Flow<List<ArtistStats>>

    @Query(
        """
        SELECT h.trackId, h.title, h.artist, 
               COALESCE(ct.artworkUriString, MAX(h.imageUrl)) as imageUrl, 
               COUNT(*) as playCount 
        FROM playback_history h
        LEFT JOIN cached_tracks ct ON ct.id = h.trackId
        WHERE h.timestamp >= :startTime 
        GROUP BY h.trackId
        ORDER BY playCount DESC 
        LIMIT :limit
    """
    )
    abstract fun getTopTracks(startTime: Long, limit: Int): Flow<List<TrackStats>>

    /**
     * HEAVY ROTATION
     * Identifies tracks with high play density in the recent window.
     */
    @Query(
        """
        SELECT h.trackId, h.title, h.artist, 
               COALESCE(ct.artworkUriString, MAX(h.imageUrl)) as imageUrl, 
               COUNT(*) as playCount 
        FROM playback_history h
        LEFT JOIN cached_tracks ct ON ct.id = h.trackId
        WHERE h.timestamp >= :since
        GROUP BY h.trackId
        HAVING playCount >= 2
        ORDER BY playCount DESC 
        LIMIT 12
    """
    )
    abstract fun getHeavyRotation(since: Long): Flow<List<TrackStats>>

    /**
     * INDIVIDUAL ARTIST STATS
     * Fetches play count and listening time for a specific artist.
     */
    @Query(
        """
        WITH NormalizedHistory AS (
            SELECT 
                trim(CASE 
                    WHEN instr(lower(artist), ' featuring ') > 0 THEN substr(artist, 1, instr(lower(artist), ' featuring ') - 1)
                    WHEN instr(lower(artist), ' feat. ') > 0 THEN substr(artist, 1, instr(lower(artist), ' feat. ') - 1)
                    WHEN instr(lower(artist), ' feat.') > 0 THEN substr(artist, 1, instr(lower(artist), ' feat.') - 1)
                    WHEN instr(lower(artist), ' ft. ') > 0 THEN substr(artist, 1, instr(lower(artist), ' ft. ') - 1)
                    WHEN instr(lower(artist), ' ft.') > 0 THEN substr(artist, 1, instr(lower(artist), ' ft.') - 1)
                    WHEN instr(lower(artist), ' & ') > 0 THEN substr(artist, 1, instr(lower(artist), ' & ') - 1)
                    WHEN instr(artist, ',') > 0 THEN substr(artist, 1, instr(artist, ',') - 1)
                    WHEN instr(artist, '/') > 0 THEN substr(artist, 1, instr(artist, '/') - 1)
                    WHEN instr(artist, ';') > 0 THEN substr(artist, 1, instr(artist, ';') - 1)
                    ELSE artist 
                END) as artistName
            FROM playback_history
        )
        SELECT COUNT(*)
        FROM NormalizedHistory
        WHERE lower(trim(artistName)) = lower(trim(:artistName))
    """
    )
    abstract suspend fun getArtistPlayCount(artistName: String): Int

    @Query(
        """
        WITH NormalizedHistory AS (
            SELECT 
                trim(CASE 
                    WHEN instr(lower(artist), ' featuring ') > 0 THEN substr(artist, 1, instr(lower(artist), ' featuring ') - 1)
                    WHEN instr(lower(artist), ' feat. ') > 0 THEN substr(artist, 1, instr(lower(artist), ' feat. ') - 1)
                    WHEN instr(lower(artist), ' feat.') > 0 THEN substr(artist, 1, instr(lower(artist), ' feat.') - 1)
                    WHEN instr(lower(artist), ' ft. ') > 0 THEN substr(artist, 1, instr(lower(artist), ' ft. ') - 1)
                    WHEN instr(lower(artist), ' ft.') > 0 THEN substr(artist, 1, instr(lower(artist), ' ft.') - 1)
                    WHEN instr(lower(artist), ' & ') > 0 THEN substr(artist, 1, instr(lower(artist), ' & ') - 1)
                    WHEN instr(artist, ',') > 0 THEN substr(artist, 1, instr(artist, ',') - 1)
                    WHEN instr(artist, '/') > 0 THEN substr(artist, 1, instr(artist, '/') - 1)
                    WHEN instr(artist, ';') > 0 THEN substr(artist, 1, instr(artist, ';') - 1)
                    ELSE artist 
                END) as artistName,
                durationListenedMs
            FROM playback_history
        )
        SELECT CAST(COALESCE(SUM(durationListenedMs), 0) / 60000 AS INTEGER) 
        FROM NormalizedHistory 
        WHERE lower(trim(artistName)) = lower(trim(:artistName))
    """
    )
    abstract suspend fun getArtistMinutesListened(artistName: String): Int

    // ==========================================
    // --- 2. THE METADATA SIGN (Artist Cache) ---
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertArtistCache(artist: ArtistCacheEntity): Long

    @Query("SELECT * FROM artist_cache")
    abstract fun getAllArtistCache(): Flow<List<ArtistCacheEntity>>

    @Query("SELECT * FROM artist_cache WHERE artistName = :name LIMIT 1")
    abstract fun getArtistCache(name: String): Flow<ArtistCacheEntity?>

    @Query("SELECT * FROM artist_cache WHERE artistName = :name LIMIT 1")
    abstract suspend fun getArtistCacheSync(name: String): ArtistCacheEntity?

    @Query("DELETE FROM artist_cache")
    abstract suspend fun clearArtistCache(): Int

    // ==========================================
    // --- 3. THE VAULT (Track Caching) ---
    // ==========================================



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCachedTracks(tracks: List<CachedTrackEntity>): List<Long>

    @Query("UPDATE cached_tracks SET isLiked = :isLiked WHERE id = :trackId")
    abstract suspend fun updateTrackLikeStatus(trackId: String, isLiked: Boolean): Int

    @Query("UPDATE cached_tracks SET artworkUriString = :newUri WHERE id = :trackId")
    abstract suspend fun updateTrackArtworkUri(trackId: String, newUri: String): Int

    @Query(
        """
        SELECT h.trackId, h.title, h.artist, 
               COALESCE(ct.artworkUriString, MAX(h.imageUrl)) as imageUrl, 
               COUNT(*) as playCount 
        FROM playback_history h
        LEFT JOIN cached_tracks ct ON ct.id = h.trackId
        GROUP BY h.trackId
        ORDER BY playCount DESC 
    """
    )
    abstract fun getAllTracksStats(): Flow<List<TrackStats>>


    // ==========================================
    // --- 3. THE VAULT (Track Caching) ---
    // ==========================================

    @Query("SELECT * FROM cached_tracks ORDER BY title ASC")
    abstract fun getCachedTracks(): Flow<List<CachedTrackEntity>>

    @Query(
        """
        WITH filtered_history AS (
            SELECT trackId, timestamp
            FROM playback_history
            WHERE timestamp >= :sinceMillis
              AND (
                (:segment = 'MORNING' AND CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) BETWEEN 5 AND 11)
                OR (:segment = 'AFTERNOON' AND CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) BETWEEN 12 AND 16)
                OR (:segment = 'EVENING' AND CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) BETWEEN 17 AND 21)
                OR (:segment = 'NIGHT' AND (CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) >= 22
                    OR CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) <= 4))
              )
        ),
        ranked AS (
            SELECT
                trackId,
                COUNT(*) AS playCount,
                MAX(timestamp) AS lastPlayed
            FROM filtered_history
            GROUP BY trackId
        )
        SELECT c.*
        FROM cached_tracks c
        LEFT JOIN ranked r ON r.trackId = c.id
        ORDER BY COALESCE(r.playCount, 0) DESC, COALESCE(r.lastPlayed, 0) DESC, c.dateAdded DESC
        LIMIT :limit
    """
    )
    abstract fun getNeonDaylistTracks(
        segment: String,
        sinceMillis: Long,
        limit: Int
    ): Flow<List<CachedTrackEntity>>

    @Query(
        """
        WITH top_artists AS (
            SELECT LOWER(TRIM(artist)) AS artistName
            FROM playback_history
            WHERE timestamp >= :sinceMillis
            GROUP BY LOWER(TRIM(artist))
            ORDER BY COUNT(*) DESC
            LIMIT 3
        ),
        never_played AS (
            SELECT
                c.id,
                c.title,
                c.artist,
                c.album,
                c.uriString,
                c.artworkUriString,
                c.durationMs,
                c.mimeType,
                c.bitrate,
                c.sampleRate,
                c.trackNumber,
                c.year,
                c.dateAdded,
                c.isLiked,
                c.path,
                CASE
                    WHEN LOWER(TRIM(c.artist)) IN (SELECT artistName FROM top_artists) THEN 0
                    ELSE 1
                END AS priority
            FROM cached_tracks c
            WHERE c.id NOT IN (SELECT DISTINCT trackId FROM playback_history)
        )
        SELECT
            id,
            title,
            artist,
            album,
            uriString,
            artworkUriString,
            durationMs,
            mimeType,
            bitrate,
            sampleRate,
            trackNumber,
            year,
            dateAdded,
            isLiked,
            path
        FROM never_played
        ORDER BY priority ASC, dateAdded DESC
        LIMIT :limit
    """
    )
    abstract fun getVaultDiscoveryTracks(
        sinceMillis: Long,
        limit: Int
    ): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks ORDER BY title ASC")
    abstract suspend fun getCachedTracksSync(): List<CachedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun internalInsertCachedTracks(tracks: List<CachedTrackEntity>): List<Long>

    @Query("DELETE FROM cached_tracks")
    abstract suspend fun internalClearTrackCache(): Int

    /**
     * THE RECONCILIATION TRANSACTION
     * Ensures the UI never sees an empty library during a refresh.
     * This is the "Nuclear" fix for flickering screens.
     */
    @Transaction
    open suspend fun insertCachedTracksTransaction(tracks: List<CachedTrackEntity>) {
        internalClearTrackCache()
        if (tracks.isNotEmpty()) {
            internalInsertCachedTracks(tracks)
        }
    }

    // ==========================================
    // --- 5. GRIMOIRE MANAGEMENT (Playlists) ---
    // ==========================================

    /**
     * 🔥 THE VISIBILITY FIX: The Missing Retrieval Query
     * You need this in your main DAO to feed the Home/Library screens.
     * Note the 'AS id' to match your Playlist data class.
     */


    @Transaction
    open suspend fun savePlaylistOrder(playlistId: Long, trackIds: List<String>) {
        // Prevent operations on the ghost -1 ID
        if (playlistId <= 0) return

        trackIds.forEachIndexed { index, trackId ->
            updatePlaylistTrackPosition(playlistId, trackId, index)
        }
    }



    // ==========================================
    // --- 4. CONTENT CACHES (Lyrics & Waveforms) ---
    // ==========================================

    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    abstract suspend fun getLyricsCache(trackId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLyricsCache(lyrics: LyricsCacheEntity): Long

    @Query("SELECT * FROM waveform_cache WHERE trackId = :trackId LIMIT 1")
    abstract suspend fun getWaveformCache(trackId: String): WaveformCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWaveformCache(waveform: WaveformCacheEntity)

    // --- THE VAULT (Track Caching) ---

    @Query("DELETE FROM cached_tracks")
    abstract suspend fun clearTrackCache(): Int

    // ==========================================
    // --- 5. GRIMOIRE MANAGEMENT (Playlists) ---
    // ==========================================




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
    abstract suspend fun updatePlaylistTrackPosition(
        playlistId: Long,
        trackId: String,
        position: Int
    ): Int

    // ==========================================
    // --- 6. SYSTEM MAINTENANCE ---
    // ==========================================

    /**
     * PRUNING RITUAL
     * Prevents the history table from bloating. Deletes everything outside the last 5,000 entries.
     */
    @Query(
        """
        DELETE FROM playback_history 
        WHERE id NOT IN (
            SELECT id FROM playback_history 
            ORDER BY timestamp DESC 
            LIMIT 5000
        )
    """
    )
    abstract suspend fun pruneExcessiveHistory(): Int

    @Query("DELETE FROM playback_history WHERE timestamp <= :cutoffTime")
    abstract suspend fun purgeHistoryBefore(cutoffTime: Long): Int

    @Query(
        """
        SELECT artworkUriString FROM cached_tracks 
        WHERE artist = :artistName 
        AND artworkUriString IS NOT NULL 
        AND artworkUriString != '' 
        LIMIT 1
    """
    )
    abstract suspend fun getLocalArtworkForArtist(artistName: String): String?


    // ==========================================
    // --- 4. CONTENT CACHES (Lyrics & Waveforms) ---
    // ==========================================


    // 🔥 THE FIX: Restoring the Lyric Maintenance Rituals
    @Query("UPDATE lyrics_cache SET lastAccessed = :timestamp WHERE trackId = :trackId")
    abstract suspend fun updateLyricsAccessTime(
        trackId: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    @Query(
        """
        DELETE FROM lyrics_cache 
        WHERE trackId NOT IN (
            SELECT trackId FROM lyrics_cache 
            ORDER BY lastAccessed DESC 
            LIMIT 2000
        )
    """
    )
    abstract suspend fun enforceLyricsCacheLimit(): Int

    @Query("DELETE FROM lyrics_cache")
    abstract suspend fun clearAllLyrics(): Int

}