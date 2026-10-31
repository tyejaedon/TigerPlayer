package com.tigerplayer.ui.player

import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.data.local.DefaultPlayerView
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.engine.AdaptiveDspEngine
import com.tigerplayer.engine.AudioReactiveFrame
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.engine.PlaybackEngine
import com.tigerplayer.engine.SleepTimerState
import com.tigerplayer.engine.StatsEngine
import com.tigerplayer.engine.WaveformEngine
import com.tigerplayer.service.MediaControllerManager
import com.tigerplayer.utils.BluetoothDeviceInfo
import com.tigerplayer.utils.BluetoothDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared, application-scoped playback state holder.
 *
 * Hoisting playback state into a @Singleton breaks the PlayerViewModel god-object pattern
 * by ensuring high-frequency player state (playback positions, waveforms, audio-reactive frames,
 * track changes, volume) is decoupled from screen-specific lifecycles and does not cascade
 * unneeded recompositions across library or stats screens (issue #72).
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val currentTrack: AudioTrack? = null,
    val currentLyrics: String? = null,
    val artistImageUrl: String? = null,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val coverScreenVolume: Float = 1f,
    val coverScreenEqPreset: CoverScreenEqPreset = CoverScreenEqPreset.NEURAL_ADAPTIVE,
    val playbackSpeed: Float = 1f,
    val pitch: Float = 1f,
    val queue: List<AudioTrack> = emptyList(),
    val currentQueueIndex: Int = -1,
    val visualMode: PlayerVisualMode = PlayerVisualMode.ARTWORK,
    val currentWaveform: List<Float> = emptyList(),
    val audioReactiveFrame: AudioReactiveFrame = AudioReactiveFrame(),
    val mainViewState: MainViewState = MainViewState.ARTWORK,
    val connectedBluetoothDevice: BluetoothDeviceInfo = BluetoothDeviceInfo(),
    val spotifyReauthRequired: Boolean = false
)

@androidx.annotation.OptIn(UnstableApi::class)
@Singleton
class PlaybackStateManager(
    private val playbackEngine: PlaybackEngine,
    val mediaControllerManager: MediaControllerManager,
    private val metadataEngine: MetadataEngine,
    private val statsEngine: StatsEngine,
    private val waveformEngine: WaveformEngine,
    private val adaptiveDspEngine: AdaptiveDspEngine,
    private val settingsDataStore: SettingsDataStore,
    private val playbackPrefs: PlaybackPrefs,
    private val audioRepository: AudioRepository,
    private val bluetoothDeviceManager: BluetoothDeviceManager,
    coroutineScope: CoroutineScope? = null
) {
    @Inject
    constructor(
        playbackEngine: PlaybackEngine,
        mediaControllerManager: MediaControllerManager,
        metadataEngine: MetadataEngine,
        statsEngine: StatsEngine,
        waveformEngine: WaveformEngine,
        adaptiveDspEngine: AdaptiveDspEngine,
        settingsDataStore: SettingsDataStore,
        playbackPrefs: PlaybackPrefs,
        audioRepository: AudioRepository,
        bluetoothDeviceManager: BluetoothDeviceManager
    ) : this(
        playbackEngine = playbackEngine,
        mediaControllerManager = mediaControllerManager,
        metadataEngine = metadataEngine,
        statsEngine = statsEngine,
        waveformEngine = waveformEngine,
        adaptiveDspEngine = adaptiveDspEngine,
        settingsDataStore = settingsDataStore,
        playbackPrefs = playbackPrefs,
        audioRepository = audioRepository,
        bluetoothDeviceManager = bluetoothDeviceManager,
        coroutineScope = null
    )

    private val scope: CoroutineScope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _activeSource = MutableStateFlow(MediaSource.LOCAL)
    val activeSource: StateFlow<MediaSource> = _activeSource.asStateFlow()

    private val _trackColor = MutableStateFlow(Color(0xFF4FC3F7))
    val trackColor: StateFlow<Color> = _trackColor.asStateFlow()

    val sleepTimerState: StateFlow<SleepTimerState> = playbackEngine.sleepTimerState

    val isPlaying: StateFlow<Boolean> = _playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    val currentPosition: StateFlow<Long> = _playbackState
        .map { it.currentPosition }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    val currentTrack: StateFlow<AudioTrack?> = _playbackState
        .map { it.currentTrack }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    val queue: StateFlow<List<AudioTrack>> = _playbackState
        .map { it.queue }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val currentQueueIndex: StateFlow<Int> = _playbackState
        .map { it.currentQueueIndex }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, -1)

    private var metadataJob: Job? = null
    private var lastHandledSpotifyTrackId: String? = null
    private var preferredDefaultPlayerView: DefaultPlayerView = DefaultPlayerView.ARTWORK_3D

    init {
        // 1. MediaController isPlaying
        scope.launch {
            mediaControllerManager.isPlaying.collect { isPlaying ->
                if (_activeSource.value == MediaSource.SPOTIFY) return@collect
                _playbackState.update { it.copy(isPlaying = isPlaying) }
            }
        }

        // 2. MediaController currentPosition
        scope.launch {
            mediaControllerManager.currentPosition.collect { pos ->
                if (_activeSource.value == MediaSource.SPOTIFY) return@collect
                _playbackState.update { it.copy(currentPosition = pos) }
            }
        }

        // 3. Spotify playback state
        scope.launch {
            playbackEngine.spotifyPlaybackState.collectLatest { spotifyState ->
                val spotifyTrack = spotifyState?.track

                if (spotifyTrack == null) {
                    if (_activeSource.value == MediaSource.SPOTIFY) {
                        _playbackState.update {
                            it.copy(
                                currentTrack = null,
                                isPlaying = false,
                                currentPosition = 0L,
                                isShuffleEnabled = mediaControllerManager.shuffleModeEnabled.value,
                                currentLyrics = null,
                                artistImageUrl = null,
                                currentWaveform = emptyList()
                            )
                        }
                        _activeSource.value = MediaSource.LOCAL
                    }
                    lastHandledSpotifyTrackId = null
                    return@collectLatest
                }

                val isNewSpotifyTrack = lastHandledSpotifyTrackId != spotifyTrack.id
                val isGenuineSpotifyEvent =
                    _activeSource.value == MediaSource.SPOTIFY || isNewSpotifyTrack || spotifyState.isPlaying

                if (!isGenuineSpotifyEvent) {
                    return@collectLatest
                }

                _activeSource.value = MediaSource.SPOTIFY

                _playbackState.update { state ->
                    state.copy(
                        currentTrack = spotifyTrack,
                        isPlaying = spotifyState.isPlaying,
                        currentPosition = spotifyState.positionMs,
                        isShuffleEnabled = spotifyState.isShuffleEnabled,
                        currentLyrics = if (isNewSpotifyTrack) null else state.currentLyrics,
                        artistImageUrl = if (isNewSpotifyTrack) null else state.artistImageUrl,
                        currentWaveform = if (isNewSpotifyTrack) emptyList() else state.currentWaveform
                    )
                }

                if (isNewSpotifyTrack) {
                    lastHandledSpotifyTrackId = spotifyTrack.id
                    metadataEngine.clearTrackMetadata()
                    metadataJob?.cancel()
                    metadataJob = scope.launch(Dispatchers.IO) {
                        metadataEngine.fetchTrackMetadata(spotifyTrack)
                    }
                    statsEngine.onTrackChanged(spotifyTrack, spotifyState.isPlaying)
                }
            }
        }

        // 4. Spotify reauth required
        scope.launch {
            playbackEngine.spotifyReauthRequired.collect { reauthRequired ->
                _playbackState.update { it.copy(spotifyReauthRequired = reauthRequired) }
            }
        }

        // 5. Settings DataStore
        scope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                preferredDefaultPlayerView = settings.defaultPlayerView
                _playbackState.update {
                    it.copy(
                        coverScreenVolume = settings.coverScreenVolume,
                        coverScreenEqPreset = settings.coverScreenEqPreset
                    )
                }
                mediaControllerManager.setVolume(settings.coverScreenVolume)
            }
        }

        // 6. Shuffle mode
        scope.launch {
            mediaControllerManager.shuffleModeEnabled.collect { shuffle ->
                if (_activeSource.value == MediaSource.SPOTIFY) return@collect
                _playbackState.update { it.copy(isShuffleEnabled = shuffle) }
            }
        }

        // 7. Repeat mode
        scope.launch {
            mediaControllerManager.repeatMode.collect { repeat ->
                _playbackState.update { it.copy(repeatMode = repeat) }
            }
        }

        // 8. Volume
        scope.launch {
            mediaControllerManager.volume.collect { volume ->
                _playbackState.update { it.copy(coverScreenVolume = volume) }
            }
        }

        // 9. Audio reactive frame
        scope.launch {
            adaptiveDspEngine.audioReactiveFrame.collect { frame ->
                _playbackState.update { it.copy(audioReactiveFrame = frame) }
            }
        }

        // 10. Bluetooth connected device
        scope.launch {
            bluetoothDeviceManager.connectedDevice.collect { device ->
                _playbackState.update { it.copy(connectedBluetoothDevice = device) }
            }
        }

        // 11. Listened duration accounting (issue #42)
        scope.launch {
            _playbackState
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { isPlaying -> statsEngine.onPlayingChanged(isPlaying) }
        }

        // 12. Playback speed & pitch
        scope.launch {
            mediaControllerManager.playbackSpeed.collect { speed ->
                _playbackState.update { it.copy(playbackSpeed = speed) }
            }
        }
        scope.launch {
            mediaControllerManager.pitch.collect { pitch ->
                _playbackState.update { it.copy(pitch = pitch) }
            }
        }

        // 13. Queue synchronization
        scope.launch {
            playbackEngine.getQueueSnapshotFlow().collect { snapshot ->
                _playbackState.update {
                    it.copy(
                        queue = snapshot.tracks,
                        currentQueueIndex = snapshot.currentIndex
                    )
                }
            }
        }

        // 14. Metadata bindings
        scope.launch {
            metadataEngine.currentLyrics.collect { lyrics ->
                _playbackState.update { it.copy(currentLyrics = lyrics) }
            }
        }
        scope.launch {
            metadataEngine.currentArtistImageUrl.collect { url ->
                _playbackState.update { it.copy(artistImageUrl = url) }
            }
        }

        // 15. Track transition ritual
        scope.launch {
            combine(
                _playbackState.map { it.queue }.distinctUntilChanged(),
                _playbackState.map { it.currentQueueIndex }.distinctUntilChanged()
            ) { queue, queueIndex ->
                queueIndex to queue.getOrNull(queueIndex)
            }.filter { (_, track) -> track != null }
                .distinctUntilChanged { old, new ->
                    old.first == new.first && old.second?.id == new.second?.id
                }
                .collectLatest { (_, resolvedTrack) ->
                    if (_activeSource.value == MediaSource.SPOTIFY) {
                        return@collectLatest
                    }

                    val track = resolvedTrack ?: return@collectLatest
                    _playbackState.update {
                        it.copy(
                            currentTrack = track,
                            currentLyrics = null,
                            artistImageUrl = null,
                            currentWaveform = emptyList()
                        )
                    }

                    metadataEngine.clearTrackMetadata()
                    metadataJob?.cancel()
                    metadataJob = scope.launch(Dispatchers.IO) {
                        metadataEngine.fetchTrackMetadata(track)
                    }
                    statsEngine.onTrackChanged(track, _playbackState.value.isPlaying)

                    if (track.isLocal && !track.artworkUri.toString().startsWith("http")) {
                        scope.launch(Dispatchers.IO) {
                            val highResUri = metadataEngine.resolveTrackArtwork(track)
                            if (highResUri != null) {
                                _playbackState.update { state ->
                                    if (state.currentTrack?.id == track.id) {
                                        state.copy(currentTrack = track.copy(artworkUri = highResUri))
                                    } else state
                                }
                            }
                        }
                    }

                    scope.launch(Dispatchers.IO) {
                        val realWaveform = waveformEngine.getWaveform(track)
                        _playbackState.update { it.copy(currentWaveform = realWaveform) }
                    }
                }
        }
    }

    fun updateTrackColor(color: Color) {
        _trackColor.value = color
    }

    fun togglePlayPause() {
        playbackEngine.togglePlayPause(_playbackState.value.currentTrack, _playbackState.value.isPlaying)
    }

    fun playTrack(track: AudioTrack, availableTracks: List<AudioTrack> = emptyList()) {
        if (!track.id.startsWith("spotify:")) _activeSource.value = MediaSource.LOCAL
        playbackEngine.playTrack(track, availableTracks)
    }

    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int) {
        _activeSource.value = MediaSource.LOCAL
        playbackEngine.setPlaylistAndPlay(tracks, startIndex)
    }

    fun playAll(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        setPlaylistAndPlay(tracks, 0)
    }

    fun skipToNext() {
        playbackEngine.skipToNext(_playbackState.value.currentTrack)
    }

    fun skipToPrevious() {
        playbackEngine.skipToPrevious(_playbackState.value.currentTrack)
    }

    fun seekTo(positionMs: Long) {
        playbackEngine.seekTo(positionMs, _playbackState.value.currentTrack)
    }

    fun toggleShuffle() {
        playbackEngine.toggleShuffle(_playbackState.value.currentTrack)
    }

    fun toggleRepeat() {
        playbackEngine.toggleRepeat(_playbackState.value.currentTrack)
    }

    fun setCoverScreenVolume(volume: Float) {
        val normalizedVolume = volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        _playbackState.update { it.copy(coverScreenVolume = normalizedVolume) }
        scope.launch {
            settingsDataStore.setCoverScreenVolume(normalizedVolume)
            mediaControllerManager.setVolume(normalizedVolume)
        }
    }

    fun adjustCoverScreenVolume(delta: Float) {
        setCoverScreenVolume(_playbackState.value.coverScreenVolume + delta)
    }

    fun setCoverScreenEqPreset(preset: CoverScreenEqPreset) {
        _playbackState.update { it.copy(coverScreenEqPreset = preset) }
        scope.launch {
            settingsDataStore.setCoverScreenEqPreset(preset)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val normalized = PlaybackRatePolicy.normalize(speed)
        scope.launch {
            mediaControllerManager.setPlaybackParameters(normalized, _playbackState.value.pitch)
        }
    }

    fun increasePlaybackSpeed() {
        val next = PlaybackRatePolicy.adjust(_playbackState.value.playbackSpeed, PlaybackRatePolicy.STEP)
        setPlaybackSpeed(next)
    }

    fun decreasePlaybackSpeed() {
        val next = PlaybackRatePolicy.adjust(_playbackState.value.playbackSpeed, -PlaybackRatePolicy.STEP)
        setPlaybackSpeed(next)
    }

    fun setPitch(pitch: Float) {
        val normalized = PlaybackRatePolicy.normalize(pitch)
        scope.launch {
            mediaControllerManager.setPlaybackParameters(_playbackState.value.playbackSpeed, normalized)
        }
    }

    fun increasePitch() {
        val next = PlaybackRatePolicy.adjust(_playbackState.value.pitch, PlaybackRatePolicy.STEP)
        setPitch(next)
    }

    fun decreasePitch() {
        val next = PlaybackRatePolicy.adjust(_playbackState.value.pitch, -PlaybackRatePolicy.STEP)
        setPitch(next)
    }

    fun addToQueue(track: AudioTrack) {
        playbackEngine.addToQueue(track)
    }

    fun addNextToQueue(track: AudioTrack) {
        playbackEngine.playNext(track)
    }

    fun removeFromQueue(index: Int) {
        playbackEngine.removeFromQueue(index)
    }

    fun clearQueue() {
        playbackEngine.clearQueue()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackEngine.moveQueueItem(fromIndex, toIndex)
    }

    fun playQueueItem(index: Int) {
        _activeSource.value = MediaSource.LOCAL
        playbackEngine.playQueueItem(index)
    }

    fun setMainViewState(state: MainViewState) {
        _playbackState.update { it.copy(mainViewState = state) }
    }

    fun toggleVisualMode(isCoverOptimized: Boolean = false) {
        val nextMode = when (_playbackState.value.visualMode) {
            PlayerVisualMode.ARTWORK -> PlayerVisualMode.WAVEFORM
            PlayerVisualMode.WAVEFORM -> if (isCoverOptimized) PlayerVisualMode.ARTWORK else PlayerVisualMode.VORTEX
            PlayerVisualMode.VORTEX -> PlayerVisualMode.ARTWORK
            PlayerVisualMode.SONIC_PRISM -> PlayerVisualMode.ARTWORK
        }
        _playbackState.update { it.copy(visualMode = nextMode) }
    }

    fun onFullPlayerOpened(isCoverOptimized: Boolean = false) {
        val preferredMode = when (preferredDefaultPlayerView) {
            DefaultPlayerView.ARTWORK_3D -> PlayerVisualMode.ARTWORK
            DefaultPlayerView.FLUID_VORTEX -> PlayerVisualMode.VORTEX
            DefaultPlayerView.SONIC_PRISM -> PlayerVisualMode.ARTWORK
        }
        val targetMode = if (isCoverOptimized && preferredMode == PlayerVisualMode.VORTEX) {
            PlayerVisualMode.WAVEFORM
        } else {
            preferredMode
        }
        _playbackState.update { it.copy(visualMode = targetMode) }
    }

    fun setFullPlayerActive(active: Boolean) {
        scope.launch {
            playbackPrefs.saveFullPlayerActive(active)
        }
    }

    fun refreshBluetoothRouteState() {
        bluetoothDeviceManager.refreshConnectedDevice()
    }

    fun setSleepTimerDuration(minutes: Int) = playbackEngine.setSleepTimerDuration(minutes)
    fun setSleepTimerEndOfTrack() = playbackEngine.setSleepTimerEndOfTrack()
    fun setSleepTimerEndOfQueue() = playbackEngine.setSleepTimerEndOfQueue()
    fun cancelSleepTimer() = playbackEngine.cancelSleepTimer()

    fun updateCurrentTrack(track: AudioTrack) {
        if (_playbackState.value.currentTrack?.id == track.id) {
            _playbackState.update { it.copy(currentTrack = track) }
        }
    }
}
