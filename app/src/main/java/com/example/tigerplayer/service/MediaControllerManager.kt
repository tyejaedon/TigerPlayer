package com.example.tigerplayer.service

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
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
import com.example.tigerplayer.data.repository.LastFmRepository
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
    private val audioRepository: AudioRepository,
    private val lastFmRepository: LastFmRepository
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null

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
    private var autoQueueJob: Job? = null
    private var crossfadeMonitorJob: Job? = null
    private var crossfadeOutJob: Job? = null
    private var crossfadeInJob: Job? = null
    @Volatile
    private var flowStateCrossfadeEnabled: Boolean = true
    @Volatile
    private var flowStateCrossfadeDurationSec: Int = FlowStateCrossfadeMath.DEFAULT_CROSSFADE_SECONDS
    @Volatile
    private var flowStateWindowMs: Long = FlowStateCrossfadeMath.windowMs(flowStateCrossfadeDurationSec)
    @Volatile
    private var flowStateFadeInMs: Long = FlowStateCrossfadeMath.fadeInMs(flowStateCrossfadeDurationSec)

    private companion object {
        const val FLOW_STATE_POLL_MS = 120L
        const val FLOW_STATE_TAIL_VOLUME = 0.08f
        const val FLOW_STATE_SEEK_ABORT_MARGIN_MS = 420L
    }

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

                // Initialize State
                _isPlaying.value = controller.isPlaying
                _shuffleModeEnabled.value = controller.shuffleModeEnabled
                _repeatMode.value = controller.repeatMode
                _currentMediaId.value = controller.currentMediaItem?.mediaId ?: ""

                setupPlayerListener(controller)
                restorePlaybackState(controller)

                if (controller.isPlaying) startPositionTicker()

            } catch (e: Exception) {
                Log.e("MediaManager", "Controller init failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPositionTicker()
                    startFlowStateCrossfadeMonitor()
                } else {
                    positionJob?.cancel()
                    crossfadeMonitorJob?.cancel()
                    crossfadeOutJob?.cancel()
                    // Save position safely to disk only when paused to prevent I/O overload
                    managerScope.launch { playbackPrefs.savePosition(controller.currentPosition) }
                }
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                _shuffleModeEnabled.value = enabled
                managerScope.launch { playbackPrefs.saveShuffleMode(enabled) }
            }

            override fun onRepeatModeChanged(mode: Int) {
                _repeatMode.value = mode
                managerScope.launch { playbackPrefs.saveRepeatMode(mode) }
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                _currentMediaId.value = item?.mediaId ?: ""
                _currentPosition.value = 0L
                saveCurrentState()

                // Incoming track blooms in after tail fade from previous track.
                startFlowStateFadeIn()
                startFlowStateCrossfadeMonitor()

                val trackId = item?.mediaId
                if (!trackId.isNullOrBlank()) {
                    maybeAppendInfiniteQueue(trackId)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (!flowStateCrossfadeEnabled) return
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    crossfadeOutJob?.cancel()
                    crossfadeInJob?.cancel()
                    mediaController?.let { controller ->
                        runCatching { controller.volume = 1f }
                    }
                    if (_isPlaying.value) {
                        startFlowStateCrossfadeMonitor()
                    }
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                _mediaControllerState.tryEmit(Unit)
            }
        })
    }

    // Ticks smoothly for the UI slider, but no longer abuses SharedPreferences
    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = managerScope.launch {
            while (isActive) {
                mediaController?.let {
                    _currentPosition.value = it.currentPosition
                }
                delay(500) // UI updates twice a second
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
        val controller = mediaController ?: return
        val mediaItems = tracks.map { createMediaItem(it) }

        controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        controller.volume = 1f
        controller.prepare()
        controller.play()

        saveCurrentState()
    }

    fun removeFromQueue(trackId: String) {
        val controller = mediaController ?: return
        for (i in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(i).mediaId == trackId) {
                controller.removeMediaItem(i)
                saveCurrentState()
                break
            }
        }
    }

    fun addNextToQueue(track: AudioTrack) {
        playNext(track)
    }

    fun playNext(track: AudioTrack) {
        val controller = mediaController ?: return
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
    }

    fun addToQueue(track: AudioTrack) {
        val controller = mediaController ?: return
        val newItem = createMediaItem(track)

        if (controller.mediaItemCount == 0) {
            controller.setMediaItem(newItem)
            controller.prepare()
            controller.play()
        } else {
            controller.addMediaItem(controller.mediaItemCount, newItem)
        }

        saveCurrentState()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        if (PlaybackSemantics.canMoveQueueItem(fromIndex, toIndex, controller.mediaItemCount)) {
            controller.moveMediaItem(fromIndex, toIndex)
            saveCurrentState()
        }
    }

    fun playQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (!PlaybackSemantics.isValidQueueIndex(index, controller.mediaItemCount)) return

        controller.seekToDefaultPosition(index)
        controller.volume = controller.volume.coerceAtLeast(FLOW_STATE_TAIL_VOLUME)
        if (!controller.isPlaying) {
            controller.play()
        }
        saveCurrentState()
    }

    private fun saveCurrentState() {
        managerScope.launch {
            val controller = mediaController ?: return@launch
            if (controller.mediaItemCount == 0) return@launch

            val queueIds = getCurrentQueue().map { it.mediaId }
            playbackPrefs.savePlaybackState(
                controller.currentMediaItem?.mediaId,
                controller.currentPosition,
                queueIds,
                queueIds // We no longer need to maintain separate 'original' lists for custom shuffle
            )
        }
    }

    private fun restorePlaybackState(controller: MediaController) {
        managerScope.launch {
            val lastId = playbackPrefs.lastTrackId.firstOrNull() ?: return@launch
            val lastPos = playbackPrefs.lastPosition.firstOrNull() ?: 0L
            val queueIds = playbackPrefs.lastQueueIds.firstOrNull() ?: emptyList()
            val savedShuffle = playbackPrefs.shuffleMode.firstOrNull() ?: false
            val savedRepeat = playbackPrefs.repeatMode.firstOrNull() ?: Player.REPEAT_MODE_OFF

            if (queueIds.isEmpty()) return@launch

            val allTracks = audioRepository.getLocalTracks().firstOrNull() ?: emptyList()
            val restored = queueIds.mapNotNull { id -> allTracks.find { it.id == id } }

            if (restored.isEmpty()) return@launch

            val startIndex = restored.indexOfFirst { it.id == lastId }.coerceAtLeast(0)

            controller.setMediaItems(restored.map { createMediaItem(it) }, startIndex, lastPos)
            controller.shuffleModeEnabled = savedShuffle
            controller.repeatMode = savedRepeat
            controller.prepare()

            _mediaControllerState.tryEmit(Unit)
        }
    }

    // --- SAFE SHUFFLE & REPEAT ---

    fun toggleShuffleMode() {
        // Let ExoPlayer handle the shuffle natively.
        // DO NOT manipulate the queue array, ExoPlayer manages this via an internal Index mapping.
        mediaController?.let {
            it.shuffleModeEnabled = PlaybackSemantics.toggledShuffle(it.shuffleModeEnabled)
        }
    }

    fun toggleRepeatMode() {
        mediaController?.let {
            it.repeatMode = PlaybackSemantics.nextRepeatMode(it.repeatMode)
        }
    }

    // --- PLAYBACK CONTROLS ---

    fun resume(fallbackTrack: AudioTrack? = null) {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0 && fallbackTrack != null) {
            controller.setMediaItem(createMediaItem(fallbackTrack))
            controller.prepare()
        } else if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
            controller.prepare()
        }
        controller.volume = controller.volume.coerceAtLeast(FLOW_STATE_TAIL_VOLUME)
        controller.play()
        startFlowStateCrossfadeMonitor()
    }

    fun pause() = mediaController?.pause()
    fun seekTo(pos: Long) = mediaController?.seekTo(pos)
    fun skipToNext() = mediaController?.seekToNext()
    fun skipToPrevious() = mediaController?.seekToPrevious()

    fun release() {
        autoQueueJob?.cancel()
        crossfadeMonitorJob?.cancel()
        crossfadeOutJob?.cancel()
        crossfadeInJob?.cancel()
        mediaController?.let { controller ->
            runCatching { controller.volume = 1f }
        }
        saveCurrentState()
        positionJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        managerScope.cancel()
    }

    fun setFlowStateCrossfadeEnabled(enabled: Boolean) {
        flowStateCrossfadeEnabled = enabled
        if (!enabled) {
            crossfadeMonitorJob?.cancel()
            crossfadeOutJob?.cancel()
            crossfadeInJob?.cancel()
            mediaController?.let { controller ->
                runCatching { controller.volume = 1f }
            }
        } else if (_isPlaying.value) {
            startFlowStateCrossfadeMonitor()
        }
    }

    fun isFlowStateCrossfadeEnabled(): Boolean = flowStateCrossfadeEnabled

    fun setFlowStateCrossfadeDurationSeconds(seconds: Int) {
        flowStateCrossfadeDurationSec = FlowStateCrossfadeMath.normalizeCrossfadeSeconds(seconds)
        flowStateWindowMs = FlowStateCrossfadeMath.windowMs(flowStateCrossfadeDurationSec)
        flowStateFadeInMs = FlowStateCrossfadeMath.fadeInMs(flowStateCrossfadeDurationSec)

        if (!flowStateCrossfadeEnabled || flowStateWindowMs <= 0L) {
            crossfadeMonitorJob?.cancel()
            crossfadeOutJob?.cancel()
            crossfadeInJob?.cancel()
            mediaController?.let { controller -> runCatching { controller.volume = 1f } }
            return
        }

        if (_isPlaying.value) {
            startFlowStateCrossfadeMonitor()
        }
    }

    fun getFlowStateCrossfadeDurationSeconds(): Int = flowStateCrossfadeDurationSec

    private fun startFlowStateCrossfadeMonitor() {
        if (!flowStateCrossfadeEnabled || flowStateWindowMs <= 0L) return
        crossfadeMonitorJob?.cancel()
        crossfadeOutJob?.cancel()

        crossfadeMonitorJob = managerScope.launch {
            val controller = mediaController ?: return@launch
            val mediaId = controller.currentMediaItem?.mediaId ?: return@launch

            while (isActive) {
                if (!controller.isPlaying) return@launch
                if (controller.currentMediaItem?.mediaId != mediaId) return@launch

                val duration = controller.duration
                if (duration == C.TIME_UNSET || duration <= 0L) {
                    delay(FLOW_STATE_POLL_MS)
                    continue
                }

                val remaining = (duration - controller.currentPosition).coerceAtLeast(0L)
                if (remaining <= flowStateWindowMs) {
                    startFlowStateFadeOut(mediaId, remaining)
                    return@launch
                }

                delay(FLOW_STATE_POLL_MS)
            }
        }
    }

    private fun startFlowStateFadeOut(mediaId: String, remainingMs: Long) {
        if (!flowStateCrossfadeEnabled || flowStateWindowMs <= 0L) return
        crossfadeOutJob?.cancel()
        crossfadeOutJob = managerScope.launch {
            val controller = mediaController ?: return@launch
            if (controller.currentMediaItem?.mediaId != mediaId) return@launch

            val startVolume = controller.volume.coerceIn(FLOW_STATE_TAIL_VOLUME, 1f)
            val fadeMs = FlowStateCrossfadeMath.computeFadeOutDuration(remainingMs, flowStateWindowMs)
            if (fadeMs <= 0L) return@launch
            val startTime = System.currentTimeMillis()

            while (isActive) {
                val nowRemaining = (controller.duration - controller.currentPosition).coerceAtLeast(0L)
                if (FlowStateCrossfadeMath.shouldAbortFadeOut(
                        isPlaying = controller.isPlaying,
                        expectedMediaId = mediaId,
                        currentMediaId = controller.currentMediaItem?.mediaId,
                        remainingMs = nowRemaining,
                        windowMs = flowStateWindowMs,
                        seekAbortMarginMs = FLOW_STATE_SEEK_ABORT_MARGIN_MS
                    )) {
                    return@launch
                }

                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
                val t = (elapsed.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
                val volume = lerp(startVolume, FLOW_STATE_TAIL_VOLUME, t)
                controller.volume = volume

                if (t >= 1f) return@launch
                delay(32L)
            }
        }
    }

    private fun startFlowStateFadeIn() {
        if (!flowStateCrossfadeEnabled || flowStateFadeInMs <= 0L) {
            mediaController?.let { controller ->
                runCatching { controller.volume = 1f }
            }
            return
        }
        crossfadeInJob?.cancel()
        crossfadeInJob = managerScope.launch {
            val controller = mediaController ?: return@launch
            val startVolume = controller.volume.coerceIn(FLOW_STATE_TAIL_VOLUME, 1f)
            val startTime = System.currentTimeMillis()

            while (isActive) {
                if (!controller.isPlaying) return@launch
                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
                val t = (elapsed.toFloat() / flowStateFadeInMs.toFloat()).coerceIn(0f, 1f)
                controller.volume = lerp(startVolume, 1f, t)

                if (t >= 1f) return@launch
                delay(32L)
            }
        }
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t
    }

    private fun maybeAppendInfiniteQueue(currentTrackId: String) {
        val controller = mediaController ?: return
        val remainingItems = controller.mediaItemCount - (controller.currentMediaItemIndex + 1)
        if (remainingItems > 1) return

        autoQueueJob?.cancel()
        autoQueueJob = managerScope.launch(Dispatchers.IO) {
            val localLibrary = audioRepository.getLocalTracks().firstOrNull().orEmpty()
            if (localLibrary.isEmpty()) return@launch

            val currentTrack = localLibrary.firstOrNull { it.id == currentTrackId } ?: return@launch
            val queueIds = withContext(Dispatchers.Main) {
                val c = mediaController ?: return@withContext emptySet<String>()
                List(c.mediaItemCount) { index -> c.getMediaItemAt(index).mediaId }.toSet()
            }
            if (queueIds.isEmpty()) return@launch

            val sameArtist = localLibrary
                .asSequence()
                .filter { it.id !in queueIds }
                .filter { it.artist.equals(currentTrack.artist, ignoreCase = true) }
                .take(5)
                .toList()

            val similarArtists = lastFmRepository.getSimilarArtistNames(currentTrack.artist, limit = 12)
                .map { it.lowercase().trim() }
                .toSet()

            val similarArtistTracks = localLibrary
                .asSequence()
                .filter { it.id !in queueIds }
                .filter { it.artist.lowercase().trim() in similarArtists }
                .take(18)
                .toList()

            val fallbackTracks = localLibrary
                .asSequence()
                .filter { it.id !in queueIds }
                .shuffled()
                .take(18)
                .toList()

            val candidates = (sameArtist + similarArtistTracks + fallbackTracks)
                .distinctBy { it.id }
                .take(12)
            if (candidates.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                val c = mediaController ?: return@withContext
                val nowRemaining = c.mediaItemCount - (c.currentMediaItemIndex + 1)
                if (nowRemaining > 1) return@withContext

                c.addMediaItems(c.mediaItemCount, candidates.map { createMediaItem(it) })
                _mediaControllerState.tryEmit(Unit)
                saveCurrentState()
            }
        }
    }
}

internal object FlowStateCrossfadeMath {
    const val DEFAULT_CROSSFADE_SECONDS = 7
    private const val MAX_CROSSFADE_SECONDS = 12

    fun normalizeCrossfadeSeconds(seconds: Int): Int = seconds.coerceIn(0, MAX_CROSSFADE_SECONDS)

    fun windowMs(seconds: Int): Long = normalizeCrossfadeSeconds(seconds) * 1_000L

    fun fadeInMs(seconds: Int): Long {
        val normalized = normalizeCrossfadeSeconds(seconds)
        if (normalized == 0) return 0L
        return (normalized * 320L).coerceIn(650L, 4_000L)
    }

    fun computeFadeOutDuration(remainingMs: Long, windowMs: Long): Long {
        if (windowMs <= 0L) return 0L
        val minFade = minOf(1_200L, windowMs.coerceAtLeast(400L))
        return remainingMs.coerceIn(minFade, windowMs)
    }

    fun shouldAbortFadeOut(
        isPlaying: Boolean,
        expectedMediaId: String,
        currentMediaId: String?,
        remainingMs: Long,
        windowMs: Long,
        seekAbortMarginMs: Long
    ): Boolean {
        if (!isPlaying) return true
        if (currentMediaId != expectedMediaId) return true
        if (windowMs <= 0L) return true
        return remainingMs > (windowMs + seekAbortMarginMs)
    }
}
