package com.example.tigerplayer.ui.equalizer

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.engine.AcousticEnvironmentMode
import com.example.tigerplayer.engine.AdaptiveDspEngine
import com.example.tigerplayer.service.AudioPlayerService
import com.example.tigerplayer.service.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AcousticEnvironmentOption(
    val mode: AcousticEnvironmentMode,
    val title: String,
    val description: String
)

data class AcousticEnvironmentUiState(
    val selectedMode: AcousticEnvironmentMode = AcousticEnvironmentMode.STUDIO,
    val options: List<AcousticEnvironmentOption> = listOf(
        AcousticEnvironmentOption(
            mode = AcousticEnvironmentMode.STUDIO,
            title = "Studio",
            description = "Transparent output with no environmental coloration."
        ),
        AcousticEnvironmentOption(
            mode = AcousticEnvironmentMode.VINYL_WARMTH,
            title = "Vinyl Warmth",
            description = "Subtle harmonic saturation with a low analog-style noise floor."
        ),
        AcousticEnvironmentOption(
            mode = AcousticEnvironmentMode.CONCERT_HALL,
            title = "Concert Hall",
            description = "Lightweight Schroeder hall reflections with stereo spatial depth."
        )
    )
)

@HiltViewModel
@OptIn(UnstableApi::class)
class AcousticEnvironmentViewModel @Inject constructor(
    private val adaptiveDspEngine: AdaptiveDspEngine,
    private val playbackPrefs: PlaybackPrefs,
    private val mediaControllerManager: MediaControllerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AcousticEnvironmentUiState())
    val uiState: StateFlow<AcousticEnvironmentUiState> = _uiState.asStateFlow()

    val audioReactiveFrame = adaptiveDspEngine.audioReactiveFrame

    init {
        viewModelScope.launch {
            playbackPrefs.acousticEnvironmentMode.collect { savedName ->
                val mode = runCatching { AcousticEnvironmentMode.valueOf(savedName) }
                    .getOrDefault(AcousticEnvironmentMode.STUDIO)
                adaptiveDspEngine.setAcousticEnvironmentMode(mode)
                _uiState.update { it.copy(selectedMode = mode) }
            }
        }
    }

    fun selectMode(mode: AcousticEnvironmentMode) {
        if (_uiState.value.selectedMode == mode) return

        adaptiveDspEngine.setAcousticEnvironmentMode(mode)
        _uiState.update { it.copy(selectedMode = mode) }

        viewModelScope.launch {
            playbackPrefs.saveAcousticEnvironmentMode(mode.name)
        }

        mediaControllerManager.mediaController?.sendCustomCommand(
            SessionCommand(AudioPlayerService.ACTION_SET_ACOUSTIC_ENV, Bundle.EMPTY),
            Bundle().apply {
                putString(AudioPlayerService.EXTRA_ACOUSTIC_ENV_MODE, mode.name)
            }
        )
    }
}


