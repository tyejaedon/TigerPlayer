package com.example.tigerplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

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
    val isLoading: Boolean = true
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val refreshSignal = MutableStateFlow(System.currentTimeMillis())

    private val daylistSegmentFlow = refreshSignal.flatMapLatest {
        val segment = currentSegment()
        MutableStateFlow(segment)
    }

    private val daylistFlow = daylistSegmentFlow.flatMapLatest { segment ->
        audioRepository.getNeonDaylistTracks(
            segment = segment.name,
            sinceMillis = System.currentTimeMillis() - FOURTEEN_DAYS_MS,
            limit = PLAYLIST_SIZE
        )
    }

    private val vaultFlow = refreshSignal.flatMapLatest {
        audioRepository.getVaultDiscoveryTracks(
            sinceMillis = System.currentTimeMillis() - FOURTEEN_DAYS_MS,
            limit = PLAYLIST_SIZE
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        daylistSegmentFlow,
        daylistFlow,
        vaultFlow
    ) { segment, daylist, vault ->
        DashboardUiState(
            segment = segment,
            neonDaylist = daylist,
            vaultTracks = vault,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(isLoading = true)
    )

    fun refresh() {
        refreshSignal.value = System.currentTimeMillis()
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

    companion object {
        private const val PLAYLIST_SIZE = 15
        private const val FOURTEEN_DAYS_MS = 14L * 24L * 60L * 60L * 1000L
    }
}

