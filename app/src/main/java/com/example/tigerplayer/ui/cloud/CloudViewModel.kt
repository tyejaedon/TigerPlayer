package com.example.tigerplayer.ui.cloud

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.example.tigerplayer.engine.PlaybackEngine
import com.example.tigerplayer.data.repository.SpotifyRepository
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val authManager: SpotifyAuthManager,
    private val playbackEngine: PlaybackEngine
) : ViewModel() {

    // --- THE CLOUD ARCHIVES ---
    val userAlbums = spotifyRepository.userAlbums
    val userPlaylists = spotifyRepository.userPlaylists
    val isSpotifyAuthenticated = spotifyRepository.isAuthenticated
    val isSpotifyRemoteConnected = spotifyRepository.isConnected

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
        // 🔥 THE FIX: Replaced mutable boolean flag with elegant .take(1) stream operation
        viewModelScope.launch {
            authManager.token
                .filter { it.isNotEmpty() }
                .take(1) // Automatically cancels itself after the first valid emission
                .collect {
                    Log.d("CloudVM", "Initial token detected! Initiating cloud sync...")
                    forceRefreshArchives()
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
            } catch (e: Exception) {
                // 🔥 THE FIX: Never swallow CancellationExceptions in Coroutines!
                if (e is CancellationException) throw e
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
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiError.value = "Failed to retrieve your playlist grimoires."
            } finally {
                _isLoadingTracks.value = false
            }
        }
    }

    fun playSpotifyUri(uri: String) {
        viewModelScope.launch {
            playbackEngine.playSpotifyUri(uri)
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearCurrentTracks() {
        _currentPlaylistTracks.value = emptyList()
    }
}