package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.local.dao.ArtistStats
import com.example.tigerplayer.data.local.dao.SonicFootprintStats
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.dao.TrackStats
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gateway to listening history.
 *
 * Every read is floored at [StatsEpoch] so pre-#42 rows - which recorded the track's nominal length
 * rather than the time actually listened - are excluded from analytics without being deleted
 * (issue #80). Public signatures are unchanged, so call sites are unaffected.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRepository @Inject constructor(
    private val tigerDao: TigerDao,
    private val statsEpoch: StatsEpoch
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

    private fun getStartOfWeek(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
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

    /** The window analytics actually cover. `0` means all history is trustworthy. */
    val statsEpochMs: Flow<Long> = statsEpoch.epochMs

    // --- 1. RECENT CHANTS ---
    val recentTracks: Flow<List<PlaybackHistoryEntity>> =
        statsEpoch.effectiveStart(0L).flatMapLatest { tigerDao.getRecentTracks(it) }

    // --- 2. AGGREGATE POWER ---
    val totalListeningTime: Flow<Long?> = getTotalListeningTime(0L)

    // Today's stats refreshed automatically
    val listeningTimeToday: Flow<Long?> = getTotalListeningTime(getStartOfToday())

    val topArtistThisWeek: Flow<String?> = getTopArtist(getStartOfWeek())

    // --- 3. ANALYTICAL QUERIES ---
    fun getTopArtist(startTime: Long = 0L): Flow<String?> =
        statsEpoch.effectiveStart(startTime).flatMapLatest { tigerDao.getTopArtist(it) }

    fun getTopTracks(startTime: Long, limit: Int): Flow<List<TrackStats>> =
        statsEpoch.effectiveStart(startTime).flatMapLatest { tigerDao.getTopTracks(it, limit) }

    fun getTopArtists(startTime: Long, limit: Int): Flow<List<ArtistStats>> =
        statsEpoch.effectiveStart(startTime).flatMapLatest { tigerDao.getTopArtists(it, limit) }

    fun observeArtistStats(artistName: String): Flow<ArtistStats?> =
        tigerDao.observeArtistStats(artistName)

    fun getSonicFootprintStats(startTime: Long): Flow<SonicFootprintStats> =
        statsEpoch.effectiveStart(startTime).flatMapLatest { tigerDao.getSonicFootprintStats(it) }

    fun getAllTracksStats(): Flow<List<TrackStats>> = tigerDao.getAllTracksStats()

    /**
     * Records a completed play.
     *
     * [durationListenedMs] must be the time **actually listened**, never the track's nominal
     * length. Whether a play qualifies at all (the >= 50% / >= 4 minute rule) is decided upstream
     * in `StatsEngine`; the floor below is only a defensive guard against a caller passing a
     * value that clearly represents a skip.
     */
    suspend fun addTrackToHistory(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        imageUrl: String?,
        durationListenedMs: Long,
        source: MediaSource
    ) {
        if (durationListenedMs < MIN_LISTENED_MS) return

        val historyEntry = PlaybackHistoryEntity(
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            imageUrl = imageUrl,
            durationListenedMs = durationListenedMs,
            source = source,
            timestamp = System.currentTimeMillis()
        )
        tigerDao.insertHistory(historyEntry)
    }

    fun getTotalListeningTime(startTime: Long): Flow<Long?> =
        statsEpoch.effectiveStart(startTime).flatMapLatest { tigerDao.getTotalListeningTimeMs(it) }

    private companion object {
        const val MIN_LISTENED_MS = 5_000L
    }
}
