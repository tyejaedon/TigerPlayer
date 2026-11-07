package com.tigerplayer.ui.equalizer

import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.engine.AcousticNode
import com.tigerplayer.engine.AdaptiveDspEngine
import com.tigerplayer.engine.AudioReactiveFrame
import com.tigerplayer.engine.FilterType
import com.tigerplayer.utils.BiquadDesigner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow

data class AuralNexusState(
    val nodes: List<SpatialNode> = emptyList(),
    val currentMood: String = "Neural Adaptive",
    val frequencyResponseCurve: FloatArray = FloatArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuralNexusState

        if (nodes != other.nodes) return false
        if (currentMood != other.currentMood) return false
        if (!frequencyResponseCurve.contentEquals(other.frequencyResponseCurve)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nodes.hashCode()
        result = 31 * result + currentMood.hashCode()
        result = 31 * result + frequencyResponseCurve.contentHashCode()
        return result
    }
}

@HiltViewModel
class AuralNexusViewModel @OptIn(UnstableApi::class)
@Inject constructor(
    private val adaptiveDspEngine: AdaptiveDspEngine,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    companion object {
        const val NUM_POINTS = 168
        private const val BUFFER_SIZE = (NUM_POINTS + 1) * 2
    }

    // Pre-allocated triple buffer for real-time frequency curve calculations without GC churn
    private val reusableCurveBuffers = Array(3) { FloatArray(BUFFER_SIZE) }
    private val activeBufferIndex = AtomicInteger(0)

    private val _uiState = MutableStateFlow(AuralNexusState(frequencyResponseCurve = reusableCurveBuffers[0]))
    val uiState: StateFlow<AuralNexusState> = _uiState.asStateFlow()

    private val neuralAdaptiveNodes = listOf(
        SpatialNode("sub", "Sub", FilterType.LOW_SHELF, 60f, Offset(-0.75f, -0.20f), Color(0xFFFF007F)),
        SpatialNode("warmth", "Body", FilterType.PEAKING, 220f, Offset(-0.30f, 0.05f), Color(0xFFFFD500)),
        SpatialNode("presence", "Presence", FilterType.PEAKING, 3600f, Offset(0.28f, -0.30f), Color(0xFF39FF14)),
        SpatialNode("air", "Air", FilterType.HIGH_SHELF, 12_000f, Offset(0.72f, -0.16f), Color(0xFF00E5FF))
    )

    private var dspUpdateJob: Job? = null
    private var visualsUpdateJob: Job? = null // FIXED: Prevent visual computation thread leaks

    val audioReactiveFrame: StateFlow<AudioReactiveFrame> = adaptiveDspEngine.audioReactiveFrame

    init {
        _uiState.value = AuralNexusState(nodes = neuralAdaptiveNodes, frequencyResponseCurve = reusableCurveBuffers[0])
        updateDspAndVisuals()
        viewModelScope.launch {
            val savedPreset = settingsDataStore.settingsFlow.first().coverScreenEqPreset
            applyMoodPreset(savedPreset.displayName)
        }
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
        CoverScreenEqPreset.entries.firstOrNull { it.displayName == mood }?.let { preset ->
            viewModelScope.launch { settingsDataStore.setCoverScreenEqPreset(preset) }
        }
        applyMoodPreset(mood)
    }

    private fun applyMoodPreset(mood: String) {
        val nodes = neuralAdaptiveNodes
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

            val index = (activeBufferIndex.getAndIncrement() and Int.MAX_VALUE) % reusableCurveBuffers.size
            val targetBuffer = reusableCurveBuffers[index]

            val minFreqLog = log10(20.0)
            val maxFreqLog = log10(20000.0)
            val rangeLog = maxFreqLog - minFreqLog

            for (i in 0..NUM_POINTS) {
                val fraction = i.toFloat() / NUM_POINTS
                val currentFreq = 10.0.pow(minFreqLog + fraction * rangeLog).toFloat()

                var totalDbGain = 0f
                filterCoeffs.forEach { coeff ->
                    totalDbGain += BiquadDesigner.magnitudeAt(currentFreq, coeff, currentSampleRate)
                }

                val normalizedY = -(totalDbGain / SpatialNode.MAX_GAIN_DB).coerceIn(-1f, 1f)
                val offset = i * 2
                targetBuffer[offset] = fraction
                targetBuffer[offset + 1] = normalizedY
            }

            _uiState.value = _uiState.value.copy(frequencyResponseCurve = targetBuffer)
        }
    }
}
