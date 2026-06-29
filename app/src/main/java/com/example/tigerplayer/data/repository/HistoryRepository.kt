package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.local.dao.GenreFootprintStat
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
    // Correctly snap to local midnight
    private fun getStartOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getTimestampDaysAgo(days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return calendar.timeInMillis
    }

    // --- 1. RECENT CHANTS ---
    val recentTracks: Flow<List<PlaybackHistoryEntity>> = tigerDao.getRecentTracks()

    // --- 2. AGGREGATE POWER ---
    val totalListeningTime: Flow<Long?> = tigerDao.getTotalListeningTimeMs(0L)

    // Today's stats refreshed automatically
    val listeningTimeToday: Flow<Long?> = tigerDao.getTotalListeningTimeMs(getStartOfToday())

    // --- 3. ANALYTICAL QUERIES ---
    fun getTopArtist(startTime: Long = 0L): Flow<String?> = tigerDao.getTopArtist(startTime)


    fun getTopTracks(startTime: Long, limit: Int): Flow<List<TrackStats>> =
        tigerDao.getTopTracks(startTime, limit)
    fun getTopArtists(startTime: Long, limit: Int): Flow<List<ArtistStats>> =
        tigerDao.getTopArtists(startTime, limit)

    fun getTopGenreFootprint(startTime: Long, limit: Int): Flow<List<GenreFootprintStat>> =
        tigerDao.getTopGenreFootprint(startTime, limit)

    fun getAllTracksStats(): Flow<List<TrackStats>> = tigerDao.getAllTracksStats()

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