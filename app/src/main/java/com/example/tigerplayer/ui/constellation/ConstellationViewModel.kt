package com.example.tigerplayer.ui.constellation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.constellation.ConstellationDataEngine
import com.example.tigerplayer.constellation.GraphEdge
import com.example.tigerplayer.constellation.NodeType
import com.example.tigerplayer.constellation.OrbitalLayoutEngine
import com.example.tigerplayer.constellation.PositionedNode
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.engine.MetadataEngine
import com.example.tigerplayer.utils.ArtistUtils
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
        val insightMessage: String,
    ) : ConstellationState()

    data class Error(val message: String) : ConstellationState()
}

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
                insightMessage = insight
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

    private fun generateGalaxyInsight(density: Float, layoutNodes: List<PositionedNode>): String {
        val artistNodes = layoutNodes.filter { it.type == NodeType.ARTIST }
        val albumNodes = layoutNodes.count { it.type == NodeType.ALBUM }
        val trackNodes = layoutNodes.count { it.type == NodeType.TRACK }

        val dominantArtist = artistNodes.asSequence()
            .filter { it.type == NodeType.ARTIST }
            .maxByOrNull { it.weight }

        val clusterCount = layoutNodes.count { it.orbitRadius < 1000f }
        val densityPercent = (density * 100).roundToInt()

        return buildString {
            append("Mapped $densityPercent% density across ${artistNodes.size} artists, $albumNodes albums, and $trackNodes tracks.\n")
            dominantArtist?.let {
                append("Top artist signal: ${it.label} (${it.playCount} plays).\n")
            }
            append("Detected $clusterCount active orbit clusters in your listening history.")
        }
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