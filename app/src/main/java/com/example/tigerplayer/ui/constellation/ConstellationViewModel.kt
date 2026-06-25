package com.example.tigerplayer.ui.constellation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.constellation.ConstellationDataEngine
import com.example.tigerplayer.constellation.GraphEdge
import com.example.tigerplayer.constellation.NodeType
import com.example.tigerplayer.constellation.OrbitalLayoutEngine
import com.example.tigerplayer.constellation.PositionedNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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

/* -----------------------------------
   🧠 VIEWMODEL
----------------------------------- */

@HiltViewModel
class ConstellationViewModel @Inject constructor(
    dataEngine: ConstellationDataEngine,
    private val layoutEngine: OrbitalLayoutEngine,
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
        val dominantArtist = layoutNodes.asSequence()
            .filter { it.type == NodeType.ARTIST }
            .maxByOrNull { it.weight }

        val clusterCount = layoutNodes.count { it.orbitRadius < 1000f }

        return buildString {
            append("🌌 Galaxy density: ${(density * 100).roundToInt()}%\n")
            dominantArtist?.let {
                append("🎵 Dominant gravitational source: ${it.label}\n")
            }
            append("🪐 Local orbital clusters detected: $clusterCount\n")
            append(
                if (clusterCount > 20)
                    "The system is entering a high-turbulence resonance field."
                else
                    "Orbital stability is within harmonic equilibrium."
            )
        }
    }

    /**
     * Technically redundant in a reactive setup, but useful for 
     * manual re-triggers if needed for animation seeds.
     */
    fun refreshUniverse() {
        // In a reactive Flow-based architecture, this would typically 
        // trigger a refresh in the DataRepository or DataEngine.
    }
}