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

    private val _isLoadingTracks = MutableStateFlow(false)
    val isLoadingTracks = _isLoadingTracks.asStateFlow()

    private val _isLoadingAlbums = MutableStateFlow(false)
    val isLoadingAlbums = _isLoadingAlbums.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // --- THE REACTIVE FILTERS (With Debounce) ---
    @OptIn(FlowPreview::class)
    val filteredPlaylists = _searchQuery
        .debounce(300) // Wait for the user to stop typing
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
        // CONTINUOUS MONITORING: Automatically sync when the token is forged
        viewModelScope.launch {
            authManager.token.collectLatest { currentToken ->
                if (currentToken.isNotEmpty()) {
                    Log.d("CloudVM", "Token detected! Initiating cloud sync...")
                    fetchSavedAlbums()
                    fetchUserPlaylists()
                }
            }
        }
    }

    private suspend fun ensureValidToken(): String? {
        val token = authManager.getValidToken()
        if (token.isEmpty()) {
            Log.w("CloudVM", "Ritual aborted: No valid token available.")
            return null
        }
        return token
    }

    /**
     * Manifests tracks for a specific playlist.
     */
    fun fetchTracksForPlaylist(playlistId: String) {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch

            clearCurrentTracks() // Banish previous results
            _isLoadingTracks.value = true

            try {
                val tracks = spotifyRepository.fetchPlaylistTracks(token, playlistId)
                _currentPlaylistTracks.value = tracks
            } catch (e: Exception) {
                Log.e("CloudVM", "Playlist fetch failed: ${e.message}")
            } finally {
                _isLoadingTracks.value = false
            }
        }
    }

    /**
     * Manifests tracks for a specific album.
     */
    fun fetchTracksForAlbum(albumId: String) {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch

            clearCurrentTracks()
            _isLoadingTracks.value = true

            try {
                val tracks = spotifyRepository.fetchAlbumTracks(token, albumId)
                _currentPlaylistTracks.value = tracks
            } catch (e: Exception) {
                Log.e("CloudVM", "Album fetch failed: ${e.message}")
            } finally {
                _isLoadingTracks.value = false
            }
        }
    }

    fun fetchSavedAlbums() {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch
            _isLoadingAlbums.value = true
            spotifyRepository.fetchUserSavedAlbums(token)
            _isLoadingAlbums.value = false
        }
    }


    fun fetchUserPlaylists() {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch
            _isLoadingTracks.value = true
            spotifyRepository.fetchUserPlaylists(token)
            _isLoadingTracks.value = false
        }
    }

    /**
     * Commands the Spotify App Remote.
     */
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