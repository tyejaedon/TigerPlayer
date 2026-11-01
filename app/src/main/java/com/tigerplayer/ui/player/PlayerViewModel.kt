package com.tigerplayer.ui.player

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.data.local.DefaultPlayerView
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.model.M3uExportResult
import com.tigerplayer.data.model.M3uImportResult
import com.tigerplayer.data.model.M3uPathMode
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
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
    val currentLyrics: Lyrics = Lyrics.None,
    val lyricsManualOffsetMs: Long = 0L,
    val artistImageUrl: String? = null,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val coverScreenVolume: Float = 1f,
    val coverScreenEqPreset: CoverScreenEqPreset = CoverScreenEqPreset.NEURAL_ADAPTIVE,
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
    val connectedBluetoothDevice: BluetoothDeviceInfo = BluetoothDeviceInfo(),
    val spotifyReauthRequired: Boolean = false
)

/**
 * High-level coordinator ViewModel.
 *
 * Formerly an 814-line god object, this class has been split (issue #72):
 * - Playback state & session lifecycle: [PlaybackStateManager] (@Singleton) and [PlaybackViewModel]
 * - Library, scanning, and sorting: [com.tigerplayer.ui.library.LibraryViewModel]
 * - Listening statistics & analytics: [com.tigerplayer.ui.home.StatsViewModel]
 *
 * This coordinator facade maintains 100% backward compatibility for existing callers while
 * delegating core responsibilities to focused, testable, and independently scoped components.
 */
@androidx.annotation.OptIn(UnstableApi::class)
/**
 * Central coordinator ViewModel acting as a backward-compatible facade.
 *
 * Coordinates scoped ViewModels ([PlaybackViewModel], [LibraryViewModel], [StatsViewModel])
 * and the application-scoped [PlaybackStateManager]. Delegates operations directly to their
 * respective domains while providing a unified contract for legacy navigation routes and tests.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playbackStateManager: PlaybackStateManager,
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
    private val bluetoothDeviceManager: BluetoothDeviceManager,
    val fluidRenderer: FluidRenderer
) : ViewModel() {

    // Backward-compatible constructor for existing tests and non-Hilt callers
    constructor(
        playbackEngine: PlaybackEngine,
        mediaControllerManager: MediaControllerManager,
        metadataEngine: MetadataEngine,
        statsEngine: StatsEngine,
        libraryEngine: LibraryEngine,
        networkEngine: NetworkEngine,
        waveformEngine: WaveformEngine,
        adaptiveDspEngine: AdaptiveDspEngine,
        settingsDataStore: SettingsDataStore,
        playbackPrefs: PlaybackPrefs,
        audioRepository: AudioRepository,
        youtubeRepository: YouTubeRepository,
        bluetoothDeviceManager: BluetoothDeviceManager,
        fluidRenderer: FluidRenderer
    ) : this(
        playbackStateManager = PlaybackStateManager(
            playbackEngine = playbackEngine,
            mediaControllerManager = mediaControllerManager,
            metadataEngine = metadataEngine,
            statsEngine = statsEngine,
            waveformEngine = waveformEngine,
            adaptiveDspEngine = adaptiveDspEngine,
            settingsDataStore = settingsDataStore,
            playbackPrefs = playbackPrefs,
            audioRepository = audioRepository,
            bluetoothDeviceManager = bluetoothDeviceManager
        ),
        playbackEngine = playbackEngine,
        mediaControllerManager = mediaControllerManager,
        metadataEngine = metadataEngine,
        statsEngine = statsEngine,
        libraryEngine = libraryEngine,
        networkEngine = networkEngine,
        waveformEngine = waveformEngine,
        adaptiveDspEngine = adaptiveDspEngine,
        settingsDataStore = settingsDataStore,
        playbackPrefs = playbackPrefs,
        audioRepository = audioRepository,
        youtubeRepository = youtubeRepository,
        bluetoothDeviceManager = bluetoothDeviceManager,
        fluidRenderer = fluidRenderer
    )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _rawSearchQuery = MutableStateFlow("")
    val rawSearchQuery: StateFlow<String> = _rawSearchQuery.asStateFlow()

    private val _sonicFootprintOpenRequest = MutableStateFlow(false)
    val sonicFootprintOpenRequest: StateFlow<Boolean> = _sonicFootprintOpenRequest.asStateFlow()

    val trackColor: StateFlow<Color> = playbackStateManager.trackColor

    private var scanJob: Job? = null
    private val libraryRefreshTrigger = MutableStateFlow(0)

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

    val coverScreenStats: StateFlow<CoverScreenStatsSnapshot> =
        statsEngine.getCoverScreenStatsSnapshotFlow().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(replayExpirationMillis = 5.minutes.inWholeMilliseconds),
            CoverScreenStatsSnapshot()
        )

    val recentTracks: StateFlow<List<AudioTrack>> = libraryEngine.getHomeUiStateFlow(unifiedTracksFlow)
        .map { it.recentlyPlayedTracks }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

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

    val sleepTimerState: StateFlow<SleepTimerState> = playbackStateManager.sleepTimerState

    init {
        // --- 1. PLAYBACK STATE SYNCHRONIZATION ---
        viewModelScope.launch {
            playbackStateManager.playbackState.collect { pb ->
                _uiState.update {
                    it.copy(
                        isPlaying = pb.isPlaying,
                        currentPosition = pb.currentPosition,
                        currentTrack = pb.currentTrack,
                        currentLyrics = pb.currentLyrics,
                        lyricsManualOffsetMs = if (pb.currentTrack?.id != it.currentTrack?.id) 0L else it.lyricsManualOffsetMs,
                        artistImageUrl = pb.artistImageUrl,
                        isShuffleEnabled = pb.isShuffleEnabled,
                        repeatMode = pb.repeatMode,
                        coverScreenVolume = pb.coverScreenVolume,
                        coverScreenEqPreset = pb.coverScreenEqPreset,
                        playbackSpeed = pb.playbackSpeed,
                        pitch = pb.pitch,
                        queue = pb.queue,
                        currentQueueIndex = pb.currentQueueIndex,
                        visualMode = pb.visualMode,
                        currentWaveform = pb.currentWaveform,
                        audioReactiveFrame = pb.audioReactiveFrame,
                        mainViewState = pb.mainViewState,
                        connectedBluetoothDevice = pb.connectedBluetoothDevice,
                        spotifyReauthRequired = pb.spotifyReauthRequired
                    )
                }
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

        // --- 2b. CUSTOM PLAYLISTS SYNC ---
        viewModelScope.launch {
            customPlaylists.collect { playlists ->
                _uiState.update { it.copy(customPlaylists = playlists) }
            }
        }

        // --- 2c. SORT ORDER SYNC ---
        viewModelScope.launch {
            _trackSortOrder.collect { order -> _uiState.update { it.copy(trackSortOrder = order) } }
        }
        viewModelScope.launch {
            _albumSortOrder.collect { order -> _uiState.update { it.copy(albumSortOrder = order) } }
        }
        viewModelScope.launch {
            _playlistSortOrder.collect { order -> _uiState.update { it.copy(playlistSortOrder = order) } }
        }

        // --- 2d. SEARCH QUERY DEBOUNCE (issue #262) ---
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _rawSearchQuery
                .drop(1)
                .debounce(250L)
                .distinctUntilChanged()
                .collect { debouncedQuery ->
                    _uiState.update { it.copy(searchQuery = debouncedQuery) }
                    libraryEngine.updateSearchQuery(debouncedQuery)
                }
        }
    }

    fun updateTrackColor(color: Color) {
        playbackStateManager.updateTrackColor(color)
    }

    // ==========================================
    // --- PLAYBACK CONTROLS ---
    // ==========================================

    fun togglePlayPause() {
        playbackStateManager.togglePlayPause()
    }

    fun playTrack(track: AudioTrack) {
        playbackStateManager.playTrack(track, _uiState.value.tracks)
    }

    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int) {
        playbackStateManager.setPlaylistAndPlay(tracks, startIndex)
    }

    fun playAll(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        playbackStateManager.playAll(tracks)
    }

    fun skipToNext() {
        playbackStateManager.skipToNext()
    }

    fun skipToPrevious() {
        playbackStateManager.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackStateManager.seekTo(positionMs)
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        _uiState.update { it.copy(lyricsManualOffsetMs = it.lyricsManualOffsetMs + deltaMs) }
    }

    fun resetLyricsOffset() {
        _uiState.update { it.copy(lyricsManualOffsetMs = 0L) }
    }

    fun toggleShuffle() {
        playbackStateManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playbackStateManager.toggleRepeat()
    }

    fun setCoverScreenVolume(volume: Float) {
        val normalizedVolume = volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        _uiState.update { it.copy(coverScreenVolume = normalizedVolume) }
        playbackStateManager.setCoverScreenVolume(volume)
    }

    fun adjustCoverScreenVolume(delta: Float) {
        playbackStateManager.adjustCoverScreenVolume(delta)
    }

    fun setCoverScreenEqPreset(preset: CoverScreenEqPreset) {
        _uiState.update { it.copy(coverScreenEqPreset = preset) }
        playbackStateManager.setCoverScreenEqPreset(preset)
    }

    // --- PLAYBACK SPEED & PITCH CONTROLS (issue #52) ---
    fun setPlaybackSpeed(speed: Float) {
        playbackStateManager.setPlaybackSpeed(speed)
    }

    fun increasePlaybackSpeed() {
        playbackStateManager.increasePlaybackSpeed()
    }

    fun decreasePlaybackSpeed() {
        playbackStateManager.decreasePlaybackSpeed()
    }

    fun setPitch(pitch: Float) {
        playbackStateManager.setPitch(pitch)
    }

    fun increasePitch() {
        playbackStateManager.increasePitch()
    }

    fun decreasePitch() {
        playbackStateManager.decreasePitch()
    }

    // ==========================================
    // --- QUEUE MANAGEMENT ---
    // ==========================================

    fun addToQueue(track: AudioTrack) {
        playbackStateManager.addToQueue(track)
    }

    fun addNextToQueue(track: AudioTrack) {
        playbackStateManager.addNextToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playbackStateManager.removeFromQueue(index)
    }

    fun clearQueue() {
        playbackStateManager.clearQueue()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackStateManager.moveQueueItem(fromIndex, toIndex)
    }

    fun playQueueItem(index: Int) {
        playbackStateManager.playQueueItem(index)
    }

    fun setMainViewState(state: MainViewState) {
        playbackStateManager.setMainViewState(state)
    }

    fun requestSonicFootprintOpen() {
        _sonicFootprintOpenRequest.value = true
    }

    fun consumeSonicFootprintOpenRequest() {
        _sonicFootprintOpenRequest.value = false
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

    fun saveCuratedPlaylist(name: String, tracks: List<AudioTrack>) {
        viewModelScope.launch {
            libraryEngine.saveCuratedPlaylist(name, tracks)
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

    // --- M3U / M3U8 IMPORT & EXPORT (issue #58) ---

    suspend fun importM3uPlaylist(
        fileUri: Uri,
        customPlaylistName: String? = null
    ): Result<M3uImportResult> {
        return libraryEngine.importM3uPlaylist(fileUri, customPlaylistName)
    }

    suspend fun exportM3uPlaylist(
        playlistId: Long,
        destinationUri: Uri,
        pathMode: M3uPathMode = M3uPathMode.ABSOLUTE
    ): Result<M3uExportResult> {
        return libraryEngine.exportM3uPlaylist(playlistId, destinationUri, pathMode)
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

    fun likeTrack(track: AudioTrack) {
        viewModelScope.launch {
            val isLiked = libraryEngine.likeTrack(track)
            val updatedTrack = track.copy(isLiked = isLiked)

            if (_uiState.value.currentTrack?.id == track.id) {
                _uiState.update { it.copy(currentTrack = updatedTrack) }
            }
            playbackStateManager.updateCurrentTrack(updatedTrack)
        }
    }

    fun toggleTrackLikeStatus(track: AudioTrack) {
        viewModelScope.launch {
            val isLiked = libraryEngine.toggleTrackLikeStatus(track)
            val updatedTrack = track.copy(isLiked = isLiked)

            if (_uiState.value.currentTrack?.id == track.id) {
                _uiState.update { it.copy(currentTrack = updatedTrack) }
            }
            playbackStateManager.updateCurrentTrack(updatedTrack)
        }
    }

    fun toggleVisualMode(isCoverOptimized: Boolean = false) {
        playbackStateManager.toggleVisualMode(isCoverOptimized)
    }

    fun onFullPlayerOpened(isCoverOptimized: Boolean = false) {
        playbackStateManager.onFullPlayerOpened(isCoverOptimized)
    }

    fun setFullPlayerActive(active: Boolean) {
        playbackStateManager.setFullPlayerActive(active)
    }

    fun onSearchQueryChanged(query: String) {
        _rawSearchQuery.value = query
    }

    fun clearSearch() {
        _rawSearchQuery.value = ""
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
    // --- SLEEPTIMER SECTION ---
    // ==========================================

    fun setSleepTimerDuration(minutes: Int) = playbackStateManager.setSleepTimerDuration(minutes)
    fun setSleepTimerEndOfTrack() = playbackStateManager.setSleepTimerEndOfTrack()
    fun setSleepTimerEndOfQueue() = playbackStateManager.setSleepTimerEndOfQueue()
    fun cancelSleepTimer() = playbackStateManager.cancelSleepTimer()

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
    }

    fun refreshBluetoothRouteState() {
        playbackStateManager.refreshBluetoothRouteState()
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
