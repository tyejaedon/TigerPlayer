package com.example.tigerplayer.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.service.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QueueUiState(
	val currentTrack: AudioTrack? = null,
	val upcomingTracks: List<AudioTrack> = emptyList(),
	val fullQueue: List<AudioTrack> = emptyList(),
	val currentIndex: Int = -1,
	val isPlaying: Boolean = false,
	val isShuffleEnabled: Boolean = false
)

@HiltViewModel
class QueueViewModel @Inject constructor(
	private val mediaControllerManager: MediaControllerManager
) : ViewModel() {

	private val _uiState = MutableStateFlow(QueueUiState())
	val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

	init {
		observeQueue()
	}

	private fun observeQueue() {
		viewModelScope.launch {
			combine(
				mediaControllerManager.getQueueFlow(),
				mediaControllerManager.currentMediaItemIndex,
				mediaControllerManager.isPlaying,
				mediaControllerManager.shuffleModeEnabled
			) { queue, currentIndexSignal, isPlaying, isShuffleEnabled ->
				val controller = mediaControllerManager.mediaController

				val currentIndex = when {
					queue.isEmpty() -> -1
					currentIndexSignal in queue.indices -> currentIndexSignal
					else -> controller?.currentMediaItemIndex?.coerceIn(-1, queue.lastIndex) ?: -1
				}
				val currentTrack = queue.getOrNull(currentIndex)
				val upcoming = mediaControllerManager.getUpcomingTracksPreview(isShuffleEnabled)

				QueueUiState(
					currentTrack = currentTrack,
					upcomingTracks = upcoming,
					fullQueue = queue,
					currentIndex = currentIndex,
					isPlaying = isPlaying,
					isShuffleEnabled = isShuffleEnabled
				)
			}.collect { _uiState.value = it }
		}
	}

	fun playTrackAt(index: Int) {
		mediaControllerManager.playQueueItem(index)
	}

	fun playNext(track: AudioTrack) {
		mediaControllerManager.playNext(track)
	}

	fun addToQueue(track: AudioTrack) {
		mediaControllerManager.addToQueue(track)
	}

	fun moveUpcomingItem(fromIndex: Int, toIndex: Int) {
		val state = _uiState.value
		if (state.isShuffleEnabled) return
		if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0) return

		val anchor = (state.currentIndex + 1).coerceAtLeast(0)
		val absoluteFrom = anchor + fromIndex
		val absoluteTo = anchor + toIndex
		if (absoluteFrom >= state.fullQueue.size || absoluteTo >= state.fullQueue.size) return

		mediaControllerManager.moveQueueItem(absoluteFrom, absoluteTo)
	}
}

