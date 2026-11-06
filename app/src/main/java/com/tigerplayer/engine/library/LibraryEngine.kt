package com.tigerplayer.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.M3uExportResult
import com.tigerplayer.data.model.M3uImportResult
import com.tigerplayer.data.model.M3uPathMode
import com.tigerplayer.data.model.MusicFolder
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.repository.HistoryRepository
import com.tigerplayer.data.repository.M3uRepository
import com.tigerplayer.data.repository.MediaDataRepository
import com.tigerplayer.data.repository.MusicFolderRepository
import com.tigerplayer.ui.home.HomeUiState
import com.tigerplayer.ui.home.UserStatistics
import com.tigerplayer.ui.player.LibraryArtist
import com.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Orchestrates local and remote music library data, aggregation, sorting, searching,
 * playlist manipulation, and custom folder indexing.
 *
 * Serves as the domain engine layer between ViewModels and data repositories.
 */
class LibraryEngine @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val audioRepository: AudioRepository,
    private val mediaDataRepository: MediaDataRepository,
    private val musicFolderRepository: MusicFolderRepository,
    private val m3uRepository: M3uRepository
) {

    /** Supported sorting criteria for tracks and albums in the library. */
    enum class SortOrder {
        TITLE, ARTIST, DATE_ADDED, RELEASE_DATE
    }

    companion object {
        /** Reserved playlist ID for liked songs. */
        const val LIKED_SONGS_ID = -1L
        /** Reserved playlist ID for recently added tracks. */
        const val RECENTLY_ADDED_ID = -2L
    }

    private val _searchQuery = MutableStateFlow("")
    /** Observable state flow representing the current library search query string. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Updates the current search query filter. */
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    /** Clears the active search query. */
    fun clearSearch() { _searchQuery.value = "" }

    /**
     * Container holding aggregated library state including filtered tracks, artists, and albums.
     */
    data class LibraryAggregation(
        val tracks: List<AudioTrack>,
        val filteredTracks: List<AudioTrack>,
        val artists: List<LibraryArtist>,
        val albums: List<LibraryAlbum>
    )

    /**
     * Domain model representing an aggregated music album in the library.
     */
    data class LibraryAlbum(
        val name: String,
        val artist: String,
        val trackCount: Int,
        val year: String?,
        val dateAdded: Long,
        val artworkUri: Uri?
    )

    /** Internal state container for home screen statistics and history. */
    private data class HomeBaseState(
        val recentEntities: List<PlaybackHistoryEntity>,
        val totalMs: Long,
        val todayMs: Long,
        val topArtist: String?,
        val allTracks: List<AudioTrack>
    )

    /**
     * Combines unified tracks, search query, and sorting preferences into an aggregated library flow.
     */
    fun getAggregatedLibraryFlow(
        unifiedTracksFlow: Flow<List<AudioTrack>>,
        trackSortOrder: Flow<SortOrder> = flowOf(SortOrder.TITLE),
        albumSortOrder: Flow<SortOrder> = flowOf(SortOrder.TITLE)
    ): Flow<LibraryAggregation> {
        return combine(unifiedTracksFlow, _searchQuery, trackSortOrder, albumSortOrder) { tracks, query, tSort, aSort ->
            aggregateLibrary(tracks, query, tSort, aSort)
        }
    }

    /**
     * Performs pure aggregation, filtering, and sorting of tracks into tracks, artists, and albums.
     */
    fun aggregateLibrary(
        tracks: List<AudioTrack>,
        query: String = "",
        trackSort: SortOrder = SortOrder.TITLE,
        albumSort: SortOrder = SortOrder.TITLE
    ): LibraryAggregation {
        val uniqueSource = tracks.distinctBy { it.id }
        val filtered = if (query.isEmpty()) uniqueSource else uniqueSource.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
        }

        val sortedTracks = when (trackSort) {
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortOrder.RELEASE_DATE -> filtered.sortedByDescending { it.year ?: "" }
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

        val aggregatedAlbums = filtered
            .groupBy { it.album.trim().lowercase() }
            .map { (_, albumTracks) ->
                val first = albumTracks.first()
                LibraryAlbum(
                    name = first.album,
                    artist = first.artist,
                    trackCount = albumTracks.size,
                    year = albumTracks.mapNotNull { it.year }.firstOrNull(),
                    dateAdded = albumTracks.maxOf { it.dateAdded },
                    artworkUri = first.artworkUri
                )
            }.let { albums ->
                when (albumSort) {
                    SortOrder.TITLE -> albums.sortedBy { it.name.lowercase() }
                    SortOrder.ARTIST -> albums.sortedBy { it.artist.lowercase() }
                    SortOrder.DATE_ADDED -> albums.sortedByDescending { it.dateAdded }
                    SortOrder.RELEASE_DATE -> albums.sortedByDescending { it.year ?: "" }
                }
            }

        return LibraryAggregation(
            tracks = uniqueSource,
            filteredTracks = sortedTracks,
            artists = aggregatedArtists,
            albums = aggregatedAlbums
        )
    }

    /**
     * Exposes the [HomeUiState] flow containing listening statistics, recent tracks, and recommendations.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getHomeUiStateFlow(allTracksFlow: Flow<List<AudioTrack>>): Flow<HomeUiState> {
        val recommendationTicker = flow {
            while (true) {
                emit(System.currentTimeMillis() / (2 * 60 * 60 * 1000))
                delay((30 * 60 * 1000).milliseconds)
            }
        }.distinctUntilChanged()

        val baseHomeStateFlow = combine(
            historyRepository.recentTracks,
            historyRepository.totalListeningTime,
            historyRepository.listeningTimeToday,
            historyRepository.topArtistThisWeek,
            allTracksFlow.distinctUntilChanged()
        ) { recentEntities, totalMs, todayMs, topArtist, allTracks ->
            HomeBaseState(
                recentEntities = recentEntities,
                totalMs = totalMs ?: 0L,
                todayMs = todayMs ?: 0L,
                topArtist = topArtist,
                allTracks = allTracks
            )
        }

        return combine(
            baseHomeStateFlow,
            recommendationTicker
        ) { baseState, seed ->
            val recentEntities = baseState.recentEntities
            val totalMs = baseState.totalMs
            val todayMs = baseState.todayMs
            val topArtist = baseState.topArtist
            val allTracks = baseState.allTracks

            val resolvedTotalMs = totalMs
            val resolvedTodayMs = todayMs
            val totalHours = resolvedTotalMs / (1000 * 60 * 60)
            val todayHours = resolvedTodayMs / (1000 * 60 * 60)
            val todayMinutes = (resolvedTodayMs / (1000 * 60)) % 60
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
                    listeningTimeTodayMs = resolvedTodayMs,
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

    /** Returns a flow of custom user playlists. */
    fun getCustomPlaylists(): Flow<List<Playlist>> = audioRepository.getCustomPlaylists()

    /** Creates a new custom playlist with the specified [name]. */
    suspend fun createPlaylist(name: String) = audioRepository.createPlaylist(name)

    /** Saves a snapshot of curated tracks into a new standalone playlist with the specified [name]. */
    suspend fun saveCuratedPlaylist(name: String, tracks: List<AudioTrack>, artworkUri: String? = null): Long =
        audioRepository.createPlaylistWithTracks(name, tracks, artworkUri)

    /** Deletes the playlist identified by [playlistId]. */
    suspend fun deletePlaylist(playlistId: Long) {
        mediaDataRepository.deletePlaylist(playlistId)
    }

    /** Renames the specified playlist. */
    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        mediaDataRepository.renamePlaylist(playlistId, newName)
    }

    /** Adds a track to a playlist. */
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        audioRepository.addTrackToPlaylist(playlistId, trackId)
    }

    /** Returns tracks for a specific playlist ID, handling virtual playlists like [RECENTLY_ADDED_ID]. */
    fun getPlaylistTracks(playlistId: Long, currentLibrary: List<AudioTrack>): Flow<List<AudioTrack>> {
        return when (playlistId) {
            RECENTLY_ADDED_ID -> flowOf(currentLibrary.reversed().take(20))
            else -> audioRepository.getTracksForPlaylist(playlistId)
        }
    }

    /** Removes a track from a specific playlist. */
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        audioRepository.removeTrackFromPlaylist(playlistId, trackId)
    }

    /** Saves a reordered sequence of tracks for a playlist. */
    suspend fun savePlaylistOrder(playlistId: Long, reorderedTracks: List<AudioTrack>) {
        val trackIds = reorderedTracks.map { it.id }
        mediaDataRepository.updatePlaylistOrder(playlistId, trackIds)
        Log.d("LibraryEngine", "Playlist $playlistId reordered successfully.")
    }

    /** Updates playlist custom artwork taking persistable URI permission. */
    suspend fun updatePlaylistArtwork(context: Context, playlistId: Long, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            mediaDataRepository.updatePlaylistArtwork(playlistId, uri.toString())
            Log.d("LibraryEngine", "Artwork updated for playlist $playlistId")
        } catch (e: Exception) {
            Log.e("LibraryEngine", "Failed to secure artwork URI permission", e)
        }
    }

    /**
     * Puts the track as a liked song in the singleton liked songs playlist (-1L)
     * and marks it as liked in persistent storage.
     */
    suspend fun likeTrack(track: AudioTrack): Boolean {
        ensureLikedPlaylistExists()
        audioRepository.addTrackToPlaylist(LIKED_SONGS_ID, track.id)
        audioRepository.updateTrackLikeStatus(track.id, true)
        return true
    }

    /** Toggles the liked status of an audio track, updating the liked songs playlist. */
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

    /** Returns a flow tracking local audio media store scan progress. */
    fun getLocalAudioScanFlow(forceRefresh: Boolean = false): Flow<com.tigerplayer.data.source.LocalAudioDataSource.ScanStatus> {
        return audioRepository.getLocalTracksWithProgress(forceRefresh)
    }

    /** Returns a flow of custom music folders (issue #50). */
    fun getMusicFolders(): Flow<List<MusicFolder>> = musicFolderRepository.getFolders()

    /** Adds a custom music folder URI (either included or excluded). */
    suspend fun addMusicFolder(treeUri: Uri, isExcluded: Boolean): Boolean {
        return musicFolderRepository.addFolder(treeUri, isExcluded)
    }

    /** Removes a custom music folder by its URI string. */
    suspend fun removeMusicFolder(uriString: String) {
        musicFolderRepository.removeFolder(uriString)
    }

    /** Builds the folder browser index for the given list of tracks. */
    fun buildFolderIndex(tracks: List<AudioTrack>): Map<String, FolderTreeBuilder.FolderContents> =
        FolderTreeBuilder.index(tracks)

    /** Ensures the synthetic "Liked Songs" playlist exists in storage and merges legacy duplicates. */
    suspend fun ensureLikedPlaylistExists() {
        audioRepository.ensureSingletonLikedPlaylist()
    }

    /**
     * Imports an M3U/M3U8 playlist from SAF Uri (issue #58).
     */
    suspend fun importM3uPlaylist(
        fileUri: Uri,
        customPlaylistName: String? = null
    ): Result<M3uImportResult> {
        return m3uRepository.importPlaylist(fileUri, customPlaylistName)
    }

    /**
     * Exports a playlist to .m3u8 format via SAF Uri (issue #58).
     */
    suspend fun exportM3uPlaylist(
        playlistId: Long,
        destinationUri: Uri,
        pathMode: M3uPathMode = M3uPathMode.ABSOLUTE
    ): Result<M3uExportResult> {
        return m3uRepository.exportPlaylist(playlistId, destinationUri, pathMode)
    }
}
