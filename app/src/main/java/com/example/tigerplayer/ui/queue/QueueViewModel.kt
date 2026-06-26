package com.example.tigerplayer.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.service.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class QueueUiState(
	val currentTrack: AudioTrack? = null,
	val upcomingTracks: List<AudioTrack> = emptyList(),
	val fullQueue: List<AudioTrack> = emptyList(),
	val currentIndex: Int = -1,
	val isPlaying: Boolean = false
)

@HiltViewModel
class QueueViewModel @Inject constructor(
	private val mediaControllerManager: MediaControllerManager,
	private val audioRepository: AudioRepository
) : ViewModel() {

	private val _uiState = MutableStateFlow(QueueUiState())
	val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

	init {
		observeQueue()
	}

	private fun observeQueue() {
		viewModelScope.launch {
			combine(
				mediaControllerManager.mediaControllerState.onStart { emit(Unit) },
				audioRepository.getLocalTracks(),
				mediaControllerManager.currentMediaId,
				mediaControllerManager.isPlaying
			) { _, libraryTracks, currentMediaId, isPlaying ->
				val trackMap = withContext(Dispatchers.Default) {
					libraryTracks.associateBy { it.id }
				}

				val controller = mediaControllerManager.mediaController
				val queue = if (controller == null) {
					emptyList()
				} else {
					(0 until controller.mediaItemCount).mapNotNull { index ->
						trackMap[controller.getMediaItemAt(index).mediaId]
					}
				}

				val currentIndex = queue.indexOfFirst { it.id == currentMediaId }
				val currentTrack = queue.getOrNull(currentIndex)
				val upcoming = if (currentIndex in queue.indices) queue.drop(currentIndex + 1) else queue

				QueueUiState(
					currentTrack = currentTrack,
					upcomingTracks = upcoming,
					fullQueue = queue,
					currentIndex = currentIndex,
					isPlaying = isPlaying
				)
			}.collect { _uiState.value = it }
		}
	}

	fun playTrack(track: AudioTrack) {
		val queue = _uiState.value.fullQueue
		val index = queue.indexOfFirst { it.id == track.id }
		val controller = mediaControllerManager.mediaController ?: return
		if (index >= 0) {
			controller.seekToDefaultPosition(index)
			controller.play()
		}
	}

	fun playNext(track: AudioTrack) {
		mediaControllerManager.playNext(track)
	}

	fun addToQueue(track: AudioTrack) {
		mediaControllerManager.addToQueue(track)
	}

	fun moveUpcomingItem(fromIndex: Int, toIndex: Int) {
		val state = _uiState.value
		if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0) return

		val anchor = (state.currentIndex + 1).coerceAtLeast(0)
		val absoluteFrom = anchor + fromIndex
		val absoluteTo = anchor + toIndex
		if (absoluteFrom >= state.fullQueue.size || absoluteTo >= state.fullQueue.size) return

		mediaControllerManager.moveQueueItem(absoluteFrom, absoluteTo)
	}
}

