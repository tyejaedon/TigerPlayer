package com.example.tigerplayer.ui.cloud

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.example.tigerplayer.data.repository.SpotifyRepository
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val authManager: SpotifyAuthManager
) : ViewModel() {

    // --- THE CLOUD ARCHIVES ---
    val userAlbums = spotifyRepository.userAlbums
    val userPlaylists = spotifyRepository.userPlaylists
    val isSpotifyConnected = spotifyRepository.isConnected

    private val _currentPlaylistTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val currentPlaylistTracks = _currentPlaylistTracks.asStateFlow()

    // --- BULLETPROOFING UI STATE ---
    private val _isLoadingTracks = MutableStateFlow(false)
    val isLoadingTracks = _isLoadingTracks.asStateFlow()

    private val _isLoadingAlbums = MutableStateFlow(false)
    val isLoadingAlbums = _isLoadingAlbums.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError = _uiError.asStateFlow()

    // Guards against infinite reload loops when OAuth token refreshes
    private var initialSyncComplete = false

    // --- THE REACTIVE FILTERS (With Debounce) ---
    @OptIn(FlowPreview::class)
    val filteredPlaylists = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .combine(userPlaylists) { query, playlists ->
            if (query.isBlank()) playlists
            else playlists.filter { it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class)
    val filteredAlbums = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .combine(userAlbums) { query, albums ->
            if (query.isBlank()) albums
            else albums.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.artists.any { a -> a.name.contains(query, ignoreCase = true) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // CONTINUOUS MONITORING
        viewModelScope.launch {
            authManager.token.collectLatest { currentToken ->
                if (currentToken.isNotEmpty() && !initialSyncComplete) {
                    initialSyncComplete = true
                    Log.d("CloudVM", "Initial token detected! Initiating cloud sync...")
                    forceRefreshArchives()
                }
            }
        }
    }

    private suspend fun ensureValidToken(): String? {
        val token = authManager.getValidToken()
        if (token.isEmpty()) {
            _uiError.value = "The oracle requires authentication."
            return null
        }
        return token
    }

    /**
     * Called by UI to force a refresh (e.g. Pull-to-refresh or Button click)
     */
    fun forceRefreshArchives() {
        fetchSavedAlbums()
        fetchUserPlaylists()
    }

    fun clearError() {
        _uiError.value = null
    }

    /**
     * Manifests tracks for a specific playlist.
     */
    fun fetchTracksForPlaylist(playlistId: String) {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch
            clearCurrentTracks()
            _isLoadingTracks.value = true

            try {
                _currentPlaylistTracks.value = spotifyRepository.fetchPlaylistTracks(token, playlistId)
            } catch (_: Exception) {
                _uiError.value = "Failed to manifest tracks."
            } finally {
                _isLoadingTracks.value = false
            }
        }
    }

    fun fetchTracksForAlbum(albumId: String) {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch
            clearCurrentTracks()
            _isLoadingTracks.value = true

            try {
                _currentPlaylistTracks.value = spotifyRepository.fetchAlbumTracks(token, albumId)
            } catch (_: Exception) {
                _uiError.value = "Failed to manifest album."
            } finally {
                _isLoadingTracks.value = false
            }
        }
    }

    private fun fetchSavedAlbums() {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch
            _isLoadingAlbums.value = true
            try {
                spotifyRepository.fetchUserSavedAlbums(token)
            } catch (_: Exception) {
                _uiError.value = "Failed to retrieve your album grimoires."
            } finally {
                _isLoadingAlbums.value = false
            }
        }
    }

    private fun fetchUserPlaylists() {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch
            _isLoadingTracks.value = true
            try {
                spotifyRepository.fetchUserPlaylists(token)
            } catch (_: Exception) {
                _uiError.value = "Failed to retrieve your playlist grimoires."
            } finally {
                _isLoadingTracks.value = false
            }
        }
    }

    fun playSpotifyUri(uri: String) {
        viewModelScope.launch {
            if (!isSpotifyConnected.value) {
                spotifyRepository.connect()
            }
            spotifyRepository.playUri(uri)
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearCurrentTracks() {
        _currentPlaylistTracks.value = emptyList()
    }
}