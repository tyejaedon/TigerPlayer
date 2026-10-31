package com.tigerplayer.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.M3uExportResult
import com.tigerplayer.data.model.M3uImportResult
import com.tigerplayer.data.model.M3uPathMode
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.data.repository.ArtistDetails
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.source.LocalAudioDataSource
import com.tigerplayer.engine.FolderTreeBuilder
import com.tigerplayer.engine.LibraryEngine
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.engine.NetworkEngine
import com.tigerplayer.ui.player.LibraryArtist
import com.tigerplayer.ui.player.PlaybackStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Encapsulates the library-specific UI state without high-frequency playback position ticks.
 * This prevents entire library views from recomposing every 500ms when audio is playing (issue #72).
 */
data class LibraryUiState(
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
    val searchQuery: String = "",
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryEngine: LibraryEngine,
    private val networkEngine: NetworkEngine,
    private val metadataEngine: MetadataEngine,
    private val audioRepository: AudioRepository,
    val playbackStateManager: PlaybackStateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _rawSearchQuery = MutableStateFlow("")
    val rawSearchQuery: StateFlow<String> = _rawSearchQuery.asStateFlow()

    private val _trackSortOrder = MutableStateFlow(LibraryEngine.SortOrder.TITLE)
    val trackSortOrder: StateFlow<LibraryEngine.SortOrder> = _trackSortOrder.asStateFlow()

    private val _albumSortOrder = MutableStateFlow(LibraryEngine.SortOrder.TITLE)
    val albumSortOrder: StateFlow<LibraryEngine.SortOrder> = _albumSortOrder.asStateFlow()

    private val _playlistSortOrder = MutableStateFlow(LibraryEngine.SortOrder.DATE_ADDED)
    val playlistSortOrder: StateFlow<LibraryEngine.SortOrder> = _playlistSortOrder.asStateFlow()

    private val libraryRefreshTrigger = MutableStateFlow(0)
    private var scanJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val unifiedTracksFlow: SharedFlow<List<AudioTrack>> = libraryRefreshTrigger
        .flatMapLatest { networkEngine.getUnifiedLibraryFlow() }
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val artistDetails: StateFlow<Map<String, ArtistDetails>> = metadataEngine.artistDetails

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

    init {
        // 1. Synchronize aggregated library
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

        // 2. Synchronize playlists into uiState
        viewModelScope.launch {
            customPlaylists.collect { playlists ->
                _uiState.update { it.copy(customPlaylists = playlists) }
            }
        }

        // 3. Synchronize sort orders
        viewModelScope.launch {
            _trackSortOrder.collect { order -> _uiState.update { it.copy(trackSortOrder = order) } }
        }
        viewModelScope.launch {
            _albumSortOrder.collect { order -> _uiState.update { it.copy(albumSortOrder = order) } }
        }
        viewModelScope.launch {
            _playlistSortOrder.collect { order -> _uiState.update { it.copy(playlistSortOrder = order) } }
        }

        // 4. Debounced search query
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

        // 5. Playback state tracking (current track & play status, decoupled from position)
        viewModelScope.launch {
            playbackStateManager.currentTrack.collect { track ->
                _uiState.update { it.copy(currentTrack = track) }
            }
        }
        viewModelScope.launch {
            playbackStateManager.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }
    }

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

    fun onSearchQueryChanged(query: String) {
        _rawSearchQuery.value = query
    }

    fun clearSearch() {
        _rawSearchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "") }
        libraryEngine.clearSearch()
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

    fun getPlaylistTracks(playlistId: Long): Flow<List<AudioTrack>> {
        return libraryEngine.getPlaylistTracks(playlistId, _uiState.value.allTracks)
    }

    fun buildFolderIndex(tracks: List<AudioTrack>): Map<String, FolderTreeBuilder.FolderContents> =
        libraryEngine.buildFolderIndex(tracks)

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

    fun fetchArtistProfile(artistName: String) {
        viewModelScope.launch {
            metadataEngine.fetchArtistProfile(artistName)
        }
    }

    fun observeArtistProfile(artistName: String): Flow<ArtistDetails?> {
        return metadataEngine.observeArtistProfile(artistName)
    }

    suspend fun connectToNavidrome(
        url: String,
        user: String,
        pass: String,
        allowCleartext: Boolean = false
    ): Result<Unit> {
        return networkEngine.connectToNavidrome(url, user, pass, allowCleartext)
    }

    // Playback convenience delegation
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

    fun togglePlayPause() {
        playbackStateManager.togglePlayPause()
    }

    fun addToQueue(track: AudioTrack) {
        playbackStateManager.addToQueue(track)
    }

    fun addNextToQueue(track: AudioTrack) {
        playbackStateManager.addNextToQueue(track)
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
}
