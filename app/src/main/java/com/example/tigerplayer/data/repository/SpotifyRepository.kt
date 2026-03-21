package com.example.tigerplayer.data.repository

import android.content.Context
import android.util.Log
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.model.SpotifyAlbum
import com.example.tigerplayer.data.remote.model.SpotifyPlaylist // <-- Added Import
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.google.gson.annotations.SerializedName
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
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class SpotifyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spotifyApiService: SpotifyApiService,
    val authManager: SpotifyAuthManager // Inject the manager here!
) {
    private val clientId = "3a9ef0f202a04e6290cf0cb3b32dd3ab"
    private val redirectUri = "tigerplayer://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null

    // --- 1. CONNECTION STREAMS ---

    // Remote connection (The "App Remote" for playing music)
    private val _isRemoteConnected = MutableStateFlow(false)
    val isRemoteConnected = _isRemoteConnected.asStateFlow()
    // THE BACKING PROPERTY: Private and Mutable (The internal vault)
    private val _currentSpotifyTrack = MutableStateFlow<String?>("Not Playing")

    // THE PUBLIC FLOW: Read-only (The window for the ViewModel)
    val currentSpotifyTrack: StateFlow<String?> = _currentSpotifyTrack.asStateFlow()


    // Web Auth connection (The "Cloud Portal" for fetching data)
    // This looks at the authManager's token reactively!
    val isConnected: StateFlow<Boolean> = authManager.token
        .map { it.isNotEmpty() }
        .stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, false)

    // --- 2. AUTHENTICATION RITUALS ---

    fun saveToken(token: String) {
        authManager.updateToken(token)
    }

    suspend fun verifyTokenWithServer(): Boolean {
        val token = authManager.getToken()
        if (token.isEmpty()) return false
        return try {
            val response = spotifyApiService.getUserPlaylists("Bearer $token", limit = 1)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // --- 3. THE ARCHIVE FETCHERS ---

    private val _userPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val userPlaylists = _userPlaylists.asStateFlow()

    private val _userAlbums = MutableStateFlow<List<SpotifyAlbum>>(emptyList())
    val userAlbums = _userAlbums.asStateFlow()

    suspend fun fetchUserPlaylists(token: String) {
        try {
            val response = spotifyApiService.getUserPlaylists("Bearer $token")
            if (response.isSuccessful) {
                _userPlaylists.value = response.body()?.items ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Playlist ritual failed", e)
        }
    }

    suspend fun fetchUserSavedAlbums(token: String) {
        try {
            val response = spotifyApiService.getUserSavedAlbums("Bearer $token")
            if (response.isSuccessful) {
                _userAlbums.value = response.body()?.items?.map { it.album } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Album ritual failed", e)
        }
    }


    /**
     * Summons the specific tracks for a chosen playlist.
     * Playlist tracks are wrapped in a 'track' object by Spotify's API.
     */
    suspend fun fetchPlaylistTracks(token: String, playlistId: String): List<SpotifyTrack> {
        return try {
            val response = spotifyApiService.getPlaylistTracks("Bearer $token", playlistId)
            if (response.isSuccessful) {
                // .mapNotNull extracts the 'track' from the wrapper and discards nulls
                response.body()?.items?.mapNotNull { it.track } ?: emptyList()
            } else {
                Log.e("SpotifyRepo", "Playlist tracks ritual failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Error in playlist track purification", e)
            emptyList()
        }
    }

    /**
     * Summons the tracks for a specific album.
     * Unlike playlists, album tracks are usually direct objects in the list.
     */
    suspend fun fetchAlbumTracks(token: String, albumId: String): List<SpotifyTrack> {
        return try {
            val response = spotifyApiService.getAlbumTracks("Bearer $token", albumId)
            if (response.isSuccessful) {
                // Album tracks are returned directly in the 'items' list
                response.body()?.items ?: emptyList()
            } else {
                Log.e("SpotifyRepo", "Album tracks ritual failed: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Error in album track purification", e)
            emptyList()
        }
    }

    // --- 4. APP REMOTE (PLAYBACK) ---

    fun connect() {
        // RECTIFIED: Use _isRemoteConnected
        if (_isRemoteConnected.value) return

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                // RECTIFIED: Use _isRemoteConnected
                _isRemoteConnected.value = true
                subscribeToPlayerState() // This will work once we add the function below
            }

            override fun onFailure(throwable: Throwable) {
                _isRemoteConnected.value = false
            }
        })
    }

    fun playUri(uri: String) {
        // RECTIFIED: Use _isRemoteConnected
        if (_isRemoteConnected.value) {
            spotifyAppRemote?.playerApi?.play(uri)
        } else {
            connect()
        }
    }


    fun pause() {
        spotifyAppRemote?.playerApi?.pause()
    }

    fun resume() {
        spotifyAppRemote?.playerApi?.resume()
    }

    fun skipNext() {
        spotifyAppRemote?.playerApi?.skipNext()
    }

    fun skipPrevious() {
        spotifyAppRemote?.playerApi?.skipPrevious()
    }
    fun seekTo(positionMs: Long) {
        spotifyAppRemote?.playerApi?.seekTo(positionMs)
    }
    fun toggleShuffle() {
        spotifyAppRemote?.playerApi?.toggleShuffle()
        Log.d("SpotifyRepo", "Toggled Spotify Shuffle")
    }

    fun toggleRepeat() {
        spotifyAppRemote?.playerApi?.toggleRepeat()
        Log.d("SpotifyRepo", "Toggled Spotify Repeat")
    }

    fun disconnect() {
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        _isRemoteConnected.value = false
        spotifyAppRemote = null
        Log.d("SpotifyRepo", "Disconnected from Spotify App Remote.")
    }

    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            val track = playerState.track
            if (track != null) {
                // This updates the "Not Playing" string we defined earlier
                val duration = track.duration
                _currentSpotifyTrack.value = "${track.name} • ${track.artist.name}"
                Log.d("SpotifyRepo", "Spotify is playing: ${track.name}")
            } else {
                _currentSpotifyTrack.value = "Paused / Stopped"
            }
        }
    }
}