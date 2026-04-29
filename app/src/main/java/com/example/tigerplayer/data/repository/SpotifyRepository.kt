package com.example.tigerplayer.data.repository

import android.content.Context
import android.util.Log
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.model.SpotifyAlbum
import com.example.tigerplayer.data.remote.model.SpotifyPlaylist
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val spotifyApiService: SpotifyApiService,
    val authManager: SpotifyAuthManager
) {
    private val clientId = "3a9ef0f202a04e6290cf0cb3b32dd3ab"
    private val redirectUri = "tigerplayer://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null

    // --- 1. CONNECTION STREAMS ---

    private val _isRemoteConnected = MutableStateFlow(false)
    private val _currentSpotifyTrack = MutableStateFlow<String?>("Not Playing")
    val currentSpotifyTrack: StateFlow<String?> = _currentSpotifyTrack.asStateFlow()

    val isConnected: StateFlow<Boolean> = authManager.token
        .map { it.isNotEmpty() }
        .stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, false)

    // --- 2. THE ARCHIVE VAULTS ---

    private val _userPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val userPlaylists = _userPlaylists.asStateFlow()

    private val _userAlbums = MutableStateFlow<List<SpotifyAlbum>>(emptyList())
    val userAlbums = _userAlbums.asStateFlow()

    // --- 3. AUTHENTICATION RITUALS ---

    suspend fun verifyTokenWithServer(): Boolean = withContext(Dispatchers.IO) {
        val token = authManager.getToken()
        if (token.isEmpty()) return@withContext false
        try {
            val response = spotifyApiService.getUserPlaylists("Bearer $token", limit = 1)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // --- 4. THE THREAD-SAFE FETCHERS ---

    suspend fun fetchUserPlaylists(token: String) = withContext(Dispatchers.IO) {
        try {
            val response = spotifyApiService.getUserPlaylists("Bearer $token")
            if (response.isSuccessful) {
                // Safeguard against null items occasionally returned by Spotify's pagination
                _userPlaylists.value = response.body()?.items?.filterNotNull() ?: emptyList()
            } else {
                Log.e("SpotifyRepo", "Playlist fetch rejected: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Playlist network failure", e)
            throw e // Re-throw so the ViewModel can catch it and notify the UI
        }
    }

    suspend fun fetchUserSavedAlbums(token: String) = withContext(Dispatchers.IO) {
        try {
            val response = spotifyApiService.getUserSavedAlbums("Bearer $token")
            if (response.isSuccessful) {
                _userAlbums.value = response.body()?.items?.mapNotNull { it.album } ?: emptyList()
            } else {
                Log.e("SpotifyRepo", "Album fetch rejected: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Album network failure", e)
            throw e
        }
    }

    suspend fun fetchPlaylistTracks(token: String, playlistId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        try {
            val response = spotifyApiService.getPlaylistTracks("Bearer $token", playlistId)
            if (response.isSuccessful) {
                response.body()?.items?.mapNotNull { it.track } ?: emptyList()
            } else {
                Log.e("SpotifyRepo", "Track fetch rejected: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Track fetch network failure", e)
            emptyList()
        }
    }

    suspend fun fetchAlbumTracks(token: String, albumId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        try {
            val response = spotifyApiService.getAlbumTracks("Bearer $token", albumId)
            if (response.isSuccessful) {
                response.body()?.items ?: emptyList()
            } else {
                Log.e("SpotifyRepo", "Album tracks rejected: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Album tracks network failure", e)
            emptyList()
        }
    }

    // --- 5. APP REMOTE (IPC PLAYBACK) ---

    fun connect() {
        if (_isRemoteConnected.value) return

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        try {
            SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    spotifyAppRemote = appRemote
                    _isRemoteConnected.value = true
                    subscribeToPlayerState()
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e("SpotifyRepo", "App Remote connection failed", throwable)
                    _isRemoteConnected.value = false
                }
            })
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "IPC Binder Exception during connect", e)
        }
    }

    fun playUri(uri: String) {
        val remote = spotifyAppRemote
        if (_isRemoteConnected.value && remote != null) {
            remote.playerApi.play(uri)
        } else {
            _isRemoteConnected.value = false
            connect()
            // In a true flagship, you'd enqueue this URI. For now, connection recovery is triggered.
        }
    }

    fun pause() = spotifyAppRemote?.playerApi?.pause()
    fun resume() = spotifyAppRemote?.playerApi?.resume()
    fun skipNext() = spotifyAppRemote?.playerApi?.skipNext()
    fun skipPrevious() = spotifyAppRemote?.playerApi?.skipPrevious()
    fun seekTo(positionMs: Long) = spotifyAppRemote?.playerApi?.seekTo(positionMs)
    fun toggleShuffle() = spotifyAppRemote?.playerApi?.toggleShuffle()
    fun toggleRepeat() = spotifyAppRemote?.playerApi?.toggleRepeat()

    fun disconnect() {
        try {
            spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
        } catch (e: IllegalArgumentException) {
            // Android occasionally unbinds the service before we can disconnect cleanly.
            Log.w("SpotifyRepo", "App Remote was already unbound.")
        } finally {
            _isRemoteConnected.value = false
            spotifyAppRemote = null
            Log.d("SpotifyRepo", "Disconnected from Spotify App Remote.")
        }
    }

    private fun subscribeToPlayerState() {
        try {
            spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
                val track = playerState.track
                if (track != null) {
                    _currentSpotifyTrack.value = "${track.name} • ${track.artist.name}"
                } else {
                    _currentSpotifyTrack.value = "Paused / Stopped"
                }
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Failed to subscribe to player state", e)
        }
    }
}