package com.example.tigerplayer.ui.player

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.source.LocalAudioDataSource
import com.example.tigerplayer.engine.*
import com.example.tigerplayer.service.MediaControllerManager
import com.example.tigerplayer.ui.home.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlayerVisualMode { ARTWORK, WAVEFORM }

// --- UI DATA MODELS ---
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
    val topArtists: List<StatItem> = emptyList(),
    val topTracks: List<StatItem> = emptyList()
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
    val tracks: List<AudioTrack> = emptyList(),
    val artists: List<LibraryArtist> = emptyList(),
    val albums: List<String> = emptyList(),
    val customPlaylists: List<Playlist> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val totalFilesToScan: Int = 0,
    val queue: List<AudioTrack> = emptyList(),
    val visualMode: PlayerVisualMode = PlayerVisualMode.ARTWORK,
    val currentWaveform: List<Float> = emptyList()
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackEngine: PlaybackEngine,
    private val mediaControllerManager: MediaControllerManager,
    private val metadataEngine: MetadataEngine,
    private val statsEngine: StatsEngine,
    private val libraryEngine: LibraryEngine,
    private val networkEngine: NetworkEngine,
    private val waveformEngine: WaveformEngine,
    private val audioRepository: AudioRepository // 🔥 RESTORED
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _trackColor = MutableStateFlow(Color(0xFF4FC3F7)) // Defaults to AardBlue
    val trackColor: StateFlow<Color> = _trackColor.asStateFlow()

    private var scanJob: Job? = null

    // A trigger to force the library flow to re-fetch when scans complete
    private val libraryRefreshTrigger = MutableStateFlow(0)

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

    val customPlaylists: StateFlow<List<Playlist>> = libraryEngine.getCustomPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artistDetails = metadataEngine.artistDetails

    init {
        // --- 1. MEDIA CONTROLLER STATE BINDINGS ---
        viewModelScope.launch {
            mediaControllerManager.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }

        viewModelScope.launch {
            mediaControllerManager.currentPosition.collect { pos ->
                _uiState.update { it.copy(currentPosition = pos) }
            }
        }

        viewModelScope.launch {
            mediaControllerManager.shuffleModeEnabled.collect { shuffle ->
                _uiState.update { it.copy(isShuffleEnabled = shuffle) }
            }
        }

        viewModelScope.launch {
            mediaControllerManager.repeatMode.collect { repeat ->
                _uiState.update { it.copy(repeatMode = repeat) }
            }
        }

        // --- 2. LIBRARY SYNCHRONIZATION ---
        viewModelScope.launch {
            libraryEngine.getAggregatedLibraryFlow(unifiedTracksFlow).collect { aggregation ->
                _uiState.update {
                    it.copy(
                        tracks = aggregation.filteredTracks,
                        artists = aggregation.artists,
                        albums = aggregation.albums
                    )
                }

                // 🔥 RESTORED: Artist Pre-Seeding
                // Once the library is fully loaded and indexed, silently fetch Artist Metadata
                // in the background so the UI doesn't hitch when scrolling the Artists tab.
                if (aggregation.filteredTracks.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        metadataEngine.preSeedArtistCache(aggregation.filteredTracks)
                    }
                }
            }
        }

        // --- 3. QUEUE SYNCHRONIZATION ---
        viewModelScope.launch {
            _uiState.map { it.tracks }.distinctUntilChanged().collectLatest { tracks ->
                if (tracks.isNotEmpty()) {
                    playbackEngine.getQueueFlow(tracks).collect { resolvedQueue ->
                        _uiState.update { it.copy(queue = resolvedQueue) }
                    }
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
            mediaControllerManager.currentMediaId.collectLatest { mediaId ->
                val allTracks = _uiState.value.tracks
                val track = allTracks.find { it.id == mediaId }

                if (track != null && _uiState.value.currentTrack?.id != track.id) {

                    _uiState.update {
                        it.copy(
                            currentTrack = track,
                            currentLyrics = null,
                            artistImageUrl = null,
                            currentWaveform = emptyList()
                        )
                    }

                    metadataEngine.clearTrackMetadata()
                    metadataEngine.fetchTrackMetadata(track)
                    statsEngine.recordPlaybackHistory(track)

                    // 🔥 RESTORED: The High-Res Artwork Upgrade
                    if (track.isLocal && track.artworkUri.toString().startsWith("content://")) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val highResUri = metadataEngine.fetchSpotifyHighResArt(track.title, track.artist)
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
    }

    fun updateTrackColor(color: Color) {
        _trackColor.value = color
    }

    // ==========================================
    // --- PLAYBACK CONTROLS (DELEGATED TO ENGINE) ---
    // ==========================================

    fun togglePlayPause() {
        playbackEngine.togglePlayPause(_uiState.value.currentTrack, _uiState.value.isPlaying)
    }

    fun playTrack(track: AudioTrack) {
        playbackEngine.playTrack(track, _uiState.value.tracks)
    }

    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int) {
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

    // ==========================================
    // --- QUEUE MANAGEMENT (DELEGATED TO ENGINE) ---
    // ==========================================

    fun addToQueue(track: AudioTrack) {
        playbackEngine.addToQueue(track)
    }

    fun addNextToQueue(track: AudioTrack) {
        playbackEngine.addToQueue(track) // Handled natively by PlaybackEngine
    }

    fun removeFromQueue(track: AudioTrack) {
        playbackEngine.removeFromQueue(track)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackEngine.moveQueueItem(fromIndex, toIndex)
    }

    // ==========================================
    // --- PLAYLIST OPERATIONS ---
    // ==========================================

    fun getPlaylistTracks(playlistId: Long): Flow<List<AudioTrack>> {
        return libraryEngine.getPlaylistTracks(playlistId, _uiState.value.tracks)
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

                        // Tell the Unified Flow to fetch the newly populated database!
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

    fun toggleVisualMode() {
        val nextMode = if (_uiState.value.visualMode == PlayerVisualMode.ARTWORK) {
            PlayerVisualMode.WAVEFORM
        } else {
            PlayerVisualMode.ARTWORK
        }
        _uiState.update { it.copy(visualMode = nextMode) }
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

    // ==========================================
    // --- METADATA FETCHING ---
    // ==========================================

    fun fetchArtistProfile(artistName: String) {
        viewModelScope.launch {
            metadataEngine.fetchArtistProfile(artistName)
        }
    }

    // ==========================================
    // --- NETWORK & CLOUD OPERATIONS ---
    // ==========================================

    fun onAuthSuccess(token: String) {
        networkEngine.onAuthSuccess(token)
    }

    suspend fun connectToNavidrome(url: String, user: String, pass: String): Result<Unit> {
        return networkEngine.connectToNavidrome(url, user, pass)
    }
}