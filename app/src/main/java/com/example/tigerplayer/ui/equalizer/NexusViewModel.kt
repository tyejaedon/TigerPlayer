package com.example.tigerplayer.ui.equalizer

import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.engine.AcousticNode
import com.example.tigerplayer.engine.AdaptiveDspEngine
import com.example.tigerplayer.engine.AudioReactiveFrame
import com.example.tigerplayer.engine.FilterType
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

data class SpatialNode(
    val id: String,
    val label: String,
    val type: FilterType,
    val baseFreq: Float,
    var spatialPos: Offset, // Normalized X,Y (-1f to 1f)
    val color: Color
) {
    fun toAcousticNode(): AcousticNode {
        val clampedX = spatialPos.x.coerceIn(-1f, 1f)
        val clampedY = spatialPos.y.coerceIn(-1f, 1f)

        // Exact log mapping from 20Hz -> 20kHz across X in [-1, 1].
        val normalizedX = (clampedX + 1f) * 0.5f
        val dynamicFreq = (20f * 1000f.pow(normalizedX)).coerceIn(20f, 20000f)

        // Exact gain mapping: top is +15dB, center is 0dB, bottom is -15dB.
        val gain = (-clampedY * 15f).coerceIn(-15f, 15f)

        val distance = sqrt(clampedX.pow(2) + clampedY.pow(2))
        val q = (0.7f + (distance * 2.3f)).coerceIn(0.7f, 4.2f)

        return AcousticNode(id, label, type, dynamicFreq, gain, q)
    }
}

data class AuralNexusState(
    val nodes: List<SpatialNode> = emptyList(),
    val currentMood: String = "Neural Adaptive",
    val frequencyResponseCurve: List<Offset> = emptyList(),
    val audioReactiveFrame: AudioReactiveFrame = AudioReactiveFrame()
)

@HiltViewModel
class AuralNexusViewModel @OptIn(UnstableApi::class)
@Inject constructor(
    private val adaptiveDspEngine: AdaptiveDspEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuralNexusState())
    val uiState: StateFlow<AuralNexusState> = _uiState.asStateFlow()

    private var dspUpdateJob: Job? = null
    private var visualsUpdateJob: Job? = null // FIXED: Prevent visual computation thread leaks

    init {
        val defaultNodes = listOf(
            SpatialNode("sub", "Sub-Bass", FilterType.LOW_SHELF, 60f, Offset(-0.6f, 0.2f), Color(0xFFFFD500)),
            SpatialNode("warmth", "Warmth", FilterType.PEAKING, 250f, Offset(-0.3f, -0.1f), Color(0xFFFF007F)),
            SpatialNode("vocal", "Presence", FilterType.PEAKING, 3500f, Offset(0.3f, -0.4f), Color(0xFF00E5FF)),
            SpatialNode("air", "Air", FilterType.HIGH_SHELF, 12000f, Offset(0.6f, -0.2f), Color(0xFF39FF14))
        )

        _uiState.value = AuralNexusState(nodes = defaultNodes)

        viewModelScope.launch {
            adaptiveDspEngine.audioReactiveFrame.collect { frame ->
                _uiState.value = _uiState.value.copy(audioReactiveFrame = frame)
            }
        }

        updateDspAndVisuals()
    }

    fun applyListeningHabitProfile(historyTracks: List<AudioTrack>) {
        val hasHeavyBassGenre = historyTracks.any { it.album.contains("Trap", true) || it.album.contains("EDM", true) }

        val newNodes = _uiState.value.nodes.map { node ->
            if (node.id == "sub" && hasHeavyBassGenre) {
                node.copy(spatialPos = node.spatialPos.copy(y = -0.5f))
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
                    "sub" -> n.copy(spatialPos = Offset(-0.6f, -0.6f))
                    "air" -> n.copy(spatialPos = Offset(0.6f, -0.4f))
                    "warmth" -> n.copy(spatialPos = Offset(-0.3f, 0.2f))
                    else -> n
                }
            }
            "Pure Vocal" -> nodes.map { n ->
                when (n.id) {
                    "vocal" -> n.copy(spatialPos = Offset(0.2f, -0.7f))
                    "sub" -> n.copy(spatialPos = Offset(-0.6f, 0.3f))
                    else -> n
                }
            }
            "Studio Flat" -> nodes.map { n ->
                n.copy(spatialPos = Offset(n.spatialPos.x, 0f))
            }
            else -> nodes
        }
        _uiState.value = _uiState.value.copy(nodes = updated, currentMood = mood)
        updateDspAndVisuals()
    }

    fun moveNode(nodeId: String, newSpatialPos: Offset) {
        val clamped = Offset(
            x = newSpatialPos.x.coerceIn(-1f, 1f),
            y = newSpatialPos.y.coerceIn(-1f, 1f)
        )
        val updatedNodes = _uiState.value.nodes.map {
            if (it.id == nodeId) it.copy(spatialPos = clamped) else it
        }
        _uiState.value = _uiState.value.copy(nodes = updatedNodes, currentMood = "Custom Shape")
        updateDspAndVisuals()
    }

    @OptIn(UnstableApi::class)
    private fun updateDspAndVisuals() {
        val acousticNodes = _uiState.value.nodes.map { it.toAcousticNode() }

        // 1. Visuals update instantly but debounced safely on their own job
        updateFrequencyResponse(acousticNodes)

        // 2. Hardware DSP updates are debounced to protect the Audio Server
        dspUpdateJob?.cancel()
        dspUpdateJob = viewModelScope.launch(Dispatchers.Default) {
            delay(150)
            adaptiveDspEngine.updateAcousticNodes(acousticNodes)
        }
    }

    private fun updateFrequencyResponse(nodes: List<AcousticNode>) {
        visualsUpdateJob?.cancel() // FIXED: Cancel previous running render jobs
        visualsUpdateJob = viewModelScope.launch(Dispatchers.Default) {
            val filterCoeffs = nodes.map { node ->
                BiquadDesigner.design(
                    type = node.filterType,
                    freq = node.frequency,
                    gainDb = node.gainDb,
                    q = node.qFactor,
                    sampleRate = 44100f
                )
            }

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

                val normalizedY = -(totalDbGain / 15f).coerceIn(-1f, 1f)
                points.add(Offset(fraction, normalizedY))
            }

            _uiState.value = _uiState.value.copy(frequencyResponseCurve = points)
        }
    }
}