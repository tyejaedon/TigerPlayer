package com.example.tigerplayer.ui.queue

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.service.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
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
				mediaControllerManager.mediaControllerState.onStart { emit(Unit) },
				mediaControllerManager.currentMediaItemIndex,
				mediaControllerManager.isPlaying,
				mediaControllerManager.shuffleModeEnabled
			) { _, currentIndexSignal, isPlaying, isShuffleEnabled ->
				val controller = mediaControllerManager.mediaController
				val queue = if (controller == null) {
					emptyList()
				} else {
					List(controller.mediaItemCount) { index ->
						fallbackTrackFromMediaItem(controller.getMediaItemAt(index))
					}
				}

				val currentIndex = when {
					controller == null -> -1
					currentIndexSignal in queue.indices -> currentIndexSignal
					else -> controller.currentMediaItemIndex.coerceIn(-1, queue.lastIndex)
				}
				val currentTrack = queue.getOrNull(currentIndex)
				val upcoming = if (isShuffleEnabled) {
					queue.filterIndexed { idx, _ -> idx != currentIndex }
				} else if (currentIndex in queue.indices) {
					queue.drop(currentIndex + 1)
				} else {
					queue
				}

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

	private fun fallbackTrackFromMediaItem(mediaItem: MediaItem): AudioTrack {
		val metadata = mediaItem.mediaMetadata
		val extras = metadata.extras
		val fallbackUri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY
		val mimeType = extras?.getString(MediaControllerManager.META_MIME_TYPE).orEmpty()

		return AudioTrack(
			id = mediaItem.mediaId,
			title = metadata.title?.toString() ?: "Unknown",
			artist = metadata.artist?.toString() ?: "Unknown Artist",
			album = metadata.albumTitle?.toString() ?: "Unknown Album",
			uri = fallbackUri,
			artworkUri = metadata.artworkUri ?: Uri.EMPTY,
			durationMs = extras?.getLong(MediaControllerManager.META_DURATION_MS, 0L) ?: 0L,
			mimeType = mimeType.ifBlank { "audio/unknown" },
			isLocal = extras?.getBoolean(MediaControllerManager.META_IS_LOCAL, false) ?: false,
			isRemote = extras?.getBoolean(MediaControllerManager.META_IS_REMOTE, fallbackUri != Uri.EMPTY)
				?: (fallbackUri != Uri.EMPTY),
			bitrate = extras?.getInt(MediaControllerManager.META_BITRATE, 0) ?: 0,
			sampleRate = extras?.getInt(MediaControllerManager.META_SAMPLE_RATE, 0) ?: 0,
			trackNumber = extras?.getInt(MediaControllerManager.META_TRACK_NUMBER, 0) ?: 0,
			serverPath = extras?.getString(MediaControllerManager.META_SERVER_PATH),
			year = extras?.getString(MediaControllerManager.META_YEAR),
			dateAdded = extras?.getLong(MediaControllerManager.META_DATE_ADDED, 0L) ?: 0L,
			isLiked = extras?.getBoolean(MediaControllerManager.META_IS_LIKED, false) ?: false,
			path = extras?.getString(MediaControllerManager.META_PATH)
		)
	}

	fun playTrackAt(index: Int) {
		val controller = mediaControllerManager.mediaController ?: return
		if (index in 0 until controller.mediaItemCount) {
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
		if (state.isShuffleEnabled) return
		if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0) return

		val anchor = (state.currentIndex + 1).coerceAtLeast(0)
		val absoluteFrom = anchor + fromIndex
		val absoluteTo = anchor + toIndex
		if (absoluteFrom >= state.fullQueue.size || absoluteTo >= state.fullQueue.size) return

		mediaControllerManager.moveQueueItem(absoluteFrom, absoluteTo)
	}
}

