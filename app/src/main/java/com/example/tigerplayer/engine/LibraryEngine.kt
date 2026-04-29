package com.example.tigerplayer.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.ui.home.HomeUiState
import com.example.tigerplayer.ui.home.UserStatistics
import com.example.tigerplayer.ui.player.LibraryArtist
import com.example.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.random.Random

class LibraryEngine @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val audioRepository: AudioRepository,
    private val mediaDataRepository: MediaDataRepository
) {

    companion object {
        const val LIKED_SONGS_ID = -1L
        const val RECENTLY_ADDED_ID = -2L
    }

    // --- SEARCH & AGGREGATION RITUALS ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }

    data class LibraryAggregation(
        val tracks: List<AudioTrack>,
        val filteredTracks: List<AudioTrack>,
        val artists: List<LibraryArtist>,
        val albums: List<String>
    )

    @OptIn(FlowPreview::class)
    fun getAggregatedLibraryFlow(unifiedTracksFlow: Flow<List<AudioTrack>>): Flow<LibraryAggregation> {
        return combine(
            unifiedTracksFlow,
            _searchQuery.debounce(250) // <--- Prevents keyboard stutter when typing fast
        ) { tracks, query ->
            aggregateLibrary(tracks, query)
        }
    }

    fun aggregateLibrary(tracks: List<AudioTrack>, query: String = ""): LibraryAggregation {
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
            }.sortedBy { it.name }

        return LibraryAggregation(
            tracks = uniqueSource,
            filteredTracks = filtered,
            artists = aggregatedArtists,
            albums = filtered.map { it.album.trim() }.distinct().sorted()
        )
    }

    // --- HOME & DISCOVER RITUALS ---
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getHomeUiStateFlow(allTracksFlow: Flow<List<AudioTrack>>): Flow<HomeUiState> {
        val recommendationTicker = flow {
            while (true) {
                emit(System.currentTimeMillis() / (2 * 60 * 60 * 1000))
                delay(30 * 60 * 1000)
            }
        }.distinctUntilChanged()

        return combine(
            historyRepository.recentTracks,
            historyRepository.totalListeningTime,
            historyRepository.listeningTimeToday,
            historyRepository.topArtist,
            allTracksFlow.distinctUntilChanged(),
            recommendationTicker
        ) { args: Array<Any?> ->
            val recentEntities = args[0] as List<PlaybackHistoryEntity>
            val totalMs = args[1] as? Long ?: 0L
            val todayMs = args[2] as? Long ?: 0L
            val topArtist = args[3] as? String
            val allTracks = args[4] as List<AudioTrack>
            val seed = args[5] as Long

            val totalHours = totalMs / (1000 * 60 * 60)
            val todayHours = todayMs / (1000 * 60 * 60)
            val todayMinutes = (todayMs / (1000 * 60)) % 60
            val random = Random(seed)

            val recommended = if (allTracks.isNotEmpty()) {
                allTracks.asSequence()
                    .filter { it.album.isNotBlank() && !it.album.equals("Unknown", true) }
                    .groupBy { it.album }
                    .values.toList().shuffled(random).take(5).flatten()
            } else emptyList()

            val recentlyPlayed = recentEntities.mapNotNull { entity ->
                allTracks.find { it.id == entity.trackId }
            }

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
        }
    }

    // --- PLAYLIST & GRIMOIRE VAULTS ---
    fun getCustomPlaylists(): Flow<List<Playlist>> = audioRepository.getCustomPlaylists()

    suspend fun createPlaylist(name: String) = audioRepository.createPlaylist(name)

    suspend fun deletePlaylist(playlistId: Long) {
        mediaDataRepository.deletePlaylist(playlistId)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        mediaDataRepository.renamePlaylist(playlistId, newName)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        audioRepository.addTrackToPlaylist(playlistId, trackId)
    }

    fun getPlaylistTracks(playlistId: Long, currentLibrary: List<AudioTrack>): Flow<List<AudioTrack>> {
        return when (playlistId) {
            RECENTLY_ADDED_ID -> flowOf(currentLibrary.reversed().take(20))
            else -> audioRepository.getTracksForPlaylist(playlistId)
        }
    }

    // NEW: Remove track from a specific playlist
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        audioRepository.removeTrackFromPlaylist(playlistId, trackId)
    }

    // NEW: Save custom rearranged order of the playlist
    suspend fun savePlaylistOrder(playlistId: Long, reorderedTracks: List<AudioTrack>) {
        // Implementation depends on your DAO. Usually requires updating a `position` column.
        val trackIds = reorderedTracks.map { it.id }
        mediaDataRepository.updatePlaylistOrder(playlistId, trackIds)
        Log.d("LibraryEngine", "Playlist $playlistId reordered successfully.")
    }

    // NEW: Update Playlist Cover Art using persistent URI permission
    suspend fun updatePlaylistArtwork(context: Context, playlistId: Long, uri: Uri) {
        try {
            // Take persistable permission so the app can read this URI after reboot
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            mediaDataRepository.updatePlaylistArtwork(playlistId, uri.toString())
            Log.d("LibraryEngine", "Artwork updated for playlist $playlistId")
        } catch (e: Exception) {
            Log.e("LibraryEngine", "Failed to secure artwork URI permission", e)
        }
    }

    // --- LIKED SONGS ---
    suspend fun toggleTrackLikeStatus(track: AudioTrack): Boolean {
        val newState = !track.isLiked
        if (newState) {
            ensureLikedPlaylistExists()
            audioRepository.addTrackToPlaylist(LIKED_SONGS_ID, track.id)
        } else {
            audioRepository.removeTrackFromPlaylist(LIKED_SONGS_ID, track.id)
        }
        audioRepository.updateTrackLikeStatus(track.id, newState)
        return newState
    }

    suspend fun addTrackToLikedSongs(trackId: String) {
        ensureLikedPlaylistExists()
        audioRepository.addTrackToPlaylist(LIKED_SONGS_ID, trackId)
        audioRepository.updateTrackLikeStatus(trackId, true)
    }

    suspend fun removeTrackFromLikedSongs(trackId: String) {
        audioRepository.removeTrackFromPlaylist(LIKED_SONGS_ID, trackId)
        audioRepository.updateTrackLikeStatus(trackId, false)
    }

    private suspend fun ensureLikedPlaylistExists() {
        val playlists = audioRepository.getCustomPlaylists().first()
        if (playlists.none { it.id == LIKED_SONGS_ID }) {
            audioRepository.createPlaylist("Liked Songs", id = LIKED_SONGS_ID)
            audioRepository.getCustomPlaylists().first { list -> list.any { it.id == LIKED_SONGS_ID } }
        }
    }
}