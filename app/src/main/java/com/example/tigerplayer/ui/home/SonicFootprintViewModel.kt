package com.example.tigerplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.local.dao.ArtistStats
import com.example.tigerplayer.data.local.dao.SonicFootprintStats
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.data.repository.LastFmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

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
    private val historyRepository: HistoryRepository,
    private val lastFmRepository: LastFmRepository
) : ViewModel() {

    private val _timeRange = MutableStateFlow(FootprintTimeRange.LIFETIME)
    private val artistGenreCache = linkedMapOf<String, List<String>>()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SonicFootprintUiState> = _timeRange.flatMapLatest { range ->
        val startTime = if (range.days > 0) historyRepository.getTimestampDaysAgo(range.days) else 0L

        combine(
            historyRepository.getSonicFootprintStats(startTime = startTime),
            historyRepository.getTotalListeningTime(startTime = startTime),
            historyRepository.getTopArtists(startTime = startTime, limit = LASTFM_GENRE_ARTIST_LIMIT)
        ) { footprintStats, totalListeningMs, topArtists ->
            Triple(footprintStats, totalListeningMs, topArtists)
        }.mapLatest { (footprintStats, totalListeningMs, topArtists) ->
            val axisRaw = buildAxisBuckets(footprintStats)
            val maxValue = axisRaw.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
            val normalized = SonicAxis.entries.associateWith { axis ->
                (axisRaw[axis] ?: 0f) / maxValue
            }

            val totalMinutes = ((totalListeningMs ?: 0L) / 60_000f).coerceAtLeast(0f)
            val topTags = buildLastFmWeightedTags(
                topArtists = topArtists,
                totalMinutes = totalMinutes
            ).ifEmpty {
                buildAxisFallbackTags(
                    axisRaw = axisRaw,
                    totalMinutes = totalMinutes
                )
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

    private fun buildAxisFallbackTags(
        axisRaw: Map<SonicAxis, Float>,
        totalMinutes: Float
    ): List<Pair<String, Float>> {
        val axisSum = axisRaw.values.sum().coerceAtLeast(1f)
        return axisRaw.entries
            .sortedByDescending { it.value }
            .take(MAX_UI_TAG_COUNT)
            .map { (axis, value) ->
                axis.label to ((value / axisSum) * totalMinutes)
            }
    }

    private suspend fun buildLastFmWeightedTags(
        topArtists: List<ArtistStats>,
        totalMinutes: Float
    ): List<Pair<String, Float>> {
        if (topArtists.isEmpty()) return emptyList()

        val consideredArtists = topArtists
            .asSequence()
            .filter { it.artistName.isNotBlank() && it.totalListeningMs > 0L }
            .take(LASTFM_GENRE_ARTIST_LIMIT)
            .toList()

        if (consideredArtists.isEmpty()) return emptyList()

        val resolved = coroutineScope {
            consideredArtists.map { artist ->
                async {
                    val minutes = (artist.totalListeningMs / 60_000f).coerceAtLeast(0f)
                    minutes to resolveGenresForArtist(artist.artistName)
                }
            }.awaitAll()
        }

        val minutesByTag = linkedMapOf<String, Float>()
        var representedMinutes = 0f

        resolved.forEach { (minutes, tags) ->
            if (minutes <= 0f || tags.isEmpty()) return@forEach
            representedMinutes += minutes
            val perTagMinutes = minutes / tags.size.coerceAtLeast(1)
            tags.forEach { tag ->
                minutesByTag[tag] = (minutesByTag[tag] ?: 0f) + perTagMinutes
            }
        }

        if (minutesByTag.isEmpty()) return emptyList()

        val scaleFactor = if (totalMinutes > 0f && representedMinutes > 0f) {
            (totalMinutes / representedMinutes).coerceIn(0.5f, 2.5f)
        } else {
            1f
        }

        return minutesByTag.entries
            .sortedByDescending { it.value }
            .take(MAX_UI_TAG_COUNT)
            .map { (tag, weightedMinutes) ->
                formatGenreTag(tag) to (weightedMinutes * scaleFactor).roundToInt().toFloat()
            }
    }

    private suspend fun resolveGenresForArtist(artistName: String): List<String> {
        val key = artistName.trim().lowercase()
        artistGenreCache[key]?.let { return it }

        val genres = lastFmRepository
            .fetchArtistProfile(artistName)
            ?.genres
            .orEmpty()
            .asSequence()
            .mapNotNull(::normalizeGenreTag)
            .distinct()
            .take(MAX_TAGS_PER_ARTIST)
            .toList()

        artistGenreCache[key] = genres
        return genres
    }

    private fun normalizeGenreTag(raw: String): String? {
        val cleaned = raw
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9+\\- ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) return null
        if (cleaned in NON_GENRE_TAGS) return null

        return when (cleaned) {
            "hip-hop", "hip hop", "hiphop", "rap" -> "hip hop"
            "rnb", "r and b", "rhythm and blues" -> "r&b"
            "edm", "electronic dance music", "dance", "dance music" -> "electronic"
            "alt rock", "alternative" -> "alternative rock"
            "synth-pop", "synthpop" -> "synth pop"
            "drum and bass", "drum n bass", "dnb" -> "drum and bass"
            "lo-fi", "lo fi" -> "lofi"
            "post rock" -> "post-rock"
            "trip hop", "trip-hop" -> "trip hop"
            else -> cleaned
        }.takeIf { it.length >= 3 }
    }

    private fun formatGenreTag(tag: String): String {
        return tag
            .split(" ")
            .joinToString(" ") { part ->
                part.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                }
            }
    }

    private companion object {
        const val LASTFM_GENRE_ARTIST_LIMIT = 8
        const val MAX_TAGS_PER_ARTIST = 3
        const val MAX_UI_TAG_COUNT = 5

        val NON_GENRE_TAGS = setOf(
            "seen live",
            "favorites",
            "favourite",
            "favorite",
            "best",
            "my favorites",
            "under 2000 listeners",
            "00s",
            "90s",
            "80s",
            "70s",
            "60s",
            "male vocalists",
            "female vocalists",
            "american",
            "british",
            "awesome",
            "cool"
        )
    }
}

