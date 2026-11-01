package com.tigerplayer.engine

import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.dao.ArtistStats
import com.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.ArtistDetails
import com.tigerplayer.data.repository.HistoryRepository
import com.tigerplayer.ui.player.DetailedStatsUiState
import com.tigerplayer.ui.player.StatItem
import com.tigerplayer.utils.ArtistUtils
import com.tigerplayer.utils.ElapsedTimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot data container for cover screen listening statistics over a 7-day window.
 *
 * @property topArtists Top listened artists.
 * @property listeningTimeTodayMs Total listening time today in milliseconds.
 * @property tracksPlayedToday Number of tracks played today.
 * @property dailyPlayCounts Daily play count breakdown across [STATS_DAYS].
 * @property hasHistory Whether any playback history exists.
 */
data class CoverScreenStatsSnapshot(
    val topArtists: List<ArtistStats> = emptyList(),
    val listeningTimeTodayMs: Long = 0L,
    val tracksPlayedToday: Int = 0,
    val dailyPlayCounts: List<Int> = List(STATS_DAYS) { 0 },
    val hasHistory: Boolean = false
) {
    companion object {
        /** Number of days tracked in cover screen statistics. */
        const val STATS_DAYS = 7
    }
}

/** Internal time window representation for daily play count queries. */
internal data class DailyPlayCountWindow(
    val startInclusive: Long,
    val endExclusive: Long
)

/**
 * Owns listening analytics: aggregation flows feeding stats surfaces, and listened-duration
 * accounting producing scrobbling history records (issue #42).
 *
 * `@Singleton` ensures in-flight play duration and session state are correctly retained.
 */
@Singleton
class StatsEngine @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val elapsedTimeSource: ElapsedTimeSource
) {
    private val _statsFilter = MutableStateFlow("Today")
    private val sessionStartedAtMs = System.currentTimeMillis()
    private val playLock = Any()
    private var pendingTrack: AudioTrack? = null
    private var accumulatedMs = 0L
    private var resumedAtMs: Long? = null

    /** Updates the active statistics filter time window (e.g. "Today", "This Week", "Lifetime"). */
    fun updateStatsFilter(newFilter: String) {
        _statsFilter.value = newFilter
    }

    /** Returns a periodic flow emitting cover screen stats snapshots. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCoverScreenStatsSnapshotFlow(): Flow<CoverScreenStatsSnapshot> = flow {
        while (true) {
            emit(Unit)
            delay(COVER_SCREEN_STATS_REFRESH_MS)
        }
    }.flatMapLatest {
        val todayStart = calculateStartTimeForFilter("Today")
        combine(
            historyRepository.getTopArtists(todayStart, limit = 3),
            historyRepository.getTotalListeningTime(todayStart),
            historyRepository.getTrackPlayCount(todayStart),
            combine(
                dailyPlayCountWindows(System.currentTimeMillis()).map { window ->
                    historyRepository.getTrackPlayCountBetween(
                        window.startInclusive,
                        window.endExclusive
                    )
                }
            ) { counts -> counts.toList() }
        ) { topArtists, listeningTimeMs, trackPlayCount, dailyPlayCounts ->
            val listeningTimeTodayMs = (listeningTimeMs ?: 0L).coerceAtLeast(0L)
            val tracksPlayedToday = trackPlayCount.coerceAtLeast(0)
            CoverScreenStatsSnapshot(
                topArtists = topArtists.take(3),
                listeningTimeTodayMs = listeningTimeTodayMs,
                tracksPlayedToday = tracksPlayedToday,
                dailyPlayCounts = dailyPlayCounts,
                hasHistory = tracksPlayedToday > 0 ||
                    listeningTimeTodayMs > 0L ||
                    dailyPlayCounts.any { it > 0 }
            )
        }
    }

    /** Returns the detailed statistics flow for the full stats screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDetailedStatsFlow(
        allTracksFlow: Flow<List<AudioTrack>>,
        artistDetailsMapFlow: Flow<Map<String, ArtistDetails>>
    ): Flow<DetailedStatsUiState> {
        return _statsFilter.flatMapLatest { filter ->
            val startTime = calculateStartTimeForFilter(filter)
            val listeningTotals = combine(
                historyRepository.getTotalListeningTime(startTime),
                historyRepository.getTotalListeningTime(0L),
                historyRepository.statsEpochMs
            ) { filteredWindowMs, lifetimeMs, epochMs ->
                Triple(filteredWindowMs, lifetimeMs, epochMs)
            }

            combine(
                listeningTotals,
                historyRepository.getTopArtists(startTime, limit = 50),
                historyRepository.getTopTracks(startTime, limit = 50),
                allTracksFlow,
                artistDetailsMapFlow
            ) { totals, topArtistsDb, topTracksDb, allTracks, artistDetailsMap ->
                val (totalTimeMs, lifetimeTotalMs, statsEpochMs) = totals
                val totalSeconds = (totalTimeMs ?: 0L) / 1000
                val hours = (totalSeconds / 3600).toInt()
                val minutes = ((totalSeconds % 3600) / 60).toInt()
                val sharePercent = if ((lifetimeTotalMs ?: 0L) > 0L) {
                    ((totalTimeMs ?: 0L).toDouble() / (lifetimeTotalMs ?: 1L).toDouble() * 100.0)
                        .toFloat()
                        .coerceIn(0f, 100f)
                } else {
                    0f
                }

                DetailedStatsUiState(
                    selectedFilter = filter,
                    totalListeningHours = hours,
                    totalListeningMinutes = minutes,
                    globalListeningSharePercent = sharePercent,
                    statsEpochMs = statsEpochMs,
                    topArtists = topArtistsDb.map { artist ->
                        val normalizedKey = ArtistUtils.getBaseArtist(artist.artistName).lowercase().trim()
                        val cachedImg = artistDetailsMap[normalizedKey]?.imageUrl
                            ?: allTracks.firstOrNull { ArtistUtils.getBaseArtist(it.artist).equals(artist.artistName, ignoreCase = true) }?.artworkUri?.toString()

                        StatItem(
                            id = artist.artistName,
                            name = artist.artistName,
                            playCount = artist.playCount,
                            secondaryText = "Artist",
                            imageUrl = cachedImg
                        )
                    },
                    topTracks = topTracksDb.map { track ->
                        val albumArt = allTracks.find { it.id == track.trackId }?.artworkUri?.toString()
                        StatItem(
                            id = track.trackId,
                            name = track.title,
                            playCount = track.playCount,
                            secondaryText = track.artist,
                            imageUrl = albumArt
                        )
                    }
                )
            }
        }
    }

    /** Called when the active track changes. Commits outgoing play and begins accounting for [track]. */
    suspend fun onTrackChanged(track: AudioTrack, isPlaying: Boolean) {
        commitPendingPlay()
        synchronized(playLock) {
            pendingTrack = track
            accumulatedMs = 0L
            resumedAtMs = if (isPlaying) elapsedTimeSource.elapsedMs() else null
        }
    }

    /** Called when playback playing state changes (play/pause). */
    fun onPlayingChanged(isPlaying: Boolean) {
        synchronized(playLock) {
            if (isPlaying) {
                if (resumedAtMs == null) resumedAtMs = elapsedTimeSource.elapsedMs()
            } else {
                accumulateLocked()
            }
        }
    }

    /** Flushes any in-flight pending play to history storage. */
    suspend fun flushPendingPlay() {
        commitPendingPlay()
    }

    /** Accumulates elapsed listening time. Caller must hold [playLock]. */
    private fun accumulateLocked() {
        val startedAt = resumedAtMs ?: return
        accumulatedMs += (elapsedTimeSource.elapsedMs() - startedAt).coerceAtLeast(0L)
        resumedAtMs = null
    }

    /** Commits the pending play if it qualifies under scrobble rules. */
    private suspend fun commitPendingPlay() {
        val pending = synchronized(playLock) {
            val track = pendingTrack ?: return@synchronized null
            accumulateLocked()
            val snapshot = PendingPlay(track, accumulatedMs)
            pendingTrack = null
            accumulatedMs = 0L
            resumedAtMs = null
            snapshot
        } ?: return

        persistPlay(pending.track, pending.listenedMs)
    }

    /** Persists a qualified play to history repository. */
    private suspend fun persistPlay(track: AudioTrack, rawListenedMs: Long) {
        val durationMs = track.durationMs
        val listenedMs = if (durationMs > 0L) rawListenedMs.coerceAtMost(durationMs) else rawListenedMs

        if (!qualifiesAsPlay(listenedMs, durationMs)) return

        historyRepository.addTrackToHistory(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            imageUrl = track.artworkUri.toString(),
            durationListenedMs = listenedMs,
            source = resolveSource(track)
        )
    }

    /** Evaluates scrobble qualification rules for listened duration and track length. */
    private fun qualifiesAsPlay(listenedMs: Long, durationMs: Long): Boolean {
        if (listenedMs < MIN_LISTENED_MS) return false
        if (durationMs in 1 until MIN_TRACK_DURATION_MS) return false
        if (listenedMs >= FULL_PLAY_THRESHOLD_MS) return true
        if (durationMs <= 0L) return false
        return listenedMs >= durationMs / 2
    }

    /** Resolves the [MediaSource] for an audio track. */
    private fun resolveSource(track: AudioTrack): MediaSource = when {
        track.id.startsWith("spotify:", ignoreCase = true) -> MediaSource.SPOTIFY
        track.id.startsWith("navidrome_", ignoreCase = true) ||
            track.id.startsWith("navidrome:", ignoreCase = true) -> MediaSource.NAVIDROME
        else -> MediaSource.LOCAL
    }

    /** Calculates start timestamp for the selected statistics filter. */
    private fun calculateStartTimeForFilter(filter: String): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (filter) {
            "Today" -> calendar.timeInMillis
            "This Week" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.timeInMillis
            }
            "Last 7 Days" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.timeInMillis
            }
            "This Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis
            }
            "Last 30 Days" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                calendar.timeInMillis
            }
            "Last 90 Days" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -89)
                calendar.timeInMillis
            }
            "This Year" -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.timeInMillis
            }
            "Lifetime" -> 0L
            else -> 0L
        }
    }

    internal fun dailyPlayCountWindows(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<DailyPlayCountWindow> {
        val firstDay = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(CoverScreenStatsSnapshot.STATS_DAYS - 1))
        }
        val boundaries = (0..CoverScreenStatsSnapshot.STATS_DAYS).map { dayOffset ->
            Calendar.getInstance(timeZone).apply {
                timeInMillis = firstDay.timeInMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }.timeInMillis
        }
        return boundaries.zipWithNext { start, end ->
            DailyPlayCountWindow(startInclusive = start, endExclusive = end)
        }
    }

    private data class PendingPlay(val track: AudioTrack, val listenedMs: Long)

    private companion object {
        const val COVER_SCREEN_STATS_REFRESH_MS = 5 * 60 * 1000L
        const val FULL_PLAY_THRESHOLD_MS = 4 * 60 * 1000L
        const val MIN_TRACK_DURATION_MS = 30_000L
        const val MIN_LISTENED_MS = 5_000L
    }
}
