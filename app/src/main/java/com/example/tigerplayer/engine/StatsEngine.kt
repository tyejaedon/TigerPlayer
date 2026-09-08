package com.example.tigerplayer.engine

import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.ui.player.DetailedStatsUiState
import com.example.tigerplayer.ui.player.StatItem
import com.example.tigerplayer.utils.ArtistUtils
import com.example.tigerplayer.utils.ElapsedTimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns listening analytics: the aggregation flows that feed the stats surfaces, and the
 * listened-duration accounting that produces the underlying history rows.
 *
 * `@Singleton` is required for correctness, not just efficiency: an in-flight play is held in
 * memory here, so a second instance would drop or double count it when a ViewModel is recreated.
 */
@Singleton
class StatsEngine @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val elapsedTimeSource: ElapsedTimeSource
) {
    private val _statsFilter = MutableStateFlow("Today")

    fun updateStatsFilter(newFilter: String) {
        _statsFilter.value = newFilter
    }

    // The complex derived flow for detailed UI stats
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDetailedStatsFlow(
        allTracksFlow: Flow<List<AudioTrack>>,
        artistDetailsMapFlow: Flow<Map<String, ArtistDetails>>
    ): Flow<DetailedStatsUiState> {
        return _statsFilter.flatMapLatest { filter ->
            val startTime = calculateStartTimeForFilter(filter)
            val listeningTotals = combine(
                historyRepository.getTotalListeningTime(startTime),
                historyRepository.getTotalListeningTime(0L)
            ) { filteredWindowMs, lifetimeMs ->
                filteredWindowMs to lifetimeMs
            }

            combine(
                listeningTotals,
                // 🔥 FIX 1: Increased limit from 5 to 50 to fuel the Constellation Galaxy and Searchable UI
                historyRepository.getTopArtists(startTime, limit = 50),
                historyRepository.getTopTracks(startTime, limit = 50),
                allTracksFlow,
                artistDetailsMapFlow
            ) { totals, topArtistsDb, topTracksDb, allTracks, artistDetailsMap ->
                val (totalTimeMs, lifetimeTotalMs) = totals
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
                    topArtists = topArtistsDb.map { artist ->
                        // 🔥 FIX 2: Normalize the key to safely extract the High-Res API image
                        val normalizedKey = ArtistUtils.getBaseArtist(artist.artistName).lowercase().trim()

                        // Fallback: If API image is missing, grab the first local album cover for this artist
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

    // ==========================================
    // --- LISTENED-DURATION ACCOUNTING (issue #42) ---
    // ==========================================
    //
    // A play is committed when the track is left, not when it starts, and it records the time
    // actually listened rather than the track's nominal length. Previously every track transition
    // wrote a full-length row, so skipping through 40 tracks recorded 40 complete plays and every
    // downstream surface (Heavy Rotation, Sonic Footprint, Day List, Discovery Weekly, listening
    // share) was inflated.

    private val playLock = Any()
    private var pendingTrack: AudioTrack? = null
    private var accumulatedMs = 0L

    /** Elapsed time when playback last started; `null` while paused. */
    private var resumedAtMs: Long? = null

    private data class PendingPlay(val track: AudioTrack, val listenedMs: Long)

    /**
     * Call when the current track changes.
     *
     * Commits the outgoing track's play if it qualifies, then begins accounting for [track].
     */
    suspend fun onTrackChanged(track: AudioTrack, isPlaying: Boolean) {
        commitPendingPlay()
        synchronized(playLock) {
            pendingTrack = track
            accumulatedMs = 0L
            resumedAtMs = if (isPlaying) elapsedTimeSource.elapsedMs() else null
        }
    }

    /**
     * Call whenever playback starts or stops, for any source.
     *
     * Idempotent: repeated calls with the same state do not double count.
     */
    fun onPlayingChanged(isPlaying: Boolean) {
        synchronized(playLock) {
            if (isPlaying) {
                if (resumedAtMs == null) resumedAtMs = elapsedTimeSource.elapsedMs()
            } else {
                accumulateLocked()
            }
        }
    }

    /** Commits any in-flight play. Safe to call when nothing is pending. */
    suspend fun flushPendingPlay() {
        commitPendingPlay()
    }

    /** Folds the currently-running interval into [accumulatedMs]. Caller must hold [playLock]. */
    private fun accumulateLocked() {
        val startedAt = resumedAtMs ?: return
        accumulatedMs += (elapsedTimeSource.elapsedMs() - startedAt).coerceAtLeast(0L)
        resumedAtMs = null
    }

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

    private suspend fun persistPlay(track: AudioTrack, rawListenedMs: Long) {
        val durationMs = track.durationMs

        // Seeking backwards, or repeat-one, can accumulate more wall-clock time than the track is
        // long. Cap so a single play can never contribute more than the track's own duration.
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

    /**
     * The standard scrobble rule: a play counts once at least half the track, or four minutes,
     * has been heard - whichever comes first. Tracks shorter than 30 seconds never qualify.
     *
     * When the duration is unknown (0 or negative, common for streams) only the absolute
     * four-minute rule can qualify the play, since a percentage is meaningless.
     */
    private fun qualifiesAsPlay(listenedMs: Long, durationMs: Long): Boolean {
        if (listenedMs < MIN_LISTENED_MS) return false
        if (durationMs in 1 until MIN_TRACK_DURATION_MS) return false
        if (listenedMs >= FULL_PLAY_THRESHOLD_MS) return true
        if (durationMs <= 0L) return false
        return listenedMs >= durationMs / 2
    }

    private fun resolveSource(track: AudioTrack): MediaSource = when {
        track.id.startsWith("spotify:", ignoreCase = true) -> MediaSource.SPOTIFY
        track.id.startsWith("navidrome:", ignoreCase = true) -> MediaSource.NAVIDROME
        else -> MediaSource.LOCAL
    }

    /**
     * 🔥 UPGRADED TEMPORAL ENGINE
     * Replaces rolling math (e.g. 24 hours ago) with absolute Calendar boundaries.
     * "Today" now accurately begins at 12:00 AM.
     */
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
                // Includes today + previous 6 calendar days.
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.timeInMillis
            }
            "This Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis
            }
            "Last 30 Days" -> {
                // Includes today + previous 29 calendar days.
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                calendar.timeInMillis
            }
            "Last 90 Days" -> {
                // Includes today + previous 89 calendar days.
                calendar.add(Calendar.DAY_OF_YEAR, -89)
                calendar.timeInMillis
            }
            "This Year" -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.timeInMillis
            }
            "Lifetime" -> 0L // Captures everything
            else -> 0L
        }
    }

    private companion object {
        /** A play always qualifies once four minutes have been heard, regardless of length. */
        const val FULL_PLAY_THRESHOLD_MS = 4 * 60 * 1000L

        /** Tracks shorter than this never qualify, matching the standard scrobble rule. */
        const val MIN_TRACK_DURATION_MS = 30_000L

        /** Absolute floor; below this the listener effectively skipped. */
        const val MIN_LISTENED_MS = 5_000L
    }
}
