package com.example.tigerplayer.ui.cloud

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.example.tigerplayer.data.repository.SpotifyRepository
import com.example.tigerplayer.ui.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.tigerplayer.data.repository.SpotifyAuthManager

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val authManager: SpotifyAuthManager // Use this instead of PlayerViewModel
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

    /**
     * Common ritual to ensure the token is still pure before striking.
     */
    private fun ensureValidToken(): String? {
        // Get the token directly from the shared manager
        val token = authManager.getToken()

        if (token.isEmpty()) {
            Log.w("CloudVM", "Ritual aborted: No token found.")
            return null
        }
        return token
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // --- REACTIVE FILTERING ---
    val filteredPlaylists = combine(_searchQuery, spotifyRepository.userPlaylists) { query, playlists ->
        if (query.isBlank()) playlists
        else playlists.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAlbums = combine(_searchQuery, spotifyRepository.userAlbums) { query, albums ->
        if (query.isBlank()) albums
        else albums.filter { it.name.contains(query, ignoreCase = true) || it.artists.any { a -> a.name.contains(query, ignoreCase = true) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Fetches specific tracks for a chosen playlist.
     */
    fun fetchTracksForPlaylist(playlistId: String) {
        val token = ensureValidToken() ?: return

        viewModelScope.launch {
            // THE FIX: Clear existing tracks so the UI shows Loading state
            _currentPlaylistTracks.value = emptyList()
            _isLoadingTracks.value = true

            try {
                // Ensure your repository is fetching specifically from the
                // playlist tracks endpoint, not the album tracks endpoint
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
    /**
     * Summons the tracks for a specific album from the Spotify Cloud.
     */
    fun fetchTracksForAlbum(albumId: String) {
        // 1. Validate the ritual token (Using the same pattern as your other functions)
        val token = ensureValidToken() ?: return

        viewModelScope.launch {
            _isLoadingTracks.value = true
            try {
                // 2. Use the token we already retrieved and validated
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


    /**
     * Fetches the user's saved albums from the Spotify Cloud.
     */
    fun fetchSavedAlbums() {
        val token = ensureValidToken() ?: return

        viewModelScope.launch {
            _isLoadingAlbums.value = true
            spotifyRepository.fetchUserSavedAlbums(token)
            _isLoadingAlbums.value = false
        }
    }

    fun fetchUserPlaylists() {
        val token = ensureValidToken() ?: return

        viewModelScope.launch {
            // You might want to add a _isLoadingPlaylists flow here too
            _isLoadingTracks.value = true // Re-using loading state for now

            spotifyRepository.fetchUserPlaylists(token)

            _isLoadingTracks.value = false
        }
    }


    /**
     * Commands the Spotify App Remote to begin playback.
     */
    fun playSpotifyUri(uri: String) {
        viewModelScope.launch {
            // Re-establish link if the S22 has dropped it
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