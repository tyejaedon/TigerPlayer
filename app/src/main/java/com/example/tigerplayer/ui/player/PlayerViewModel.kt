package com.example.tigerplayer.ui.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
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
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.data.repository.*
import com.example.tigerplayer.data.source.LocalAudioDataSource
import com.example.tigerplayer.di.SubsonicHostManager
import com.example.tigerplayer.service.MediaControllerManager
import com.example.tigerplayer.ui.home.HomeUiState
import com.example.tigerplayer.ui.home.UserStatistics
import com.example.tigerplayer.utils.ArtistUtils
import com.example.tigerplayer.utils.NavidromeMapper.toAudioTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

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
    val artistImageUrl: String? = null,
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val totalFilesToScan: Int = 0,
    val queue: List<AudioTrack> = emptyList()
)

data class StatItem(
    val id: String,
    val name: String,
    val playCount: Int,
    val secondaryText: String,
    val imageUrl: String? = null
)

data class DetailedStatsUiState(
    val selectedFilter: String = "Today",
    val totalListeningHours: Int = 0,
    val totalListeningMinutes: Int = 0,
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
    private val lastFmRepository: LastFmRepository,
    private val navidromeRepository: NavidromeRepository,
    private val spotifyRepository: SpotifyRepository,
    private val hostManager: SubsonicHostManager,
    private val authManager: SpotifyAuthManager,
    private val navidromePrefs: NavidromePrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _artistDetails = MutableStateFlow<Map<String, ArtistDetails>>(emptyMap())
    val artistDetails: StateFlow<Map<String, ArtistDetails>> = _artistDetails.asStateFlow()

    private val _trackColor = MutableStateFlow(Color(0xFF007AFF))
    val trackColor: StateFlow<Color> = _trackColor.asStateFlow()

    private val _statsFilter = MutableStateFlow("Today")

    private var lyricsFetchJob: Job? = null
    private var artistImageFetchJob: Job? = null

    private val _localTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _remoteTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private var metadataFetchJob: Job? = null
    val currentTrackTitle: StateFlow<String> = uiState
        .map { it.currentTrack?.title ?: "Unknown" }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Unknown")

    val customPlaylists: StateFlow<List<Playlist>> =
        audioRepository.getCustomPlaylists()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )



    init {
        observeLibrary()
        loadLocalAudio()

        observeMediaEngine()
        observeHistory()
        observeSpotifyRemote()
        performAutoRitual()
        syncNavidromeArchives()




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
                audioRepository.getUnifiedTracks(user, pass, baseUrl)
            }.catch { e ->
                Log.e("TigerDebug", "Library Sync Error: ${e.message}")
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }.collect { trackList ->
                _uiState.update { currentState ->
                    val newState = aggregateLibrary(trackList, currentState.searchQuery)
                    newState.copy(
                        isLoading = false,
                        tracks = trackList,
                        filteredTracks = if (currentState.searchQuery.isEmpty()) trackList else newState.filteredTracks
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeHistory() {
        // 1. THE RECOMMENDATION TICKER: Rotates the 'Discover' seed every 2 hours
        val recommendationTicker = flow {
            while (true) {
                emit(System.currentTimeMillis() / (2 * 60 * 60 * 1000))
                delay(30 * 60 * 1000) // Check every 30 mins, but only emits when hours change
            }
        }.distinctUntilChanged()

        viewModelScope.launch {
            // 2. THE COMBINED FLOWS (6 Sources of Power)
            combine(
                historyRepository.recentTracks,
                historyRepository.totalListeningTime,
                historyRepository.listeningTimeToday,
                historyRepository.topArtist,
                // We observe the main library state to map ID's to actual AudioTrack objects
                _uiState.map { it.tracks }.distinctUntilChanged(),
                recommendationTicker
            ) { args: Array<Any?> ->

                // 3. UNPACKING THE ARCHIVES (Explicit Casting)
                val recentEntities = args[0] as List<PlaybackHistoryEntity>
                val totalMs = args[1] as? Long ?: 0L
                val todayMs = args[2] as? Long ?: 0L
                val topArtist = args[3] as? String
                val allTracks = args[4] as List<AudioTrack>
                val seed = args[5] as Long

                // 4. THE TIME CALCULATIONS
                val totalHours = totalMs / (1000 * 60 * 60)
                val todayHours = todayMs / (1000 * 60 * 60)
                val todayMinutes = (todayMs / (1000 * 60)) % 60

                val random = Random(seed)

                // 5. RECOMMENDATION LOGIC: Grouping by Volume (Album)
                val recommended = if (allTracks.isNotEmpty()) {
                    allTracks.asSequence()
                        .filter { it.album.isNotBlank() && !it.album.equals("Unknown", true) }
                        .groupBy { it.album }
                        .values
                        .toList()
                        .shuffled(random)
                        .take(5) // Suggest 5 different albums
                        .flatten()
                } else emptyList()

                // 6. RECENT TRACKS MAPPING: Linking History IDs to Local Files
                val recentlyPlayed = recentEntities.mapNotNull { entity ->
                    allTracks.find { it.id == entity.trackId }
                }

                // 7. EMITTING THE FINAL UI STATE
                HomeUiState(
                    statistics = UserStatistics(
                        listeningTimeToday = "${todayHours}h ${todayMinutes}m",
                        listeningTimeTodayMs = todayMs,
                        topArtistThisWeek = topArtist ?: "New Recruit",
                        totalTracksCount = allTracks.size,
                        totalListeningTimeHours = totalHours.toInt()
                    ),
                    discoverTracks = allTracks.shuffled(random).take(12),
                    recentlyPlayedTracks = recentlyPlayed,
                    recommendedTracks = recommended,
                    isLoading = false
                )
            }.collect { state ->
                // Update the state holder for the Compose UI
                _homeUiState.value = state
            }
        }
    }

    private fun observeMediaEngine() {
        viewModelScope.launch {
            mediaControllerManager.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }

        viewModelScope.launch {
            var lastRecordedMediaId: String? = null
            mediaControllerManager.currentPosition.collect { currentPos ->
                _uiState.update { it.copy(currentPosition = currentPos) }

                val currentTrack = _uiState.value.currentTrack
                // 10-second history anchor
                if (currentTrack != null && currentTrack.id != lastRecordedMediaId && currentPos > 10000) {
                    recordPlaybackHistory(currentTrack)
                    lastRecordedMediaId = currentTrack.id
                }
            }
        }

        viewModelScope.launch {
            mediaControllerManager.currentMediaId.collect { mediaId ->
                val track = _uiState.value.tracks.find { it.id == mediaId }
                if (track != null && _uiState.value.currentTrack?.id != track.id) {
                    // Clear old metadata instantly so the UI shows placeholders
                    _uiState.update {
                        it.copy(currentTrack = track, currentLyrics = null, artistImageUrl = null)
                    }
                    fetchTrackMetadata(track)
                }
            }
        }

        // THE FIX: Merged the two mediaControllerState collectors into one clean pipeline
        viewModelScope.launch {
            mediaControllerManager.mediaControllerState.collect { _ ->
                val controller = mediaControllerManager.mediaController ?: return@collect
                val queueTracks = mutableListOf<AudioTrack>()

                for (i in 0 until controller.mediaItemCount) {
                    val mediaId = controller.getMediaItemAt(i).mediaId
                    _uiState.value.tracks.find { it.id == mediaId }?.let { queueTracks.add(it) }
                }

                _uiState.update { it.copy(queue = queueTracks) }

                updateQueue()
            }
        }

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
    private fun observeSpotifyRemote() {
        viewModelScope.launch {
            spotifyRepository.currentSpotifyTrack.collect { spotifyInfo ->
                if (!spotifyInfo.isNullOrBlank() && spotifyInfo != "Not Playing" && spotifyInfo != "Paused / Stopped") {
                    val parts = spotifyInfo.split(" • ")
                    val title = parts.getOrNull(0) ?: "Unknown"
                    val artist = parts.getOrNull(1) ?: "Spotify Cloud"

                    val tempTrack = AudioTrack(
                        id = "spotify:remote",
                        title = title,
                        artist = artist,
                        album = "Spotify Archive",
                        durationMs = 0L,
                        uri = Uri.EMPTY,
                        trackNumber = 0,
                        artworkUri = Uri.EMPTY,
                        mimeType = "audio/spotify",
                        isLocal = false,
                        isRemote = true,
                        bitrate = 0,
                        sampleRate = 0,
                        serverPath = null,
                        path = null
                    )

                    _uiState.update {
                        it.copy(
                            isPlaying = true,
                            currentTrack = tempTrack,
                            currentLyrics = null
                        )
                    }

                    fetchSpotifyHighResArt(title, artist)
                }
            }
        }
    }



    private fun fetchSpotifyHighResArt(title: String, artist: String) {
        viewModelScope.launch {
            // --- CONSULT THE ARCHIVES ---
            // We call the repository function we just polished.
            mediaDataRepository.getHighResAlbumArt(title, artist)
                .collect { highResUrl ->
                    highResUrl?.let { url ->
                        val newUri = Uri.parse(url)

                        _uiState.update { state ->
                            val current = state.currentTrack

                            // VALIDATION: Ensure we are still playing the Spotify Remote track
                            // and that the artwork isn't already set to this URI.
                            if (current?.id == "spotify:remote" &&
                                current.title == title &&
                                current.artworkUri != newUri) {

                                Log.d("TigerVM", "High-res art manifest successful for: $title")
                                state.copy(
                                    currentTrack = current.copy(artworkUri = newUri)
                                )
                            } else {
                                state
                            }
                        }
                    }
                }
        }
    }
    fun playTrack(track: AudioTrack) {
        viewModelScope.launch {
            val isSpotifyTrack = track.id.startsWith("spotify:")
            if (isSpotifyTrack) {
                mediaControllerManager.pause()
                spotifyRepository.playUri(track.id)
            } else {
                spotifyRepository.pause()
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
            if (_uiState.value.isPlaying) spotifyRepository.pause() else spotifyRepository.resume()
        } else {
            if (_uiState.value.isPlaying) mediaControllerManager.pause() else mediaControllerManager.resume()
        }
    }

    fun seekTo(position: Long) {
        val isSpotify = _uiState.value.currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.seekTo(position) else mediaControllerManager.seekTo(position)
    }

    fun toggleShuffle() {
        val isSpotify = _uiState.value.currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.toggleShuffle()
            _uiState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
        } else {
            mediaControllerManager.toggleShuffleMode()
        }
    }

    fun toggleRepeat() {
        val isSpotify = _uiState.value.currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.toggleRepeat()
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
        if (isSpotify) spotifyRepository.skipNext() else mediaControllerManager.skipToNext()
    }

    fun skipToPrevious() {
        val isSpotify = _uiState.value.currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.skipPrevious() else mediaControllerManager.skipToPrevious()
    }

    fun addToQueue(track: AudioTrack) {
        val isSpotify = track.id.startsWith("spotify:")
        if (isSpotify) {
            Log.w("TigerPlayer", "Spotify queueing requires extended API access.")
        } else {
            mediaControllerManager.addNextToQueue(track)
        }
    }

    private fun performAutoRitual() {
        viewModelScope.launch {
            combine(
                navidromePrefs.serverUrl,
                navidromePrefs.username,
                navidromePrefs.password
            ) { url, user, pass -> Triple(url, user, pass) }
                .collect { (url, user, pass) ->
                    if (!url.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank()) {
                        val finalUrl = url.ensureValidUrl()
                        hostManager.currentBaseUrl = finalUrl

                        navidromeRepository.pingServer(user, pass).onSuccess {
                            loadLocalAudio()
                        }.onFailure { e ->
                            Log.e("TigerRitual", "Navidrome Overture Failed: ${e.message}")
                        }
                    }
                }
        }
    }

    private fun String.ensureValidUrl(): String {
        val clean = if (startsWith("http")) this else "http://$this"
        return if (clean.endsWith("/")) clean else "$clean/"
    }

    private fun fetchTrackMetadata(track: AudioTrack) {
        // Cancel the previous hunt to save bandwidth and CPU
        metadataFetchJob?.cancel()

        metadataFetchJob = viewModelScope.launch {
            // We launch these as siblings in one job for easier management
            launch {
                // 1. Hunt for Artist Lore (Spotify + Last.fm + DB Cache)
                mediaDataRepository.getArtistDetails(track.artist).collect { details ->
                    // Update the persistent map for the session
                    _artistDetails.update { it + (track.artist to details) }

                    // Update UI state for the current view
                    _uiState.update { it.copy(artistImageUrl = details.imageUrl) }
                }
            }

            launch {
                // 2. Hunt for Lyrics
                lyricsRepository.getLyrics(track).collect { lyrics ->
                    _uiState.update { it.copy(currentLyrics = lyrics) }
                }
            }
        }
    }

    /**
     * THE ARCHIVE CONSULTATION:
     * Pre-fetches or restores artist profiles without interrupting playback.
     */
    fun fetchArtistProfile(artistName: String) {
        val baseName = ArtistUtils.getBaseArtist(artistName)

        // Check memory cache first to prevent redundant Flow emissions
        if (_artistDetails.value.containsKey(baseName)) return

        viewModelScope.launch {
            Log.d("TigerVM", "Consulting Archives for artist profile: $baseName")

            // Use the unified Repository instead of bypassing it.
            // This ensures the hunt checks the DB and Spotify before Last.fm.
            mediaDataRepository.getArtistDetails(baseName).collect { details ->
                _artistDetails.update { it + (baseName to details) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val detailedStatsState: StateFlow<DetailedStatsUiState> = _statsFilter.flatMapLatest { filter ->
        val startTime = calculateStartTimeForFilter(filter)
        combine(
            historyRepository.getTotalListeningTime(startTime),
            historyRepository.getTopArtists(startTime, limit = 5),
            historyRepository.getTopTracks(startTime, limit = 5),
            _uiState.map { it.tracks }
        ) { totalTimeMs, topArtistsDb, topTracksDb, allTracks ->
            val totalSeconds = (totalTimeMs ?: 0L) / 1000
            val hours = (totalSeconds / 3600).toInt()
            val minutes = ((totalSeconds % 3600) / 60).toInt()

            DetailedStatsUiState(
                selectedFilter = filter,
                totalListeningHours = hours,
                totalListeningMinutes = minutes,
                topArtists = topArtistsDb.map { artist ->
                    val cachedImg = _artistDetails.value[artist.artistName]?.imageUrl
                    StatItem(
                        artist.artistName,
                        artist.artistName,
                        artist.playCount,
                        "Artist",
                        cachedImg
                    )
                },
                topTracks = topTracksDb.map { track ->
                    val albumArt = allTracks.find { it.id == track.trackId }?.artworkUri?.toString()
                    StatItem(track.trackId, track.title, track.playCount, track.artist, albumArt)
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

    // --- RECTIFIED HISTORY RECORDING ---
    private suspend fun recordPlaybackHistory(track: AudioTrack) {
        // We record the track's actual duration (or at least the milestone reached)
        // rather than a hardcoded 10 seconds.
        historyRepository.addTrackToHistory(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            imageUrl = track.artworkUri.toString(),
            durationMs = track.durationMs, // Use the actual track duration
            source =  MediaSource.LOCAL
        )
    }
    companion object {
        // THE ANCHOR: This ID is reserved for internal logic.
        // It prevents "shadow" duplicates because Room/SQLite won't allow two -1L IDs.
        const val LIKED_SONGS_ID = -1L
    }

    fun addTrackToLikedSongs(track: AudioTrack) {
        viewModelScope.launch {
            val playlists = customPlaylists.value
            val exists = playlists.any { it.id == LIKED_SONGS_ID }

            // 1. Forge the vault if it's missing from the realm
            if (!exists) {
                // NOTE: Ensure your repository.createPlaylist supports an ID parameter
                audioRepository.createPlaylist("Liked Songs", id = LIKED_SONGS_ID)

                // Wait for the flow to emit the new list to ensure the DB has settled
                customPlaylists.first { list -> list.any { it.id == LIKED_SONGS_ID } }
            }

            // 2. Direct injection using the Constant ID
            audioRepository.addTrackToPlaylist(LIKED_SONGS_ID, track.id)

            // 3. Update the local entity and ensure the Repository persists this flag
            track.isLiked = true
            audioRepository.updateTrackLikeStatus(track.id, true)
        }
    }

    fun removeTrackFromLikedSongs(track: AudioTrack) {
        viewModelScope.launch {
            // No need to "find" the playlist. We target the Anchor ID directly.
            audioRepository.removeTrackFromPlaylist(LIKED_SONGS_ID, track.id)

            // Update local state and persist to database
            track.isLiked = false
            audioRepository.updateTrackLikeStatus(track.id, false)
        }
    }

    fun toggleTrackLikeStatus(track: AudioTrack) {
        viewModelScope.launch {
            // 1. Determine the new state
            val newState = !track.isLiked

            if (newState) {
                // RITUAL OF ADDITION
                ensureLikedPlaylistExists()
                audioRepository.addTrackToPlaylist(PlayerViewModel.LIKED_SONGS_ID, track.id)
            } else {
                // RITUAL OF REMOVAL
                audioRepository.removeTrackFromPlaylist(PlayerViewModel.LIKED_SONGS_ID, track.id)
            }

            // 2. Persist the 'Heart' flag in the cached_tracks table
            // This ensures the heart stays filled even after a restart
            audioRepository.updateTrackLikeStatus(track.id, newState)

            // 3. Update the local track object if it's currently being viewed
            // If your UI is observing a Flow of tracks, this update will propagate automatically.
            track.isLiked = newState

             _uiState.update { it.copy(currentTrack = it.currentTrack?.copy(isLiked = newState)) }
        }
    }

    private suspend fun ensureLikedPlaylistExists() {
        val playlists = customPlaylists.value
        val exists = playlists.any { it.id == PlayerViewModel.LIKED_SONGS_ID }

        if (!exists) {
            // Create the singleton 'Liked Songs' vault with our reserved ID
            audioRepository.createPlaylist("Liked Songs", id = PlayerViewModel.LIKED_SONGS_ID)

            // Wait for the flow to acknowledge the new playlist to prevent race conditions
            customPlaylists.first { list -> list.any { it.id == PlayerViewModel.LIKED_SONGS_ID } }
        }
    }


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

    fun onAuthSuccess(newToken: String) {
        authManager.updateToken(newToken)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state -> aggregateLibrary(state.tracks, query) }
    }

    fun clearSearch() {
        onSearchQueryChanged("")
    }

    // 1. HARDEN THE LIBRARY AGGREGATION
    private fun aggregateLibrary(tracks: List<AudioTrack>, query: String = ""): PlayerUiState {
        // Filter out duplicates from the source
        val uniqueSource = tracks.distinctBy { it.id }

        val filtered = if (query.isEmpty()) uniqueSource else uniqueSource.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
        }

        val aggregatedArtists = filtered
            .groupBy { ArtistUtils.getBaseArtist(it.artist).lowercase() }
            .map { (_, artistTracks) ->
                val properName = ArtistUtils.getBaseArtist(artistTracks.first().artist)
                LibraryArtist(
                    name = properName,
                    trackCount = artistTracks.size,
                    albumCount = artistTracks.distinctBy { it.album.trim().lowercase() }.size
                )
            }
            .sortedBy { it.name }

        return _uiState.value.copy(
            tracks = uniqueSource,
            filteredTracks = filtered,
            artists = aggregatedArtists,
            albums = filtered.map { it.album.trim() }.distinct().sorted(),
            searchQuery = query
        )
    }

    fun syncNavidromeArchives() {
        viewModelScope.launch(Dispatchers.IO) {
            // Grab the latest credentials from DataStore
            val url = navidromePrefs.serverUrl.firstOrNull()
            val user = navidromePrefs.username.firstOrNull()
            val pass = navidromePrefs.password.firstOrNull()

            // If no credentials, the user hasn't logged in. Abort gracefully.
            if (url.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) {
                Log.d("LibrarySync", "No Navidrome credentials found. Skipping remote sync.")
                _remoteTracks.value = emptyList() // Ensure remote is cleared if they logged out
                return@launch
            }

            try {
                Log.d("LibrarySync", "Contacting Navidrome Server at $url...")

                // Fetch the Subsonic JSON models
                val result = navidromeRepository.getAllRemoteTracks(user, pass)

                result.onSuccess { remoteList ->
                    // THE TRANMUTATION: Convert RemoteTrack -> AudioTrack using our Mapper
                    val mappedTracks = remoteList.map { remoteTrack ->
                        remoteTrack.toAudioTrack(
                            serverUrl = url,
                            username = user,
                            pass = pass
                        )
                    }

                    Log.d("LibrarySync", "Navidrome sync complete! Added ${mappedTracks.size} tracks.")
                    _remoteTracks.value = mappedTracks

                }.onFailure { error ->
                    Log.e("LibrarySync", "Failed to reach Navidrome: ${error.message}")
                    // Don't clear existing _remoteTracks on network error, so they can still see cached UI
                }
            } catch (e: Exception) {
                Log.e("LibrarySync", "Critical failure during Navidrome sync", e)
            }
        }
    }

    // 2. HARDEN THE QUEUE SYNC
    private fun updateQueue() {
        val controller = mediaControllerManager.mediaController ?: return
        val knownTracks = _uiState.value.tracks.associateBy { it.id }

        val resolvedQueue = (0 until controller.mediaItemCount).map { i ->
            val mediaItem = controller.getMediaItemAt(i)
            // If the track is in our library, use the full object.
            // Otherwise, use the converted MediaItem.
            knownTracks[mediaItem.mediaId] ?: mediaItem.toAudioTrack()
        }

        _uiState.update { it.copy(queue = resolvedQueue) }
    }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadLocalAudio(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            audioRepository.getLocalTracksWithProgress(forceRefresh)
                .onStart {
                    _uiState.update { it.copy(isLoading = true, isScanning = true) }
                }
                .catch { e ->
                    Log.e("TigerPlayer", "Scan Ritual Failed", e)
                    _uiState.update { it.copy(isScanning = false, isLoading = false) }
                }
                .collect { status ->
                    when (status) {
                        is LocalAudioDataSource.ScanStatus.Started -> {
                            _uiState.update {
                                it.copy(
                                    isScanning = true,
                                    totalFilesToScan = status.total
                                )
                            }
                        }
                        is LocalAudioDataSource.ScanStatus.InProgress -> {
                            _uiState.update {
                                it.copy(
                                    isScanning = true,
                                    scanProgress = status.current,
                                    totalFilesToScan = status.total
                                )
                            }
                        }
                        is LocalAudioDataSource.ScanStatus.Complete -> {
                            _uiState.update { currentState ->
                                val newState = aggregateLibrary(status.tracks, currentState.searchQuery)
                                newState.copy(
                                    tracks = status.tracks,
                                    filteredTracks = if (currentState.searchQuery.isEmpty()) status.tracks else newState.filteredTracks,
                                    isScanning = false,
                                    isLoading = false
                                )
                            }
                        }
                    }
                }
        }
    }
    /**
     * THE QUEUE MANIFEST:
     * Synchronizes the internal Media3 playlist with the UI state,
     * resolving IDs into full AudioTrack objects.
     */

    // THE MISSING RITUAL: Sever the shadow
    fun removeFromQueue(track: AudioTrack) {
        // Tell the Media3 Engine to drop the track
        mediaControllerManager.removeFromQueue(track.id)

        // NOTE: You do NOT need to call updateQueue() here manually!
        // The MediaController will fire 'onTimelineChanged', which automatically
        // triggers the observer we just set up above. It's perfectly reactive.
    }    @OptIn(UnstableApi::class)
    private fun androidx.media3.common.MediaItem.toAudioTrack(): AudioTrack {
        val metadata = mediaMetadata
        return AudioTrack(
            id = mediaId,
            title = metadata.title?.toString() ?: "Unknown Scroll",
            artist = metadata.artist?.toString() ?: "Unknown Witcher",
            album = metadata.albumTitle?.toString() ?: "Unknown Archive",
            durationMs = 0L, // Duration is usually handled by the player state
            uri = Uri.EMPTY,
            trackNumber = metadata.trackNumber ?: 0,
            artworkUri = metadata.artworkUri ?: Uri.EMPTY,
            mimeType = "audio/*",
            isLocal = mediaId.startsWith("/"), // Simple heuristic
            isRemote = mediaId.startsWith("http") || mediaId.contains("spotify")
        )
    }
}