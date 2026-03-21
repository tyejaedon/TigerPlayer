package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.local.dao.ArtistStats
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.dao.TrackStats
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val tigerDao: TigerDao
) {
    val recentTracks: Flow<List<PlaybackHistoryEntity>> = tigerDao.getRecentTracks()
    val totalListeningTime: Flow<Long?> = tigerDao.getTotalListeningTimeMs()
    val topArtist: Flow<String?> = tigerDao.getTopArtist()

    fun getTotalListeningTime(startTime: Long): Flow<Long?> = tigerDao.getTotalListeningTimeMs(startTime)
    
    fun getTopArtists(startTime: Long, limit: Int): Flow<List<ArtistStats>> = 
        tigerDao.getTopArtists(startTime, limit)

    fun getTopTracks(startTime: Long, limit: Int): Flow<List<TrackStats>> = 
        tigerDao.getTopTracks(startTime, limit)

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
            source = source
        )
        tigerDao.insertHistory(historyEntry)
    }
}
