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
import com.example.tigerplayer.data.model.AudioTrack
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import android.net.Uri
import androidx.core.net.toUri

@Singleton
class MediaControllerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null

    // --- 1. REACTIVE UI STATES (The Medallion's Hum) ---
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

    // Coroutine tools for the "Pulse" (Ticker)
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

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

                // If we connect while music is already playing, start the pulse
                if (controller.isPlaying) startPositionTicker()

            } catch (e: Exception) {
                Log.e("MediaManager", "Failed to bind MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.update { isPlaying }
                if (isPlaying) startPositionTicker() else positionJob?.cancel()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleModeEnabled.update { shuffleModeEnabled }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.update { repeatMode }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { newId ->
                    _currentMediaId.update { newId }
                }
                _currentPosition.update { 0L }
            }
        })
    }

    // --- 2. THE PULSE (Seek Bar Ticker) ---
    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = managerScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _currentPosition.update { controller.currentPosition }
                }
                delay(500) // 500ms is the sweet spot for S22 performance
            }
        }
    }

    // --- 3. CORE PLAYBACK RITUALS ---

    fun playTrack(track: AudioTrack) {
        val controller = mediaController ?: return
        val mediaItem = createMediaItem(track)
        controller.setMediaItem(mediaItem)
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

    private fun createMediaItem(track: AudioTrack): MediaItem {
        // If your compiler expected a String, we drop the Uri.parse() entirely.
        val trackUri = track.uri        // If it's complaining, it means your specific version has an overload or your track.uri is already the expected type.

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(trackUri) // Set it here
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(trackUri) // AND set it here for the Service callback to find
                    .build()
            )
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

    // --- 4. COMMAND LOGIC ---

    fun pause() = mediaController?.pause()
    fun resume() = mediaController?.play()
    fun seekTo(position: Long) = mediaController?.seekTo(position)
    fun skipToNext() = mediaController?.seekToNextMediaItem()
    fun skipToPrevious() = mediaController?.seekToPreviousMediaItem()

    fun toggleShuffleMode() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    // Add this inside the MediaControllerManager class
    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L

    fun release() {
        positionJob?.cancel()
        managerScope.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}