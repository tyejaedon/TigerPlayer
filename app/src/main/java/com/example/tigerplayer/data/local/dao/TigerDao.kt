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
    val imageUrl: String? = null
)

data class TrackStats(
    val trackId: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val playCount: Int
)

data class GenreFootprintStat(
    val genre: String,
    val weightMs: Long
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

    @Query("SELECT COALESCE(SUM(durationListenedMs), 0) FROM playback_history")
    abstract fun getTotalListeningTimeMs(): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationListenedMs), 0) FROM playback_history WHERE timestamp >= :startTime")
    abstract fun getTotalListeningTimeMs(startTime: Long): Flow<Long>

    /**
     * IDENTIFY TOP ARTIST
     * Performs a single-pass string cleaning to identify the most played artist.
     * Upgraded with CTE for better performance on large histories.
     */
    @Query(
        """
        WITH CleanedHistory AS (
            SELECT 
                trim(CASE 
                    WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
                    WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
                    WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
                    WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
                    ELSE artist 
                END) as artistName
            FROM playback_history 
            WHERE timestamp >= :startTime
        )
        SELECT artistName
        FROM CleanedHistory
        GROUP BY artistName 
        ORDER BY COUNT(*) DESC 
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
        WITH CleanedHistory AS (
            SELECT 
                trim(CASE 
                    WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
                    WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
                    WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
                    WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
                    ELSE artist 
                END) as artistName
            FROM playback_history
            WHERE timestamp >= :startTime
        )
        SELECT h.artistName, COUNT(*) as playCount, ac.imageUrl as imageUrl
        FROM CleanedHistory h
        LEFT JOIN artist_cache ac ON ac.artistName = LOWER(h.artistName)
        GROUP BY h.artistName
        ORDER BY playCount DESC 
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

    @Query(
        """
        WITH CleanedHistory AS (
            SELECT
                trim(CASE
                    WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
                    WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
                    WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
                    WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
                    ELSE artist
                END) AS artistName,
                durationListenedMs
            FROM playback_history
            WHERE timestamp >= :startTime
        ),
        GenreRows AS (
            WITH RECURSIVE split(artistName, genre, rest) AS (
                SELECT
                    artistName,
                    '',
                    lower(trim(genres)) || ','
                FROM artist_cache
                WHERE genres IS NOT NULL AND trim(genres) != ''
                UNION ALL
                SELECT
                    artistName,
                    trim(substr(rest, 1, instr(rest, ',') - 1)),
                    substr(rest, instr(rest, ',') + 1)
                FROM split
                WHERE rest != ''
            )
            SELECT artistName, genre
            FROM split
            WHERE genre != ''
        )
        SELECT
            g.genre AS genre,
            CAST(COALESCE(SUM(ch.durationListenedMs), 0) AS INTEGER) AS weightMs
        FROM CleanedHistory ch
        JOIN GenreRows g ON lower(ch.artistName) = lower(g.artistName)
        GROUP BY g.genre
        ORDER BY weightMs DESC
        LIMIT :limit
    """
    )
    abstract fun getTopGenreFootprint(startTime: Long, limit: Int = 24): Flow<List<GenreFootprintStat>>

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
        WITH CleanedHistory AS (
            SELECT 
                trim(CASE 
                    WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
                    WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
                    WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
                    WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
                    ELSE artist 
                END) as artistName
            FROM playback_history
        )
        SELECT COUNT(*) FROM CleanedHistory WHERE artistName = :artistName
    """
    )
    abstract suspend fun getArtistPlayCount(artistName: String): Int

    @Query(
        """
        WITH CleanedHistory AS (
            SELECT 
                trim(CASE 
                    WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
                    WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
                    WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
                    WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
                    ELSE artist 
                END) as artistName,
                durationListenedMs
            FROM playback_history
        )
        SELECT CAST(COALESCE(SUM(durationListenedMs), 0) / 60000 AS INTEGER) 
        FROM CleanedHistory 
        WHERE artistName = :artistName
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
        WITH BucketedHistory AS (
            SELECT
                trackId,
                trim(CASE
                    WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
                    WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
                    WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
                    WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
                    ELSE artist
                END) AS artistName,
                CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hourOfDay
            FROM playback_history
            WHERE timestamp >= :historyStartMs
        ),
        FilteredHistory AS (
            SELECT trackId, artistName
            FROM BucketedHistory
            WHERE (
                (:bucketStartHour <= :bucketEndHour AND hourOfDay BETWEEN :bucketStartHour AND :bucketEndHour)
                OR
                (:bucketStartHour > :bucketEndHour AND (hourOfDay >= :bucketStartHour OR hourOfDay <= :bucketEndHour))
            )
        ),
        TopArtists AS (
            SELECT artistName, COUNT(*) AS playCount
            FROM FilteredHistory
            GROUP BY artistName
            ORDER BY playCount DESC
            LIMIT 8
        ),
        TopGenres AS (
            SELECT DISTINCT lower(trim(
                CASE
                    WHEN instr(ac.genres, ',') > 0 THEN substr(ac.genres, 1, instr(ac.genres, ',') - 1)
                    ELSE ac.genres
                END
            )) AS genre
            FROM TopArtists ta
            JOIN artist_cache ac ON ac.artistName = lower(ta.artistName)
            WHERE ac.genres IS NOT NULL AND trim(ac.genres) != ''
        ),
        TrackAffinity AS (
            SELECT
                ct.id AS trackId,
                MAX(CASE WHEN ta.artistName IS NOT NULL THEN 1 ELSE 0 END) AS artistHit,
                MAX(CASE WHEN tg.genre IS NOT NULL THEN 1 ELSE 0 END) AS genreHit,
                COUNT(fh.trackId) AS bucketPlayCount
            FROM cached_tracks ct
            LEFT JOIN TopArtists ta ON lower(ct.artist) = lower(ta.artistName)
            LEFT JOIN artist_cache acTrack ON acTrack.artistName = lower(ct.artist)
            LEFT JOIN TopGenres tg
                ON acTrack.genres IS NOT NULL
                AND lower(acTrack.genres) LIKE '%' || tg.genre || '%'
            LEFT JOIN FilteredHistory fh ON fh.trackId = ct.id
            GROUP BY ct.id
        )
        SELECT ct.*
        FROM cached_tracks ct
        JOIN TrackAffinity ta ON ta.trackId = ct.id
        ORDER BY
            (ta.artistHit * 4 + ta.genreHit * 2 + CASE WHEN ta.bucketPlayCount > 0 THEN 1 ELSE 0 END) DESC,
            RANDOM()
        LIMIT :limit
    """
    )
    abstract fun getDaylistTracks(
        historyStartMs: Long,
        bucketStartHour: Int,
        bucketEndHour: Int,
        limit: Int = 20
    ): Flow<List<CachedTrackEntity>>

    @Query(
        """
        WITH CleanedHistory AS (
            SELECT trim(CASE
                WHEN artist LIKE '% ft.%' THEN substr(artist, 1, instr(artist, ' ft.') - 1)
                WHEN artist LIKE '% feat.%' THEN substr(artist, 1, instr(artist, ' feat.') - 1)
                WHEN artist LIKE '% & %' THEN substr(artist, 1, instr(artist, ' & ') - 1)
                WHEN artist LIKE '%,%' THEN substr(artist, 1, instr(artist, ',') - 1)
                ELSE artist
            END) AS artistName
            FROM playback_history
        ),
        TopArtists AS (
            SELECT artistName, COUNT(*) AS playCount
            FROM CleanedHistory
            GROUP BY artistName
            ORDER BY playCount DESC
            LIMIT 5
        ),
        TopGenres AS (
            SELECT DISTINCT lower(trim(
                CASE
                    WHEN instr(ac.genres, ',') > 0 THEN substr(ac.genres, 1, instr(ac.genres, ',') - 1)
                    ELSE ac.genres
                END
            )) AS genre
            FROM TopArtists ta
            JOIN artist_cache ac ON ac.artistName = lower(ta.artistName)
            WHERE ac.genres IS NOT NULL AND trim(ac.genres) != ''
        ),
        TrackHistory AS (
            SELECT
                trackId,
                COUNT(*) AS playCount,
                MAX(timestamp) AS lastPlayedMs
            FROM playback_history
            GROUP BY trackId
        )
        SELECT ct.*
        FROM cached_tracks ct
        LEFT JOIN TrackHistory th ON th.trackId = ct.id
        LEFT JOIN artist_cache acTrack ON acTrack.artistName = lower(ct.artist)
        WHERE
            COALESCE(th.playCount, 0) = 0
            OR th.lastPlayedMs IS NULL
            OR th.lastPlayedMs < :staleBeforeMs
        ORDER BY
            CASE
                WHEN EXISTS (
                    SELECT 1
                    FROM TopGenres tg
                    WHERE acTrack.genres IS NOT NULL
                      AND lower(acTrack.genres) LIKE '%' || tg.genre || '%'
                ) THEN 1
                ELSE 0
            END DESC,
            RANDOM()
        LIMIT :limit
    """
    )
    abstract fun getDiscoveryWeeklyTracks(
        staleBeforeMs: Long,
        limit: Int = 30
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