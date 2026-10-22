package com.tigerplayer.ui.player

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.engine.SleepTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Scoped ViewModel for playback-focused surfaces (FullPlayerScreen, MiniPlayer, PipVisualizerSurface).
 * Delegates state observation and control directly to [PlaybackStateManager].
 */
@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    val playbackStateManager: PlaybackStateManager
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackStateManager.playbackState
    val currentPosition: StateFlow<Long> = playbackStateManager.currentPosition
    val isPlaying: StateFlow<Boolean> = playbackStateManager.isPlaying
    val currentTrack: StateFlow<AudioTrack?> = playbackStateManager.currentTrack
    val queue: StateFlow<List<AudioTrack>> = playbackStateManager.queue
    val currentQueueIndex: StateFlow<Int> = playbackStateManager.currentQueueIndex
    val trackColor: StateFlow<Color> = playbackStateManager.trackColor
    val sleepTimerState: StateFlow<SleepTimerState> = playbackStateManager.sleepTimerState

    fun togglePlayPause() = playbackStateManager.togglePlayPause()

    fun playTrack(track: AudioTrack, availableTracks: List<AudioTrack> = emptyList()) =
        playbackStateManager.playTrack(track, availableTracks)

    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int) =
        playbackStateManager.setPlaylistAndPlay(tracks, startIndex)

    fun playAll(tracks: List<AudioTrack>) = playbackStateManager.playAll(tracks)

    fun skipToNext() = playbackStateManager.skipToNext()

    fun skipToPrevious() = playbackStateManager.skipToPrevious()

    fun seekTo(positionMs: Long) = playbackStateManager.seekTo(positionMs)

    fun toggleShuffle() = playbackStateManager.toggleShuffle()

    fun toggleRepeat() = playbackStateManager.toggleRepeat()

    fun setCoverScreenVolume(volume: Float) = playbackStateManager.setCoverScreenVolume(volume)

    fun adjustCoverScreenVolume(delta: Float) = playbackStateManager.adjustCoverScreenVolume(delta)

    fun setCoverScreenEqPreset(preset: CoverScreenEqPreset) = playbackStateManager.setCoverScreenEqPreset(preset)

    fun setPlaybackSpeed(speed: Float) = playbackStateManager.setPlaybackSpeed(speed)

    fun increasePlaybackSpeed() = playbackStateManager.increasePlaybackSpeed()

    fun decreasePlaybackSpeed() = playbackStateManager.decreasePlaybackSpeed()

    fun setPitch(pitch: Float) = playbackStateManager.setPitch(pitch)

    fun increasePitch() = playbackStateManager.increasePitch()

    fun decreasePitch() = playbackStateManager.decreasePitch()

    fun addToQueue(track: AudioTrack) = playbackStateManager.addToQueue(track)

    fun addNextToQueue(track: AudioTrack) = playbackStateManager.addNextToQueue(track)

    fun removeFromQueue(index: Int) = playbackStateManager.removeFromQueue(index)

    fun clearQueue() = playbackStateManager.clearQueue()

    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackStateManager.moveQueueItem(fromIndex, toIndex)

    fun playQueueItem(index: Int) = playbackStateManager.playQueueItem(index)

    fun setMainViewState(state: MainViewState) = playbackStateManager.setMainViewState(state)

    fun updateTrackColor(color: Color) = playbackStateManager.updateTrackColor(color)

    fun toggleVisualMode(isCoverOptimized: Boolean = false) =
        playbackStateManager.toggleVisualMode(isCoverOptimized)

    fun onFullPlayerOpened(isCoverOptimized: Boolean = false) =
        playbackStateManager.onFullPlayerOpened(isCoverOptimized)

    fun setFullPlayerActive(active: Boolean) = playbackStateManager.setFullPlayerActive(active)

    fun refreshBluetoothRouteState() = playbackStateManager.refreshBluetoothRouteState()

    fun setSleepTimerDuration(minutes: Int) = playbackEngineSetDuration(minutes)
    private fun playbackEngineSetDuration(minutes: Int) = playbackStateManager.setSleepTimerDuration(minutes)

    fun setSleepTimerEndOfTrack() = playbackStateManager.setSleepTimerEndOfTrack()

    fun setSleepTimerEndOfQueue() = playbackStateManager.setSleepTimerEndOfQueue()

    fun cancelSleepTimer() = playbackStateManager.cancelSleepTimer()
}
