package com.tigerplayer.ui.player

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tigerplayer.data.local.DefaultPlayerView
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.data.remote.api.YouTubeRepository
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.repository.ArtistDetails
import com.tigerplayer.data.source.LocalAudioDataSource
import com.tigerplayer.engine.*
import com.tigerplayer.service.MediaControllerManager
import com.tigerplayer.ui.home.HomeUiState
import com.tigerplayer.utils.BluetoothDeviceInfo
import com.tigerplayer.utils.BluetoothDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlayerVisualMode { ARTWORK, WAVEFORM, VORTEX, SONIC_PRISM }

enum class MainViewState { ARTWORK, LYRICS, QUEUE, YOUTUBE_VIEWPORT }

data class LibraryArtist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int
)

data class StatItem(
    val id: String,
    val name: String,
    val playCount: Int,
    val secondaryText: String = "",
    val imageUrl: String? = null
)

data class DetailedStatsUiState(
    val selectedFilter: String = "Today",
    val totalListeningHours: Int = 0,
    val totalListeningMinutes: Int = 0,
    val globalListeningSharePercent: Float = 0f,
    val topArtists: List<StatItem> = emptyList(),
    val topTracks: List<StatItem> = emptyList(),
    /**
     * Epoch-millis from which these figures are trustworthy, or 0 if all history counts.
     * Non-zero means pre-#42 rows are being excluded and the UI should say so (issue #80).
     */
    val statsEpochMs: Long = 0L
)

data class PlayerUiState(
    val searchQuery: String = "",
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val currentTrack: AudioTrack? = null,
    val currentLyrics: String? = null,
    val artistImageUrl: String? = null,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1f,
    val pitch: Float = 1f,
    val allTracks: List<AudioTrack> = emptyList(),
    val tracks: List<AudioTrack> = emptyList(),
    val artists: List<LibraryArtist> = emptyList(),
    val albums: List<LibraryEngine.LibraryAlbum> = emptyList(),
    val customPlaylists: List<Playlist> = emptyList(),
    val trackSortOrder: LibraryEngine.SortOrder = LibraryEngine.SortOrder.TITLE,
    val albumSortOrder: LibraryEngine.SortOrder = LibraryEngine.SortOrder.TITLE,
    val playlistSortOrder: LibraryEngine.SortOrder = LibraryEngine.SortOrder.DATE_ADDED,
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val totalFilesToScan: Int = 0,
    val queue: List<AudioTrack> = emptyList(),
    val currentQueueIndex: Int = -1,
    val visualMode: PlayerVisualMode = PlayerVisualMode.ARTWORK,
    val currentWaveform: List<Float> = emptyList(),
    val audioReactiveFrame: AudioReactiveFrame = AudioReactiveFrame(),
    val mainViewState: MainViewState = MainViewState.ARTWORK,
    val connectedBluetoothDevice: BluetoothDeviceInfo = BluetoothDeviceInfo()
)

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackEngine: PlaybackEngine,
    val mediaControllerManager: MediaControllerManager,
    private val metadataEngine: MetadataEngine,
    private val statsEngine: StatsEngine,
    private val libraryEngine: LibraryEngine,
    private val networkEngine: NetworkEngine,
    private val waveformEngine: WaveformEngine,
    private val adaptiveDspEngine: AdaptiveDspEngine,
    private val settingsDataStore: SettingsDataStore,
    private val playbackPrefs: PlaybackPrefs,
    private val audioRepository: AudioRepository,
    val youtubeRepository: YouTubeRepository,
    private val bluetoothDeviceManager: BluetoothDeviceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _trackColor = MutableStateFlow(Color(0xFF4FC3F7))
    val trackColor: StateFlow<Color> = _trackColor.asStateFlow()

    private var scanJob: Job? = null
    private var metadataJob: Job? = null
    private var lastHandledSpotifyTrackId: String? = null

    /**
     * Authoritative source of truth for which transport currently drives [uiState], set
     * explicitly at every playback-initiating call rather than inferred by sniffing
     * `currentTrack?.id` for a "spotify:" prefix. That inference used to race with
     * [SpotifyRepository.pause] re-emitting stale Spotify state as a side effect of starting local
     * playback, which could permanently block the UI from ever switching back to a local track.
     */
    private val activeSource = MutableStateFlow(MediaSource.LOCAL)
    private val libraryRefreshTrigger = MutableStateFlow(0)
    private var preferredDefaultPlayerView: DefaultPlayerView = DefaultPlayerView.ARTWORK_3D

    private val _trackSortOrder = MutableStateFlow(LibraryEngine.SortOrder.TITLE)
    private val _albumSortOrder = MutableStateFlow(LibraryEngine.SortOrder.TITLE)
    private val _playlistSortOrder = MutableStateFlow(LibraryEngine.SortOrder.DATE_ADDED)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val unifiedTracksFlow: SharedFlow<List<AudioTrack>> = libraryRefreshTrigger
        .flatMapLatest { networkEngine.getUnifiedLibraryFlow() }
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val homeUiState: StateFlow<HomeUiState> = libraryEngine.getHomeUiStateFlow(
        unifiedTracksFlow
    ).stateIn(viewModelScope, SharingStarted.Lazily, HomeUiState())

    val detailedStatsState: StateFlow<DetailedStatsUiState> = statsEngine.getDetailedStatsFlow(
        unifiedTracksFlow,
        metadataEngine.artistDetails
    ).stateIn(viewModelScope, SharingStarted.Lazily, DetailedStatsUiState())

    val customPlaylists: StateFlow<List<Playlist>> = combine(
        libraryEngine.getCustomPlaylists(),
        _playlistSortOrder
    ) { playlists, sortOrder ->
        when (sortOrder) {
            LibraryEngine.SortOrder.TITLE -> playlists.sortedBy { it.name.lowercase() }
            LibraryEngine.SortOrder.DATE_ADDED -> playlists.sortedByDescending { it.createdAt }
            else -> playlists
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artistDetails = metadataEngine.artistDetails

    init {
        // --- 1. MEDIA CONTROLLER STATE BINDINGS ---
        viewModelScope.launch {
            mediaControllerManager.isPlaying.collect { isPlaying ->
                if (activeSource.value == MediaSource.SPOTIFY) return@collect
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }

        viewModelScope.launch {
            mediaControllerManager.currentPosition.collect { pos ->
                if (activeSource.value == MediaSource.SPOTIFY) return@collect
                _uiState.update { it.copy(currentPosition = pos) }
            }
        }

        viewModelScope.launch {
            playbackEngine.spotifyPlaybackState.collectLatest { spotifyState ->
                val spotifyTrack = spotifyState?.track

                if (spotifyTrack == null) {
                    if (activeSource.value == MediaSource.SPOTIFY) {
                        _uiState.update {
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
                        activeSource.value = MediaSource.LOCAL
                    }
                    lastHandledSpotifyTrackId = null
                    return@collectLatest
                }

                // lastHandledSpotifyTrackId is only ever advanced from inside this collector, so
                // it is immune to whatever the queue-transition collector below concurrently does
                // to uiState.currentTrack - unlike comparing against currentTrack directly, this
                // can't race.
                val isNewSpotifyTrack = lastHandledSpotifyTrackId != spotifyTrack.id
                val isGenuineSpotifyEvent =
                    activeSource.value == MediaSource.SPOTIFY || isNewSpotifyTrack || spotifyState.isPlaying

                if (!isGenuineSpotifyEvent) {
                    // Stale echo: SpotifyRepository.pause() was called as a side effect of the
                    // user starting local playback (see PlaybackEngine.playTrack). Local is
                    // already the active source, so this must not resurrect the old Spotify track.
                    return@collectLatest
                }

                activeSource.value = MediaSource.SPOTIFY

                _uiState.update { state ->
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
                    metadataJob = viewModelScope.launch(Dispatchers.IO) {
                        metadataEngine.fetchTrackMetadata(spotifyTrack)
                    }
                    statsEngine.onTrackChanged(spotifyTrack, spotifyState.isPlaying)
                }
            }
        }

        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                preferredDefaultPlayerView = settings.defaultPlayerView
            }
        }

        viewModelScope.launch {
            mediaControllerManager.shuffleModeEnabled.collect { shuffle ->
                if (activeSource.value == MediaSource.SPOTIFY) return@collect
                _uiState.update { it.copy(isShuffleEnabled = shuffle) }
            }
        }

        viewModelScope.launch {
            mediaControllerManager.repeatMode.collect { repeat ->
                _uiState.update { it.copy(repeatMode = repeat) }
            }
        }

        viewModelScope.launch {
            adaptiveDspEngine.audioReactiveFrame.collect { frame ->
                _uiState.update { it.copy(audioReactiveFrame = frame) }
            }
        }

        viewModelScope.launch {
            bluetoothDeviceManager.connectedDevice.collect { device ->
                _uiState.update { it.copy(connectedBluetoothDevice = device) }
            }
        }

        // --- 1b. LISTENED-DURATION ACCOUNTING (issue #42) ---
        // Driven from the unified UI state rather than a single transport, so local and Spotify
        // playback both accumulate correctly. StatsEngine is a @Singleton, so an in-flight play
        // survives this ViewModel being recreated.
        viewModelScope.launch {
            _uiState
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { isPlaying -> statsEngine.onPlayingChanged(isPlaying) }
        }

        // --- PLAYBACK PARAMETERS (speed & pitch) ---
        viewModelScope.launch {
            mediaControllerManager.playbackSpeed.collect { speed ->
                _uiState.update { it.copy(playbackSpeed = speed) }
            }
        }

        viewModelScope.launch {
            mediaControllerManager.pitch.collect { pitch ->
                _uiState.update { it.copy(pitch = pitch) }
            }
        }

        // --- 2. LIBRARY SYNCHRONIZATION ---
        viewModelScope.launch {
            libraryEngine.getAggregatedLibraryFlow(
                unifiedTracksFlow,
                _trackSortOrder,
                _albumSortOrder
            ).collect { aggregation ->
                _uiState.update {
                    it.copy(
                        allTracks = aggregation.tracks,
                        tracks = aggregation.filteredTracks,
                        artists = aggregation.artists,
                        albums = aggregation.albums
                    )
                }

                if (aggregation.filteredTracks.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        metadataEngine.preSeedArtistCache(aggregation.filteredTracks)
                    }
                }
            }
        }

        // --- 2b. SORT ORDER SYNC ---
        viewModelScope.launch {
            _trackSortOrder.collect { order -> _uiState.update { it.copy(trackSortOrder = order) } }
        }
        viewModelScope.launch {
            _albumSortOrder.collect { order -> _uiState.update { it.copy(albumSortOrder = order) } }
        }
        viewModelScope.launch {
            _playlistSortOrder.collect { order -> _uiState.update { it.copy(playlistSortOrder = order) } }
        }

        // --- 3. QUEUE SYNCHRONIZATION ---
        viewModelScope.launch {
            playbackEngine.getQueueSnapshotFlow().collect { snapshot ->
                _uiState.update {
                    it.copy(
                        queue = snapshot.tracks,
                        currentQueueIndex = snapshot.currentIndex
                    )
                }
            }
        }

        // --- 4. METADATA BINDINGS ---
        viewModelScope.launch {
            metadataEngine.currentLyrics.collect { lyrics ->
                _uiState.update { it.copy(currentLyrics = lyrics) }
            }
        }

        viewModelScope.launch {
            metadataEngine.currentArtistImageUrl.collect { url ->
                _uiState.update { it.copy(artistImageUrl = url) }
            }
        }

        // --- 5. THE TRACK TRANSITION RITUAL ---
        viewModelScope.launch {
            combine(
                _uiState.map { it.queue }.distinctUntilChanged(),
                _uiState.map { it.currentQueueIndex }.distinctUntilChanged()
            ) { queue, queueIndex ->
                queueIndex to queue.getOrNull(queueIndex)
            }.filter { (_, track) -> track != null }
             .distinctUntilChanged { old, new ->
                 old.first == new.first && old.second?.id == new.second?.id
             }
             .collectLatest { (_, resolvedTrack) ->
                if (activeSource.value == MediaSource.SPOTIFY) {
                    return@collectLatest
                }

                val track = resolvedTrack ?: return@collectLatest
                _uiState.update {
                    it.copy(
                        currentTrack = track,
                        currentLyrics = null,
                        artistImageUrl = null,
                        currentWaveform = emptyList()
                    )
                }

                metadataEngine.clearTrackMetadata()
                metadataJob?.cancel()
                metadataJob = viewModelScope.launch(Dispatchers.IO) {
                    metadataEngine.fetchTrackMetadata(track)
                }
                statsEngine.onTrackChanged(track, _uiState.value.isPlaying)

                if (track.isLocal && track.artworkUri.toString().startsWith("content://")) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val highResUri = metadataEngine.fetchSpotifyHighResArt(track.title, track.artist, track.album)
                        if (highResUri != null) {
                            audioRepository.updateTrackArtworkUri(track.id, highResUri.toString())
                            _uiState.update { state ->
                                if (state.currentTrack?.id == track.id) {
                                    state.copy(currentTrack = track.copy(artworkUri = highResUri))
                                } else state
                            }
                        }
                    }
                }

                viewModelScope.launch(Dispatchers.IO) {
                    val realWaveform = waveformEngine.getWaveform(track)
                    _uiState.update { it.copy(currentWaveform = realWaveform) }
                }
            }
        }
    }

    fun updateTrackColor(color: Color) {
        _trackColor.value = color
    }

    // ==========================================
    // --- PLAYBACK CONTROLS ---
    // ==========================================

    fun togglePlayPause() {
        playbackEngine.togglePlayPause(_uiState.value.currentTrack, _uiState.value.isPlaying)
    }

    fun playTrack(track: AudioTrack) {
        if (!track.id.startsWith("spotify:")) activeSource.value = MediaSource.LOCAL
        playbackEngine.playTrack(track, _uiState.value.tracks)
    }

    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int) {
        activeSource.value = MediaSource.LOCAL
        playbackEngine.setPlaylistAndPlay(tracks, startIndex)
    }

    fun skipToNext() {
        playbackEngine.skipToNext(_uiState.value.currentTrack)
    }

    fun skipToPrevious() {
        playbackEngine.skipToPrevious(_uiState.value.currentTrack)
    }

    fun seekTo(positionMs: Long) {
        playbackEngine.seekTo(positionMs, _uiState.value.currentTrack)
    }

    fun toggleShuffle() {
        playbackEngine.toggleShuffle(_uiState.value.currentTrack)
    }

    fun toggleRepeat() {
        playbackEngine.toggleRepeat(_uiState.value.currentTrack)
    }

    // --- PLAYBACK SPEED & PITCH CONTROLS (issue #52) ---
    fun setPlaybackSpeed(speed: Float) {
        val normalized = PlaybackRatePolicy.normalize(speed)
        viewModelScope.launch { mediaControllerManager.setPlaybackParameters(normalized, _uiState.value.pitch) }
    }

    fun increasePlaybackSpeed() {
        val next = PlaybackRatePolicy.adjust(_uiState.value.playbackSpeed, PlaybackRatePolicy.STEP)
        setPlaybackSpeed(next)
    }

    fun decreasePlaybackSpeed() {
        val next = PlaybackRatePolicy.adjust(_uiState.value.playbackSpeed, -PlaybackRatePolicy.STEP)
        setPlaybackSpeed(next)
    }

    fun setPitch(pitch: Float) {
        val normalized = PlaybackRatePolicy.normalize(pitch)
        viewModelScope.launch { mediaControllerManager.setPlaybackParameters(_uiState.value.playbackSpeed, normalized) }
    }

    fun increasePitch() {
        val next = PlaybackRatePolicy.adjust(_uiState.value.pitch, PlaybackRatePolicy.STEP)
        setPitch(next)
    }

    fun decreasePitch() {
        val next = PlaybackRatePolicy.adjust(_uiState.value.pitch, -PlaybackRatePolicy.STEP)
        setPitch(next)
    }

    // ==========================================
    // --- QUEUE MANAGEMENT ---
    // ==========================================

    fun addToQueue(track: AudioTrack) {
        playbackEngine.addToQueue(track)
    }

    fun addNextToQueue(track: AudioTrack) {
        playbackEngine.playNext(track)
    }

    fun removeFromQueue(index: Int) {
        playbackEngine.removeFromQueue(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackEngine.moveQueueItem(fromIndex, toIndex)
    }

    fun playQueueItem(index: Int) {
        activeSource.value = MediaSource.LOCAL
        playbackEngine.playQueueItem(index)
    }

    fun setMainViewState(state: MainViewState) {
        _uiState.update { it.copy(mainViewState = state) }
    }

    // ==========================================
    // --- PLAYLIST OPERATIONS ---
    // ==========================================

    fun getPlaylistTracks(playlistId: Long): Flow<List<AudioTrack>> {
        return libraryEngine.getPlaylistTracks(playlistId, _uiState.value.allTracks)
    }

    // --- FOLDER BROWSING (issue #50) ---

    fun buildFolderIndex(tracks: List<AudioTrack>) = libraryEngine.buildFolderIndex(tracks)

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            libraryEngine.createPlaylist(name)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: AudioTrack) {
        viewModelScope.launch {
            libraryEngine.addTrackToPlaylist(playlistId, track.id)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, track: AudioTrack) {
        viewModelScope.launch {
            libraryEngine.removeTrackFromPlaylist(playlistId, track.id)
        }
    }

    fun savePlaylistOrder(playlistId: Long, tracks: List<AudioTrack>) {
        viewModelScope.launch {
            libraryEngine.savePlaylistOrder(playlistId, tracks)
        }
    }

    fun updatePlaylistImage(context: Context, playlistId: Long, uri: Uri) {
        viewModelScope.launch {
            libraryEngine.updatePlaylistArtwork(context, playlistId, uri)
        }
    }

    // ==========================================
    // --- LIBRARY & STATE OPERATIONS ---
    // ==========================================

    fun loadLocalAudio(forceRefresh: Boolean = false) {
        if (_uiState.value.isScanning && !forceRefresh) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            libraryEngine.getLocalAudioScanFlow(forceRefresh).collect { status ->
                when (status) {
                    is LocalAudioDataSource.ScanStatus.Started -> {
                        _uiState.update {
                            it.copy(
                                isScanning = true,
                                totalFilesToScan = status.total,
                                scanProgress = 0
                            )
                        }
                    }
                    is LocalAudioDataSource.ScanStatus.InProgress -> {
                        _uiState.update {
                            it.copy(
                                isScanning = true,
                                scanProgress = status.current,
                                totalFilesToScan = status.total
                            )
                        }
                    }
                    is LocalAudioDataSource.ScanStatus.Complete -> {
                        _uiState.update { it.copy(isScanning = false) }
                        libraryRefreshTrigger.value += 1
                    }
                }
            }
        }
    }

    fun toggleTrackLikeStatus(track: AudioTrack) {
        viewModelScope.launch {
            val isLiked = libraryEngine.toggleTrackLikeStatus(track)
            val updatedTrack = track.copy(isLiked = isLiked)

            if (_uiState.value.currentTrack?.id == track.id) {
                _uiState.update { it.copy(currentTrack = updatedTrack) }
            }
        }
    }

    // FullPlayer visual cycling excludes Sonic Prism; Prism is controlled from Home.
    // On cover screens we skip VORTEX to reduce GPU/battery load.
    fun toggleVisualMode(isCoverOptimized: Boolean = false) {
        val nextMode = when (_uiState.value.visualMode) {
            PlayerVisualMode.ARTWORK -> PlayerVisualMode.WAVEFORM
            PlayerVisualMode.WAVEFORM -> if (isCoverOptimized) PlayerVisualMode.ARTWORK else PlayerVisualMode.VORTEX
            PlayerVisualMode.VORTEX -> PlayerVisualMode.ARTWORK
            PlayerVisualMode.SONIC_PRISM -> PlayerVisualMode.ARTWORK
        }
        _uiState.update { it.copy(visualMode = nextMode) }
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
        _uiState.update { it.copy(visualMode = targetMode) }
    }

    fun setFullPlayerActive(active: Boolean) {
        viewModelScope.launch {
            playbackPrefs.saveFullPlayerActive(active)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        libraryEngine.updateSearchQuery(query)
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        libraryEngine.clearSearch()
    }

    fun updateStatsFilter(filter: String) {
        statsEngine.updateStatsFilter(filter)
    }

    fun setTrackSortOrder(order: LibraryEngine.SortOrder) {
        _trackSortOrder.value = order
    }

    fun setAlbumSortOrder(order: LibraryEngine.SortOrder) {
        _albumSortOrder.value = order
    }

    fun setPlaylistSortOrder(order: LibraryEngine.SortOrder) {
        _playlistSortOrder.value = order
    }

    // ==========================================
    // --- SLEEPTIMER  SECTION ---
    // ==========================================
    val sleepTimerState: StateFlow<SleepTimerState> = playbackEngine.sleepTimerState
        .stateIn(viewModelScope, SharingStarted.Lazily, SleepTimerState())

    fun setSleepTimerDuration(minutes: Int) = playbackEngine.setSleepTimerDuration(minutes)
    fun setSleepTimerEndOfTrack() = playbackEngine.setSleepTimerEndOfTrack()
    fun setSleepTimerEndOfQueue() = playbackEngine.setSleepTimerEndOfQueue()
    fun cancelSleepTimer() = playbackEngine.cancelSleepTimer()


    // ==========================================
    // --- METADATA FETCHING ---
    // ==========================================

    fun fetchArtistProfile(artistName: String) {
        viewModelScope.launch {
            metadataEngine.fetchArtistProfile(artistName)
        }
    }

    fun observeArtistProfile(artistName: String): Flow<ArtistDetails?> {
        return metadataEngine.observeArtistProfile(artistName)
    }

    // ==========================================
    // --- NETWORK & CLOUD OPERATIONS ---
    // ==========================================

    fun onAuthSuccess(token: String) {
        networkEngine.onAuthSuccess(token)
        // App Remote authorization is a separate Spotify-app handshake and must be opened lazily
        // from an explicit playback action, not eagerly during the browser/custom-tab return path.
    }

    fun refreshBluetoothRouteState() {
        bluetoothDeviceManager.refreshConnectedDevice()
    }

    suspend fun connectToNavidrome(
        url: String,
        user: String,
        pass: String,
        allowCleartext: Boolean = false
    ): Result<Unit> {
        return networkEngine.connectToNavidrome(url, user, pass, allowCleartext)
    }
}

object PlaybackRatePolicy {
    const val MIN_VALUE = 0.5f
    const val MAX_VALUE = 2.0f
    const val STEP = 0.25f
    const val DEFAULT_VALUE = 1.0f

    fun normalize(value: Float): Float = value.coerceIn(MIN_VALUE, MAX_VALUE)
    fun adjust(current: Float, delta: Float): Float = normalize(current + delta)
}
