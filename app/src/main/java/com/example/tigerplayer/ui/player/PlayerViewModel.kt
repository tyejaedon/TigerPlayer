package com.example.tigerplayer.ui.player

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresExtension
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.local.NavidromePrefs
import com.example.tigerplayer.data.local.dao.ArtistStats
import com.example.tigerplayer.data.local.dao.TrackStats
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.*
import com.example.tigerplayer.service.MediaControllerManager
import com.example.tigerplayer.ui.home.HomeUiState
import com.example.tigerplayer.ui.home.UserStatistics
import com.example.tigerplayer.utils.ArtistUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryArtist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int
)

data class PlayerUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val filteredTracks: List<AudioTrack> = emptyList(),
    val artists: List<LibraryArtist> = emptyList(),
    val albums: List<String> = emptyList(),
    val searchQuery: String = "",
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val currentLyrics: String? = null,
    val artistImageUrl: String? = null
)

data class StatItem(
    val id: String,
    val name: String,
    val playCount: Int,
    val secondaryText: String,
    val imageUrl: String? = null // <-- ADD THIS
)

data class DetailedStatsUiState(
    val selectedFilter: String = "Today",
    val totalListeningHours: Int = 0,
    val topArtists: List<StatItem> = emptyList(),
    val topTracks: List<StatItem> = emptyList()
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    val mediaControllerManager: MediaControllerManager,
    private val historyRepository: HistoryRepository,
    private val lyricsRepository: LyricsRepository,
    private val mediaDataRepository: MediaDataRepository,
    private val navidromeRepository: NavidromeRepository,
    private val spotifyRepository: SpotifyRepository,
    private val hostManager: com.example.tigerplayer.di.SubsonicHostManager,
    private val authManager: SpotifyAuthManager, // Inject the manager
    private val navidromePrefs: NavidromePrefs
) : ViewModel() {

    fun onAuthSuccess(newToken: String) {
        authManager.updateToken(newToken)
    }


    private var lyricsFetchJob: kotlinx.coroutines.Job? = null
    private var artistImageFetchJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _artistDetails = MutableStateFlow<Map<String, ArtistDetails>>(emptyMap())
    val artistDetails: StateFlow<Map<String, ArtistDetails>> = _artistDetails.asStateFlow()

    private val _trackColor = MutableStateFlow(Color(0xFF007AFF))
    val trackColor: StateFlow<Color> = _trackColor.asStateFlow()

    // When authentication finishes:

    private var lastUser: String? = null
    private var lastPass: String? = null
    private var lastBaseUrl: String? = null

    init {
        loadLocalAudio()
        observeLibrary()      // Now handles both Local and Remote automatically
        observeMediaEngine()  // Handles Player states
        observeHistory()      // Handles the Home UI stats
        viewModelScope.launch {
            spotifyRepository.currentSpotifyTrack.collect { spotifyInfo ->
                if (spotifyInfo != null && spotifyInfo != "Not Playing" && spotifyInfo != "Paused / Stopped") {
                    val parts = spotifyInfo.split(" • ")
                    val title = parts.getOrNull(0) ?: "Unknown"
                    val artist = parts.getOrNull(1) ?: "Spotify Cloud"

                    // 1. UPDATE THE UI INSTANTLY (with a placeholder/empty uri)
                    val tempTrack = AudioTrack(
                        id = "spotify:remote",
                        title = title,
                        artist = artist,
                        album = "Spotify Archive",
                        durationMs = 0L,
                        uri = android.net.Uri.EMPTY,
                        trackNumber = 0,
                        artworkUri = android.net.Uri.EMPTY
                    )

                    _uiState.update { it.copy(isPlaying = true, currentTrack = tempTrack) }

                    // 2. THE ARTWORK RITUAL: Fetch the actual high-res image
                    viewModelScope.launch {
                        mediaDataRepository.getHighResAlbumArt(title, artist).collect { highResUrl ->
                            highResUrl?.let { url ->
                                _uiState.update { state ->
                                    if (state.currentTrack?.id == "spotify:remote") {
                                        state.copy(currentTrack = tempTrack.copy(artworkUri = android.net.Uri.parse(url)))
                                    } else state
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLibrary() {
        viewModelScope.launch {
            combine(
                navidromePrefs.serverUrl,
                navidromePrefs.username,
                navidromePrefs.password
            ) { url, user, pass ->
                val finalUrl = url?.let {
                    val clean = if (it.startsWith("http")) it else "http://$it"
                    if (clean.endsWith("/")) clean else "$clean/"
                }

                if (finalUrl != null) hostManager.currentBaseUrl = finalUrl
                Triple(user, pass, finalUrl)
            }.flatMapLatest { (user, pass, baseUrl) ->
                // This is the single source of truth for your library data
                audioRepository.getUnifiedTracks(user, pass, baseUrl)
            }.catch { e ->
                Log.e("TigerDebug", "Library Sync Error: ${e.message}")
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }.collect { trackList ->
                // Use the aggregateLibrary to handle the "First Artist" logic
                _uiState.update { aggregateLibrary(trackList, it.searchQuery).copy(isLoading = false) }
            }
        }
    }

    // --- CONSOLIDATED AGGREGATION ---
    private fun aggregateLibrary(tracks: List<AudioTrack>, query: String = ""): PlayerUiState {
        val filtered = if (query.isEmpty()) tracks else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
        }

        val aggregatedArtists = filtered
            .groupBy { ArtistUtils.getBaseArtist(it.artist).lowercase() }
            .map { (baseLower, artistTracks) ->
                val properName = ArtistUtils.getBaseArtist(artistTracks.first().artist)
                LibraryArtist(
                    name = properName,
                    trackCount = artistTracks.size,
                    albumCount = artistTracks.distinctBy { it.album.trim().lowercase() }.size
                )
            }
            .sortedBy { it.name }

        return _uiState.value.copy(
            tracks = tracks,
            filteredTracks = filtered,
            artists = aggregatedArtists,
            albums = filtered.map { it.album.trim() }.distinct().sorted(),
            searchQuery = query
        )
    }

    // --- HELPER EXTENSION ---





    @OptIn(ExperimentalCoroutinesApi::class)

    private fun observeHistory() {
        viewModelScope.launch {
            combine(
                historyRepository.recentTracks,
                historyRepository.totalListeningTime,
                historyRepository.topArtist,
                _uiState.map { it.tracks }.distinctUntilChanged()
            ) { recent, totalTime, topArtist, allTracks ->
                val hours = (totalTime ?: 0L) / (1000 * 60 * 60)
                val minutes = ((totalTime ?: 0L) / (1000 * 60)) % 60
                HomeUiState(
                    statistics = UserStatistics(
                        totalListeningTimeHours = hours.toInt(),
                        topArtistThisWeek = topArtist ?: "New Recruit",
                        listeningTimeToday = "${hours}h ${minutes}m",
                        losslessTrackCount = allTracks.count {
                            it.mimeType.contains("flac", ignoreCase = true) || it.bitrate > 320000
                        }
                    ),
                    discoverTracks = allTracks.shuffled().take(10),
                    recentlyPlayedTracks = recent.mapNotNull { entity ->
                        allTracks.find { it.id == entity.trackId }
                    },
                    recommendedTracks = allTracks.take(5),
                    isLoading = false
                )
            }
                .onStart { Log.d("TigerDebug", "History observation started...") }
                .collect { state ->
                    _homeUiState.value = state
                }
        }
    }
    fun addTrackToLikedSongs(track: AudioTrack) {
        viewModelScope.launch {
            val playlists = customPlaylists.value
            var likedPlaylist = playlists.find { it.name.equals("Liked Songs", ignoreCase = true) }

            if (likedPlaylist == null) {
                // Forge the playlist if it doesn't exist yet
                audioRepository.createPlaylist("Liked Songs")
                // Small delay to ensure DB finishes creating it before we fetch again
                kotlinx.coroutines.delay(200)
                val updatedPlaylists = audioRepository.getCustomPlaylists().first()
                likedPlaylist = updatedPlaylists.find { it.name.equals("Liked Songs", ignoreCase = true) }
            }

            likedPlaylist?.let {
                audioRepository.addTrackToPlaylist(it.id, track.id)
                Log.d("TigerRitual", "Added ${track.title} to Liked Songs")
            }
        }
    }


    private fun observeMediaEngine() {
        // 1. Observe Playback State
        viewModelScope.launch {
            mediaControllerManager.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }

        // 2. THE FIX: Observe the "Pulse" (Position) directly from the Manager
        viewModelScope.launch {
            var lastRecordedMediaId: String? = null
            mediaControllerManager.currentPosition.collect { currentPos ->
                _uiState.update { it.copy(currentPosition = currentPos) }

                // History Logging Ritual
                val currentTrack = _uiState.value.currentTrack
                if (currentTrack != null && currentTrack.id != lastRecordedMediaId && currentPos > 10000) {
                    recordPlaybackHistory(currentTrack)
                    lastRecordedMediaId = currentTrack.id
                }
            }
        }

        // 3. Observe Track Changes
        viewModelScope.launch {
            mediaControllerManager.currentMediaId.collect { mediaId ->
                val track = _uiState.value.tracks.find { it.id == mediaId }
                if (track != null && _uiState.value.currentTrack?.id != track.id) {
                    _uiState.update {
                        it.copy(
                            currentTrack = track,
                            currentLyrics = null,
                            artistImageUrl = null
                        )
                    }
                    fetchTrackMetadata(track)
                }
            }
        }

        // 4. Observe Shuffle & Repeat
        viewModelScope.launch {
            mediaControllerManager.shuffleModeEnabled.collect { isShuffle ->
                _uiState.update { it.copy(isShuffleEnabled = isShuffle) }
            }
        }
        viewModelScope.launch {
            mediaControllerManager.repeatMode.collect { mode ->
                _uiState.update { it.copy(repeatMode = mode) }
            }
        }
    }

    private suspend fun recordPlaybackHistory(track: AudioTrack) {
        historyRepository.addTrackToHistory(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            imageUrl = track.artworkUri?.toString(),
            durationMs = 10000,
            source = MediaSource.LOCAL
        )
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            aggregateLibrary(state.tracks, query)
        }
    }



    private fun fetchTrackMetadata(track: AudioTrack) {
        lyricsFetchJob?.cancel()
        artistImageFetchJob?.cancel()

        // Hunt for the Artist's Face (Spotify)
        artistImageFetchJob = viewModelScope.launch {
            mediaDataRepository.getArtistDetails(track.artist).collect { details ->
                _artistDetails.update { it + (track.artist to details) }
                // Use Spotify image if found, fallback to nothing (UI will use Album Art)
                _uiState.update { it.copy(artistImageUrl = details.imageUrl) }
            }
        }

        // Hunt for the Words (Lyrics API)
        lyricsFetchJob = viewModelScope.launch {
            lyricsRepository.getLyrics(track).collect { lyrics ->
                _uiState.update { it.copy(currentLyrics = lyrics) }
            }
        }


    /**
     * Call this when navigating to an Artist's profile to pre-fetch the
     * high-res Spotify image and Wikipedia bio.
     */
    fun fetchArtistProfile(artistName: String) {
        val baseName = ArtistUtils.getBaseArtist(artistName)
        if (_artistDetails.value.containsKey(baseName)) return

        viewModelScope.launch {
            mediaDataRepository.getArtistDetails(baseName).collect { details ->
                _artistDetails.update { it + (baseName to details) }
            }
        }
    }

    // --- UPDATED STATS TO INCLUDE IMAGES ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val detailedStatsState: StateFlow<DetailedStatsUiState> = _statsFilter.flatMapLatest { filter ->
        val startTime = calculateStartTimeForFilter(filter)
        combine(
            historyRepository.getTotalListeningTime(startTime),
            historyRepository.getTopArtists(startTime, limit = 5),
            historyRepository.getTopTracks(startTime, limit = 5),
            _uiState.map { it.tracks }
        ) { totalTime, topArtistsDb, topTracksDb, allTracks ->
            DetailedStatsUiState(
                selectedFilter = filter,
                totalListeningHours = ((totalTime ?: 0L) / (1000 * 60 * 60)).toInt(),
                topArtists = topArtistsDb.map { artist ->
                    // Try to find the artist profile pic in our cache
                    val cachedImg = _artistDetails.value[artist.artistName]?.imageUrl
                    StatItem(artist.artistName, artist.artistName, artist.playCount, "Artist", cachedImg)
                },
                topTracks = topTracksDb.map { track ->
                    // Pull the Album Art from the track library
                    val albumArt = allTracks.find { it.id == track.trackId }?.artworkUri?.toString()
                    StatItem(track.trackId, track.title, track.playCount, track.artist, albumArt)
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailedStatsUiState())
}




    fun playTrack(track: AudioTrack) {
        viewModelScope.launch {
            val isSpotifyTrack = track.id.startsWith("spotify:")

            if (isSpotifyTrack) {
                // 1. SILENCE THE LOCAL ENGINE
                mediaControllerManager.pause()
                // 2. AWAKEN THE CLOUD
                spotifyRepository.playUri(track.id)
            } else {
                // 1. SILENCE THE CLOUD
                spotifyRepository.pause()
                // 2. COMMENCE THE LOCAL RITUAL
                mediaControllerManager.setPlaylistAndPlay(
                    _uiState.value.tracks,
                    _uiState.value.tracks.indexOf(track)
                )
            }
        }
    }
    fun togglePlayPause() {
        val currentTrack = _uiState.value.currentTrack
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true

        if (isSpotify) {
            // Since we don't have a simple 'toggle' in the Remote API,
            // we check the last known state or just send a resume/pause.
            if (_uiState.value.isPlaying) spotifyRepository.pause()
            else spotifyRepository.resume()
        } else {
            if (_uiState.value.isPlaying) mediaControllerManager.pause()
            else mediaControllerManager.resume()
        }
    }

    fun seekTo(position: Long) {
        val isSpotify = _uiState.value.currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.seekTo(position)
        } else {
            mediaControllerManager.seekTo(position)
        }
    }

    fun toggleShuffle() {
        val isSpotify = _uiState.value.currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.toggleShuffle()

            // Optimistically update the UI so the button changes color immediately on the S22
            _uiState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
        } else {
            mediaControllerManager.toggleShuffleMode()
        }
    }

    fun toggleRepeat() {
        val isSpotify = _uiState.value.currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.toggleRepeat()

            // Optimistically update the UI for Spotify's 3-state repeat (Off -> Context -> Track)
            _uiState.update { state ->
                val nextMode = when (state.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                state.copy(repeatMode = nextMode)
            }
        } else {
            mediaControllerManager.toggleRepeatMode()
        }
    }



    fun skipToNext() {
        val isSpotify = _uiState.value.currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.skipNext()
        } else {
            mediaControllerManager.skipToNext()
        }
    }

    fun skipToPrevious() {
        val isSpotify = _uiState.value.currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.skipPrevious()
        } else {
            mediaControllerManager.skipToPrevious()
        }
    }
    fun addToQueue(track: AudioTrack) {
        val isSpotify = track.id.startsWith("spotify:")
        if (isSpotify) {
            // Note for Beta: The basic Spotify App Remote doesn't queue as easily,
            // but checking for it prevents ExoPlayer from crashing!
            Log.w("TigerPlayer", "Spotify queueing requires extended API access.")
        } else {
            mediaControllerManager.addNextToQueue(track)
        }
    }


    // Note: The Spotify App Remote *does* support Shuffle and Repeat,
    // but the implementation requires setting boolean states via the remote API.
    // For the Beta, we will leave Shuffle/Repeat local-only to ensure stability,
    // or you can add them to the SpotifyRepo later!


    override fun onCleared() {
        super.onCleared()
        mediaControllerManager.release()
    }

    private val _statsFilter = MutableStateFlow("Today")

    @OptIn(ExperimentalCoroutinesApi::class)
    val detailedStatsState: StateFlow<DetailedStatsUiState> = _statsFilter.flatMapLatest { filter ->
        val startTime = calculateStartTimeForFilter(filter)
        combine(
            historyRepository.getTotalListeningTime(startTime),
            historyRepository.getTopArtists(startTime, limit = 5),
            historyRepository.getTopTracks(startTime, limit = 5)
        ) { totalTime, topArtistsDb, topTracksDb ->
            DetailedStatsUiState(
                selectedFilter = filter,
                totalListeningHours = ((totalTime ?: 0L) / (1000 * 60 * 60)).toInt(),
                topArtists = topArtistsDb.map { artist: ArtistStats ->
                    StatItem(artist.artistName, artist.artistName, artist.playCount, "Artist")
                },
                topTracks = topTracksDb.map { track: TrackStats ->
                    StatItem(track.trackId, track.title, track.playCount, track.artist)
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailedStatsUiState())

    fun updateStatsFilter(newFilter: String) {
        _statsFilter.value = newFilter
    }

    private fun calculateStartTimeForFilter(filter: String): Long {
        val now = System.currentTimeMillis()
        val dayMs = 86400000L
        return when (filter) {
            "Today" -> now - dayMs
            "This Week" -> now - (dayMs * 7)
            "This Month" -> now - (dayMs * 30)
            else -> 0L
        }
    }

    /**
     * Strips away "ft.", "feat.", "&", and commas to return only the primary artist.
     */



    fun fetchArtistProfile(artistName: String) {
        // Clean the name before checking the cache or firing the API
        val baseName = ArtistUtils.getBaseArtist(artistName)

        if (_artistDetails.value.containsKey(baseName)) return

        viewModelScope.launch {
            mediaDataRepository.getArtistDetails(baseName).collect { details ->
                _artistDetails.update { it + (baseName to details) }
            }
        }
    }

    val customPlaylists: StateFlow<List<com.example.tigerplayer.data.model.Playlist>> =
        audioRepository.getCustomPlaylists()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            audioRepository.createPlaylist(name)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: AudioTrack) {
        viewModelScope.launch {
            audioRepository.addTrackToPlaylist(playlistId, track.id)
        }
    }

    fun getPlaylistTracks(playlistId: Long): Flow<List<AudioTrack>> {
        if (playlistId == -1L) return flowOf(emptyList())
        if (playlistId == -2L) return flowOf(_uiState.value.tracks.reversed().take(20))
        return audioRepository.getTracksForPlaylist(playlistId)
    }

    /**
     * Executes the login ritual. If successful, it commits the 
     * credentials to the DataStore for future auto-syncs.
     */
    fun connectToNavidrome(
        url: String,
        user: String,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val cleanUrl = if (url.startsWith("http")) url else "http://$url"
            val finalUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"

            hostManager.currentBaseUrl = finalUrl

            navidromeRepository.pingServer(user, pass).onSuccess {
                navidromePrefs.saveCredentials(finalUrl, user, pass)
                refreshLibrary()
                onResult(true, null)
            }.onFailure { error ->
                onResult(false, error.message ?: "The ritual was rejected by the server.")
            }
        }
    }

    /**
     * The Master Sync: Pulls local FLACs and Navidrome streams into one view.
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val user = navidromePrefs.username.firstOrNull()
            val pass = navidromePrefs.password.firstOrNull()
            val url = navidromePrefs.serverUrl.firstOrNull()

            url?.let { hostManager.currentBaseUrl = it }

            audioRepository.getUnifiedTracks(user, pass, url)
                .catch { e ->
                    Log.e("TigerDebug", "Sync Failed: ${e.message}")
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { unifiedTracks ->
                    _uiState.value = aggregateLibrary(unifiedTracks, _uiState.value.searchQuery)
                    _uiState.update { it.copy(tracks = unifiedTracks, isLoading = false) }
                }
        }
    }
    /**
     * Loads the physical artifacts stored on the device's internal storage.
     */
    fun loadLocalAudio() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            audioRepository.getLocalTracks()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { localTracks ->
                    // Organize the raw files into Artists and Albums for the UI
                    val updatedState = aggregateLibrary(localTracks, _uiState.value.searchQuery)

                    _uiState.update {
                        it.copy(
                            tracks = localTracks,
                            isLoading = false,
                            artists = updatedState.artists,
                            albums = updatedState.albums
                        )
                    }
                }
        }
    }

    /**
     * Observes the DataStore and automatically re-links the Navidrome archive
     * if credentials have been previously forged.
     */
    private fun performAutoRitual() {
        viewModelScope.launch {
            // We explicitly define the flows to avoid compiler type-inference errors
            val urlFlow: Flow<String?> = navidromePrefs.serverUrl
            val userFlow: Flow<String?> = navidromePrefs.username
            val passFlow: Flow<String?> = navidromePrefs.password

            combine(urlFlow, userFlow, passFlow) { url, user, pass ->
                Triple(url, user, pass)
            }.collect { (url, user, pass) ->

                // Proceed only if the ritual components are fully present
                if (!url.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank()) {

                    // 1. Standardize the URL for the Network Interceptor
                    val cleanUrl = if (url.startsWith("http")) url else "http://$url"
                    val finalUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"

                    hostManager.currentBaseUrl = finalUrl

                    // 2. Attempt to synchronize with the remote archives
                    navidromeRepository.pingServer(user, pass).onSuccess {
                        Log.d("TigerRitual", "Auto-Sync Success: Archive link re-established.")

                        // 3. Trigger a full unified refresh (Local + Remote)
                        refreshLibrary()
                    }.onFailure { e ->
                        Log.e("TigerRitual", "Auto-Sync Failed: ${e.message}")
                    }
                }
            }
        }
    }



}
