package com.example.tigerplayer.ui.dashboard

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val refreshTickMs = MutableStateFlow(System.currentTimeMillis())

    val daylistTracks: StateFlow<List<AudioTrack>> = refreshTickMs
        .flatMapLatest { now ->
            val (bucketStartHour, bucketEndHour) = currentDayBucket(now)
            audioRepository.getDaylistTracks(
                historyStartMs = now - DAYLIST_LOOKBACK_MS,
                bucketStartHour = bucketStartHour,
                bucketEndHour = bucketEndHour,
                limit = DAYLIST_SIZE
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val discoveryWeeklyTracks: StateFlow<List<AudioTrack>> = refreshTickMs
        .flatMapLatest { now ->
            audioRepository.getDiscoveryWeeklyTracks(
                staleBeforeMs = now - DISCOVERY_STALE_MS,
                limit = DISCOVERY_SIZE
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    fun refreshForYou() {
        refreshTickMs.value = System.currentTimeMillis()
    }

    private fun currentDayBucket(now: Long): Pair<Int, Int> {
        val hour = Calendar.getInstance().apply { timeInMillis = now }
            .get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> 5 to 11    // Morning
            in 12..16 -> 12 to 16  // Afternoon
            in 17..21 -> 17 to 21  // Evening
            else -> 22 to 4        // Night (wraps across midnight)
        }
    }

    private companion object {
        const val DAYLIST_SIZE = 20
        const val DISCOVERY_SIZE = 30
        const val STOP_TIMEOUT_MS = 5_000L
        const val DAYLIST_LOOKBACK_MS = 14L * 24L * 60L * 60L * 1_000L
        const val DISCOVERY_STALE_MS = 180L * 24L * 60L * 60L * 1_000L
    }
}

