package com.example.tigerplayer.engine

import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.ui.player.DetailedStatsUiState
import com.example.tigerplayer.ui.player.StatItem
import com.example.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

class StatsEngine @Inject constructor(
    private val historyRepository: HistoryRepository
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
            val listeningWindowFlow = combine(
                historyRepository.getTotalListeningTime(startTime),
                historyRepository.getTotalListeningTime(0L)
            ) { selectedMs, lifetimeMs ->
                Pair(selectedMs, lifetimeMs)
            }

            combine(
                listeningWindowFlow,
                // 🔥 FIX 1: Increased limit from 5 to 50 to fuel the Constellation Galaxy and Searchable UI
                historyRepository.getTopArtists(startTime, limit = 50),
                historyRepository.getTopTracks(startTime, limit = 50),
                allTracksFlow,
                artistDetailsMapFlow
            ) { listeningMs, topArtistsDb, topTracksDb, allTracks, artistDetailsMap ->
                val totalTimeMs = listeningMs.first
                val lifetimeTimeMs = listeningMs.second
                val totalSeconds = (totalTimeMs ?: 0L) / 1000
                val hours = (totalSeconds / 3600).toInt()
                val minutes = ((totalSeconds % 3600) / 60).toInt()
                val lifetime = (lifetimeTimeMs ?: 0L).coerceAtLeast(1L)
                val sharePercent = (((totalTimeMs ?: 0L).toFloat() / lifetime.toFloat()) * 100f).coerceIn(0f, 100f)

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

                        val listenedMinutes = (artist.totalListeningMs / 60_000L).toInt()

                        StatItem(
                            id = artist.artistName,
                            name = artist.artistName,
                            playCount = artist.playCount,
                            secondaryText = "${listenedMinutes}m listened",
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

    suspend fun recordPlaybackHistory(track: AudioTrack, listenedDurationMs: Long) {
        if (listenedDurationMs <= 0L) return
        historyRepository.addTrackToHistory(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            imageUrl = track.artworkUri.toString(),
            listenedDurationMs = listenedDurationMs,
            source = MediaSource.LOCAL
        )
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
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                calendar.timeInMillis
            }
            "This Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis
            }
            "Last 30 Days" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                calendar.timeInMillis
            }
            "Last 90 Days" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -90)
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
}