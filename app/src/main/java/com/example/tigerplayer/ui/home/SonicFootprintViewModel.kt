package com.example.tigerplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.local.dao.SonicFootprintStats
import com.example.tigerplayer.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class SonicAxis(val label: String) {
    ACOUSTIC("Acoustic"),
    ELECTRONIC("Electronic"),
    BASS_HEAVY("Bass-Heavy"),
    VOCAL("Vocal"),
    ATMOSPHERIC("Atmospheric")
}

enum class FootprintTimeRange(val label: String, val days: Int) {
    LIFETIME("Lifetime", 0),
    MONTH("Past Month", 30),
    WEEK("Past Week", 7)
}

data class SonicFootprintUiState(
    val axisValues: Map<SonicAxis, Float> = SonicAxis.entries.associateWith { 0f },
    val topTags: List<Pair<String, Float>> = emptyList(),
    val totalListeningHours: Float = 0f,
    val timeRange: FootprintTimeRange = FootprintTimeRange.LIFETIME
)

@HiltViewModel
class SonicFootprintViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _timeRange = MutableStateFlow(FootprintTimeRange.LIFETIME)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SonicFootprintUiState> = _timeRange.flatMapLatest { range ->
        val startTime = if (range.days > 0) historyRepository.getTimestampDaysAgo(range.days) else 0L

        combine(
            historyRepository.getSonicFootprintStats(startTime = startTime),
            historyRepository.getTotalListeningTime(startTime = startTime)
        ) { footprintStats, totalListeningMs ->
            val axisRaw = buildAxisBuckets(footprintStats)
            val maxValue = axisRaw.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
            val normalized = SonicAxis.entries.associateWith { axis ->
                (axisRaw[axis] ?: 0f) / maxValue
            }

            val totalMinutes = ((totalListeningMs ?: 0L) / 60_000f).coerceAtLeast(0f)
            val axisSum = axisRaw.values.sum().coerceAtLeast(1f)
            val topTags = axisRaw.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { (axis, value) ->
                    axis.label to ((value / axisSum) * totalMinutes)
                }

            SonicFootprintUiState(
                axisValues = normalized,
                topTags = topTags,
                totalListeningHours = ((totalListeningMs ?: 0L) / 3_600_000f).coerceAtLeast(0f),
                timeRange = range
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SonicFootprintUiState()
    )

    fun setTimeRange(range: FootprintTimeRange) {
        _timeRange.value = range
    }

    private fun buildAxisBuckets(stats: SonicFootprintStats): Map<SonicAxis, Float> {
        return mapOf(
            SonicAxis.ACOUSTIC to stats.acoustic.toFloat(),
            SonicAxis.ELECTRONIC to stats.electronic.toFloat(),
            SonicAxis.BASS_HEAVY to stats.bassHeavy.toFloat(),
            SonicAxis.VOCAL to stats.vocal.toFloat(),
            SonicAxis.ATMOSPHERIC to stats.atmospheric.toFloat()
        )
    }
}

