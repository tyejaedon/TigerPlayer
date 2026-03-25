package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.local.dao.ArtistStats
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
    // --- 1. THE RECENT CHANTS ---
    val recentTracks: Flow<List<PlaybackHistoryEntity>> = tigerDao.getRecentTracks()

    // --- 2. LIFETIME POWER (Total Listening Time) ---
    val totalListeningTime: Flow<Long?> = tigerDao.getTotalListeningTimeMs()

    // --- 3. THE DAILY RITUAL (Listening Time Today) ---
    // This calculates the start of the current day to filter the archives
    val listeningTimeToday: Flow<Long?> = tigerDao.getTotalListeningTimeMs(
        startTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    )

    // --- 4. TOP PERFORMERS ---
    val topArtist: Flow<String?> = tigerDao.getTopArtist()

    // --- 5. CUSTOM QUERIES (For Weekly/Monthly Stats) ---
    fun getTotalListeningTime(startTime: Long): Flow<Long?> =
        tigerDao.getTotalListeningTimeMs(startTime)

    fun getTopArtists(startTime: Long, limit: Int): Flow<List<ArtistStats>> =
        tigerDao.getTopArtists(startTime, limit)

    fun getTopTracks(startTime: Long, limit: Int): Flow<List<TrackStats>> =
        tigerDao.getTopTracks(startTime, limit)

    /**
     * ADDS A MANIFESTATION TO THE ARCHIVES
     * Records the track, its source, and the time spent listening.
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
        val historyEntry = PlaybackHistoryEntity(
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            imageUrl = imageUrl,
            durationListenedMs = durationMs,
            source = source,
            // Timestamp is usually handled by the Entity's default value
            timestamp = System.currentTimeMillis()
        )
        tigerDao.insertHistory(historyEntry)
    }
}