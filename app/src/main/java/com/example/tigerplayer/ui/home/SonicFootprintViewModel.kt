package com.example.tigerplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.local.dao.GenreFootprintStat
import com.example.tigerplayer.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

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
            historyRepository.getTopGenreFootprint(startTime = startTime, limit = 30),
            historyRepository.getTotalListeningTime(startTime = startTime)
        ) { topGenres, totalListeningMs ->
            val axisRaw = buildAxisBuckets(topGenres)
            val maxValue = axisRaw.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
            val normalized = SonicAxis.entries.associateWith { axis ->
                (axisRaw[axis] ?: 0f) / maxValue
            }

            SonicFootprintUiState(
                axisValues = normalized,
                topTags = topGenres.take(5).map {
                    val weightMinutes = (it.weightMs / 60000f).coerceAtLeast(0f)
                    it.genre to weightMinutes
                },
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

    private fun buildAxisBuckets(topGenres: List<GenreFootprintStat>): Map<SonicAxis, Float> {
        val buckets = SonicAxis.entries.associateWith { 0f }.toMutableMap()

        topGenres.forEach { stat ->
            val key = stat.genre.lowercase().trim()
            val weight = stat.weightMs.toFloat().coerceAtLeast(0f)

            val axis = mapGenreToAxis(key)
            buckets[axis] = (buckets[axis] ?: 0f) + weight
        }

        return buckets
    }

    private fun mapGenreToAxis(genre: String): SonicAxis {
        return when {
            containsAny(genre, "acoustic", "folk", "singer", "unplugged", "country", "piano", "classical") -> SonicAxis.ACOUSTIC
            containsAny(genre, "electronic", "edm", "techno", "house", "trance", "synth", "dance", "industrial") -> SonicAxis.ELECTRONIC
            containsAny(genre, "bass", "trap", "drill", "dubstep", "hip hop", "hip-hop", "grime", "phonk") -> SonicAxis.BASS_HEAVY
            containsAny(genre, "vocal", "soul", "rnb", "r&b", "pop", "choir", "gospel", "ballad") -> SonicAxis.VOCAL
            containsAny(genre, "ambient", "chill", "lofi", "lo-fi", "cinematic", "soundtrack", "dream", "atmospheric", "meditation") -> SonicAxis.ATMOSPHERIC
            else -> SonicAxis.ATMOSPHERIC
        }
    }

    private fun containsAny(input: String, vararg tokens: String): Boolean {
        return tokens.any { token -> input.contains(token) }
    }
}

