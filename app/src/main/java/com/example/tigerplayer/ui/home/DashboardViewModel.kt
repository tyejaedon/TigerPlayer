@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.tigerplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.engine.DiscoveryEngine
import com.example.tigerplayer.engine.ListeningDensitySnapshot
import com.example.tigerplayer.engine.ListeningDensityTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DaylistSegment {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT
}

data class DashboardUiState(
    val segment: DaylistSegment = DaylistSegment.MORNING,
    val neonDaylist: List<AudioTrack> = emptyList(),
    val vaultTracks: List<AudioTrack> = emptyList(),
    val isColdStartMode: Boolean = true,
    val daylistIsStale: Boolean = false,
    val vaultIsStale: Boolean = false,
    val daylistMessage: String? = null,
    val vaultMessage: String? = null,
    val lastGeneratedAt: Long = 0L,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val historyRepository: HistoryRepository,
    private val mediaDataRepository: MediaDataRepository,
    private val listeningDensityTracker: ListeningDensityTracker,
    private val discoveryEngine: DiscoveryEngine
) : ViewModel() {

    private val generationSignal = MutableStateFlow(System.currentTimeMillis())

    private val daylistSegmentFlow: Flow<DaylistSegment> = generationSignal
        .map { currentSegment() }
        .distinctUntilChanged()

    private val allAvailableTracksFlow: Flow<List<AudioTrack>> =
        audioRepository.getCachedLibraryTracks().distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val recentHistoryFlow = generationSignal.flatMapLatest { anchor ->
        historyRepository.getPlaybackHistorySince(anchor - FOURTEEN_DAYS_MS)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = combine(
        daylistSegmentFlow,
        recentHistoryFlow,
        allAvailableTracksFlow,
        listeningDensityTracker.density,
        mediaDataRepository.lastDiscoveryGenerationAt
    ) { segment, recentHistory, allTracks, density, lastGeneratedAt ->
        DiscoveryInput(
            segment = segment,
            recentHistory = recentHistory,
            allTracks = allTracks,
            density = density,
            lastGeneratedAt = lastGeneratedAt
        )
    }.mapLatest { input ->
        val segmentHistory = input.recentHistory.filter { historyEntry ->
            isInSegment(historyEntry.timestamp, input.segment)
        }
        val effectiveDaylistHistory = if (segmentHistory.isNotEmpty()) {
            segmentHistory
        } else {
            input.recentHistory
        }

        val seed = input.lastGeneratedAt.takeIf { it > 0L } ?: System.currentTimeMillis()

        val daylist = discoveryEngine.generateDaylist(
            playbackHistory = effectiveDaylistHistory,
            allAvailableTracks = input.allTracks,
            density = input.density,
            limit = PLAYLIST_SIZE,
            seed = seed xor input.segment.name.hashCode().toLong()
        )

        val vault = discoveryEngine.generateVault(
            playbackHistory = input.recentHistory,
            allAvailableTracks = input.allTracks,
            density = input.density,
            limit = PLAYLIST_SIZE,
            seed = seed xor 0x2A2A2A2AL
        )

        val now = System.currentTimeMillis()
        val stale = input.lastGeneratedAt > 0L && (now - input.lastGeneratedAt) > ONE_DAY_MS

        DashboardUiState(
            segment = input.segment,
            neonDaylist = daylist,
            vaultTracks = vault,
            isColdStartMode = !input.density.isPersonalizedReady,
            daylistIsStale = stale,
            vaultIsStale = stale,
            daylistMessage = when {
                daylist.isEmpty() && input.allTracks.isEmpty() -> "No local tracks available yet."
                daylist.isEmpty() -> "No matches for this day segment yet."
                !input.density.isPersonalizedReady -> "Cold Start: trendsetters mix active."
                else -> null
            },
            vaultMessage = when {
                vault.isEmpty() && input.allTracks.isEmpty() -> "No local tracks available yet."
                vault.isEmpty() -> "Vault is waiting for more discovery candidates."
                !input.density.isPersonalizedReady -> "Cold Start: trendsetters mix active."
                else -> null
            },
            lastGeneratedAt = input.lastGeneratedAt,
            isLoading = false
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(isLoading = true)
    )

    init {
        scheduleDiscoveryRefresh()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val last = mediaDataRepository.getLastDiscoveryGenerationTimestamp()
            if (last <= 0L || (now - last) > ONE_DAY_MS) {
                triggerDiscoveryGeneration(now)
            } else {
                generationSignal.value = last
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            triggerDiscoveryGeneration(System.currentTimeMillis())
        }
    }

    private fun scheduleDiscoveryRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(millisUntilNextMidnight())
                triggerDiscoveryGeneration(System.currentTimeMillis())
            }
        }
    }

    private suspend fun triggerDiscoveryGeneration(timestamp: Long) {
        mediaDataRepository.updateDiscoveryGenerationTimestamp(timestamp)
        generationSignal.value = timestamp
    }

    private fun currentSegment(now: Calendar = Calendar.getInstance()): DaylistSegment {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> DaylistSegment.MORNING
            in 12..16 -> DaylistSegment.AFTERNOON
            in 17..21 -> DaylistSegment.EVENING
            else -> DaylistSegment.NIGHT
        }
    }

    private fun isInSegment(timestamp: Long, segment: DaylistSegment): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = timestamp }
            .get(Calendar.HOUR_OF_DAY)
        return when (segment) {
            DaylistSegment.MORNING -> hour in 5..11
            DaylistSegment.AFTERNOON -> hour in 12..16
            DaylistSegment.EVENING -> hour in 17..21
            DaylistSegment.NIGHT -> hour >= 22 || hour <= 4
        }
    }

    private fun millisUntilNextMidnight(now: Calendar = Calendar.getInstance()): Long {
        val nextMidnight = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (nextMidnight.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    }

    private data class DiscoveryInput(
        val segment: DaylistSegment,
        val recentHistory: List<PlaybackHistoryEntity>,
        val allTracks: List<AudioTrack>,
        val density: ListeningDensitySnapshot,
        val lastGeneratedAt: Long
    )

    companion object {
        private const val PLAYLIST_SIZE = 15
        private const val FOURTEEN_DAYS_MS = 14L * 24L * 60L * 60L * 1000L
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }
}

