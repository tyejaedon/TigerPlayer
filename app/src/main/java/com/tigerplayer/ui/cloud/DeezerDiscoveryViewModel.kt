package com.tigerplayer.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.repository.DeezerAlbum
import com.tigerplayer.data.repository.DeezerArtist
import com.tigerplayer.data.repository.DeezerRepository
import com.tigerplayer.data.repository.MediaDataRepository
import com.tigerplayer.engine.PlaybackEngine
import com.tigerplayer.utils.ArtistUtils
import com.tigerplayer.utils.MusicMetadataSearch.cleanSearchTerm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeezerSearchTab(val label: String) {
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums")
}

@HiltViewModel
class DeezerDiscoveryViewModel @Inject constructor(
    private val deezerRepository: DeezerRepository,
    private val playbackEngine: PlaybackEngine,
    private val audioRepository: AudioRepository,
    private val mediaDataRepository: MediaDataRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSearchTab = MutableStateFlow(DeezerSearchTab.TRACKS)
    val selectedSearchTab: StateFlow<DeezerSearchTab> = _selectedSearchTab.asStateFlow()

    private val _chartTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val chartTracks: StateFlow<List<AudioTrack>> = _chartTracks.asStateFlow()

    private val _chartArtists = MutableStateFlow<List<DeezerArtist>>(emptyList())
    val chartArtists: StateFlow<List<DeezerArtist>> = _chartArtists.asStateFlow()

    private val _searchTrackResults = MutableStateFlow<List<AudioTrack>>(emptyList())
    val searchTrackResults: StateFlow<List<AudioTrack>> = _searchTrackResults.asStateFlow()

    private val _searchArtistResults = MutableStateFlow<List<DeezerArtist>>(emptyList())
    val searchArtistResults: StateFlow<List<DeezerArtist>> = _searchArtistResults.asStateFlow()

    private val _searchAlbumResults = MutableStateFlow<List<DeezerAlbum>>(emptyList())
    val searchAlbumResults: StateFlow<List<DeezerAlbum>> = _searchAlbumResults.asStateFlow()

    private val _drilldownTitle = MutableStateFlow<String?>(null)
    val drilldownTitle: StateFlow<String?> = _drilldownTitle.asStateFlow()

    private val _drilldownTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val drilldownTracks: StateFlow<List<AudioTrack>> = _drilldownTracks.asStateFlow()

    private val _isLoadingCharts = MutableStateFlow(false)
    val isLoadingCharts: StateFlow<Boolean> = _isLoadingCharts.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _previewTrack = MutableStateFlow<AudioTrack?>(null)
    val previewTrack: StateFlow<AudioTrack?> = _previewTrack.asStateFlow()

    val isPreviewPlaying: StateFlow<Boolean> = combine(
        _previewTrack,
        playbackEngine.currentMediaId,
        playbackEngine.isPlaying
    ) { preview, activeId, playing ->
        preview != null && preview.id == activeId && playing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val previewRemainingSeconds: StateFlow<Int> = combine(
        _previewTrack,
        playbackEngine.currentMediaId,
        playbackEngine.currentPosition
    ) { preview, activeId, positionMs ->
        if (preview != null && preview.id == activeId) {
            val elapsedSec = (positionMs / 1000L).toInt().coerceIn(0, PREVIEW_DURATION_SECONDS)
            (PREVIEW_DURATION_SECONDS - elapsedSec).coerceIn(0, PREVIEW_DURATION_SECONDS)
        } else {
            PREVIEW_DURATION_SECONDS
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PREVIEW_DURATION_SECONDS)

    init {
        loadCharts()
        observeDebouncedSearch()
    }

    fun loadCharts() {
        viewModelScope.launch {
            _isLoadingCharts.value = true
            try {
                deezerRepository.getChartTracks().collect { result ->
                    result.onSuccess { _chartTracks.value = it }
                        .onFailure { _errorMessage.value = it.message ?: "Failed to load Deezer charts." }
                }
                deezerRepository.getChartArtists().collect { result ->
                    result.onSuccess { _chartArtists.value = it }
                }
            } finally {
                _isLoadingCharts.value = false
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeDebouncedSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query ->
                    val trimmed = query.trim()
                    if (trimmed.isBlank()) {
                        _searchTrackResults.value = emptyList()
                        _searchArtistResults.value = emptyList()
                        _searchAlbumResults.value = emptyList()
                        return@collect
                    }
                    executeSearch(trimmed)
                }
        }
    }

    private suspend fun executeSearch(query: String) {
        _isSearching.value = true
        try {
            deezerRepository.searchTracks(query).collect { result ->
                result.onSuccess { _searchTrackResults.value = it }
                    .onFailure { _errorMessage.value = it.message ?: "Track search failed." }
            }
            deezerRepository.searchArtists(query).collect { result ->
                result.onSuccess { _searchArtistResults.value = it }
            }
            deezerRepository.searchAlbums(query).collect { result ->
                result.onSuccess { _searchAlbumResults.value = it }
            }
        } finally {
            _isSearching.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSearchTabSelected(tab: DeezerSearchTab) {
        _selectedSearchTab.value = tab
    }

    fun playPreview(track: AudioTrack) {
        if (track.uri.toString().isBlank()) {
            _statusMessage.value = "No 30s preview available for ${track.title}"
            return
        }
        _previewTrack.value = track
        playbackEngine.playPreview(track)
    }

    fun togglePreviewPlayPause() {
        val current = _previewTrack.value ?: return
        playbackEngine.togglePlayPause(current, isPreviewPlaying.value)
    }

    fun stopPreview() {
        val current = _previewTrack.value
        if (current != null && isPreviewPlaying.value) {
            playbackEngine.togglePlayPause(current, true)
        }
        _previewTrack.value = null
    }

    fun openArtistTopTracks(artist: DeezerArtist) {
        _drilldownTitle.value = artist.name
        viewModelScope.launch {
            _isSearching.value = true
            try {
                deezerRepository.getArtistTopTracks(artist.id).collect { result ->
                    result.onSuccess { _drilldownTracks.value = it }
                        .onFailure { _errorMessage.value = it.message }
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun openAlbumTracks(album: DeezerAlbum) {
        _drilldownTitle.value = album.title
        viewModelScope.launch {
            _isSearching.value = true
            try {
                deezerRepository.getAlbumTracks(album.id).collect { result ->
                    result.onSuccess { _drilldownTracks.value = it }
                        .onFailure { _errorMessage.value = it.message }
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun closeDrilldown() {
        _drilldownTitle.value = null
        _drilldownTracks.value = emptyList()
    }

    fun findAndPlayLocalMatch(track: AudioTrack) {
        viewModelScope.launch {
            try {
                val localTracks = audioRepository.getCachedLocalTracks().firstOrNull().orEmpty()
                val targetTitle = cleanSearchTerm(track.title).lowercase()
                val targetArtist = ArtistUtils.getBaseArtist(track.artist).lowercase()
                val match = localTracks.firstOrNull { local ->
                    cleanSearchTerm(local.title).lowercase() == targetTitle &&
                        ArtistUtils.getBaseArtist(local.artist).lowercase() == targetArtist
                }
                if (match != null) {
                    playbackEngine.playTrack(match, localTracks)
                    _statusMessage.value = "Playing local match: ${match.title}"
                } else {
                    _statusMessage.value = "No local library match found for ${track.title}"
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _statusMessage.value = "Local library lookup failed"
            }
        }
    }

    fun triggerInfinitePlayFromPreview(track: AudioTrack) {
        viewModelScope.launch {
            try {
                val recommendations = mediaDataRepository.getInfinitePlayRecommendations(track, limit = 12)
                if (recommendations.isNotEmpty()) {
                    playbackEngine.playTrack(recommendations.first(), recommendations)
                    _statusMessage.value = "Started Infinite Play (${recommendations.size} tracks)"
                } else {
                    _statusMessage.value = "No matching local tracks found for Infinite Play"
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _statusMessage.value = "Could not start Infinite Play"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    companion object {
        const val PREVIEW_DURATION_SECONDS = 30
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
