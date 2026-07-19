package com.example.tigerplayer.ui.prism

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.tigerplayer.data.local.PrismSpectralAnalysis
import com.example.tigerplayer.data.local.SettingsDataStore
import com.example.tigerplayer.engine.AdaptiveDspEngine
import com.example.tigerplayer.engine.PrismMixLevels
import com.example.tigerplayer.engine.PrismMode
import com.example.tigerplayer.engine.SpectralAnalysisMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class PrismPreset(
    val displayName: String,
    val mix: PrismMixLevels
) {
    BALANCED("Balanced", PrismMixLevels(vocals = 1f, beats = 1f, instruments = 1f)),
    VOCAL_FOCUS("Vocal Focus", PrismMixLevels(vocals = 1f, beats = 0.35f, instruments = 0.35f)),
    BEAT_PUNCH("Beat Punch", PrismMixLevels(vocals = 0.35f, beats = 1f, instruments = 0.45f)),
    INSTRUMENTAL("Instrumental", PrismMixLevels(vocals = 0.15f, beats = 0.45f, instruments = 1f)),
    CUSTOM("Custom", PrismMixLevels(vocals = 1f, beats = 1f, instruments = 1f))
}

data class PrismUiState(
    val vocals: Float = 1f,
    val beats: Float = 1f,
    val instruments: Float = 1f,
    val isPrismEnabled: Boolean = false,
    val preset: PrismPreset = PrismPreset.BALANCED,
    val spectralAnalysis: PrismSpectralAnalysis = PrismSpectralAnalysis.FFT,
    val spectralBands: List<Float> = List(6) { 0f },
    val observedAnalysisMode: PrismSpectralAnalysis = PrismSpectralAnalysis.FFT,
    val analysisCostMicros: Float = 0f
)

@HiltViewModel
@OptIn(FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PrismViewModel @Inject constructor(
    private val adaptiveDspEngine: AdaptiveDspEngine,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private data class PrismControlSnapshot(
        val vocals: Float,
        val beats: Float,
        val instruments: Float,
        val isEnabled: Boolean,
        val spectralAnalysis: PrismSpectralAnalysis
    )

    private val _uiState = MutableStateFlow(PrismUiState())
    val uiState: StateFlow<PrismUiState> = _uiState.asStateFlow()

    private var hasHydrated = false

    init {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            val hydrated = PrismUiState(
                vocals = settings.prismVocals,
                beats = settings.prismBeats,
                instruments = settings.prismInstruments,
                isPrismEnabled = settings.prismEnabled,
                preset = resolvePreset(settings.prismVocals, settings.prismBeats, settings.prismInstruments),
                spectralAnalysis = settings.prismSpectralAnalysis,
                spectralBands = _uiState.value.spectralBands,
                observedAnalysisMode = _uiState.value.observedAnalysisMode,
                analysisCostMicros = _uiState.value.analysisCostMicros
            )
            _uiState.value = hydrated
            applyToEngine(hydrated)
            hasHydrated = true
        }

        viewModelScope.launch {
            uiState
                .map { state ->
                    PrismControlSnapshot(
                        vocals = state.vocals.coerceIn(0f, 1f),
                        beats = state.beats.coerceIn(0f, 1f),
                        instruments = state.instruments.coerceIn(0f, 1f),
                        isEnabled = state.isPrismEnabled,
                        spectralAnalysis = state.spectralAnalysis
                    )
                }
                .distinctUntilChanged()
                .debounce(16)
                .collect { state ->
                    if (!hasHydrated) return@collect

                    applyToEngine(
                        _uiState.value.copy(
                            vocals = state.vocals,
                            beats = state.beats,
                            instruments = state.instruments,
                            isPrismEnabled = state.isEnabled,
                            spectralAnalysis = state.spectralAnalysis
                        )
                    )
                    settingsDataStore.setPrismEnabled(state.isEnabled)
                    settingsDataStore.setPrismMix(state.vocals, state.beats, state.instruments)
                    settingsDataStore.setPrismSpectralAnalysis(state.spectralAnalysis)
                }
        }

        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .drop(1)
                .map { settings ->
                    PrismUiState(
                        vocals = settings.prismVocals,
                        beats = settings.prismBeats,
                        instruments = settings.prismInstruments,
                        isPrismEnabled = settings.prismEnabled,
                        preset = resolvePreset(settings.prismVocals, settings.prismBeats, settings.prismInstruments),
                        spectralAnalysis = settings.prismSpectralAnalysis,
                        spectralBands = _uiState.value.spectralBands,
                        observedAnalysisMode = _uiState.value.observedAnalysisMode,
                        analysisCostMicros = _uiState.value.analysisCostMicros
                    )
                }
                .collect { synced ->
                    if (synced != _uiState.value) {
                        _uiState.value = synced
                    }
                }
        }

        viewModelScope.launch {
            adaptiveDspEngine.audioReactiveFrame.collect { frame ->
                val bands = if (frame.spectralBands.size == 6) {
                    frame.spectralBands.map { it.coerceIn(0f, 1f) }
                } else {
                    List(6) { 0f }
                }
                val observedMode = when (frame.analysisMode) {
                    SpectralAnalysisMode.BANDPASS -> PrismSpectralAnalysis.BANDPASS
                    SpectralAnalysisMode.FFT -> PrismSpectralAnalysis.FFT
                }
                _uiState.value = _uiState.value.copy(
                    spectralBands = bands,
                    observedAnalysisMode = observedMode,
                    analysisCostMicros = frame.analysisCostMicros.coerceAtLeast(0f)
                )
            }
        }
    }

    fun updateVocals(value: Float) {
        val vocals = value.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(vocals = vocals, preset = PrismPreset.CUSTOM)
    }

    fun updateBeats(value: Float) {
        val beats = value.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(beats = beats, preset = PrismPreset.CUSTOM)
    }

    fun updateInstruments(value: Float) {
        val instruments = value.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(instruments = instruments, preset = PrismPreset.CUSTOM)
    }

    fun applyPreset(preset: PrismPreset) {
        if (preset == PrismPreset.CUSTOM) return
        _uiState.value = _uiState.value.copy(
            vocals = preset.mix.vocals,
            beats = preset.mix.beats,
            instruments = preset.mix.instruments,
            isPrismEnabled = true,
            preset = preset
        )
    }

    fun resetMixToBalanced() {
        applyPreset(PrismPreset.BALANCED)
    }

    fun setPrismEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isPrismEnabled = enabled)
    }

    fun setSpectralAnalysis(mode: PrismSpectralAnalysis) {
        _uiState.value = _uiState.value.copy(spectralAnalysis = mode)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun disablePrismAndReset() {
        // Preserve user mix and preset; only disable processing when requested.
        _uiState.value = _uiState.value.copy(isPrismEnabled = false)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCleared() {
        adaptiveDspEngine.updatePrismMix(_uiState.value.vocals, _uiState.value.beats, _uiState.value.instruments)
        adaptiveDspEngine.setPrismMode(PrismMode.BYPASS)
        super.onCleared()
    }

    private fun resolvePreset(vocals: Float, beats: Float, instruments: Float): PrismPreset {
        val tolerance = 0.025f
        return PrismPreset.entries.firstOrNull { preset ->
            preset != PrismPreset.CUSTOM &&
                abs(preset.mix.vocals - vocals) <= tolerance &&
                abs(preset.mix.beats - beats) <= tolerance &&
                abs(preset.mix.instruments - instruments) <= tolerance
        } ?: PrismPreset.CUSTOM
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyToEngine(state: PrismUiState) {
        adaptiveDspEngine.updatePrismMix(state.vocals, state.beats, state.instruments)
        adaptiveDspEngine.setPrismMode(if (state.isPrismEnabled) PrismMode.ISOLATION else PrismMode.BYPASS)
        val analysisMode = when (state.spectralAnalysis) {
            PrismSpectralAnalysis.BANDPASS -> SpectralAnalysisMode.BANDPASS
            PrismSpectralAnalysis.FFT -> SpectralAnalysisMode.FFT
        }
        adaptiveDspEngine.setSpectralAnalysisMode(analysisMode)
    }
}


