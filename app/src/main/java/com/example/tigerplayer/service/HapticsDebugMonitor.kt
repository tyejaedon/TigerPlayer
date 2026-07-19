package com.example.tigerplayer.service

import com.example.tigerplayer.data.local.AudioReactiveHapticsProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HapticsDebugEvent {
    IDLE,
    STATUS,
    SKIP,
    FIRE
}

data class HapticsDebugState(
    val event: HapticsDebugEvent = HapticsDebugEvent.IDLE,
    val reason: String = "",
    val enabled: Boolean = false,
    val profile: AudioReactiveHapticsProfile = AudioReactiveHapticsProfile.BALANCED,
    val routeToSystemDecoderDsp: Boolean = false,
    val isBitPerfectMode: Boolean = false,
    val isPlaying: Boolean = false,
    val rawScore: Float = 0f,
    val smoothedScore: Float = 0f,
    val minScore: Float = 0f,
    val cooldownRemainingMs: Long = 0L,
    val bass: Float = 0f,
    val energy: Float = 0f,
    val flux: Float = 0f,
    val amplitude: Int = 0,
    val durationMs: Long = 0L,
    val pulseCount: Long = 0L,
    val updatedAtMs: Long = 0L
)

@Singleton
class HapticsDebugMonitor @Inject constructor() {
    private val _state = MutableStateFlow(HapticsDebugState())
    val state: StateFlow<HapticsDebugState> = _state.asStateFlow()

    fun update(transform: (HapticsDebugState) -> HapticsDebugState) {
        _state.update(transform)
    }
}

