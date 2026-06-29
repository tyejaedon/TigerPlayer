package com.example.tigerplayer.ui.prism

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.tigerplayer.engine.AdaptiveDspEngine
import com.example.tigerplayer.engine.PrismMixLevels
import com.example.tigerplayer.engine.PrismMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PrismUiState(
    val vocals: Float = 1f,
    val beats: Float = 1f,
    val instruments: Float = 1f,
    val isPrismEnabled: Boolean = true
)

@HiltViewModel
@OptIn(FlowPreview::class)
class PrismViewModel @androidx.annotation.OptIn(UnstableApi::class)
@Inject constructor(
    private val adaptiveDspEngine: AdaptiveDspEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrismUiState())
    val uiState: StateFlow<PrismUiState> = _uiState.asStateFlow()

    init {
        adaptiveDspEngine.setPrismMode(PrismMode.ISOLATION)
        adaptiveDspEngine.updatePrismMix(1f, 1f, 1f)

        viewModelScope.launch {
            uiState
                .map { state ->
                    PrismMixLevels(
                        vocals = state.vocals,
                        beats = state.beats,
                        instruments = state.instruments
                    )
                }
                .distinctUntilChanged()
                .debounce(12)
                .collect { mix ->
                    adaptiveDspEngine.updatePrismMix(
                        vocals = mix.vocals,
                        beats = mix.beats,
                        instruments = mix.instruments
                    )
                }
        }
    }

    fun updateVocals(value: Float) {
        _uiState.value = _uiState.value.copy(vocals = value.coerceIn(0f, 1f))
    }

    fun updateBeats(value: Float) {
        _uiState.value = _uiState.value.copy(beats = value.coerceIn(0f, 1f))
    }

    fun updateInstruments(value: Float) {
        _uiState.value = _uiState.value.copy(instruments = value.coerceIn(0f, 1f))
    }

    fun setPrismEnabled(enabled: Boolean) {
        if (enabled) {
            val state = _uiState.value
            adaptiveDspEngine.setPrismMode(PrismMode.ISOLATION)
            adaptiveDspEngine.updatePrismMix(state.vocals, state.beats, state.instruments)
            _uiState.value = state.copy(isPrismEnabled = true)
        } else {
            adaptiveDspEngine.setPrismMode(PrismMode.BYPASS)
            _uiState.value = _uiState.value.copy(isPrismEnabled = false)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun disablePrismAndReset() {
        adaptiveDspEngine.updatePrismMix(1f, 1f, 1f)
        adaptiveDspEngine.setPrismMode(PrismMode.BYPASS)
        _uiState.value = _uiState.value.copy(isPrismEnabled = false)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCleared() {
        adaptiveDspEngine.updatePrismMix(1f, 1f, 1f)
        adaptiveDspEngine.setPrismMode(PrismMode.BYPASS)
        super.onCleared()
    }
}


