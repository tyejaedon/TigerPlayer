package com.example.tigerplayer.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import com.example.tigerplayer.utils.BluetoothDeviceManager
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
    private val mediaDataRepository: MediaDataRepository,
    private val bluetoothDeviceManager: BluetoothDeviceManager
) {

    companion object {
        private const val FLOW_STATE_DEFAULT_WINDOW_MS = 7_000L
        private const val FLOW_STATE_MIN_WINDOW_MS = 3_000L
        private const val FLOW_STATE_MAX_WINDOW_MS = 12_000L
        private const val FLOW_STATE_MIN_VOLUME = 0.18f
        private const val FLOW_STATE_MAX_VOLUME = 1.0f
        private const val FLOW_STATE_STEP_MS = 90L
        private const val FLOW_STATE_FADE_IN_MS = 2_200L
        private const val QUEUE_ITEM_SEPARATOR = "\u001F"
        private const val QUEUE_FIELD_SEPARATOR = "\u001E"

        // Metadata keys embedded in MediaItem extras so queue resolution does not depend on library flows.
        const val META_IS_LOCAL = "tp_meta_is_local"
        const val META_IS_REMOTE = "tp_meta_is_remote"
        const val META_DURATION_MS = "tp_meta_duration_ms"
        const val META_MIME_TYPE = "tp_meta_mime_type"
        const val META_BITRATE = "tp_meta_bitrate"
        const val META_SAMPLE_RATE = "tp_meta_sample_rate"
        const val META_TRACK_NUMBER = "tp_meta_track_number"
        const val META_SERVER_PATH = "tp_meta_server_path"
        const val META_YEAR = "tp_meta_year"
        const val META_DATE_ADDED = "tp_meta_date_added"
        const val META_IS_LIKED = "tp_meta_is_liked"
        const val META_PATH = "tp_meta_path"
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _currentMediaId = MutableStateFlow("")
    val currentMediaId: StateFlow<String> = _currentMediaId

    private val _currentMediaItemIndex = MutableStateFlow(-1)
    val currentMediaItemIndex: StateFlow<Int> = _currentMediaItemIndex

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
    private var lastInfiniteAnchorId: String? = null
    private var activeFlowStateMediaId: String? = null
    private var pendingFlowStateFadeIn = false
    private var isRouteReceiverRegistered = false
    private var routeReceiverRegisteredAtMs = 0L
    private val lastRouteActionHandledAt = mutableMapOf<String, Long>()

    private val routeStickyGraceMs = 1_500L
    private val routeEventDebounceMs = 1_200L

    @Volatile private var flowStateEnabled = true
    @Volatile private var flowStateWindowMs = FLOW_STATE_DEFAULT_WINDOW_MS
    @Volatile private var flowStateTrueOverlap = false
    @Volatile private var gaplessPlaybackEnabled = true
    @Volatile private var audioReactiveHapticsEnabled = false
    @Volatile private var resumeOnBluetoothConnect = true
    @Volatile private var resumeOnWiredHeadsetConnect = false

    private val audioRouteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val now = SystemClock.elapsedRealtime()

            // Ignore startup sticky broadcasts right after receiver registration.
            if (now - routeReceiverRegisteredAtMs <= routeStickyGraceMs) return

            // Debounce duplicate route broadcasts that arrive in rapid succession.
            val lastHandledAt = lastRouteActionHandledAt[action] ?: 0L
            if (now - lastHandledAt <= routeEventDebounceMs) return
            lastRouteActionHandledAt[action] = now

            when (action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    if (state == 1 && resumeOnWiredHeadsetConnect) {
                        maybeResumeOnRouteConnect("wired")
                    }
                }

                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    bluetoothDeviceManager.refreshConnectedDevice()
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    if (state == BluetoothProfile.STATE_CONNECTED && resumeOnBluetoothConnect) {
                        maybeResumeOnRouteConnect("bluetooth")
                    }
                }
            }
        }
    }

    private val infiniteLookAhead = 1
    private val infiniteBatchSize = 12

    init {
        observeFlowStatePreferences()
        observeControlMatrixSettings()
        registerAudioRouteReceiver()
        initializeController()
    }

    private fun observeFlowStatePreferences() {
        // Preference observation kept for UI compatibility, but logic is disabled in audio path
    }

    private fun observeControlMatrixSettings() {
        managerScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val crossfadeWindowMs = (settings.crossfadeDurationSec * 1_000L)
                    .coerceIn(FLOW_STATE_MIN_WINDOW_MS, FLOW_STATE_MAX_WINDOW_MS)
                val isCrossfadeEnabled = settings.crossfadeDurationSec > 0

                flowStateEnabled = isCrossfadeEnabled && settings.gaplessPlayback
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
                _currentMediaItemIndex.value = controller.currentMediaItemIndex

                setupPlayerListener(controller)
                restorePlaybackState(controller)

                if (controller.isPlaying) {
                    startPositionTicker()
                    bluetoothDeviceManager.startTrackingListeningTime()
                }

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
                    bluetoothDeviceManager.startTrackingListeningTime()
                    maybeStartFlowStateFadeOut(controller)
                    maybeScheduleInfinitePlay(controller.currentMediaItem?.mediaId)
                } else {
                    positionJob?.cancel()
                    bluetoothDeviceManager.stopTrackingListeningTime()
                    // Ensure we never resume from an attenuated crossfade volume.
                    resetFlowStatePipeline(restoreFullVolume = true)
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
                _currentMediaItemIndex.value = controller.currentMediaItemIndex
                _currentPosition.value = 0L
                maybeEmitTransitionHaptic()
                flowStateFadeOutJob?.cancel()
                activeFlowStateMediaId = null

                if (pendingFlowStateFadeIn && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
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
                _currentMediaItemIndex.value = controller.currentMediaItemIndex
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
                .getCachedLocalTracks()
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
                mediaController?.let { controller ->
                    _currentPosition.value = controller.currentPosition
                    
                    // OPTIMIZATION: Only perform heavy FlowState checks in the final 20 seconds
                    val duration = controller.duration
                    if (duration != C.TIME_UNSET && duration > 0) {
                        val remaining = duration - controller.currentPosition
                        if (remaining < 20_000L) {
                            maybeStartFlowStateFadeOut(controller)
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun maybeStartFlowStateFadeOut(controller: MediaController) {
        if (!flowStateEnabled || !gaplessPlaybackEnabled) return
        if (!controller.isPlaying || controller.playbackState != Player.STATE_READY) return
        if (controller.mediaItemCount <= 1) return

        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= controller.mediaItemCount - 1) return

        val durationMs = controller.duration
        if (durationMs == C.TIME_UNSET || durationMs <= 0L) return

        val remainingMs = durationMs - controller.currentPosition
        if (remainingMs > flowStateWindowMs || remainingMs <= 0L) return

        val mediaId = controller.currentMediaItem?.mediaId ?: return
        
        // AUDIOPHILE DECISION: Disable True Overlap Crossfade via MediaPlayer.
        // It bypasses the High-Resolution DSP chain and compromises bit-depth during transitions.
        // Standard Media3 gapless transitions are preserved for bit-perfect timing.
        /*
        if (flowStateTrueOverlap) {
            val overlapStarted = maybeStartTrueOverlapCrossfade(controller, mediaId, remainingMs)
            if (overlapStarted) return
        }
        */

        if (flowStateFadeOutJob?.isActive == true && activeFlowStateMediaId == mediaId) return

        startFlowStateFadeOut(controller, mediaId, remainingMs)
    }

    private fun startFlowStateFadeOut(controller: MediaController, mediaId: String, remainingMs: Long) {
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
        if (!flowStateEnabled) return

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

    private fun registerAudioRouteReceiver() {
        if (isRouteReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }

        runCatching {
            context.registerReceiver(audioRouteReceiver, filter)
            isRouteReceiverRegistered = true
            routeReceiverRegisteredAtMs = SystemClock.elapsedRealtime()
            lastRouteActionHandledAt.clear()
        }.onFailure {
            Log.w("MediaManager", "Failed to register audio route receiver: ${it.message}")
        }
    }

    private fun unregisterAudioRouteReceiver() {
        if (!isRouteReceiverRegistered) return

        runCatching {
            context.unregisterReceiver(audioRouteReceiver)
        }
        isRouteReceiverRegistered = false
        routeReceiverRegisteredAtMs = 0L
        lastRouteActionHandledAt.clear()
    }

    private fun maybeResumeOnRouteConnect(route: String) {
        val controller = mediaController ?: return
        if (controller.isPlaying || controller.mediaItemCount == 0) return

        controller.play()
        Log.d("MediaManager", "Auto-resumed playback on $route route connection")
    }

    private fun maybeEmitTransitionHaptic() {
        if (!audioReactiveHapticsEnabled) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                vibrator?.let(::emitShortHaptic)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let(::emitShortHaptic)
            }
        }
    }

    private fun emitShortHaptic(vibrator: Vibrator) {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(12L)
        }
    }

    private fun resetFlowStatePipeline(restoreFullVolume: Boolean) {
        flowStateFadeOutJob?.cancel()
        flowStateFadeInJob?.cancel()

        flowStateFadeOutJob = null
        flowStateFadeInJob = null

        activeFlowStateMediaId = null
        pendingFlowStateFadeIn = false

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

    fun getQueueFlow(): Flow<List<AudioTrack>> {
        return mediaControllerState
            .onStart { emit(Unit) }
            .map { getQueueTracks() }
    }

    fun getQueueTracks(): List<AudioTrack> {
        val controller = mediaController ?: return emptyList()
        return List(controller.mediaItemCount) { index ->
            mediaItemToAudioTrack(controller.getMediaItemAt(index))
        }
    }

    fun getUpcomingTracksPreview(isShuffleEnabled: Boolean): List<AudioTrack> {
        val controller = mediaController ?: return emptyList()
        val queue = getQueueTracks()
        if (queue.isEmpty()) return emptyList()

        if (controller.currentTimeline.windowCount == 0) {
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex == C.INDEX_UNSET) return queue
            return queue.drop((currentIndex + 1).coerceAtMost(queue.size))
        }

        val upcoming = mutableListOf<AudioTrack>()
        var loopIndex = controller.nextMediaItemIndex
        var itemsAdded = 0
        val maxItems = controller.mediaItemCount - 1

        while (loopIndex != C.INDEX_UNSET && itemsAdded < maxItems) {
            upcoming.add(mediaItemToAudioTrack(controller.getMediaItemAt(loopIndex)))
            loopIndex = controller.currentTimeline.getNextWindowIndex(
                loopIndex,
                Player.REPEAT_MODE_OFF,
                isShuffleEnabled
            )
            itemsAdded++
        }
        return upcoming
    }

    fun mediaItemToAudioTrack(mediaItem: MediaItem): AudioTrack {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val fallbackUri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY
        val mimeType = extras?.getString(META_MIME_TYPE).orEmpty()

        return AudioTrack(
            id = mediaItem.mediaId,
            title = metadata.title?.toString() ?: "Unknown",
            artist = metadata.artist?.toString() ?: "Unknown Artist",
            album = metadata.albumTitle?.toString() ?: "Unknown Album",
            uri = fallbackUri,
            artworkUri = metadata.artworkUri ?: Uri.EMPTY,
            durationMs = extras?.getLong(META_DURATION_MS, 0L) ?: 0L,
            mimeType = mimeType.ifBlank { "audio/unknown" },
            isLocal = extras?.getBoolean(META_IS_LOCAL, false) ?: false,
            isRemote = extras?.getBoolean(META_IS_REMOTE, fallbackUri != Uri.EMPTY)
                ?: (fallbackUri != Uri.EMPTY),
            bitrate = extras?.getInt(META_BITRATE, 0) ?: 0,
            sampleRate = extras?.getInt(META_SAMPLE_RATE, 0) ?: 0,
            trackNumber = extras?.getInt(META_TRACK_NUMBER, 0) ?: 0,
            serverPath = extras?.getString(META_SERVER_PATH),
            path = extras?.getString(META_PATH),
            year = extras?.getString(META_YEAR),
            dateAdded = extras?.getLong(META_DATE_ADDED, 0L) ?: 0L,
            isLiked = extras?.getBoolean(META_IS_LIKED, false) ?: false
        )
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
                    .setExtras(buildQueueMetadataExtras(track))
                    .build()
            )
            .build()
    }

    private fun buildQueueMetadataExtras(track: AudioTrack): Bundle {
        return Bundle().apply {
            putBoolean(META_IS_LOCAL, track.isLocal)
            putBoolean(META_IS_REMOTE, track.isRemote)
            putLong(META_DURATION_MS, track.durationMs)
            putString(META_MIME_TYPE, track.mimeType)
            putInt(META_BITRATE, track.bitrate)
            putInt(META_SAMPLE_RATE, track.sampleRate)
            putInt(META_TRACK_NUMBER, track.trackNumber)
            putString(META_SERVER_PATH, track.serverPath)
            putString(META_YEAR, track.year)
            putLong(META_DATE_ADDED, track.dateAdded)
            putBoolean(META_IS_LIKED, track.isLiked)
            putString(META_PATH, track.path)
        }
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
                removeFromQueueAt(i)
                break
            }
        }
    }

    fun removeFromQueueAt(index: Int) {
        val controller = mediaController ?: return
        if (index !in 0 until controller.mediaItemCount) return

        controller.removeMediaItem(index)
        _currentMediaItemIndex.value = controller.currentMediaItemIndex
        saveCurrentState()
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

    fun playQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekTo(index, C.TIME_UNSET)
            controller.play()
        }
    }

    private fun saveCurrentState() {
        managerScope.launch {
            val controller = mediaController ?: return@launch
            if (controller.mediaItemCount == 0) {
                playbackPrefs.clearPlaybackState()
                return@launch
            }

            val queue = getCurrentQueue()
            val queueIds = queue.map { it.mediaId }
            playbackPrefs.savePlaybackState(
                controller.currentMediaItem?.mediaId,
                controller.currentPosition,
                queueIds,
                queueIds, // We no longer need to maintain separate 'original' lists for custom shuffle
                queueSnapshot = serializeQueueSnapshot(queue)
            )
        }
    }

    private fun serializeQueueSnapshot(queue: List<MediaItem>): String {
        return queue.joinToString(QUEUE_ITEM_SEPARATOR) { item ->
            val metadata = item.mediaMetadata
            val extras = metadata.extras
            listOf(
                Uri.encode(item.mediaId),
                Uri.encode(item.localConfiguration?.uri?.toString().orEmpty()),
                Uri.encode(metadata.title?.toString().orEmpty()),
                Uri.encode(metadata.artist?.toString().orEmpty()),
                Uri.encode(metadata.albumTitle?.toString().orEmpty()),
                Uri.encode(metadata.artworkUri?.toString().orEmpty()),
                Uri.encode((extras?.getBoolean(META_IS_LOCAL, false) ?: false).toString()),
                Uri.encode((extras?.getBoolean(META_IS_REMOTE, false) ?: false).toString()),
                Uri.encode((extras?.getLong(META_DURATION_MS, 0L) ?: 0L).toString()),
                Uri.encode(extras?.getString(META_MIME_TYPE).orEmpty()),
                Uri.encode((extras?.getInt(META_BITRATE, 0) ?: 0).toString()),
                Uri.encode((extras?.getInt(META_SAMPLE_RATE, 0) ?: 0).toString()),
                Uri.encode((extras?.getInt(META_TRACK_NUMBER, 0) ?: 0).toString()),
                Uri.encode(extras?.getString(META_SERVER_PATH).orEmpty()),
                Uri.encode(extras?.getString(META_YEAR).orEmpty()),
                Uri.encode((extras?.getLong(META_DATE_ADDED, 0L) ?: 0L).toString()),
                Uri.encode((extras?.getBoolean(META_IS_LIKED, false) ?: false).toString()),
                Uri.encode(extras?.getString(META_PATH).orEmpty())
            ).joinToString(QUEUE_FIELD_SEPARATOR)
        }
    }

    private fun deserializeQueueSnapshot(snapshot: String?): List<AudioTrack> {
        if (snapshot.isNullOrBlank()) return emptyList()

        return snapshot.split(QUEUE_ITEM_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { encodedItem ->
                val fields = encodedItem.split(QUEUE_FIELD_SEPARATOR)
                if (fields.size < 6) return@mapNotNull null

                val id = Uri.decode(fields[0]).orEmpty()
                if (id.isBlank()) return@mapNotNull null

                val uriString = Uri.decode(fields[1]).orEmpty()
                val title = Uri.decode(fields[2]).orEmpty().ifBlank { "Unknown" }
                val artist = Uri.decode(fields[3]).orEmpty().ifBlank { "Unknown Artist" }
                val album = Uri.decode(fields[4]).orEmpty().ifBlank { "Unknown Album" }
                val artworkString = Uri.decode(fields[5]).orEmpty()
                val isLocal = fields.getOrNull(6)?.let(Uri::decode)?.toBooleanStrictOrNull() ?: false
                val isRemote = fields.getOrNull(7)?.let(Uri::decode)?.toBooleanStrictOrNull()
                    ?: uriString.isNotBlank()
                val durationMs = fields.getOrNull(8)?.let(Uri::decode)?.toLongOrNull() ?: 0L
                val mimeType = fields.getOrNull(9)?.let(Uri::decode).orEmpty().ifBlank { "audio/unknown" }
                val bitrate = fields.getOrNull(10)?.let(Uri::decode)?.toIntOrNull() ?: 0
                val sampleRate = fields.getOrNull(11)?.let(Uri::decode)?.toIntOrNull() ?: 0
                val trackNumber = fields.getOrNull(12)?.let(Uri::decode)?.toIntOrNull() ?: 0
                val serverPath = fields.getOrNull(13)?.let(Uri::decode)?.ifBlank { null }
                val year = fields.getOrNull(14)?.let(Uri::decode)?.ifBlank { null }
                val dateAdded = fields.getOrNull(15)?.let(Uri::decode)?.toLongOrNull() ?: 0L
                val isLiked = fields.getOrNull(16)?.let(Uri::decode)?.toBooleanStrictOrNull() ?: false
                val path = fields.getOrNull(17)?.let(Uri::decode)?.ifBlank { null }

                AudioTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    uri = uriString.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY,
                    artworkUri = artworkString.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY,
                    durationMs = durationMs,
                    mimeType = mimeType,
                    isLocal = isLocal,
                    isRemote = isRemote,
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    trackNumber = trackNumber,
                    serverPath = serverPath,
                    year = year,
                    dateAdded = dateAdded,
                    isLiked = isLiked,
                    path = path
                )
            }
    }

    private fun restorePlaybackState(controller: MediaController) {
        managerScope.launch {
            val lastId = playbackPrefs.lastTrackId.firstOrNull()
            val lastPos = playbackPrefs.lastPosition.firstOrNull() ?: 0L
            val queueIds = playbackPrefs.lastQueueIds.firstOrNull() ?: emptyList()
            val queueSnapshot = playbackPrefs.lastQueueSnapshot.firstOrNull()
            val savedShuffle = playbackPrefs.shuffleMode.firstOrNull() ?: false
            val savedRepeat = playbackPrefs.repeatMode.firstOrNull() ?: Player.REPEAT_MODE_OFF

            if (queueIds.isEmpty()) {
                playbackPrefs.clearPlaybackState()
                return@launch
            }

            val allTracks = audioRepository.getCachedLocalTracks().firstOrNull() ?: emptyList()
            val snapshotTracks = deserializeQueueSnapshot(queueSnapshot)

            // Prefer snapshot ordering/data for mixed-source queues, enrich with local tracks when available.
            val restored = withContext(Dispatchers.Default) {
                val trackMap = allTracks.associateBy { it.id }
                queueIds.mapIndexedNotNull { index, id ->
                    val snapshotTrack = snapshotTracks.getOrNull(index)?.takeIf { it.id == id }
                    val localTrack = trackMap[id]

                    when {
                        localTrack != null -> localTrack
                        snapshotTrack != null -> snapshotTrack
                        else -> null
                    }
                }
            }

            if (restored.isEmpty()) {
                playbackPrefs.clearPlaybackState()
                return@launch
            }

            val startIndex = lastId?.let { id ->
                restored.indexOfFirst { it.id == id }.coerceAtLeast(0)
            } ?: 0

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
        bluetoothDeviceManager.stopTrackingListeningTime()
        resetFlowStatePipeline(restoreFullVolume = false)
        unregisterAudioRouteReceiver()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        managerScope.cancel()
    }
}