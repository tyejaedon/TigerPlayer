package com.example.tigerplayer.engine

import com.example.tigerplayer.data.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

data class ListeningDensitySnapshot(
    val totalListenMinutes: Int,
    val uniqueArtistCount: Int,
    val uniqueTrackCount: Int,
    val isPersonalizedReady: Boolean
)

@Singleton
class ListeningDensityTracker @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    val density: Flow<ListeningDensitySnapshot> = combine(
        historyRepository.totalListeningTime,
        historyRepository.uniqueArtistCount,
        historyRepository.uniqueTrackCount
    ) { totalMs, uniqueArtists, uniqueTracks ->
        val listenMinutes = ((totalMs ?: 0L) / 60_000L).toInt()
        ListeningDensitySnapshot(
            totalListenMinutes = listenMinutes,
            uniqueArtistCount = uniqueArtists,
            uniqueTrackCount = uniqueTracks,
            isPersonalizedReady =
                listenMinutes >= MIN_LISTEN_THRESHOLD_MINUTES ||
                    uniqueTracks >= MIN_UNIQUE_TRACK_THRESHOLD
        )
    }.distinctUntilChanged()

    companion object {
        const val MIN_LISTEN_THRESHOLD_MINUTES = 500
        const val MIN_UNIQUE_TRACK_THRESHOLD = 50
    }
}

