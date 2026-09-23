package com.tigerplayer.ui.constellation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tigerplayer.constellation.ConstellationDataEngine
import com.tigerplayer.constellation.GraphEdge
import com.tigerplayer.constellation.NodeType
import com.tigerplayer.constellation.OrbitalLayoutEngine
import com.tigerplayer.constellation.PositionedNode
import com.tigerplayer.data.repository.HistoryRepository
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.utils.ArtistUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/* -----------------------------------
   🌌 UI STATE
----------------------------------- */

sealed class ConstellationState {
    object Loading : ConstellationState()

    data class Success(
        val nodes: Map<String, PositionedNode>,
        val edges: List<GraphEdge>,
        val density: Float,
        val seed: Long,
        val insight: GalaxyInsight,
    ) : ConstellationState()

    data class Error(val message: String) : ConstellationState()
}

/**
 * Structured "Cosmic Insight" data, shown when the central Sun node is tapped.
 * Kept as discrete fields (rather than a pre-formatted paragraph) so the overlay can
 * render it as separate, readable rows instead of a wall of text.
 */
data class GalaxyInsight(
    val densityPercent: Int,
    val artistCount: Int,
    val albumCount: Int,
    val trackCount: Int,
    val topArtistName: String?,
    val topArtistPlays: Int,
    val clusterCount: Int
)

data class ConstellationArtistReading(
    val artistName: String,
    val playCount: Int,
    val minutesListened: Int,
    val listeningSharePercent: Float,
    val genres: List<String> = emptyList(),
    val bioSnippet: String? = null,
    val imageUrl: String? = null
)

/* -----------------------------------
   🧠 VIEWMODEL
----------------------------------- */

@HiltViewModel
class ConstellationViewModel @Inject constructor(
    private val dataEngine: ConstellationDataEngine,
    private val layoutEngine: OrbitalLayoutEngine,
    private val historyRepository: HistoryRepository,
    private val metadataEngine: MetadataEngine
) : ViewModel() {

    /**
     * 🔥 THE SUPREME REACTIVE PIPELINE
     * Converts the raw semantic graph into a physics-positioned UI state.
     * flowOn ensures the heavy layout math happens on the Default dispatcher.
     */
    val uiState: StateFlow<ConstellationState> = dataEngine.getGraphFlow()
        .map { graph ->
            // 1. APPLY ORBITAL LAYOUT (Physics Layer)
            val layoutNodes = layoutEngine.layout(graph)
            val nodeMap = layoutNodes.associateBy { it.id }

            // 2. GENERATE INSIGHTS (Narration Layer)
            val insight = generateGalaxyInsight(graph.density, layoutNodes)

            // 3. EMIT SUCCESS
            ConstellationState.Success(
                nodes = nodeMap,
                edges = graph.edges,
                density = graph.density,
                seed = graph.seed,
                insight = insight
            ) as ConstellationState
        }
        .flowOn(Dispatchers.Default) // Perform physics calculations off the Main thread
        .catch { e ->
            Log.e("ConstellationVM", "Universe collapse detected", e)
            emit(ConstellationState.Error("The constellation collapsed: ${e.localizedMessage ?: "Unknown error"}"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConstellationState.Loading
        )

    /* -----------------------------------
       🌌 INSIGHT ENGINE
    ----------------------------------- */

    private fun generateGalaxyInsight(density: Float, layoutNodes: List<PositionedNode>): GalaxyInsight {
        val artistNodes = layoutNodes.filter { it.type == NodeType.ARTIST }
        val albumNodes = layoutNodes.count { it.type == NodeType.ALBUM }
        val trackNodes = layoutNodes.count { it.type == NodeType.TRACK }

        val dominantArtist = artistNodes.asSequence()
            .filter { it.type == NodeType.ARTIST }
            .maxByOrNull { it.weight }

        val clusterCount = layoutNodes.count { it.orbitRadius < 1000f }
        val densityPercent = (density * 100).roundToInt()

        return GalaxyInsight(
            densityPercent = densityPercent,
            artistCount = artistNodes.size,
            albumCount = albumNodes,
            trackCount = trackNodes,
            topArtistName = dominantArtist?.label,
            topArtistPlays = dominantArtist?.playCount ?: 0,
            clusterCount = clusterCount
        )
    }

    fun prefetchArtistReading(artistName: String) {
        val cleanArtist = ArtistUtils.getBaseArtist(artistName).trim()
        if (cleanArtist.isBlank()) return
        viewModelScope.launch {
            metadataEngine.fetchArtistProfile(cleanArtist)
        }
    }

    fun observeArtistReading(artistName: String): Flow<ConstellationArtistReading?> {
        val cleanArtist = ArtistUtils.getBaseArtist(artistName).trim()
        if (cleanArtist.isBlank()) return flowOf(null)
        val cacheKey = cleanArtist.lowercase()

        return combine(
            historyRepository.observeArtistStats(cleanArtist),
            historyRepository.getTotalListeningTime(0L).map { it ?: 0L },
            metadataEngine.artistDetails.map { detailsMap -> detailsMap[cacheKey] }
        ) { stats, totalListeningMs, lore ->
            if (stats == null) {
                return@combine null
            }

            val minutes = (stats.totalListeningMs / 60_000L).toInt().coerceAtLeast(0)
            val share = if (totalListeningMs > 0L) {
                ((stats.totalListeningMs.toFloat() / totalListeningMs.toFloat()) * 100f)
                    .coerceIn(0f, 100f)
            } else {
                0f
            }

            ConstellationArtistReading(
                artistName = stats.artistName.ifBlank { cleanArtist },
                playCount = stats.playCount,
                minutesListened = minutes,
                listeningSharePercent = share,
                genres = lore?.genres.orEmpty(),
                bioSnippet = lore?.bio
                    ?.substringBefore("<")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
                imageUrl = lore?.imageUrl ?: stats.imageUrl
            )
        }.distinctUntilChanged()
    }

    /**
     * Technically redundant in a reactive setup, but useful for 
     * manual re-triggers if needed for animation seeds.
     */
    fun refreshUniverse() {
        viewModelScope.launch {
            dataEngine.refreshGraphData()
        }
    }
}