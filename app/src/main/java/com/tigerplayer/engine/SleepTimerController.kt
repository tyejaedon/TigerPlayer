package com.tigerplayer.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

enum class SleepTimerMode { OFF, DURATION, END_OF_TRACK, END_OF_QUEUE }

data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val remainingMs: Long = 0L,
    val fadeOutEnabled: Boolean = true
)

/**
 * Pure timer/state logic, deliberately with no Player reference so it is unit-testable
 * without a real MediaSession (see testing.instructions.md â€” prefer testing an engine class
 * directly; AudioPlayerService itself is hard to unit test).
 */
@Singleton
class SleepTimerController @Inject constructor() {

    private var tickJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state

    /** Fires exactly once when the duration elapses; caller pauses playback. */
    var onExpired: (() -> Unit)? = null

    fun startDuration(durationMs: Long, fadeOutEnabled: Boolean = true) {
        cancel()
        _state.value = SleepTimerState(SleepTimerMode.DURATION, durationMs, fadeOutEnabled)
        tickJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1_000L.milliseconds)
                remaining -= 1_000L
                _state.value = _state.value.copy(remainingMs = remaining.coerceAtLeast(0))
            }
            _state.value = SleepTimerState()
            onExpired?.invoke()
        }
    }

    /** Caller (MediaControllerManager) resolves "end of track/queue" against real player timing
     *  and calls [startDuration] with the computed remaining ms, then re-arms on transition if
     *  mode == END_OF_QUEUE. Keeping Player-awareness out of this class avoids coupling. */
    fun armEndOfTrack() {
        cancel()
        _state.value = SleepTimerState(mode = SleepTimerMode.END_OF_TRACK)
    }

    fun armEndOfQueue() {
        cancel()
        _state.value = SleepTimerState(mode = SleepTimerMode.END_OF_QUEUE)
    }

    fun cancel() {
        tickJob?.cancel()
        tickJob = null
        _state.value = SleepTimerState()
    }
}
