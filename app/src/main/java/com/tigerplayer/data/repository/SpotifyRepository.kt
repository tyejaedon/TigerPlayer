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
import com.tigerplayer.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyPlaybackState(
    val track: AudioTrack,
    val isPlaying: Boolean,
    val positionMs: Long,
    val isShuffleEnabled: Boolean = false,
    /**
     * `true` while this is a locally-assumed state published the instant playback was requested,
     * before the Spotify app has confirmed anything over App Remote. Real remote events always
     * clear it. The UI must not present an optimistic position or duration as authoritative.
     */
    val isOptimistic: Boolean = false
)

data class SpotifyCurationResult(
    val daylist: List<AudioTrack>,
    val discoveryWeekly: List<AudioTrack>
)

@Singleton
class SpotifyRepository @Inject constructor(
    private val spotifyApiService: SpotifyApiService,
    val authManager: SpotifyAuthManager,
    private val appRemoteClient: SpotifyAppRemoteClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "tigerplayer://callback"
    private var pendingUriToPlay: String? = null
    private var pendingKnownTrack: AudioTrack? = null
    private var playbackConfirmationJob: Job? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** `false` in the `foss` flavor; UI should hide Spotify App Remote controls when this is false. */
    val isAppRemoteSupported: Boolean = appRemoteClient.isSupported

    // --- 1. CONNECTION STREAMS ---

    private val _isRemoteConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isRemoteConnected.asStateFlow()

    /**
     * Non-null while the last App Remote playback attempt is known to have failed. Without this,
     * a failed connection left the optimistic placeholder on screen at 0:00 forever with the
     * failure visible only in logcat (issue #170).
     */
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

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

    suspend fun verifyTokenWithServer(): Boolean = withContext(ioDispatcher) {
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

    suspend fun fetchUserPlaylists(token: String) = withContext(ioDispatcher) {
        try {
            val response = spotifyApiService.getUserPlaylists("Bearer $token")
            if (response.isSuccessful) {
                // Safeguard against null items occasionally returned by Spotify's pagination
                _userPlaylists.value = response.body()?.items?.filterNotNull() ?: emptyList()
            } else {
                Log.e(TAG, "Playlist fetch rejected: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playlist network failure", e)
            throw e // Re-throw so the ViewModel can catch it and notify the UI
        }
    }

    suspend fun fetchAllUserPlaylists(
        token: String,
        pageSize: Int = 50,
        maxPages: Int = 6
    ): List<SpotifyPlaylist> = withContext(ioDispatcher) {
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
                Log.e(TAG, "Paged playlist fetch failed", e)
                break
            }

            if (!response.isSuccessful) {
                Log.e(TAG, "Paged playlist fetch rejected: ${response.code()}")
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

    suspend fun fetchHomeCurations(token: String): SpotifyCurationResult = withContext(ioDispatcher) {
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

    suspend fun fetchUserSavedAlbums(token: String) = withContext(ioDispatcher) {
        try {
            val response = spotifyApiService.getUserSavedAlbums("Bearer $token")
            if (response.isSuccessful) {
                _userAlbums.value = response.body()?.items?.mapNotNull { it.album } ?: emptyList()
            } else {
                Log.e(TAG, "Album fetch rejected: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Album network failure", e)
            throw e
        }
    }

    suspend fun fetchPlaylistTracks(token: String, playlistId: String): List<SpotifyTrack> = withContext(ioDispatcher) {
        try {
            val response = spotifyApiService.getPlaylistTracks("Bearer $token", playlistId)
            if (response.isSuccessful) {
                response.body()?.items?.mapNotNull { it.track } ?: emptyList()
            } else {
                Log.e(TAG, "Track fetch rejected: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Track fetch network failure", e)
            emptyList()
        }
    }

    suspend fun fetchAlbumTracks(token: String, albumId: String): List<SpotifyTrack> = withContext(ioDispatcher) {
        try {
            val response = spotifyApiService.getAlbumTracks("Bearer $token", albumId)
            if (response.isSuccessful) {
                response.body()?.items ?: emptyList()
            } else {
                Log.e(TAG, "Album tracks rejected: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Album tracks network failure", e)
            emptyList()
        }
    }

    // --- 5. APP REMOTE (IPC PLAYBACK) ---

    fun connect() {
        if (!appRemoteClient.isSupported || _isRemoteConnected.value) return

        appRemoteClient.connect(
            clientId = clientId,
            redirectUri = redirectUri,
            onConnectionChanged = { connected ->
                _isRemoteConnected.value = connected
                if (connected) {
                    _connectionError.value = null
                    pendingUriToPlay?.let { uri ->
                        appRemoteClient.play(uri)
                        publishOptimisticPlayback(uri, pendingKnownTrack)
                        pendingUriToPlay = null
                        pendingKnownTrack = null
                    }
                }
            },
            onPlayerStateChanged = { remoteState ->
                // A real event from the Spotify app: playback is confirmed, so the watchdog and
                // any stale failure message no longer apply.
                playbackConfirmationJob?.cancel()
                _connectionError.value = null
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
                        isShuffleEnabled = it.isShuffling,
                        isOptimistic = false
                    )
                }
            },
            onConnectionFailed = { throwable ->
                Log.e(TAG, "App Remote connection failed", throwable)
                _isRemoteConnected.value = false
                failPendingPlayback(ERROR_CONNECTION_FAILED)
            }
        )
    }

    /**
     * Starts playback of [uri], optionally seeding the transient placeholder state with metadata
     * the caller already holds so the player never shows a raw Spotify ID at 0:00 (issue #169).
     */
    fun playUri(uri: String, knownTrack: AudioTrack? = null) {
        if (!appRemoteClient.isSupported) {
            _connectionError.value = ERROR_APP_REMOTE_UNSUPPORTED
            return
        }

        _connectionError.value = null
        pendingUriToPlay = uri
        pendingKnownTrack = knownTrack
        publishOptimisticPlayback(uri, knownTrack)

        if (_isRemoteConnected.value) {
            appRemoteClient.play(uri)
            pendingUriToPlay = null
            pendingKnownTrack = null
        } else {
            connect()
        }

        startPlaybackConfirmationWatchdog()
    }

    /** Plays a single track whose full metadata is already known, e.g. from a playlist listing. */
    fun playTrack(track: SpotifyTrack) {
        val audioTrack = track.toAudioTrack()
        playUri(audioTrack.id, audioTrack)
    }

    /**
     * Plays a collection (playlist/album) URI. No single track is known yet, so [displayName] is
     * used as the placeholder title instead of the collection's base62 id.
     */
    fun playCollection(uri: String, displayName: String) {
        playUri(uri, audioTrackFromUri(uri, displayName))
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

    fun clearConnectionError() {
        _connectionError.value = null
    }

    fun disconnect() {
        playbackConfirmationJob?.cancel()
        appRemoteClient.disconnect()
        _isRemoteConnected.value = false
        pendingUriToPlay = null
        pendingKnownTrack = null
        _spotifyPlaybackState.value = null
    }

    /**
     * Nothing else advances Spotify playback state locally, so an App Remote session that connects
     * but never emits a player-state event would otherwise leave the placeholder on screen
     * indefinitely. Time the attempt out and report it instead (issue #170).
     */
    private fun startPlaybackConfirmationWatchdog() {
        playbackConfirmationJob?.cancel()
        playbackConfirmationJob = repositoryScope.launch {
            delay(PLAYBACK_CONFIRMATION_TIMEOUT_MS)
            if (_spotifyPlaybackState.value?.isOptimistic == true) {
                Log.w(TAG, "App Remote never confirmed playback; clearing optimistic state.")
                failPendingPlayback(ERROR_PLAYBACK_UNCONFIRMED)
            }
        }
    }

    private fun failPendingPlayback(message: String) {
        playbackConfirmationJob?.cancel()
        pendingUriToPlay = null
        pendingKnownTrack = null
        _connectionError.value = message
        if (_spotifyPlaybackState.value?.isOptimistic == true) {
            _spotifyPlaybackState.value = null
        }
    }

    private fun publishOptimisticPlayback(uri: String, knownTrack: AudioTrack? = null) {
        _spotifyPlaybackState.value = SpotifyPlaybackState(
            track = knownTrack ?: audioTrackFromUri(uri),
            isPlaying = true,
            positionMs = 0L,
            isShuffleEnabled = _spotifyPlaybackState.value?.isShuffleEnabled ?: false,
            isOptimistic = true
        )
    }

    /**
     * Last-resort placeholder for a URI whose metadata is not known. It describes the *kind* of
     * thing being opened; it must never echo the raw base62 id back at the user (issue #169).
     */
    private fun audioTrackFromUri(uri: String, displayName: String? = null): AudioTrack {
        val kind = uri.split(':').getOrNull(1)?.lowercase()
        val title = displayName?.takeIf { it.isNotBlank() } ?: when (kind) {
            "track" -> "Spotify track"
            "playlist" -> "Spotify playlist"
            "album" -> "Spotify album"
            "artist" -> "Spotify artist"
            "show", "episode" -> "Spotify episode"
            else -> "Spotify"
        }

        return AudioTrack(
            id = uri,
            title = title,
            artist = "Connecting to Spotify…",
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

    private companion object {
        const val TAG = "SpotifyRepo"

        /**
         * How long to wait for the Spotify app to confirm playback before giving up on the
         * optimistic placeholder. Generous enough to cover a cold start of the Spotify app.
         */
        const val PLAYBACK_CONFIRMATION_TIMEOUT_MS = 15_000L

        const val ERROR_CONNECTION_FAILED =
            "Couldn't reach the Spotify app. Make sure it's installed, open, and signed in with Premium."
        const val ERROR_PLAYBACK_UNCONFIRMED =
            "Spotify didn't start playback. Open the Spotify app, then try again."
        const val ERROR_APP_REMOTE_UNSUPPORTED =
            "Spotify playback isn't available in this build. Browsing still works."
    }
}
