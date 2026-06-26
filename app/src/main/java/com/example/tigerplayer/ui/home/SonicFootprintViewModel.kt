package com.example.tigerplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class SonicFootprintFilter(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_90_DAYS("Last 90 Days"),
    THIS_YEAR("This Year"),
    LIFETIME("Lifetime")
}

data class SonicFootprintAxis(
    val label: String,
    val value: Float
)

data class SonicFootprintUiState(
    val axes: List<SonicFootprintAxis> = listOf(
        SonicFootprintAxis("Acoustic", 0f),
        SonicFootprintAxis("Electronic", 0f),
        SonicFootprintAxis("Bass-Heavy", 0f),
        SonicFootprintAxis("Vocal", 0f),
        SonicFootprintAxis("Atmospheric", 0f)
    ),
    val totalPlays: Int = 0,
    val selectedMinutes: Int = 0,
    val lifetimeMinutes: Int = 0,
    val globalListeningSharePercent: Float = 0f,
    val selectedFilter: SonicFootprintFilter = SonicFootprintFilter.LIFETIME,
    val isEmpty: Boolean = true
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SonicFootprintViewModel @Inject constructor(
    historyRepository: HistoryRepository
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(SonicFootprintFilter.LIFETIME)
    val availableFilters: List<SonicFootprintFilter> = SonicFootprintFilter.entries

    private val sonicStatsFlow = selectedFilter.flatMapLatest { filter ->
        historyRepository.getSonicFootprintStats(startTime = calculateStartTime(filter))
    }

    private val selectedWindowListeningMs = selectedFilter.flatMapLatest { filter ->
        historyRepository.getTotalListeningTime(calculateStartTime(filter))
    }

    private val lifetimeListeningMs = historyRepository.getTotalListeningTime(0L)

    val uiState: StateFlow<SonicFootprintUiState> = combine(
        sonicStatsFlow,
        selectedWindowListeningMs,
        lifetimeListeningMs,
        selectedFilter
    ) { stats, selectedMs, lifetimeMs, filter ->
            val values = listOf(
                stats.acoustic.toFloat(),
                stats.electronic.toFloat(),
                stats.bassHeavy.toFloat(),
                stats.vocal.toFloat(),
                stats.atmospheric.toFloat()
            )
            val maxValue = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val selectedMinutes = ((selectedMs ?: 0L) / 60_000L).toInt().coerceAtLeast(0)
            val lifetimeMinutes = ((lifetimeMs ?: 0L) / 60_000L).toInt().coerceAtLeast(0)
            val sharePercent = if (lifetimeMinutes > 0) {
                (selectedMinutes.toFloat() / lifetimeMinutes.toFloat() * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }

            SonicFootprintUiState(
                axes = listOf(
                    SonicFootprintAxis("Acoustic", (stats.acoustic / maxValue).coerceIn(0f, 1f)),
                    SonicFootprintAxis("Electronic", (stats.electronic / maxValue).coerceIn(0f, 1f)),
                    SonicFootprintAxis("Bass-Heavy", (stats.bassHeavy / maxValue).coerceIn(0f, 1f)),
                    SonicFootprintAxis("Vocal", (stats.vocal / maxValue).coerceIn(0f, 1f)),
                    SonicFootprintAxis("Atmospheric", (stats.atmospheric / maxValue).coerceIn(0f, 1f))
                ),
                totalPlays = stats.total,
                selectedMinutes = selectedMinutes,
                lifetimeMinutes = lifetimeMinutes,
                globalListeningSharePercent = sharePercent,
                selectedFilter = filter,
                isEmpty = stats.total == 0
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SonicFootprintUiState()
        )

    fun setFilter(filter: SonicFootprintFilter) {
        selectedFilter.value = filter
    }

    private fun calculateStartTime(filter: SonicFootprintFilter): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (filter) {
            SonicFootprintFilter.TODAY -> calendar.timeInMillis
            SonicFootprintFilter.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.timeInMillis
            }
            SonicFootprintFilter.LAST_7_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                calendar.timeInMillis
            }
            SonicFootprintFilter.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis
            }
            SonicFootprintFilter.LAST_30_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                calendar.timeInMillis
            }
            SonicFootprintFilter.LAST_90_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -90)
                calendar.timeInMillis
            }
            SonicFootprintFilter.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.timeInMillis
            }
            SonicFootprintFilter.LIFETIME -> 0L
        }
    }
}

