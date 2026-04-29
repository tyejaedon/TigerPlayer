package com.example.tigerplayer.ui.equalizer

import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.engine.dsp.AcousticNode
import com.example.tigerplayer.engine.dsp.AdaptiveDspEngine
import com.example.tigerplayer.engine.dsp.FilterType
import com.example.tigerplayer.utils.BiquadDesigner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

// --- UI STATE MODELS ---

data class SpatialNode(
    val id: String,
    val label: String,
    val type: FilterType,
    val baseFreq: Float, // The center frequency this node affects
    var spatialPos: Offset, // Normalised X,Y (-1f to 1f) from center
    val color: Color
) {
    // Converts spatial canvas position into actual Audio Math
    fun toAcousticNode(): AcousticNode {
        // Y-axis = Gain (-15dB to +15dB) -> Dragging UP (negative Y) boosts, Dragging DOWN (positive Y) cuts
        val gain = -(spatialPos.y * 15f)

        // X-axis = Frequency Shift (Allows user to slide the bass node from 40Hz to 120Hz)
        val freqShift = 2.0.pow((spatialPos.x).toDouble()).toFloat()
        val dynamicFreq = (baseFreq * freqShift).coerceIn(20f, 20000f)

        // Distance from center = Q Factor (Resonance/Width)
        val distance = sqrt(spatialPos.x.pow(2) + spatialPos.y.pow(2))
        val q = 0.5f + (distance * 2f).coerceIn(0.1f, 4f)

        return AcousticNode(id, label, type, dynamicFreq, gain, q, color)
    }
}

data class AuralNexusState(
    val nodes: List<SpatialNode> = emptyList(),
    val currentMood: String = "Neural Adaptive",
    val frequencyResponseCurve: List<Offset> = emptyList() // Rendered on GPU
)

@HiltViewModel
class AuralNexusViewModel @OptIn(UnstableApi::class)
@Inject constructor(
    private val adaptiveDspEngine: AdaptiveDspEngine // 🔥 INJECTING DIRECT HARDWARE DSP
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuralNexusState())
    val uiState: StateFlow<AuralNexusState> = _uiState.asStateFlow()

    private var dspUpdateJob: Job? = null

    init {
        // Initialize with default spatial traits
        val defaultNodes = listOf(
            SpatialNode("sub", "Sub-Bass", FilterType.LOW_SHELF, 60f, Offset(-0.6f, 0.2f), Color(0xFFFF5252)),
            SpatialNode("warmth", "Warmth", FilterType.PEAKING, 250f, Offset(-0.3f, -0.1f), Color(0xFFFFD700)),
            SpatialNode("vocal", "Presence", FilterType.PEAKING, 3500f, Offset(0.3f, -0.4f), Color(0xFF4FC3F7)),
            SpatialNode("air", "Air", FilterType.HIGH_SHELF, 12000f, Offset(0.6f, -0.2f), Color(0xFFB388FF))
        )

        _uiState.value = AuralNexusState(nodes = defaultNodes)
        updateDspAndVisuals()
    }

    /**
     * 🧠 AI COGNITIVE PROFILE
     * Reads listening history to generate a starting sound signature.
     */
    fun applyListeningHabitProfile(historyTracks: List<AudioTrack>) {
        val hasHeavyBassGenre = historyTracks.any { it.album.contains("Trap", true) || it.album.contains("EDM", true) }

        val newNodes = _uiState.value.nodes.map { node ->
            if (node.id == "sub" && hasHeavyBassGenre) {
                node.copy(spatialPos = node.spatialPos.copy(y = -0.5f)) // Boost bass natively
            } else node
        }

        _uiState.value = _uiState.value.copy(nodes = newNodes, currentMood = "Cognitive Profile Applied")
        updateDspAndVisuals()
    }

    fun setMoodPreset(mood: String) {
        val nodes = _uiState.value.nodes
        val updated = when (mood) {
            "Night Drive" -> nodes.map { n ->
                when (n.id) {
                    "sub" -> n.copy(spatialPos = Offset(-0.6f, -0.6f)) // Huge bass
                    "air" -> n.copy(spatialPos = Offset(0.6f, -0.4f))  // Shimmering highs
                    "warmth" -> n.copy(spatialPos = Offset(-0.3f, 0.2f)) // Scooped mud
                    else -> n
                }
            }
            "Pure Vocal" -> nodes.map { n ->
                when (n.id) {
                    "vocal" -> n.copy(spatialPos = Offset(0.2f, -0.7f)) // Vocals pushed to the front
                    "sub" -> n.copy(spatialPos = Offset(-0.6f, 0.3f))   // Bass controlled
                    else -> n
                }
            }
            "Studio Flat" -> nodes.map { n ->
                n.copy(spatialPos = Offset(n.spatialPos.x, 0f)) // UI automatically applies magnetic center snap
            }
            else -> nodes
        }
        _uiState.value = _uiState.value.copy(nodes = updated, currentMood = mood)
        updateDspAndVisuals()
    }

    fun moveNode(nodeId: String, newSpatialPos: Offset) {
        val updatedNodes = _uiState.value.nodes.map {
            if (it.id == nodeId) it.copy(spatialPos = newSpatialPos) else it
        }
        _uiState.value = _uiState.value.copy(nodes = updatedNodes, currentMood = "Custom Shape")
        updateDspAndVisuals()
    }

    @OptIn(UnstableApi::class)
    private fun updateDspAndVisuals() {
        val acousticNodes = _uiState.value.nodes.map { it.toAcousticNode() }

        // 1. Visuals update instantly at 120fps for buttery smooth UI
        updateFrequencyResponse(acousticNodes)

        // 2. Hardware DSP updates are debounced to protect the Audio Server
        dspUpdateJob?.cancel()
        dspUpdateJob = viewModelScope.launch(Dispatchers.Default) {
            delay(150)

            // 🔥 DIRECT INJECTION: Passes mathematical instructions straight to the audio engine!
            adaptiveDspEngine.updateAcousticNodes(acousticNodes)
        }
    }

    /**
     * 🌌 REAL-TIME MATHEMATICAL VISUALIZER
     */
    private fun updateFrequencyResponse(nodes: List<AcousticNode>) {
        viewModelScope.launch(Dispatchers.Default) {

            // Pre-calculate the heavy biquad coefficients once per frame.
            val filterCoeffs = nodes.map { BiquadDesigner.design(it, 44100f) }

            val points = mutableListOf<Offset>()
            val numPoints = 120
            val minFreqLog = log10(20.0)
            val maxFreqLog = log10(20000.0)
            val rangeLog = maxFreqLog - minFreqLog

            for (i in 0..numPoints) {
                val fraction = i.toFloat() / numPoints
                val currentFreq = 10.0.pow(minFreqLog + fraction * rangeLog).toFloat()

                var totalDbGain = 0f
                filterCoeffs.forEach { coeff ->
                    totalDbGain += BiquadDesigner.magnitudeAt(currentFreq, coeff, 44100f)
                }

                // Inverted the Y-axis calculation. A positive dB boost maps to negative Y (UP on screen)
                val normalizedY = -(totalDbGain / 15f).coerceIn(-1f, 1f)
                points.add(Offset(fraction, normalizedY))
            }

            _uiState.value = _uiState.value.copy(frequencyResponseCurve = points)
        }
    }
}