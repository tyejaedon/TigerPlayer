package com.example.tigerplayer.ui.constellation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.constellation.ConstellationDataEngine
import com.example.tigerplayer.constellation.GraphEdge
import com.example.tigerplayer.constellation.NodeType
import com.example.tigerplayer.constellation.OrbitalLayoutEngine
import com.example.tigerplayer.constellation.PositionedNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConstellationState {
    object Loading : ConstellationState()
    data class Success(
        val nodes: Map<String, PositionedNode>,
        val edges: List<GraphEdge>,
        val insightMessage: String
    ) : ConstellationState()
    data class Error(val message: String) : ConstellationState()
}

@HiltViewModel
class ConstellationViewModel @Inject constructor(
    private val dataEngine: ConstellationDataEngine,
    private val layoutEngine: OrbitalLayoutEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConstellationState>(ConstellationState.Loading)
    val uiState: StateFlow<ConstellationState> = _uiState.asStateFlow()

    init {
        loadUniverse()
    }

    private fun loadUniverse() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ConstellationState.Loading
            try {
                val graph = dataEngine.buildGraph()
                val layoutNodes = layoutEngine.layout(graph)

                // The UI Canvas is optimized to read from a Map O(1) instead of searching Lists O(N)
                val nodeMap = layoutNodes.associateBy { it.id }

                val topStar = layoutNodes.firstOrNull { it.type == NodeType.ARTIST }?.label ?: "the unknown"
                val insight = "Your neural mapping revolves heavily around the gravity of $topStar. A dense cluster of high-frequency individual chants floats directly south."

                _uiState.value = ConstellationState.Success(
                    nodes = nodeMap,
                    edges = graph.edges,
                    insightMessage = insight
                )
            } catch (e: Exception) {
                _uiState.value = ConstellationState.Error("The telescope failed to align: ${e.message}")
            }
        }
    }
}