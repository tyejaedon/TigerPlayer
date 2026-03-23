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
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val lastFmRepository: LastFmRepository, // <--- THE LAST.FM INJECTION
    private val navidromeRepository: NavidromeRepository,
    private val spotifyRepository: SpotifyRepository,
    private val hostManager: SubsonicHostManager,
    private val authManager: SpotifyAuthManager,
    private val navidromePrefs: NavidromePrefs
) : ViewModel() {

    // --- State Flows ---
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _artistDetails = MutableStateFlow<Map<String, ArtistDetails>>(emptyMap())
    val artistDetails: StateFlow<Map<String, ArtistDetails>> = _artistDetails.asStateFlow()

    private val _trackColor = MutableStateFlow(Color(0xFF007AFF))
    val trackColor: StateFlow<Color> = _trackColor.asStateFlow()

    private val _statsFilter = MutableStateFlow("Today")

    // --- Jobs & Caches ---
    private var lyricsFetchJob: Job? = null
    private var artistImageFetchJob: Job? = null

    val customPlaylists: StateFlow<List<Playlist>> =
        audioRepository.getCustomPlaylists()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    init {
        observeLibrary()
        observeMediaEngine()
        observeHistory()
        observeSpotifyRemote()
        performAutoRitual()

        loadLocalAudio()
    }

    // ==========================================
    // --- OBSERVERS & CORE ENGINE ---
    // ==========================================

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
        val recommendationTicker = flow {
            while (true) {
                emit(System.currentTimeMillis() / (2 * 60 * 60 * 1000))
                delay(30 * 60 * 1000)
            }
        }.distinctUntilChanged()

        viewModelScope.launch {
            combine(
                historyRepository.recentTracks,
                historyRepository.totalListeningTime,
                historyRepository.topArtist,
                _uiState.map { it.tracks }.distinctUntilChanged(),
                recommendationTicker
            ) { recent, totalTime, topArtist, allTracks, seed ->
                val hours = (totalTime ?: 0L) / (1000 * 60 * 60)
                val minutes = ((totalTime ?: 0L) / (1000 * 60)) % 60

                val random = Random(seed)
                val recommended = if (allTracks.isNotEmpty()) {
                    allTracks.asSequence()
                        .filter { it.album.isNotBlank() }
                        .groupBy { it.album }
                        .values
                        .toList()
                        .shuffled(random)
                        .take(5)
                        .flatten()
                } else emptyList()

                HomeUiState(
                    statistics = UserStatistics(
                        totalListeningTimeHours = hours.toInt(),
                        topArtistThisWeek = topArtist ?: "New Recruit",
                        listeningTimeToday = "${hours}h ${minutes}m",
                        losslessTrackCount = allTracks.count {
                            it.mimeType.contains("flac", ignoreCase = true) || it.bitrate > 320000
                        }
                    ),
                    discoverTracks = allTracks.shuffled(random).take(10),
                    recentlyPlayedTracks = recent.mapNotNull { entity ->
                        allTracks.find { it.id == entity.trackId }
                    },
                    recommendedTracks = recommended,
                    isLoading = false
                )
            }.collect { state ->
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
                    _uiState.update {
                        it.copy(currentTrack = track, currentLyrics = null, artistImageUrl = null)
                    }
                    fetchTrackMetadata(track)
                }
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

        viewModelScope.launch {
            mediaControllerManager.mediaControllerState.collect { _ ->
                val controller = mediaControllerManager.mediaController ?: return@collect
                val queueTracks = mutableListOf<AudioTrack>()
                for (i in 0 until controller.mediaItemCount) {
                    val mediaId = controller.getMediaItemAt(i).mediaId
                    _uiState.value.tracks.find { it.id == mediaId }?.let { queueTracks.add(it) }
                }
                _uiState.update { it.copy(queue = queueTracks) }
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
            mediaDataRepository.getHighResAlbumArt(title, artist).collect { highResUrl ->
                highResUrl?.let { url ->
                    _uiState.update { state ->
                        if (state.currentTrack?.id == "spotify:remote" && state.currentTrack.title == title) {
                            state.copy(
                                currentTrack = state.currentTrack.copy(artworkUri = Uri.parse(url))
                            )
                        } else state
                    }
                }
            }
        }
    }

    // ==========================================
    // --- PLAYBACK CONTROLS ---
    // ==========================================

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
        if (isSpotify) spotifyRepository.seekTo(position) else mediaControllerManager.seekTo(
            position
        )
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


    // ==========================================
    // --- METADATA, STATS & PLAYLISTS ---
    // ==========================================

    private fun fetchTrackMetadata(track: AudioTrack) {
        lyricsFetchJob?.cancel()
        artistImageFetchJob?.cancel()

        // Leaves MediaDataRepository here for quick image loading on the player screen
        artistImageFetchJob = viewModelScope.launch {
            mediaDataRepository.getArtistDetails(track.artist).collect { details ->
                _artistDetails.update { it + (track.artist to details) }
                _uiState.update { it.copy(artistImageUrl = details.imageUrl) }
            }
        }

        lyricsFetchJob = viewModelScope.launch {
            lyricsRepository.getLyrics(track).collect { lyrics ->
                _uiState.update { it.copy(currentLyrics = lyrics) }
            }
        }
    }

    /**
     * THE LORE RITUAL UPDATE
     * Uses Last.fm to fetch the rich biography and listener tags.
     */
    fun fetchArtistProfile(artistName: String) {
        val baseName = ArtistUtils.getBaseArtist(artistName)
        if (_artistDetails.value.containsKey(baseName)) return

        viewModelScope.launch {
            Log.d("PlayerVM", "Consulting Last.fm archives for: $baseName")

            // Calls the new Last.fm Repository
            val lastFmDetails = lastFmRepository.fetchArtistProfile(baseName)

            if (lastFmDetails != null) {
                // Assuming ArtistDetails is the model returned by the repository
                _artistDetails.update { (it + (baseName to lastFmDetails)) as Map<String, ArtistDetails> }
            } else {
                Log.w("PlayerVM", "No lore found for $baseName on Last.fm")
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

    fun addTrackToLikedSongs(track: AudioTrack) {
        viewModelScope.launch {
            val playlists = customPlaylists.value
            var likedPlaylist = playlists.find { it.name.equals("Liked Songs", ignoreCase = true) }

            if (likedPlaylist == null) {
                audioRepository.createPlaylist("Liked Songs")
                delay(200)
                val updatedPlaylists = audioRepository.getCustomPlaylists().first()
                likedPlaylist =
                    updatedPlaylists.find { it.name.equals("Liked Songs", ignoreCase = true) }
            }

            likedPlaylist?.let {
                audioRepository.addTrackToPlaylist(it.id, track.id)
            }
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

    // ==========================================
    // --- LIBRARY & NETWORK MANAGEMENT ---
    // ==========================================

    fun onAuthSuccess(newToken: String) {
        authManager.updateToken(newToken)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state -> aggregateLibrary(state.tracks, query) }
    }

    fun clearSearch() {
        onSearchQueryChanged("")
    }

    private fun aggregateLibrary(tracks: List<AudioTrack>, query: String = ""): PlayerUiState {
        val filtered = if (query.isEmpty()) tracks else tracks.filter {
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
            tracks = tracks,
            filteredTracks = filtered,
            artists = aggregatedArtists,
            albums = filtered.map { it.album.trim() }.distinct().sorted(),
            searchQuery = query
        )
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
}