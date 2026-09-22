package com.tigerplayer.data.repository

import android.net.Uri
import android.util.Log
import com.tigerplayer.BuildConfig
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.remote.api.SpotifyApiService
import com.tigerplayer.data.remote.model.SpotifyAlbum
import com.tigerplayer.data.remote.model.SpotifyImage
import com.tigerplayer.data.remote.model.SpotifyPlaylist
import com.tigerplayer.data.remote.model.SpotifyTrack
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
    private val spotifyApiService: SpotifyApiService,
    val authManager: SpotifyAuthManager,
    private val appRemoteClient: SpotifyAppRemoteClient
) {
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "tigerplayer://callback"
    private var pendingUriToPlay: String? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** `false` in the `foss` flavor; UI should hide Spotify App Remote controls when this is false. */
    val isAppRemoteSupported: Boolean = appRemoteClient.isSupported

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
            state?.track?.let { "${it.title} â€¢ ${it.artist}" } ?: "Not Playing"
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

        appRemoteClient.connect(
            clientId = clientId,
            redirectUri = redirectUri,
            onConnectionChanged = { connected ->
                _isRemoteConnected.value = connected
                if (connected) {
                    pendingUriToPlay?.let { uri ->
                        appRemoteClient.play(uri)
                        publishOptimisticPlayback(uri)
                        pendingUriToPlay = null
                    }
                }
            },
            onPlayerStateChanged = { remoteState ->
                _spotifyPlaybackState.value = remoteState?.let {
                    SpotifyPlaybackState(
                        track = AudioTrack(
                            id = it.trackUri,
                            title = it.trackName,
                            artist = it.artistName,
                            album = "Spotify",
                            uri = Uri.EMPTY,
                            artworkUri = Uri.EMPTY,
                            durationMs = it.durationMs,
                            mimeType = "audio/spotify",
                            isLocal = false,
                            isRemote = true,
                            serverPath = null,
                            path = it.trackUri
                        ),
                        isPlaying = !it.isPaused,
                        positionMs = it.positionMs,
                        isShuffleEnabled = it.isShuffling
                    )
                }
            },
            onConnectionFailed = { throwable ->
                Log.e("SpotifyRepo", "App Remote connection failed", throwable)
                _isRemoteConnected.value = false
            }
        )
    }

    fun playUri(uri: String) {
        pendingUriToPlay = uri
        publishOptimisticPlayback(uri)

        if (_isRemoteConnected.value) {
            appRemoteClient.play(uri)
            pendingUriToPlay = null
        } else {
            _isRemoteConnected.value = false
            connect()
        }
    }

    fun pause() {
        appRemoteClient.pause()
        _spotifyPlaybackState.value = _spotifyPlaybackState.value?.copy(isPlaying = false)
    }

    fun resume() {
        appRemoteClient.resume()
        _spotifyPlaybackState.value = _spotifyPlaybackState.value?.copy(isPlaying = true)
    }

    fun skipNext() = appRemoteClient.skipNext()
    fun skipPrevious() = appRemoteClient.skipPrevious()

    fun seekTo(positionMs: Long) {
        appRemoteClient.seekTo(positionMs)
        _spotifyPlaybackState.value = _spotifyPlaybackState.value?.copy(positionMs = positionMs)
    }

    fun toggleShuffle() {
        appRemoteClient.toggleShuffle()
        _spotifyPlaybackState.value = _spotifyPlaybackState.value?.let {
            it.copy(isShuffleEnabled = !it.isShuffleEnabled)
        }
    }
    fun toggleRepeat() = appRemoteClient.toggleRepeat()

    fun disconnect() {
        appRemoteClient.disconnect()
        _isRemoteConnected.value = false
        pendingUriToPlay = null
        _spotifyPlaybackState.value = null
    }

    private fun publishOptimisticPlayback(uri: String) {
        _spotifyPlaybackState.value = SpotifyPlaybackState(
            track = audioTrackFromUri(uri),
            isPlaying = true,
            positionMs = 0L,
            isShuffleEnabled = _spotifyPlaybackState.value?.isShuffleEnabled ?: false
        )
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
