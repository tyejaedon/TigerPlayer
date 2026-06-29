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
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

data class SpatialNode(
    val id: String,
    val label: String,
    val type: FilterType,
    val baseFreq: Float,
    var spatialPos: Offset, // Normalized X,Y (-1f to 1f)
    val color: Color
) {
    companion object {
        private const val MIN_FREQ = 20f
        private const val MAX_FREQ = 20_000f
        private const val MAX_GAIN_DB = 15f
    }

    fun toAcousticNode(): AcousticNode {
        val clampedX = spatialPos.x.coerceIn(-1f, 1f)
        val clampedY = spatialPos.y.coerceIn(-1f, 1f)

        // Exact logarithmic mapping from 20Hz..20kHz across the X axis.
        val t = (clampedX + 1f) * 0.5f
        val frequency = MIN_FREQ * (MAX_FREQ / MIN_FREQ).pow(t)

        // Strict +15dB..-15dB mapping where top is positive gain.
        val gain = (-clampedY) * MAX_GAIN_DB

        val radial = (abs(clampedX) + abs(clampedY)) * 0.5f
        val q = when (type) {
            FilterType.LOW_SHELF, FilterType.HIGH_SHELF -> 0.65f + radial * 0.9f
            FilterType.PEAKING -> 0.9f + (1f - abs(clampedY)) * 3.1f
        }.coerceIn(0.5f, 4.6f)

        return AcousticNode(id, label, type, frequency, gain, q)
    }
}

data class AuralNexusState(
    val nodes: List<SpatialNode> = emptyList(),
    val currentMood: String = "Neural Adaptive",
    val frequencyResponseCurve: List<Offset> = emptyList()
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

    val audioReactiveFrame: StateFlow<AudioReactiveFrame> = adaptiveDspEngine.audioReactiveFrame

    init {
        val defaultNodes = listOf(
            SpatialNode("sub", "Sub", FilterType.LOW_SHELF, 60f, Offset(-0.75f, -0.20f), Color(0xFFFF007F)),
            SpatialNode("warmth", "Body", FilterType.PEAKING, 220f, Offset(-0.30f, 0.05f), Color(0xFFFFD500)),
            SpatialNode("presence", "Presence", FilterType.PEAKING, 3600f, Offset(0.28f, -0.30f), Color(0xFF39FF14)),
            SpatialNode("air", "Air", FilterType.HIGH_SHELF, 12_000f, Offset(0.72f, -0.16f), Color(0xFF00E5FF))
        )

        _uiState.value = AuralNexusState(nodes = defaultNodes)
        updateDspAndVisuals()
    }

    fun applyListeningHabitProfile(historyTracks: List<AudioTrack>) {
        val hasHeavyBassGenre = historyTracks.any { it.album.contains("Trap", true) || it.album.contains("EDM", true) }

        val newNodes = _uiState.value.nodes.map { node ->
            if (node.id == "sub" && hasHeavyBassGenre) {
                node.copy(spatialPos = node.spatialPos.copy(y = -0.45f))
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
                    "presence" -> n.copy(spatialPos = Offset(0.2f, -0.7f))
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
        val clamped = Offset(newSpatialPos.x.coerceIn(-1f, 1f), newSpatialPos.y.coerceIn(-1f, 1f))
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

    @OptIn(UnstableApi::class)
    private fun updateFrequencyResponse(nodes: List<AcousticNode>) {
        visualsUpdateJob?.cancel()
        visualsUpdateJob = viewModelScope.launch(Dispatchers.Default) {
            val currentSampleRate = adaptiveDspEngine.getSampleRate().toFloat()
            
            val filterCoeffs = nodes.map { node ->
                BiquadDesigner.design(
                    type = node.filterType,
                    freq = node.frequency,
                    gainDb = node.gainDb,
                    q = node.qFactor,
                    sampleRate = currentSampleRate
                )
            }

            val points = mutableListOf<Offset>()
            val numPoints = 168
            val minFreqLog = log10(20.0)
            val maxFreqLog = log10(20000.0)
            val rangeLog = maxFreqLog - minFreqLog

            for (i in 0..numPoints) {
                val fraction = i.toFloat() / numPoints
                val currentFreq = 10.0.pow(minFreqLog + fraction * rangeLog).toFloat()

                var totalDbGain = 0f
                filterCoeffs.forEach { coeff ->
                    totalDbGain += BiquadDesigner.magnitudeAt(currentFreq, coeff, currentSampleRate)
                }

                val normalizedY = -(totalDbGain / 15f).coerceIn(-1f, 1f)
                points.add(Offset(fraction, normalizedY))
            }

            _uiState.value = _uiState.value.copy(frequencyResponseCurve = points)
        }
    }
}