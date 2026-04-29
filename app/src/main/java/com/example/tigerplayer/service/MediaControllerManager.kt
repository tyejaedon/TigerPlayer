package com.example.tigerplayer.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.AudioRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
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

    companion object {
        const val ACTION_SET_EQ = "ACTION_SET_EQ"
    }

    init {
        initializeController()
    }

    // =========================
    // 🎧 INITIALIZE CONTROLLER
    // =========================
    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioPlayerService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

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
        }, MoreExecutors.directExecutor())
    }

    // =========================
    // 🎧 PLAYER LISTENER
    // =========================
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

            override fun onTimelineChanged(
                timeline: androidx.media3.common.Timeline,
                reason: Int
            ) {
                _mediaControllerState.tryEmit(Unit)
            }
        }

        playerListener = listener
        mediaController?.addListener(listener)
    }

    // =========================
    // ⏱ POSITION TRACKER
    // =========================
    private fun startPositionTicker() {
        positionJob?.cancel()

        positionJob = managerScope.launch {
            var tick = 0

            while (isActive) {
                mediaController?.let {
                    val pos = it.currentPosition
                    _currentPosition.value = pos

                    if (++tick % 10 == 0) {
                        playbackPrefs.savePosition(pos)
                    }
                }
                delay(500)
            }
        }
    }

    // =========================
    // 🎵 QUEUE HELPERS
    // =========================
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

    // =========================
    // ▶ PLAYLIST
    // =========================
    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int = 0) {
        val controller = mediaController ?: return

        val mediaItems = tracks.map { createMediaItem(it) }
        val ids = tracks.map { it.id }

        controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        controller.prepare()
        controller.play()

        managerScope.launch {
            playbackPrefs.savePlaybackState(
                trackId = tracks.getOrNull(startIndex)?.id,
                position = 0L,
                queueIds = ids,
                originalQueueIds = ids
            )
        }
    }
    fun removeFromQueue(trackId: String) {
        val controller = mediaController ?: return

        val currentIndex = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem

        // =========================
        // 🎧 BUILD CURRENT QUEUE
        // =========================
        val queue = List(controller.mediaItemCount) { index ->
            controller.getMediaItemAt(index)
        }.toMutableList()

        // Find item to remove
        val removeIndex = queue.indexOfFirst { it.mediaId == trackId }
        if (removeIndex == -1) return

        // Remove item
        queue.removeAt(removeIndex)

        // =========================
        // ⚠️ HANDLE PLAYBACK SAFELY
        // =========================
        var newStartIndex = currentIndex

        when {
            // If removing item before current → shift index left
            removeIndex < currentIndex -> {
                newStartIndex = currentIndex - 1
            }

            // If removing currently playing item
            removeIndex == currentIndex -> {
                newStartIndex = currentIndex.coerceAtMost(queue.lastIndex)
            }

            // If removing after current → no change
            else -> {
                newStartIndex = currentIndex
            }
        }

        // Fix bounds
        if (queue.isEmpty()) {
            controller.stop()
            controller.clearMediaItems()
            return
        }

        newStartIndex = newStartIndex.coerceIn(0, queue.lastIndex)

        // =========================
        // 🎵 APPLY NEW QUEUE
        // =========================
        controller.setMediaItems(queue, newStartIndex, controller.currentPosition)
        controller.prepare()

        // =========================
        // 💾 PERSIST STATE
        // =========================
        managerScope.launch {
            playbackPrefs.savePlaybackState(
                trackId = controller.currentMediaItem?.mediaId,
                position = controller.currentPosition,
                queueIds = queue.map { it.mediaId }
            )
        }

        // =========================
        // 🔔 NOTIFY UI
        // =========================
        _mediaControllerState.tryEmit(Unit)
    }
    fun addNextToQueue(track: AudioTrack) {
        val controller = mediaController ?: return

        val newItem = createMediaItem(track)

        // =========================
        // 🎧 CURRENT STATE
        // =========================
        val currentIndex = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem

        // Build current queue snapshot
        val queue = List(controller.mediaItemCount) { index ->
            controller.getMediaItemAt(index)
        }.toMutableList()

        // =========================
        // ⚠️ HANDLE EMPTY QUEUE
        // =========================
        if (queue.isEmpty()) {
            controller.setMediaItem(newItem)
            controller.prepare()
            controller.play()

            managerScope.launch {
                playbackPrefs.savePlaybackState(
                    trackId = track.id,
                    position = 0L,
                    queueIds = listOf(track.id)
                )
            }

            _mediaControllerState.tryEmit(Unit)
            return
        }

        // =========================
        // ➕ INSERT NEXT TO CURRENT
        // =========================
        val insertIndex = (currentIndex + 1).coerceAtMost(queue.size)

        queue.add(insertIndex, newItem)

        // =========================
        // 🎯 PRESERVE CURRENT PLAYBACK POSITION
        // =========================
        val newStartIndex = queue.indexOf(currentItem)
            .let { if (it == -1) currentIndex else it }

        // =========================
        // 🎵 APPLY UPDATED QUEUE
        // =========================
        controller.setMediaItems(
            queue,
            newStartIndex,
            controller.currentPosition
        )
        controller.prepare()

        // =========================
        // 💾 PERSIST STATE
        // =========================
        managerScope.launch {
            playbackPrefs.savePlaybackState(
                trackId = controller.currentMediaItem?.mediaId,
                position = controller.currentPosition,
                queueIds = queue.map { it.mediaId }
            )
        }

        // =========================
        // 🔔 NOTIFY UI
        // =========================
        _mediaControllerState.tryEmit(Unit)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return

        // Get current queue from Media3
        val queue = List(controller.mediaItemCount) {
            controller.getMediaItemAt(it)
        }.toMutableList()

        // Safety checks
        if (fromIndex !in queue.indices || toIndex !in queue.indices) return

        // Preserve current playback position item
        val currentItem = controller.currentMediaItem

        // =========================
        // 🔀 REORDER LOGIC
        // =========================
        val movedItem = queue.removeAt(fromIndex)
        queue.add(toIndex, movedItem)

        // =========================
        // 🎧 APPLY TO MEDIA3
        // =========================
        controller.setMediaItems(queue, queue.indexOf(currentItem).coerceAtLeast(0), controller.currentPosition)
        controller.prepare()

        // =========================
        // 💾 PERSIST NEW ORDER
        // =========================
        managerScope.launch {
            playbackPrefs.savePlaybackState(
                trackId = controller.currentMediaItem?.mediaId,
                position = controller.currentPosition,
                queueIds = queue.map { it.mediaId }
            )
        }

        // =========================
        // 🔔 NOTIFY UI
        // =========================
        _mediaControllerState.tryEmit(Unit)
    }

    // =========================
    // 💾 SAVE STATE
    // =========================
    private fun saveCurrentState() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) return

        val queueIds = getCurrentQueue().map { it.mediaId }

        managerScope.launch {
            playbackPrefs.savePlaybackState(
                controller.currentMediaItem?.mediaId,
                controller.currentPosition,
                queueIds
            )
        }
    }

    // =========================
    // 🔄 RESTORE STATE
    // =========================
    private fun restorePlaybackState(controller: MediaController) {
        managerScope.launch {
            val lastId = playbackPrefs.lastTrackId.first()
            val lastPos = playbackPrefs.lastPosition.first()
            val queueIds = playbackPrefs.lastQueueIds.first()
            val savedShuffle = playbackPrefs.shuffleMode.first()
            val savedRepeat = playbackPrefs.repeatMode.first()

            if (queueIds.isEmpty() || lastId == null) return@launch

            val allTracks = audioRepository.getLocalTracks().first()

            val restored = queueIds.mapNotNull { id ->
                allTracks.find { it.id == id }
            }

            if (restored.isEmpty()) return@launch

            val startIndex = restored.indexOfFirst { it.id == lastId }
                .let { if (it == -1) 0 else it }

            controller.setMediaItems(
                restored.map { createMediaItem(it) },
                startIndex,
                lastPos
            )

            controller.shuffleModeEnabled = savedShuffle
            controller.repeatMode = savedRepeat
            controller.prepare()

            _mediaControllerState.tryEmit(Unit)
        }
    }

    // =========================
    // 🔀 SHUFFLE (FULL CONTROL)
    // =========================
    fun toggleShuffleMode() {
        val controller = mediaController ?: return
        val enableShuffle = !controller.shuffleModeEnabled

        val currentItem = controller.currentMediaItem
        val currentPosition = controller.currentPosition

        managerScope.launch {

            val currentQueue = getCurrentQueue()
            val currentIds = currentQueue.map { it.mediaId }

            val originalIdsStored = playbackPrefs.originalQueueIds.first()
            val allTracks = audioRepository.getLocalTracks().first()

            if (enableShuffle) {

                // Save original if not already saved
                if (originalIdsStored != currentIds) {
                    playbackPrefs.savePlaybackState(
                        trackId = currentItem?.mediaId,
                        position = currentPosition,
                        queueIds = currentIds,
                        originalQueueIds = currentIds
                    )
                }

                val shuffled = currentQueue.toMutableList().apply { shuffle() }

                currentItem?.let {
                    shuffled.remove(it)
                    shuffled.add(0, it)
                }

                controller.setMediaItems(shuffled, 0, currentPosition)
                controller.prepare()
                controller.play()

                playbackPrefs.savePlaybackState(
                    trackId = currentItem?.mediaId,
                    position = currentPosition,
                    queueIds = shuffled.map { it.mediaId }
                )

            } else {

                if (originalIdsStored.isEmpty()) return@launch

                val restored = originalIdsStored.mapNotNull { id ->
                    allTracks.find { it.id == id }
                }.map { createMediaItem(it) }

                if (restored.isEmpty()) return@launch

                val startIndex = restored.indexOfFirst {
                    it.mediaId == currentItem?.mediaId
                }.let { if (it == -1) 0 else it }

                controller.setMediaItems(restored, startIndex, currentPosition)
                controller.prepare()
                controller.play()

                playbackPrefs.savePlaybackState(
                    trackId = currentItem?.mediaId,
                    position = currentPosition,
                    queueIds = originalIdsStored
                )
            }

            controller.shuffleModeEnabled = enableShuffle
            _mediaControllerState.tryEmit(Unit)
        }
    }

    // =========================
    // 🔁 REPEAT
    // =========================
    fun toggleRepeatMode() {
        val controller = mediaController ?: return

        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // =========================
    // ▶ CONTROLS
    // =========================
    fun resume() = mediaController?.play()
    fun pause() = mediaController?.pause()
    fun seekTo(pos: Long) = mediaController?.seekTo(pos)
    fun skipToNext() = mediaController?.seekToNext()
    fun skipToPrevious() = mediaController?.seekToPrevious()

    // =========================
    // 🧹 CLEANUP
    // =========================
    fun release() {
        saveCurrentState()
        positionJob?.cancel()
        playerListener?.let { mediaController?.removeListener(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        managerScope.cancel()
    }
}