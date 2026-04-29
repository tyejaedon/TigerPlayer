package com.example.tigerplayer.engine

import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.ui.player.DetailedStatsUiState
import com.example.tigerplayer.ui.player.StatItem
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class StatsEngine @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    private val _statsFilter = MutableStateFlow("Today")
    val statsFilter: StateFlow<String> = _statsFilter.asStateFlow()

    fun updateStatsFilter(newFilter: String) {
        _statsFilter.value = newFilter
    }

    // The complex derived flow for detailed UI stats
    fun getDetailedStatsFlow(
        allTracksFlow: Flow<List<AudioTrack>>,
        artistDetailsMapFlow: Flow<Map<String, ArtistDetails>>
    ): Flow<DetailedStatsUiState> {
        return _statsFilter.flatMapLatest { filter ->
            val startTime = calculateStartTimeForFilter(filter)
            combine(
                historyRepository.getTotalListeningTime(startTime),
                historyRepository.getTopArtists(startTime, limit = 5),
                historyRepository.getTopTracks(startTime, limit = 5),
                allTracksFlow,
                artistDetailsMapFlow
            ) { totalTimeMs, topArtistsDb, topTracksDb, allTracks, artistDetailsMap ->
                val totalSeconds = (totalTimeMs ?: 0L) / 1000
                val hours = (totalSeconds / 3600).toInt()
                val minutes = ((totalSeconds % 3600) / 60).toInt()

                DetailedStatsUiState(
                    selectedFilter = filter,
                    totalListeningHours = hours,
                    totalListeningMinutes = minutes,
                    topArtists = topArtistsDb.map { artist ->
                        val cachedImg = artistDetailsMap[artist.artistName]?.imageUrl
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

    suspend fun recordPlaybackHistory(track: AudioTrack) {
        historyRepository.addTrackToHistory(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            imageUrl = track.artworkUri.toString(),
            durationMs = track.durationMs,
            source = MediaSource.LOCAL
        )
    }

    private fun calculateStartTimeForFilter(filter: String): Long {
        val now = System.currentTimeMillis()
        val dayMs = 86400000L
        return when (filter) {
            "Today" -> now - dayMs
            "This Week" -> now - (dayMs * 7)
            "This Month" -> now - (dayMs * 30)
            else -> 0L
        }
    }
}