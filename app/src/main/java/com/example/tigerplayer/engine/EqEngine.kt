package com.example.tigerplayer.engine

import android.os.Bundle
import androidx.media3.session.SessionCommand
import com.example.tigerplayer.service.AudioPlayerService
import com.example.tigerplayer.service.AutoEqParser
import com.example.tigerplayer.service.PeqProfile
import com.example.tigerplayer.service.MediaControllerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class EqUiState(
    val isBitPerfect: Boolean = true,
    val availableProfiles: List<PeqProfile> = emptyList(),
    val selectedProfile: PeqProfile? = null
)

class EqEngine @Inject constructor(
    private val mediaControllerManager: MediaControllerManager
) {
    private val _uiState = MutableStateFlow(EqUiState())
    val uiState: StateFlow<EqUiState> = _uiState.asStateFlow()

    init {
        // We parse three distinct acoustic profiles from your dataset
        val rawPresets = listOf(
            "Reference Monitor (Flat)" to """
                Preamp: -2.6 dB
                Filter 1: ON PK Fc 125 Hz Gain 2.6 dB Q 0.8
                Filter 2: ON PK Fc 1400 Hz Gain -1.5 dB Q 2.0
                Filter 3: ON PK Fc 2680 Hz Gain -1.0 dB Q 4.0
                Filter 4: ON PK Fc 3457 Hz Gain -1.7 dB Q 3.0
                Filter 5: ON PK Fc 6050 Hz Gain -2.7 dB Q 6.0
                Filter 6: ON PK Fc 10750 Hz Gain -7.2 dB Q 4.0
            """.trimIndent(),

            "Cinematic Bass (Harman)" to """
                Preamp: -4.1 dB
                Filter 1: ON PK Fc 64 Hz Gain -1.8 dB Q 2.0
                Filter 2: ON LS Fc 105 Hz Gain 9.9 dB Q 0.71
                Filter 3: ON PK Fc 115 Hz Gain 4.3 dB Q 2.0
                Filter 4: ON LS Fc 150 Hz Gain -12.0 dB Q 0.71
                Filter 5: ON PK Fc 170 Hz Gain 6.5 dB Q 1.0
                Filter 6: ON PK Fc 400 Hz Gain -1.2 dB Q 1.0
                Filter 7: ON PK Fc 2350 Hz Gain -2.5 dB Q 3.0
                Filter 8: ON PK Fc 10500 Hz Gain -5.0 dB Q 2.0
                Filter 9: ON PK Fc 11000 Hz Gain 5.0 dB Q 0.5
            """.trimIndent(),

            "Vocal Clarity (Mid-Boost)" to """
                Preamp: -0.5 dB
                Filter 1: ON LS Fc 200 Hz Gain -5.5 dB Q 0.71
                Filter 2: ON PK Fc 440 Hz Gain 0.8 dB Q 2.0
                Filter 3: ON PK Fc 1250 Hz Gain 1.0 dB Q 1.4
                Filter 4: ON PK Fc 2500 Hz Gain -11.0 dB Q 1.2
                Filter 5: ON PK Fc 2700 Hz Gain -1.0 dB Q 6.0
                Filter 6: ON PK Fc 3450 Hz Gain 6.0 dB Q 1.0
                Filter 7: ON PK Fc 4650 Hz Gain -6.3 dB Q 3.5
                Filter 8: ON HS Fc 8500 Hz Gain -6.0 dB Q 0.71
            """.trimIndent()
        )

        val parsed = rawPresets.map { AutoEqParser.parse(it.second, it.first) }
        _uiState.update { it.copy(availableProfiles = parsed, selectedProfile = parsed.firstOrNull()) }
    }

    fun toggleBitPerfect() {
        val nextState = !_uiState.value.isBitPerfect
        _uiState.update { it.copy(isBitPerfect = nextState) }

        mediaControllerManager.mediaController?.sendCustomCommand(
            SessionCommand(AudioPlayerService.ACTION_TOGGLE_DSP, Bundle.EMPTY),
            Bundle.EMPTY
        )

        // If we turned DSP ON, make sure the currently selected profile is loaded into it
        if (!nextState && _uiState.value.selectedProfile != null) {
            loadProfile(_uiState.value.selectedProfile!!)
        }
    }

    fun loadProfile(profile: PeqProfile) {
        _uiState.update { it.copy(selectedProfile = profile, isBitPerfect = false) }

        val bundle = Bundle().apply {
            putString("peq_raw_text", profile.rawText)
            putString("peq_profile_name", profile.name)
        }

        mediaControllerManager.mediaController?.sendCustomCommand(
            SessionCommand(AudioPlayerService.ACTION_LOAD_PEQ, Bundle.EMPTY),
            bundle
        )
    }
}