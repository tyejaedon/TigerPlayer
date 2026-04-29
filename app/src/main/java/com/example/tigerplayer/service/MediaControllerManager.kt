package com.example.tigerplayer.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresExtension
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.AudioRepository
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControllerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackPrefs: PlaybackPrefs,
    private val audioRepository: AudioRepository
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null

    // =========================
    // 🔁 UI STATE FLOWS
    // =========================
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _currentMediaId = MutableStateFlow("")
    val currentMediaId: StateFlow<String> = _currentMediaId

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _mediaControllerState = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mediaControllerState: SharedFlow<Unit> = _mediaControllerState

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null
    private var playerListener: Player.Listener? = null

    init {
        initializeController()
    }

        @OptIn(UnstableApi::class)
    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioPlayerService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken)
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()

        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller

                _isPlaying.value = controller.isPlaying
                _shuffleModeEnabled.value = controller.shuffleModeEnabled
                _repeatMode.value = controller.repeatMode
                _currentMediaId.value = controller.currentMediaItem?.mediaId ?: ""

                setupPlayerListener()
                restorePlaybackState(controller)

                if (controller.isPlaying) startPositionTicker()

            } catch (e: Exception) {
                Log.e("MediaManager", "Controller init failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListener() {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startPositionTicker() else positionJob?.cancel()
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                _shuffleModeEnabled.value = enabled
                managerScope.launch { playbackPrefs.saveShuffleMode(enabled) }
                _mediaControllerState.tryEmit(Unit)
            }

            override fun onRepeatModeChanged(mode: Int) {
                _repeatMode.value = mode
                managerScope.launch { playbackPrefs.saveRepeatMode(mode) }
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                _currentMediaId.value = item?.mediaId ?: ""
                _currentPosition.value = 0L
                saveCurrentState()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                _mediaControllerState.tryEmit(Unit)
            }
        }

        playerListener = listener
        mediaController?.addListener(listener)
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = managerScope.launch {
            var tick = 0
            while (isActive) {
                mediaController?.let {
                    val pos = it.currentPosition
                    _currentPosition.value = pos
                    if (++tick % 10 == 0) playbackPrefs.savePosition(pos)
                }
                delay(500)
            }
        }
    }

    private fun getCurrentQueue(): List<MediaItem> {
        val controller = mediaController ?: return emptyList()
        return List(controller.mediaItemCount) { controller.getMediaItemAt(it) }
    }

    fun createMediaItem(track: AudioTrack): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkUri)
                    .build()
            )
            .build()
    }

    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int = 0) {
        managerScope.launch {
            val controller = mediaController ?: return@launch
            val mediaItems = tracks.map { createMediaItem(it) }
            val ids = tracks.map { it.id }

            controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            controller.prepare()
            controller.play()

            playbackPrefs.savePlaybackState(
                trackId = tracks.getOrNull(startIndex)?.id,
                position = 0L,
                queueIds = ids,
                originalQueueIds = ids
            )
        }
    }

    fun removeFromQueue(trackId: String) {
        managerScope.launch {
            val controller = mediaController ?: return@launch
            var targetIndex = -1
            for (i in 0 until controller.mediaItemCount) {
                if (controller.getMediaItemAt(i).mediaId == trackId) {
                    targetIndex = i
                    break
                }
            }

            if (targetIndex != -1) {
                controller.removeMediaItem(targetIndex)
                saveCurrentState()
                _mediaControllerState.tryEmit(Unit)
            }
        }
    }

    fun addNextToQueue(track: AudioTrack) {
        managerScope.launch {
            val controller = mediaController ?: return@launch
            val newItem = createMediaItem(track)

            if (controller.mediaItemCount == 0) {
                controller.setMediaItem(newItem)
                controller.prepare()
                controller.play()
            } else {
                val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
                controller.addMediaItem(insertIndex, newItem)
            }

            saveCurrentState()
            _mediaControllerState.tryEmit(Unit)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        managerScope.launch {
            val controller = mediaController ?: return@launch
            if (fromIndex in 0 until controller.mediaItemCount && toIndex in 0 until controller.mediaItemCount) {
                controller.moveMediaItem(fromIndex, toIndex)
                saveCurrentState()
                _mediaControllerState.tryEmit(Unit)
            }
        }
    }

    private fun saveCurrentState() {
        managerScope.launch {
            val controller = mediaController ?: return@launch
            if (controller.mediaItemCount == 0) return@launch
            val queueIds = getCurrentQueue().map { it.mediaId }
            playbackPrefs.savePlaybackState(
                controller.currentMediaItem?.mediaId,
                controller.currentPosition,
                queueIds
            )
        }
    }

        private fun restorePlaybackState(controller: MediaController) {
        managerScope.launch {
            val lastId = playbackPrefs.lastTrackId.firstOrNull()
            val lastPos = playbackPrefs.lastPosition.firstOrNull() ?: 0L
            val queueIds = playbackPrefs.lastQueueIds.firstOrNull() ?: emptyList()
            val savedShuffle = playbackPrefs.shuffleMode.firstOrNull() ?: false
            val savedRepeat = playbackPrefs.repeatMode.firstOrNull() ?: Player.REPEAT_MODE_OFF

            if (queueIds.isEmpty() || lastId == null) return@launch

            val allTracks = audioRepository.getLocalTracks().firstOrNull() ?: emptyList()
            val restored = queueIds.mapNotNull { id -> allTracks.find { it.id == id } }

            if (restored.isEmpty()) return@launch

            val startIndex = restored.indexOfFirst { it.id == lastId }.let { if (it == -1) 0 else it }

            controller.setMediaItems(restored.map { createMediaItem(it) }, startIndex, lastPos)
            controller.shuffleModeEnabled = savedShuffle
            controller.repeatMode = savedRepeat
            controller.prepare()

            _mediaControllerState.tryEmit(Unit)
        }
    }

        fun toggleShuffleMode() {
        managerScope.launch {
            val controller = mediaController ?: return@launch
            val enableShuffle = !controller.shuffleModeEnabled
            val currentItem = controller.currentMediaItem ?: return@launch
            val currentPosition = controller.currentPosition
            val currentQueue = getCurrentQueue()
            val currentIds = currentQueue.map { it.mediaId }
            val originalIdsStored = playbackPrefs.originalQueueIds.firstOrNull() ?: emptyList()
            val allTracks = audioRepository.getLocalTracks().firstOrNull() ?: emptyList()

            if (enableShuffle) {
                if (originalIdsStored.isEmpty() || originalIdsStored != currentIds) {
                    playbackPrefs.savePlaybackState(currentItem.mediaId, currentPosition, currentIds, currentIds)
                }

                val currentIdx = controller.currentMediaItemIndex
                if (controller.mediaItemCount > currentIdx + 1) controller.removeMediaItems(currentIdx + 1, controller.mediaItemCount)
                if (currentIdx > 0) controller.removeMediaItems(0, currentIdx)

                val restShuffled = currentQueue.filter { it.mediaId != currentItem.mediaId }.shuffled()
                controller.addMediaItems(1, restShuffled)

                playbackPrefs.savePlaybackState(currentItem.mediaId, currentPosition, listOf(currentItem.mediaId) + restShuffled.map { it.mediaId })
            } else {
                if (originalIdsStored.isEmpty()) return@launch
                val restored = originalIdsStored.mapNotNull { id -> allTracks.find { it.id == id } }.map { createMediaItem(it) }
                if (restored.isEmpty()) return@launch

                val targetIdx = restored.indexOfFirst { it.mediaId == currentItem.mediaId }.let { if (it == -1) 0 else it }
                val currentIdx = controller.currentMediaItemIndex

                if (controller.mediaItemCount > currentIdx + 1) controller.removeMediaItems(currentIdx + 1, controller.mediaItemCount)
                if (currentIdx > 0) controller.removeMediaItems(0, currentIdx)

                val itemsBefore = restored.subList(0, targetIdx)
                if (itemsBefore.isNotEmpty()) controller.addMediaItems(0, itemsBefore)

                val itemsAfter = restored.subList(targetIdx + 1, restored.size)
                if (itemsAfter.isNotEmpty()) controller.addMediaItems(targetIdx + 1, itemsAfter)

                playbackPrefs.savePlaybackState(currentItem.mediaId, currentPosition, originalIdsStored)
            }

            controller.shuffleModeEnabled = enableShuffle
            _mediaControllerState.tryEmit(Unit)
        }
    }

    fun toggleRepeatMode() {
        managerScope.launch {
            val controller = mediaController ?: return@launch
            controller.repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // =========================
    // ▶ THE BULLETPROOF RESUME
    // =========================
    fun resume(fallbackTrack: AudioTrack? = null) = managerScope.launch {
        val controller = mediaController ?: return@launch

        // 🔥 THE FIX: If the system completely cleared the queue, instantly rebuild it
        if (controller.mediaItemCount == 0 && fallbackTrack != null) {
            controller.setMediaItem(createMediaItem(fallbackTrack))
            controller.prepare()
        }
        // 🔥 THE FIX: Wakes up the player if it finished playing or errored out
        else if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
            controller.prepare()
        }

        controller.play()
    }

    fun pause() = managerScope.launch { mediaController?.pause() }
    fun seekTo(pos: Long) = managerScope.launch { mediaController?.seekTo(pos) }
    fun skipToNext() = managerScope.launch { mediaController?.seekToNext() }
    fun skipToPrevious() = managerScope.launch { mediaController?.seekToPrevious() }

    fun release() {
        saveCurrentState()
        positionJob?.cancel()
        playerListener?.let { mediaController?.removeListener(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        managerScope.cancel()
    }
}