package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.local.dao.ArtistStats
import com.example.tigerplayer.data.local.dao.SonicFootprintStats
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.dao.TrackStats
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val tigerDao: TigerDao
) {
    // Local midnight anchor used by home stats chips.
    private fun getStartOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
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
     * Optimization: If listened duration is < 7s, skip recording to avoid polluting stats with accidental transitions.
     */
    suspend fun addTrackToHistory(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        imageUrl: String?,
        listenedDurationMs: Long,
        source: MediaSource
    ) {
        if (listenedDurationMs < 7000L) return // Ignore micro-skips and accidental transitions.

        val historyEntry = PlaybackHistoryEntity(
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            imageUrl = imageUrl,
            durationListenedMs = listenedDurationMs,
            source = source,
            timestamp = System.currentTimeMillis()
        )
        tigerDao.insertHistory(historyEntry)
    }

    fun getTotalListeningTime(startTime: Long): Flow<Long?> =
        tigerDao.getTotalListeningTimeMs(startTime)

    fun getSonicFootprintStats(startTime: Long): Flow<SonicFootprintStats> =
        tigerDao.getSonicFootprintStats(startTime)
}