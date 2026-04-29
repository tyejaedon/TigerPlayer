package com.example.tigerplayer.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.data.source.LocalAudioDataSource
import com.example.tigerplayer.engine.LibraryEngine
import com.example.tigerplayer.engine.MetadataEngine
import com.example.tigerplayer.engine.NetworkEngine
import com.example.tigerplayer.engine.PlaybackEngine
import com.example.tigerplayer.engine.StatsEngine
import com.example.tigerplayer.engine.EqEngine
import com.example.tigerplayer.engine.EqUiState
import com.example.tigerplayer.service.PeqProfile
import com.example.tigerplayer.ui.home.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryArtist(val name: String, val trackCount: Int, val albumCount: Int)
data class StatItem(val id: String, val name: String, val playCount: Int, val secondaryText: String, val imageUrl: String? = null)
data class DetailedStatsUiState(val selectedFilter: String = "Today", val totalListeningHours: Int = 0, val totalListeningMinutes: Int = 0, val topArtists: List<StatItem> = emptyList(), val topTracks: List<StatItem> = emptyList())

// Visual Mode Enum
enum class PlayerVisualMode {
    ARTWORK,
    WAVEFORM
}

data class PlayerUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val filteredTracks: List<AudioTrack> = emptyList(),
    val artists: List<LibraryArtist> = emptyList(),
    val albums: List<String> = emptyList(),
    val searchQuery: String = "",
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val currentLyrics: String? = null,
    val artistImageUrl: String? = null,
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val totalFilesToScan: Int = 0,
    val queue: List<AudioTrack> = emptyList(),
    val visualMode: PlayerVisualMode = PlayerVisualMode.ARTWORK
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackEngine: PlaybackEngine,
    private val metadataEngine: MetadataEngine,
    private val statsEngine: StatsEngine,
    private val libraryEngine: LibraryEngine,
    private val networkEngine: NetworkEngine,
    private val eqEngine: EqEngine // 🔥 NEW: Injected EQ Engine
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _trackColor = MutableStateFlow(Color(0xFF007AFF))
    val trackColor: StateFlow<Color> = _trackColor.asStateFlow()

    val currentTrackTitle: StateFlow<String> = uiState
        .map { it.currentTrack?.title ?: "Unknown" }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Unknown")

    val customPlaylists: StateFlow<List<Playlist>> = libraryEngine.getCustomPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artistDetails: StateFlow<Map<String, ArtistDetails>> = metadataEngine.artistDetails

    val detailedStatsState: StateFlow<DetailedStatsUiState> = statsEngine.getDetailedStatsFlow(
        allTracksFlow = _uiState.map { it.tracks },
        artistDetailsMapFlow = metadataEngine.artistDetails
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailedStatsUiState())

    // 🔥 NEW: Expose the Audio Fidelity / EQ State
    val eqState: StateFlow<EqUiState> = eqEngine.uiState

    init {
        observeLibrary()
        observeHistory()
        performAutoRitual()
        syncNavidromeArchives()
        observePlaybackEngine()
        observeMetadataEngine()
    }

    // --- VISUAL & UI DELEGATION ---

    fun toggleVisualMode() {
        _uiState.update { state ->
            val next = when (state.visualMode) {
                PlayerVisualMode.ARTWORK -> PlayerVisualMode.WAVEFORM
                PlayerVisualMode.WAVEFORM -> PlayerVisualMode.ARTWORK
            }
            state.copy(visualMode = next)
        }
    }

    fun onSearchQueryChanged(query: String) = libraryEngine.updateSearchQuery(query)
    fun clearSearch() = libraryEngine.clearSearch()
    fun updateStatsFilter(newFilter: String) = statsEngine.updateStatsFilter(newFilter)

    // --- AUDIO FIDELITY & EQ DELEGATION ---

    // 🔥 NEW: Routes UI toggles to the EqEngine which instructs the MediaController
    fun toggleBitPerfect() = eqEngine.toggleBitPerfect()

    // 🔥 NEW: Loads a specific AutoEq parametric profile
    fun loadEqProfile(profile: PeqProfile) = eqEngine.loadProfile(profile)

    // --- PLAYBACK DELEGATION ---

    fun playTrack(track: AudioTrack) = playbackEngine.playTrack(track, _uiState.value.tracks)
    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int) = playbackEngine.setPlaylistAndPlay(tracks, startIndex)
    fun togglePlayPause() = playbackEngine.togglePlayPause(_uiState.value.currentTrack, _uiState.value.isPlaying)
    fun seekTo(position: Long) = playbackEngine.seekTo(position, _uiState.value.currentTrack)
    fun toggleShuffle() = playbackEngine.toggleShuffle(_uiState.value.currentTrack)
    fun toggleRepeat() = playbackEngine.toggleRepeat(_uiState.value.currentTrack)
    fun skipToNext() = playbackEngine.skipToNext(_uiState.value.currentTrack)
    fun skipToPrevious() = playbackEngine.skipToPrevious(_uiState.value.currentTrack)
    fun addToQueue(track: AudioTrack) = playbackEngine.addToQueue(track)
    fun addNextToQueue(track: AudioTrack) = playbackEngine.addToQueue(track)
    fun removeFromQueue(track: AudioTrack) = playbackEngine.removeFromQueue(track)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackEngine.moveQueueItem(fromIndex, toIndex)

    // --- METADATA DELEGATION ---

    fun fetchArtistProfile(artistName: String) = viewModelScope.launch(Dispatchers.IO) { metadataEngine.fetchArtistProfile(artistName) }

    // --- PLAYLIST & GRIMOIRE VAULTS DELEGATION ---

    fun createPlaylist(name: String) = viewModelScope.launch { libraryEngine.createPlaylist(name) }
    fun deletePlaylist(playlistId: Long) = viewModelScope.launch(Dispatchers.IO) { libraryEngine.deletePlaylist(playlistId) }
    fun renamePlaylist(playlistId: Long, newName: String) = viewModelScope.launch(Dispatchers.IO) { libraryEngine.renamePlaylist(playlistId, newName) }
    fun addTrackToPlaylist(playlistId: Long, track: AudioTrack) = viewModelScope.launch { libraryEngine.addTrackToPlaylist(playlistId, track.id) }
    fun getPlaylistTracks(playlistId: Long): Flow<List<AudioTrack>> = libraryEngine.getPlaylistTracks(playlistId, _uiState.value.tracks)

    fun removeTrackFromPlaylist(playlistId: Long, track: AudioTrack) = viewModelScope.launch(Dispatchers.IO) { libraryEngine.removeTrackFromPlaylist(playlistId, track.id) }
    fun savePlaylistOrder(playlistId: Long, tracks: List<AudioTrack>) = viewModelScope.launch(Dispatchers.IO) { libraryEngine.savePlaylistOrder(playlistId, tracks) }
    fun updatePlaylistImage(context: Context, playlistId: Long, uri: Uri) = viewModelScope.launch(Dispatchers.IO) { libraryEngine.updatePlaylistArtwork(context, playlistId, uri) }

    fun toggleTrackLikeStatus(track: AudioTrack) = viewModelScope.launch {
        val newState = libraryEngine.toggleTrackLikeStatus(track)
        _uiState.update { it.withUpdatedLike(track.id, newState) }
    }

    private fun PlayerUiState.withUpdatedLike(trackId: String, liked: Boolean): PlayerUiState {
        return copy(
            tracks = tracks.map { if (it.id == trackId) it.copy(isLiked = liked) else it },
            filteredTracks = filteredTracks.map { if (it.id == trackId) it.copy(isLiked = liked) else it },
            queue = queue.map { if (it.id == trackId) it.copy(isLiked = liked) else it },
            currentTrack = currentTrack?.let { if (it.id == trackId) it.copy(isLiked = liked) else it }
        )
    }

    // --- NETWORK & SYNC ARCHIVES DELEGATION ---

    fun syncNavidromeArchives() = viewModelScope.launch(Dispatchers.IO) { networkEngine.syncNavidromeArchives() }
    fun connectToNavidrome(url: String, user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            networkEngine.connectToNavidrome(url, user, pass).onSuccess {
                refreshLibrary()
                onResult(true, null)
            }.onFailure { error -> onResult(false, error.message ?: "The ritual was rejected by the server.") }
        }
    }
    fun refreshLibrary() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        networkEngine.getUnifiedLibraryFlow().catch { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
            .collect { unifiedTracks ->
                val aggregation = libraryEngine.aggregateLibrary(unifiedTracks, _uiState.value.searchQuery)
                _uiState.update { it.copy(tracks = aggregation.tracks, filteredTracks = aggregation.filteredTracks, artists = aggregation.artists, albums = aggregation.albums, isLoading = false) }
            }
    }
    fun loadLocalAudio(forceRefresh: Boolean = false) = viewModelScope.launch {
        networkEngine.getLocalAudioScanFlow(forceRefresh).onStart { _uiState.update { it.copy(isLoading = true, isScanning = true) } }
            .catch { _uiState.update { it.copy(isScanning = false, isLoading = false) } }
            .collect { status ->
                when (status) {
                    is LocalAudioDataSource.ScanStatus.Started -> _uiState.update { it.copy(isScanning = true, totalFilesToScan = status.total) }
                    is LocalAudioDataSource.ScanStatus.InProgress -> _uiState.update { it.copy(isScanning = true, scanProgress = status.current, totalFilesToScan = status.total) }
                    is LocalAudioDataSource.ScanStatus.Complete -> {
                        val aggregation = libraryEngine.aggregateLibrary(status.tracks, _uiState.value.searchQuery)
                        _uiState.update { it.copy(tracks = aggregation.tracks, filteredTracks = aggregation.filteredTracks, artists = aggregation.artists, albums = aggregation.albums, isScanning = false, isLoading = false) }
                    }
                }
            }
    }
    fun onAuthSuccess(newToken: String) = networkEngine.onAuthSuccess(newToken)

    // --- ENGINE INITIALIZATION OBSERVERS ---

    private fun performAutoRitual() {
        viewModelScope.launch {
            networkEngine.autoConnectEvent.collect { isConnected ->
                if (isConnected) loadLocalAudio()
            }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            val unifiedFlow = networkEngine.getUnifiedLibraryFlow()
            libraryEngine.getAggregatedLibraryFlow(unifiedFlow).collect { aggregation ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        tracks = aggregation.tracks,
                        filteredTracks = aggregation.filteredTracks,
                        artists = aggregation.artists,
                        albums = aggregation.albums,
                        searchQuery = libraryEngine.searchQuery.value
                    )
                }
                metadataEngine.preSeedArtistCache(aggregation.tracks)
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            libraryEngine.getHomeUiStateFlow(_uiState.map { it.tracks }).collect { state -> _homeUiState.value = state }
        }
    }

    private fun observePlaybackEngine() {
        viewModelScope.launch { playbackEngine.isPlaying.collect { isPlaying -> _uiState.update { it.copy(isPlaying = isPlaying) } } }

        viewModelScope.launch {
            var lastRecordedMediaId: String? = null
            playbackEngine.currentPosition.collect { currentPos ->
                _uiState.update { it.copy(currentPosition = currentPos) }
                val currentTrack = _uiState.value.currentTrack
                if (currentTrack != null && currentTrack.id != lastRecordedMediaId && currentPos > 10000) {
                    statsEngine.recordPlaybackHistory(currentTrack)
                    lastRecordedMediaId = currentTrack.id
                }
            }
        }

        viewModelScope.launch {
            playbackEngine.currentMediaId.collect { mediaId ->
                val track = _uiState.value.tracks.find { it.id == mediaId }
                if (track != null && _uiState.value.currentTrack?.id != track.id) {
                    _uiState.update { it.copy(currentTrack = track, currentLyrics = null, artistImageUrl = null) }
                    metadataEngine.clearTrackMetadata()
                    metadataEngine.fetchTrackMetadata(track)
                }
            }
        }
        viewModelScope.launch { playbackEngine.getQueueFlow(_uiState.value.tracks).collect { resolvedQueue -> _uiState.update { it.copy(queue = resolvedQueue) } } }
        viewModelScope.launch { playbackEngine.shuffleModeEnabled.collect { isShuffle -> _uiState.update { it.copy(isShuffleEnabled = isShuffle) } } }
        viewModelScope.launch { playbackEngine.repeatMode.collect { mode -> _uiState.update { it.copy(repeatMode = mode) } } }
        viewModelScope.launch {
            playbackEngine.spotifyRemoteTrack.filterNotNull().collect { tempTrack ->
                _uiState.update { it.copy(isPlaying = true, currentTrack = tempTrack, currentLyrics = null) }
                viewModelScope.launch(Dispatchers.IO) {
                    val uri = metadataEngine.fetchSpotifyHighResArt(tempTrack.title, tempTrack.artist)
                    if (uri != null) {
                        _uiState.update { state ->
                            val current = state.currentTrack
                            if (current?.id == "spotify:remote" && current.title == tempTrack.title) {
                                state.copy(currentTrack = current.copy(artworkUri = uri))
                            } else state
                        }
                    }
                }
            }
        }
    }

    private fun observeMetadataEngine() {
        viewModelScope.launch { metadataEngine.currentLyrics.collect { lyrics -> _uiState.update { it.copy(currentLyrics = lyrics) } } }
        viewModelScope.launch { metadataEngine.currentArtistImageUrl.collect { imageUrl -> _uiState.update { it.copy(artistImageUrl = imageUrl) } } }
    }
}