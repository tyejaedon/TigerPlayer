package com.example.tigerplayer.ui.cloud

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.example.tigerplayer.data.repository.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.tigerplayer.data.repository.SpotifyAuthManager

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val authManager: SpotifyAuthManager
) : ViewModel() {

    // --- ARCHIVE STREAMS ---
    val userAlbums = spotifyRepository.userAlbums
    val userPlaylists = spotifyRepository.userPlaylists
    val isSpotifyConnected = spotifyRepository.isConnected

    private val _currentPlaylistTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val currentPlaylistTracks = _currentPlaylistTracks.asStateFlow()

    private val _isLoadingTracks = MutableStateFlow(false)
    val isLoadingTracks = _isLoadingTracks.asStateFlow()

    private val _isLoadingAlbums = MutableStateFlow(false)
    val isLoadingAlbums = _isLoadingAlbums.asStateFlow()

    // --- THE SEARCH FIX ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // --- THE MISSING RITUALS: REACTIVE FILTERING ---
    val filteredPlaylists = combine(_searchQuery, userPlaylists) { query, playlists ->
        if (query.isBlank()) playlists
        else playlists.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAlbums = combine(_searchQuery, userAlbums) { query, albums ->
        if (query.isBlank()) albums
        else albums.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.artists.any { a -> a.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    init {
        // Listen to the token continuously!
        viewModelScope.launch {
            authManager.token.collectLatest { currentToken ->
                if (currentToken.isNotEmpty()) {
                    Log.d("CloudVM", "Token detected! Initiating cloud sync...")
                    // Automatically fetch data when a valid token arrives
                    fetchSavedAlbums()
                    fetchUserPlaylists()
                } else {
                    Log.w("CloudVM", "Token cleared or missing. Waiting...")
                }
            }
        }
    }

    /**
     * Updated ritual to use the Suspend function to ensure validity
     */
    private suspend fun ensureValidToken(): String? {
        val token = authManager.getValidToken()
        if (token.isEmpty()) {
            Log.w("CloudVM", "Ritual aborted: No valid token available.")
            return null
        }
        return token
    }

    fun fetchTracksForPlaylist(playlistId: String) {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch

            _currentPlaylistTracks.value = emptyList()
            _isLoadingTracks.value = true

            try {
                val tracks = spotifyRepository.fetchPlaylistTracks(token, playlistId)
                _currentPlaylistTracks.value = tracks
                Log.d("CloudVM", "Manifested ${tracks.size} tracks from the cloud.")
            } catch (e: Exception) {
                Log.e("CloudVM", "The fetch ritual failed: ${e.message}")
            } finally {
                _isLoadingTracks.value = false
            }
        }
    }

    fun fetchTracksForAlbum(albumId: String) {
        viewModelScope.launch {
            val token = ensureValidToken() ?: return@launch

            _isLoadingTracks.value = true
            try {
                val tracks = spotifyRepository.fetchAlbumTracks(token, albumId)
                _currentPlaylistTracks.value = tracks
                Log.d("CloudVM", "Successfully retrieved ${tracks.size} tracks for album $albumId")
            } catch (e: Exception) {
                Log.e("CloudVM", "Ritual failed: ${e.message}")
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
     * Commands the Spotify App Remote to begin playback.
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
}