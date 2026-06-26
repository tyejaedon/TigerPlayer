package com.example.tigerplayer.service

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
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
import com.example.tigerplayer.data.local.SettingsDataStore
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
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
    private val settingsDataStore: SettingsDataStore,
    private val audioRepository: AudioRepository,
    private val mediaDataRepository: MediaDataRepository
) {

    companion object {
        private const val FLOW_STATE_DEFAULT_WINDOW_MS = 7_000L
        private const val FLOW_STATE_MIN_WINDOW_MS = 3_000L
        private const val FLOW_STATE_MAX_WINDOW_MS = 12_000L
        private const val FLOW_STATE_MIN_VOLUME = 0.18f
        private const val FLOW_STATE_MAX_VOLUME = 1.0f
        private const val FLOW_STATE_STEP_MS = 90L
        private const val FLOW_STATE_FADE_IN_MS = 2_200L
    }

    private data class OverlapSession(
        val sourceMediaId: String,
        val targetMediaId: String
    )

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
    private var infinitePlayJob: Job? = null
    private var flowStateFadeOutJob: Job? = null
    private var flowStateFadeInJob: Job? = null
    private var overlapCrossfadeJob: Job? = null
    private var lastInfiniteAnchorId: String? = null
    private var activeFlowStateMediaId: String? = null
    private var pendingFlowStateFadeIn = false
    private var overlapPlayer: MediaPlayer? = null
    private var activeOverlapSession: OverlapSession? = null

    @Volatile private var flowStateEnabled = true
    @Volatile private var flowStateWindowMs = FLOW_STATE_DEFAULT_WINDOW_MS
    @Volatile private var flowStateTrueOverlap = true
    @Volatile private var gaplessPlaybackEnabled = true
    @Volatile private var audioReactiveHapticsEnabled = false
    @Volatile private var resumeOnBluetoothConnect = true
    @Volatile private var resumeOnWiredHeadsetConnect = false

    private val infiniteLookAhead = 1
    private val infiniteBatchSize = 12

    init {
        observeFlowStatePreferences()
        observeControlMatrixSettings()
        initializeController()
    }

    private fun observeFlowStatePreferences() {
        managerScope.launch {
            playbackPrefs.flowStateTrueOverlap.collect { overlapEnabled ->
                flowStateTrueOverlap = overlapEnabled
                if (!overlapEnabled) {
                    releaseOverlapSession()
                }
            }
        }
    }

    private fun observeControlMatrixSettings() {
        managerScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val crossfadeWindowMs = (settings.crossfadeDurationSec * 1_000L)
                    .coerceIn(FLOW_STATE_MIN_WINDOW_MS, FLOW_STATE_MAX_WINDOW_MS)

                flowStateEnabled = settings.crossfadeDurationSec > 0
                flowStateWindowMs = crossfadeWindowMs
                gaplessPlaybackEnabled = settings.gaplessPlayback
                audioReactiveHapticsEnabled = settings.audioReactiveHaptics
                resumeOnBluetoothConnect = settings.resumeOnBluetoothConnect
                resumeOnWiredHeadsetConnect = settings.resumeOnWiredHeadsetConnect

                if (!flowStateEnabled) {
                    resetFlowStatePipeline(restoreFullVolume = true)
                }
            }
        }
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
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    maybeScheduleInfinitePlay(controller.currentMediaItem?.mediaId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPositionTicker()
                    maybeStartFlowStateFadeOut(controller)
                    maybeScheduleInfinitePlay(controller.currentMediaItem?.mediaId)
                } else {
                    positionJob?.cancel()
                    resetFlowStatePipeline(restoreFullVolume = false)
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
                flowStateFadeOutJob?.cancel()
                activeFlowStateMediaId = null

                val overlapHandled = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    handleOverlapTransition(controller, item?.mediaId)

                if (!overlapHandled && pendingFlowStateFadeIn && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    startFlowStateFadeIn(item?.mediaId)
                } else {
                    pendingFlowStateFadeIn = false
                    flowStateFadeInJob?.cancel()
                    controller.volume = FLOW_STATE_MAX_VOLUME
                }
                maybeScheduleInfinitePlay(item?.mediaId)
                saveCurrentState()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                _mediaControllerState.tryEmit(Unit)
            }
        })
    }

    private fun maybeScheduleInfinitePlay(anchorId: String?) {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex < 0 || controller.mediaItemCount == 0) return

        val remaining = (controller.mediaItemCount - currentIndex - 1).coerceAtLeast(0)
        if (remaining > infiniteLookAhead) return

        val resolvedAnchorId = anchorId ?: controller.currentMediaItem?.mediaId ?: return
        if (infinitePlayJob?.isActive == true && resolvedAnchorId == lastInfiniteAnchorId) return
        lastInfiniteAnchorId = resolvedAnchorId

        infinitePlayJob?.cancel()
        infinitePlayJob = managerScope.launch(Dispatchers.IO) {
            val currentTrack = audioRepository
                .getLocalTracks()
                .firstOrNull()
                .orEmpty()
                .firstOrNull { it.id == resolvedAnchorId }
                ?: return@launch

            val recommendations = mediaDataRepository
                .getInfinitePlayRecommendations(currentTrack, infiniteBatchSize)

            if (recommendations.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                val activeController = mediaController ?: return@withContext
                val existingIds = (0 until activeController.mediaItemCount)
                    .map { activeController.getMediaItemAt(it).mediaId }
                    .toHashSet()

                val newItems = recommendations
                    .filterNot { existingIds.contains(it.id) }
                    .map { createMediaItem(it) }

                if (newItems.isNotEmpty()) {
                    activeController.addMediaItems(newItems)
                    _mediaControllerState.tryEmit(Unit)
                    saveCurrentState()
                }
            }
        }
    }

    // Ticks smoothly for the UI slider, but no longer abuses SharedPreferences
    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = managerScope.launch {
            while (isActive) {
                mediaController?.let {
                    _currentPosition.value = it.currentPosition
                    maybeStartFlowStateFadeOut(it)
                }
                delay(250)
            }
        }
    }

    private fun maybeStartFlowStateFadeOut(controller: MediaController) {
        if (!flowStateEnabled) return
        if (!controller.isPlaying || controller.playbackState != Player.STATE_READY) return
        if (controller.mediaItemCount <= 1) return

        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= controller.mediaItemCount - 1) return

        val durationMs = controller.duration
        if (durationMs == C.TIME_UNSET || durationMs <= 0L) return

        val remainingMs = durationMs - controller.currentPosition
        if (remainingMs > flowStateWindowMs || remainingMs <= 0L) return

        val mediaId = controller.currentMediaItem?.mediaId ?: return
        if (flowStateTrueOverlap) {
            val overlapStarted = maybeStartTrueOverlapCrossfade(controller, mediaId, remainingMs)
            if (overlapStarted) return
        }

        if (flowStateFadeOutJob?.isActive == true && activeFlowStateMediaId == mediaId) return

        startFlowStateFadeOut(controller, mediaId, remainingMs)
    }

    private fun startFlowStateFadeOut(controller: MediaController, mediaId: String, remainingMs: Long) {
        releaseOverlapSession()
        flowStateFadeInJob?.cancel()
        flowStateFadeOutJob?.cancel()

        activeFlowStateMediaId = mediaId
        pendingFlowStateFadeIn = true

        val startVolume = controller.volume.coerceIn(FLOW_STATE_MIN_VOLUME, FLOW_STATE_MAX_VOLUME)
        val fadeDurationMs = remainingMs.coerceIn(1_400L, flowStateWindowMs)
        val steps = (fadeDurationMs / FLOW_STATE_STEP_MS).coerceAtLeast(1L).toInt()

        flowStateFadeOutJob = managerScope.launch {
            for (step in 1..steps) {
                val activeController = mediaController ?: return@launch
                if (!activeController.isPlaying || activeController.currentMediaItem?.mediaId != mediaId) return@launch

                val t = step.toFloat() / steps.toFloat()
                val smooth = t * t * (3f - (2f * t))
                val volume = lerp(startVolume, FLOW_STATE_MIN_VOLUME, smooth)
                activeController.volume = volume
                delay(FLOW_STATE_STEP_MS)
            }

            mediaController?.takeIf { it.currentMediaItem?.mediaId == mediaId }?.let {
                it.volume = FLOW_STATE_MIN_VOLUME
            }
        }
    }

    private fun startFlowStateFadeIn(mediaId: String?) {
        val targetId = mediaId ?: return
        if (!flowStateEnabled || flowStateTrueOverlap) return

        flowStateFadeInJob?.cancel()
        flowStateFadeInJob = managerScope.launch {
            val controller = mediaController ?: return@launch
            val startVolume = controller.volume.coerceIn(FLOW_STATE_MIN_VOLUME, FLOW_STATE_MAX_VOLUME)
            val steps = (FLOW_STATE_FADE_IN_MS / FLOW_STATE_STEP_MS).coerceAtLeast(1L).toInt()

            for (step in 1..steps) {
                val activeController = mediaController ?: return@launch
                if (activeController.currentMediaItem?.mediaId != targetId) return@launch

                val t = step.toFloat() / steps.toFloat()
                val smooth = 1f - ((1f - t) * (1f - t))
                val volume = lerp(startVolume, FLOW_STATE_MAX_VOLUME, smooth)
                activeController.volume = volume
                delay(FLOW_STATE_STEP_MS)
            }

            mediaController?.takeIf { it.currentMediaItem?.mediaId == targetId }?.let {
                it.volume = FLOW_STATE_MAX_VOLUME
            }
            pendingFlowStateFadeIn = false
        }
    }

    private fun maybeStartTrueOverlapCrossfade(
        controller: MediaController,
        sourceMediaId: String,
        remainingMs: Long
    ): Boolean {
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= controller.mediaItemCount - 1) return false

        val nextItem = controller.getMediaItemAt(currentIndex + 1)
        val targetMediaId = nextItem.mediaId
        val targetUri = nextItem.localConfiguration?.uri ?: return false

        if (targetMediaId.isBlank()) return false
        if (activeOverlapSession?.sourceMediaId == sourceMediaId) return true

        startTrueOverlapCrossfade(controller, sourceMediaId, targetMediaId, targetUri, remainingMs)
        return true
    }

    private fun startTrueOverlapCrossfade(
        controller: MediaController,
        sourceMediaId: String,
        targetMediaId: String,
        targetUri: Uri,
        remainingMs: Long
    ) {
        resetFlowStatePipeline(restoreFullVolume = false)

        pendingFlowStateFadeIn = false
        activeFlowStateMediaId = sourceMediaId
        activeOverlapSession = OverlapSession(sourceMediaId = sourceMediaId, targetMediaId = targetMediaId)

        val warmPlayer = MediaPlayer()
        overlapPlayer = warmPlayer

        warmPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )

        warmPlayer.setOnErrorListener { _, _, _ ->
            resetFlowStatePipeline(restoreFullVolume = true)
            false
        }

        warmPlayer.setOnPreparedListener { preparedPlayer ->
            preparedPlayer.setVolume(0f, 0f)
            preparedPlayer.start()

            val startVolume = controller.volume.coerceIn(FLOW_STATE_MIN_VOLUME, FLOW_STATE_MAX_VOLUME)
            val fadeDurationMs = remainingMs.coerceIn(1_400L, flowStateWindowMs)
            val steps = (fadeDurationMs / FLOW_STATE_STEP_MS).coerceAtLeast(1L).toInt()

            overlapCrossfadeJob = managerScope.launch {
                for (step in 1..steps) {
                    val activeController = mediaController ?: return@launch
                    val activeSession = activeOverlapSession ?: return@launch
                    val activePlayer = overlapPlayer ?: return@launch

                    if (!activeController.isPlaying || activeController.currentMediaItem?.mediaId != activeSession.sourceMediaId) {
                        return@launch
                    }

                    val t = step.toFloat() / steps.toFloat()
                    val smooth = t * t * (3f - 2f * t)
                    val outVolume = lerp(startVolume, FLOW_STATE_MIN_VOLUME, smooth)
                    val inVolume = lerp(0f, FLOW_STATE_MAX_VOLUME, smooth)

                    activeController.volume = outVolume
                    activePlayer.setVolume(inVolume, inVolume)
                    delay(FLOW_STATE_STEP_MS)
                }
            }
        }

        runCatching {
            warmPlayer.setDataSource(context, targetUri)
            warmPlayer.prepareAsync()
        }.onFailure {
            resetFlowStatePipeline(restoreFullVolume = true)
        }
    }

    private fun handleOverlapTransition(controller: MediaController, transitionedMediaId: String?): Boolean {
        val activeSession = activeOverlapSession ?: return false
        if (transitionedMediaId == null || transitionedMediaId != activeSession.targetMediaId) {
            releaseOverlapSession()
            return false
        }

        val overlapPositionMs = runCatching {
            overlapPlayer?.currentPosition?.toLong() ?: 0L
        }.getOrDefault(0L).coerceAtLeast(0L)

        if (overlapPositionMs > 0L) {
            controller.seekTo(overlapPositionMs)
        }

        controller.volume = FLOW_STATE_MAX_VOLUME
        releaseOverlapSession()
        return true
    }

    private fun releaseOverlapSession() {
        overlapCrossfadeJob?.cancel()
        overlapCrossfadeJob = null

        overlapPlayer?.let { player ->
            runCatching {
                player.setVolume(0f, 0f)
                if (player.isPlaying) player.stop()
            }
            runCatching { player.release() }
        }
        overlapPlayer = null
        activeOverlapSession = null
    }

    private fun resetFlowStatePipeline(restoreFullVolume: Boolean) {
        flowStateFadeOutJob?.cancel()
        flowStateFadeInJob?.cancel()
        overlapCrossfadeJob?.cancel()

        flowStateFadeOutJob = null
        flowStateFadeInJob = null
        overlapCrossfadeJob = null

        activeFlowStateMediaId = null
        pendingFlowStateFadeIn = false
        releaseOverlapSession()

        if (restoreFullVolume) {
            mediaController?.volume = FLOW_STATE_MAX_VOLUME
        }
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return start + (end - start) * clamped
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

        resetFlowStatePipeline(restoreFullVolume = true)

        controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
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

    // Compatibility shim for existing call sites.
    fun addNextToQueue(track: AudioTrack) {
        playNext(track)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        if (fromIndex in 0 until controller.mediaItemCount && toIndex in 0 until controller.mediaItemCount) {
            controller.moveMediaItem(fromIndex, toIndex)
            saveCurrentState()
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
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun toggleRepeatMode() {
        mediaController?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
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
        controller.play()
    }

    fun pause() = mediaController?.pause()
    fun seekTo(pos: Long) = mediaController?.seekTo(pos)
    fun skipToNext() {
        resetFlowStatePipeline(restoreFullVolume = true)
        mediaController?.seekToNext()
    }

    fun skipToPrevious() {
        resetFlowStatePipeline(restoreFullVolume = true)
        mediaController?.seekToPrevious()
    }

    fun release() {
        saveCurrentState()
        positionJob?.cancel()
        infinitePlayJob?.cancel()
        resetFlowStatePipeline(restoreFullVolume = false)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        managerScope.cancel()
    }
}