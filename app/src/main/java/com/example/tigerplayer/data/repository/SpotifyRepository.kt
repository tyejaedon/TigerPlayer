package com.example.tigerplayer.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.tigerplayer.BuildConfig
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.model.SpotifyAlbum
import com.example.tigerplayer.data.remote.model.SpotifyImage
import com.example.tigerplayer.data.remote.model.SpotifyPlaylist
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyPlaybackState(
    val track: AudioTrack,
    val isPlaying: Boolean,
    val positionMs: Long,
    val isShuffleEnabled: Boolean = false
)

data class SpotifyCurationResult(
    val daylist: List<AudioTrack>,
    val discoveryWeekly: List<AudioTrack>
)

@Singleton
class SpotifyRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val spotifyApiService: SpotifyApiService,
    val authManager: SpotifyAuthManager
) {
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "tigerplayer://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var pendingUriToPlay: String? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- 1. CONNECTION STREAMS ---

    private val _isRemoteConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isRemoteConnected.asStateFlow()

    val isAuthenticated: StateFlow<Boolean> = authManager.token
        .map { it.isNotEmpty() }
        .stateIn(repositoryScope, SharingStarted.Eagerly, false)

    private val _spotifyPlaybackState = MutableStateFlow<SpotifyPlaybackState?>(null)
    val spotifyPlaybackState: StateFlow<SpotifyPlaybackState?> = _spotifyPlaybackState.asStateFlow()

    val currentSpotifyTrack: StateFlow<String?> = _spotifyPlaybackState
        .map { state ->
            state?.track?.let { "${it.title} • ${it.artist}" } ?: "Not Playing"
        }
        .stateIn(repositoryScope, SharingStarted.Eagerly, "Not Playing")

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

    suspend fun fetchAllUserPlaylists(
        token: String,
        pageSize: Int = 50,
        maxPages: Int = 6
    ): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        var pages = 0

        while (pages < maxPages) {
            val response = try {
                spotifyApiService.getUserPlaylists(
                    bearerToken = "Bearer $token",
                    limit = pageSize,
                    offset = offset
                )
            } catch (e: Exception) {
                Log.e("SpotifyRepo", "Paged playlist fetch failed", e)
                break
            }

            if (!response.isSuccessful) {
                Log.e("SpotifyRepo", "Paged playlist fetch rejected: ${response.code()}")
                break
            }

            val body = response.body() ?: break
            val items = body.items
            if (items.isEmpty()) break

            collected += items
            offset += items.size
            pages += 1

            if (body.next.isNullOrBlank()) break
            if (offset >= body.total) break
        }

        collected
    }

    suspend fun fetchHomeCurations(token: String): SpotifyCurationResult = withContext(Dispatchers.IO) {
        val playlists = fetchAllUserPlaylists(token)

        fun pickPlaylist(keyword: String): SpotifyPlaylist? {
            val candidates = playlists.filter { it.name.contains(keyword, ignoreCase = true) }
            return candidates.firstOrNull { it.owner.id.equals("spotify", ignoreCase = true) }
                ?: candidates.firstOrNull()
        }

        val daylistPlaylist = pickPlaylist("daylist")
        val discoverWeeklyPlaylist = playlists.firstOrNull {
            it.name.equals("Discover Weekly", ignoreCase = true)
        } ?: pickPlaylist("discover weekly")

        val daylistTracks = daylistPlaylist
            ?.let { fetchPlaylistTracks(token, it.id) }
            .orEmpty()
            .map { it.toAudioTrack() }

        val discoveryWeeklyTracks = discoverWeeklyPlaylist
            ?.let { fetchPlaylistTracks(token, it.id) }
            .orEmpty()
            .map { it.toAudioTrack() }

        SpotifyCurationResult(
            daylist = daylistTracks,
            discoveryWeekly = discoveryWeeklyTracks
        )
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

                    pendingUriToPlay?.let { uri ->
                        appRemote.playerApi.play(uri)
                        publishOptimisticPlayback(uri)
                        pendingUriToPlay = null
                    }
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
        pendingUriToPlay = uri
        publishOptimisticPlayback(uri)

        val remote = spotifyAppRemote
        if (_isRemoteConnected.value && remote != null) {
            remote.playerApi.play(uri)
            pendingUriToPlay = null
        } else {
            _isRemoteConnected.value = false
            connect()
        }
    }

    fun pause() {
        spotifyAppRemote?.playerApi?.pause()
        _spotifyPlaybackState.value = _spotifyPlaybackState.value?.copy(isPlaying = false)
    }

    fun resume() {
        spotifyAppRemote?.playerApi?.resume()
        _spotifyPlaybackState.value = _spotifyPlaybackState.value?.copy(isPlaying = true)
    }

    fun skipNext() = spotifyAppRemote?.playerApi?.skipNext()
    fun skipPrevious() = spotifyAppRemote?.playerApi?.skipPrevious()

    fun seekTo(positionMs: Long) {
        spotifyAppRemote?.playerApi?.seekTo(positionMs)
        _spotifyPlaybackState.value = _spotifyPlaybackState.value?.copy(positionMs = positionMs)
    }

    fun toggleShuffle() {
        spotifyAppRemote?.playerApi?.toggleShuffle()
        _spotifyPlaybackState.value = _spotifyPlaybackState.value?.let {
            it.copy(isShuffleEnabled = !it.isShuffleEnabled)
        }
    }
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
            pendingUriToPlay = null
            _spotifyPlaybackState.value = null
            Log.d("SpotifyRepo", "Disconnected from Spotify App Remote.")
        }
    }

    private fun subscribeToPlayerState() {
        try {
            spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
                val track = playerState.track
                if (track == null) {
                    _spotifyPlaybackState.value = null
                    return@setEventCallback
                }

                val resolvedTrack = AudioTrack(
                    id = track.uri,
                    title = track.name,
                    artist = track.artist.name,
                    album = "Spotify",
                    uri = Uri.EMPTY,
                    artworkUri = Uri.EMPTY,
                    durationMs = track.duration,
                    mimeType = "audio/spotify",
                    isLocal = false,
                    isRemote = true,
                    serverPath = null,
                    path = track.uri
                )

                // App Remote versions differ in typed accessors; reflection keeps this resilient.
                val shuffleEnabled = extractShuffleEnabled(
                    playerState = playerState,
                    fallback = _spotifyPlaybackState.value?.isShuffleEnabled ?: false
                )

                _spotifyPlaybackState.value = SpotifyPlaybackState(
                    track = resolvedTrack,
                    isPlaying = !playerState.isPaused,
                    positionMs = playerState.playbackPosition,
                    isShuffleEnabled = shuffleEnabled
                )
            }
        } catch (e: Exception) {
            Log.e("SpotifyRepo", "Failed to subscribe to player state", e)
        }
    }

    private fun publishOptimisticPlayback(uri: String) {
        _spotifyPlaybackState.value = SpotifyPlaybackState(
            track = audioTrackFromUri(uri),
            isPlaying = true,
            positionMs = 0L,
            isShuffleEnabled = _spotifyPlaybackState.value?.isShuffleEnabled ?: false
        )
    }

    private fun extractShuffleEnabled(playerState: Any, fallback: Boolean): Boolean {
        val playbackOptions = runCatching {
            playerState.javaClass.getMethod("getPlaybackOptions").invoke(playerState)
        }.getOrNull() ?: return fallback

        return runCatching {
            playbackOptions.javaClass.getMethod("isShuffling").invoke(playbackOptions) as? Boolean
        }.getOrNull() ?: fallback
    }

    private fun audioTrackFromUri(uri: String): AudioTrack {
        val titleSeed = uri.substringAfterLast(":", "Spotify")
            .replace('-', ' ')
            .ifBlank { "Spotify" }

        return AudioTrack(
            id = uri,
            title = titleSeed,
            artist = "Spotify",
            album = "Spotify",
            uri = Uri.EMPTY,
            artworkUri = Uri.EMPTY,
            durationMs = 0L,
            mimeType = "audio/spotify",
            isLocal = false,
            isRemote = true,
            serverPath = null,
            path = uri
        )
    }

    private fun SpotifyTrack.toAudioTrack(): AudioTrack {
        val trackUri = uri.ifBlank { "spotify:track:$id" }
        val artwork = album?.images.bestImageUrl()

        return AudioTrack(
            id = trackUri,
            title = name,
            artist = artists.joinToString(", ") { it.name }.ifBlank { "Spotify" },
            album = album?.name ?: "Spotify",
            durationMs = durationMs,
            uri = Uri.parse(trackUri),
            artworkUri = artwork?.let(Uri::parse) ?: Uri.EMPTY,
            mimeType = "audio/spotify",
            isLocal = false,
            isRemote = true,
            trackNumber = 0,
            bitrate = 0,
            sampleRate = 0,
            serverPath = null,
            path = null
        )
    }

    private fun List<SpotifyImage>?.bestImageUrl(): String? {
        return this
            ?.sortedByDescending { (it.width ?: 0) * (it.height ?: 0) }
            ?.firstOrNull()
            ?.url
    }
}