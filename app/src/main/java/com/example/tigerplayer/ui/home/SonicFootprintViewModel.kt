package com.example.tigerplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.local.dao.GenreFootprintStat
import com.example.tigerplayer.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SonicAxis(val label: String) {
    ACOUSTIC("Acoustic"),
    ELECTRONIC("Electronic"),
    BASS_HEAVY("Bass-Heavy"),
    VOCAL("Vocal"),
    ATMOSPHERIC("Atmospheric")
}

data class SonicFootprintUiState(
    val axisValues: Map<SonicAxis, Float> = SonicAxis.entries.associateWith { 0f },
    val topTags: List<Pair<String, Float>> = emptyList(),
    val totalListeningHours: Float = 0f
)

@HiltViewModel
class SonicFootprintViewModel @Inject constructor(
    historyRepository: HistoryRepository
) : ViewModel() {

    val uiState: StateFlow<SonicFootprintUiState> = combine(
        historyRepository.getTopGenreFootprint(startTime = 0L, limit = 30),
        historyRepository.getTotalListeningTime(startTime = 0L)
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
            totalListeningHours = ((totalListeningMs ?: 0L) / 3_600_000f).coerceAtLeast(0f)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SonicFootprintUiState()
    )

    private fun buildAxisBuckets(topGenres: List<GenreFootprintStat>): Map<SonicAxis, Float> {
        val buckets = SonicAxis.entries.associateWith { 0f }.toMutableMap()

        topGenres.forEach { stat ->
            val key = stat.genre.lowercase().trim()
            val weight = stat.weightMs.toFloat().coerceAtLeast(0f)

            val axis = when {
                containsAny(key, "acoustic", "folk", "singer", "unplugged", "country", "piano") -> SonicAxis.ACOUSTIC
                containsAny(key, "electronic", "edm", "techno", "house", "trance", "synth", "dance") -> SonicAxis.ELECTRONIC
                containsAny(key, "bass", "trap", "drill", "dubstep", "hip hop", "hip-hop", "grime") -> SonicAxis.BASS_HEAVY
                containsAny(key, "vocal", "soul", "rnb", "r&b", "pop", "choir", "gospel") -> SonicAxis.VOCAL
                containsAny(key, "ambient", "chill", "lofi", "lo-fi", "cinematic", "soundtrack", "dream") -> SonicAxis.ATMOSPHERIC
                else -> SonicAxis.ATMOSPHERIC
            }

            buckets[axis] = (buckets[axis] ?: 0f) + weight
        }

        return buckets
    }

    private fun containsAny(input: String, vararg tokens: String): Boolean {
        return tokens.any { token -> input.contains(token) }
    }
}

