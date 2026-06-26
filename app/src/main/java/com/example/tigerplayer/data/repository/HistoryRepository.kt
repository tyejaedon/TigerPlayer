package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.local.dao.ArtistStats
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.dao.TrackStats
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val tigerDao: TigerDao
) {
    // Helper to get start of day without expensive Calendar objects
    private fun getStartOfToday(): Long {
        val now = System.currentTimeMillis()
        return now - (now % 86400000L) // Simple math to snap to UTC midnight
    }

    // --- 1. RECENT CHANTS ---
    val recentTracks: Flow<List<PlaybackHistoryEntity>> = tigerDao.getRecentTracks()

    // --- 2. AGGREGATE POWER ---
    val totalListeningTime: Flow<Long?> = tigerDao.getTotalListeningTimeMs()

    // Today's stats refreshed automatically
    val listeningTimeToday: Flow<Long?> = tigerDao.getTotalListeningTimeMs(getStartOfToday())

    // --- 3. ANALYTICAL QUERIES ---

    // Top Artist for the current day/week/month
    fun getTopArtist(startTime: Long = 0L): Flow<String?> = tigerDao.getTopArtist(startTime)


    fun getTopTracks(startTime: Long, limit: Int): Flow<List<TrackStats>> =
        tigerDao.getTopTracks(startTime, limit)
    fun getTopArtists(startTime: Long, limit: Int): Flow<List<ArtistStats>> =
        tigerDao.getTopArtists(startTime, limit)

    fun getAllTracksStats(): Flow<List<TrackStats>> = tigerDao.getAllTracksStats()
    val getAllTracks: Flow<List<TrackStats>> = tigerDao.getAllTracksStats()

    /**
     * Records a manifestation.
     * Optimization: If duration is < 5s, we skip recording to avoid polluting stats with "skips".
     */
    suspend fun addTrackToHistory(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        imageUrl: String?,
        durationMs: Long,
        source: MediaSource
    ) {
        if (durationMs < 5000) return // Ignore brief skips

        val historyEntry = PlaybackHistoryEntity(
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            imageUrl = imageUrl,
            durationListenedMs = durationMs,
            source = source,
            timestamp = System.currentTimeMillis()
        )
        tigerDao.insertHistory(historyEntry)
    }

    fun getTotalListeningTime(startTime: Long): Flow<Long?> =
        tigerDao.getTotalListeningTimeMs(startTime)
}