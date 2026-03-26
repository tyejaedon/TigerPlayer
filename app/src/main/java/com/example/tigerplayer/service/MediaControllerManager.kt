package com.example.tigerplayer.service

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
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

    // --- 1. REACTIVE UI STATES ---
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentMediaId = MutableStateFlow("")
    val currentMediaId: StateFlow<String> = _currentMediaId.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Used to signal the ViewModel that the queue/timeline has shifted
    private val _mediaControllerState = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mediaControllerState: SharedFlow<Unit> = _mediaControllerState.asSharedFlow()

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

    // Player listener reference so we can remove it during teardown
    private var playerListener: Player.Listener? = null

    init {
        initializeController()
    }

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

                // Initial State Sync
                _isPlaying.update { controller.isPlaying }
                _shuffleModeEnabled.update { controller.shuffleModeEnabled }
                _repeatMode.update { controller.repeatMode }
                _currentMediaId.update { controller.currentMediaItem?.mediaId ?: "" }

                setupPlayerListener()
                restorePlaybackState(controller)

                if (controller.isPlaying) startPositionTicker()

            } catch (e: Exception) {
                Log.e("MediaManager", "Failed to bind MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.update { isPlaying }
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    positionJob?.cancel()
                    saveCurrentState()
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleModeEnabled.update { shuffleModeEnabled }
                managerScope.launch { playbackPrefs.saveShuffleMode(shuffleModeEnabled) }
                _mediaControllerState.tryEmit(Unit) // Queue order visually changes
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.update { repeatMode }
                managerScope.launch { playbackPrefs.saveRepeatMode(repeatMode) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentMediaId.update { mediaItem?.mediaId ?: "" }
                _currentPosition.update { 0L }
                saveCurrentState()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                // THE FIX: Trigger the ViewModel to pull the fresh queue
                _mediaControllerState.tryEmit(Unit)
            }
        }

        playerListener = listener
        mediaController?.addListener(listener)
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = managerScope.launch {
            var tickCount = 0
            while (isActive) {
                mediaController?.let { controller ->
                    val pos = controller.currentPosition
                    _currentPosition.update { pos }

                    // Periodically save position (every 5 seconds)
                    if (++tickCount % 10 == 0) {
                        playbackPrefs.savePosition(pos)
                    }
                }
                delay(500)
            }
        }
    }

    private fun saveCurrentState() {
        val controller = mediaController ?: return
        val currentId = controller.currentMediaItem?.mediaId ?: return
        val currentPos = controller.currentPosition

        // Ensure we don't save an empty state if the player just crashed
        if (controller.mediaItemCount == 0) return

        val queueIds = mutableListOf<String>()
        for (i in 0 until controller.mediaItemCount) {
            queueIds.add(controller.getMediaItemAt(i).mediaId)
        }

        managerScope.launch {
            playbackPrefs.savePlaybackState(currentId, currentPos, queueIds)
        }
    }

    private fun restorePlaybackState(controller: MediaController) {
        managerScope.launch {
            val lastId = playbackPrefs.lastTrackId.first()
            val lastPos = playbackPrefs.lastPosition.first()
            val queueIds = playbackPrefs.lastQueueIds.first()
            val savedShuffle = playbackPrefs.shuffleMode.first()
            val savedRepeat = playbackPrefs.repeatMode.first()

            if (queueIds.isNotEmpty() && lastId != null) {
                val allTracks = audioRepository.getLocalTracks().first()
                val tracksToRestore = queueIds.mapNotNull { id -> allTracks.find { it.id == id } }

                if (tracksToRestore.isNotEmpty()) {
                    val startIndex = tracksToRestore.indexOfFirst { it.id == lastId }.coerceAtLeast(0)
                    val mediaItems = tracksToRestore.map { createMediaItem(it) }

                    controller.setMediaItems(mediaItems, startIndex, lastPos)
                    controller.shuffleModeEnabled = savedShuffle
                    controller.repeatMode = savedRepeat
                    controller.prepare()

                    _mediaControllerState.tryEmit(Unit)
                }
            }
        }
    }

    // --- 3. CORE PLAYBACK RITUALS ---

    fun playTrack(track: AudioTrack) {
        val controller = mediaController ?: return
        controller.setMediaItem(createMediaItem(track))
        controller.prepare()
        controller.play()
    }

    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int = 0) {
        val controller = mediaController ?: return
        val mediaItems = tracks.map { createMediaItem(it) }
        controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        controller.prepare()
        controller.play()
    }

    fun addNextToQueue(track: AudioTrack) {
        val controller = mediaController ?: return
        val mediaItem = createMediaItem(track)

        if (controller.mediaItemCount == 0) {
            playTrack(track)
        } else {
            val insertIndex = controller.currentMediaItemIndex + 1
            controller.addMediaItem(insertIndex, mediaItem)
        }
    }

    // THE MISSING RITUAL: Queue Modification
    fun removeFromQueue(trackId: String) {
        val controller = mediaController ?: return
        for (i in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(i).mediaId == trackId) {
                controller.removeMediaItem(i)
                break // Remove only the first instance if there are duplicates
            }
        }
    }

    // Clear the entire queue and stop playback
    fun clearQueue() {
        val controller = mediaController ?: return
        controller.clearMediaItems()
        controller.stop()
    }

    private fun createMediaItem(track: AudioTrack): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.uri)
            // THE FIX: Pass absolute metadata for the notification/lockscreen
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

    // --- 4. COMMANDS ---
    fun resume() = mediaController?.play()
    fun pause() = mediaController?.pause()
    fun seekTo(position: Long) = mediaController?.seekTo(position)
    fun skipToNext() = mediaController?.seekToNext()
    fun skipToPrevious() = mediaController?.seekToPrevious()

    fun toggleShuffleMode() {
        mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun release() {
        saveCurrentState()
        positionJob?.cancel()

        // Prevent memory leak by removing listener
        playerListener?.let { mediaController?.removeListener(it) }

        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        managerScope.cancel()
    }
}