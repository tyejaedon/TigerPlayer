package com.tigerplayer.data.repository

import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.dao.TrackStats
import com.tigerplayer.data.local.entity.CachedTrackEntity
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.model.ArtistSource
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.data.source.LocalAudioDataSource
import com.tigerplayer.data.source.SafFolderScanner
import com.tigerplayer.di.IoDispatcher
import com.tigerplayer.utils.NavidromeMapper.toAudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class AudioRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localAudioDataSource: LocalAudioDataSource,
    private val playlistDao: PlaylistDao,
    private val tigerDao: TigerDao,
    private val navidromeRepository: NavidromeRepository,
    private val statsEpoch: StatsEpoch,
    private val musicFolderRepository: MusicFolderRepository,
    private val safFolderScanner: SafFolderScanner,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "AudioRepository"
        private const val MAX_ARTISTS_PER_PAGE = 50

        // Coalesces bursts of MediaStore change notifications (e.g. a multi-file copy) into a
        // single incremental resync instead of triggering one per row.
        private const val MEDIA_STORE_CHANGE_DEBOUNCE_MS = 1_500L
    }

    private val remoteCache: MutableStateFlow<List<AudioTrack>> = MutableStateFlow(emptyList())
    private val remoteRefreshMutex = Mutex()
    private val hasPrimedLocalScan = AtomicBoolean(false)
    private val localScanMutex = Mutex()

    // Lives for the process lifetime, mirroring this @Singleton's own lifecycle - there is no
    // narrower scope to tie it to, since MediaStore changes can arrive whenever the app process
    // is alive, not only while a screen observing the library is on-screen.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mediaStoreChangeSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        observeMediaStoreChanges()
    }

    /**
     * INCREMENTAL INDEXING (issue #49)
     * Registers a ContentObserver on the external audio collection so external library changes
     * (files added/removed/edited by other apps) are picked up as a cheap incremental resync
     * while the app is running, instead of relying on a full rescan at the next cold start.
     */
    @OptIn(FlowPreview::class)
    private fun observeMediaStoreChanges() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Never do the actual scan/diff work on the main thread - just signal it.
                mediaStoreChangeSignal.tryEmit(Unit)
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                /* notifyForDescendants = */ true,
                observer
            )
        } catch (e: Exception) {
            // Registration failing must never take the app down with it - the library still
            // works via the existing scan-on-demand path, it just won't pick up external
            // changes live until the next explicit refresh.
            Log.e(TAG, "Failed to register MediaStore ContentObserver: ${e.message}")
        }

        repositoryScope.launch {
            mediaStoreChangeSignal
                .debounce(MEDIA_STORE_CHANGE_DEBOUNCE_MS.milliseconds)
                .collectLatest {
                    try {
                        // SAF custom folders aren't covered by this observer (it only fires for
                        // MediaStore changes), so skip re-walking them on every debounced resync.
                        refreshLocalCache(forceRefresh = false, includeSafFolders = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Incremental MediaStore resync failed: ${e.message}")
                    }
                }
        }
    }

    /**
     * Combines local high-fidelity tracks (from Room cache) and Navidrome remote streams.
     * Ensures local files and remote tracks are merged into a single alphabetically sorted list
     * without blocking callers or leaking credentials (remote URIs are resolved on-demand).
     */
    fun getUnifiedTracks(
        user: String?,
        pass: String?,
        baseUrl: String?,
        forceRefresh: Boolean = false
    ): Flow<List<AudioTrack>> {
        val hasRemoteCredentials = !user.isNullOrBlank() && !pass.isNullOrBlank() && !baseUrl.isNullOrBlank()

        // Kick off (or await) the remote refresh as a side effect, not inline in the flow body.
        if (hasRemoteCredentials) {
            repositoryScope.launch {
                if (forceRefresh || remoteCache.value.isEmpty()) {
                    remoteRefreshMutex.withLock {
                        // double-check after acquiring the lock to avoid duplicate fetches
                        if (forceRefresh || remoteCache.value.isEmpty()) {
                            navidromeRepository.getAllRemoteTracks(user, pass)
                                .onSuccess { remoteTracks ->
                                    remoteCache.value = remoteTracks.map { it.toAudioTrack() }
                                }
                                .onFailure { error ->
                                    Log.e(TAG, "Archive sync failed: ${error.message}")
                                }
                        }
                    }
                }
            }
        }

        val remoteFlow = if (hasRemoteCredentials) remoteCache else flowOf(emptyList())

        return combine(getLocalTracks(forceRefresh), remoteFlow) { local, remote ->
            (local + remote).sortedBy { it.title.lowercase() }
        }
    }


    /**
     * Retrieves locally cached audio tracks. On first access (or when [forceRefresh] is true),
     * triggers an incremental reconcile against MediaStore and configured SAF directories.
     * Executed strictly on Dispatchers.IO to maintain main-safety.
     */
    fun getLocalTracks(forceRefresh: Boolean = false): Flow<List<AudioTrack>> = flow {
        val shouldScan = forceRefresh || hasPrimedLocalScan.compareAndSet(false, true)
        if (shouldScan) {
            refreshLocalCache(forceRefresh = forceRefresh)
        }

        emitAll(getCachedLocalTracks())
    }.flowOn(Dispatchers.IO)

    fun getCachedLocalTracks(): Flow<List<AudioTrack>> =
        tigerDao.getCachedTracks().map { entities ->
            entities.map { it.toDomainModel() }
        }

    fun getArtists(query: String, limit: Int, offset: Int): Flow<List<Artist>> {
        val boundedLimit = limit.coerceIn(1, MAX_ARTISTS_PER_PAGE)
        val boundedOffset = offset.coerceAtLeast(0)
        return tigerDao.getArtistPage(query.trim(), boundedLimit, boundedOffset)
            .map { artists ->
                artists.map { artist ->
                    Artist(
                        id = artist.name.lowercase(),
                        name = artist.name,
                        source = ArtistSource.LOCAL,
                        trackCount = artist.trackCount,
                        albumCount = artist.albumCount,
                        genres = artist.genres.orEmpty()
                            .split(',')
                            .map(String::trim)
                            .filter(String::isNotEmpty),
                        imageUrl = artist.imageUrl
                    )
                }
            }
            .flowOn(ioDispatcher)
    }

    /**
     * Used specifically when the UI needs to display the ScanningOverlay.
     */
    fun getLocalTracksWithProgress(forceRefresh: Boolean = false): Flow<LocalAudioDataSource.ScanStatus> = flow {
        val cachedTracks = tigerDao.getCachedTracksSync().map { it.toDomainModel() }

        if (cachedTracks.isNotEmpty() && !forceRefresh) {
            emit(LocalAudioDataSource.ScanStatus.Complete(cachedTracks))
        }

        // Always scan to ensure the ledger is accurate, emitting progress to the UI
        localAudioDataSource.getLocalAudioFiles().collect { status ->
            emit(status)

            if (status is LocalAudioDataSource.ScanStatus.Complete) {
                hasPrimedLocalScan.set(true)
                val mergedTracks = mergeWithCustomFolders(status.tracks, includeSafFolders = true)
                applyLibraryDiff(mergedTracks, forceRefresh)
            }
        }
    }

    private suspend fun refreshLocalCache(forceRefresh: Boolean, includeSafFolders: Boolean = true) {
        var scannedTracks: List<AudioTrack>? = null
        localAudioDataSource.getLocalAudioFiles().collect { status ->
            if (status is LocalAudioDataSource.ScanStatus.Complete) {
                scannedTracks = status.tracks
            }
        }

        val mergedTracks = mergeWithCustomFolders(scannedTracks ?: return, includeSafFolders)
        applyLibraryDiff(mergedTracks, forceRefresh)
    }

    /**
     * CUSTOM FOLDERS & EXCLUSIONS (issue #50)
     * Applies the exclude list to the MediaStore fast path, then - unless [includeSafFolders] is
     * false (skipped for cheap incremental MediaStore-only resyncs) - walks every "include" SAF
     * root and merges its tracks in, preferring the MediaStore-indexed copy when the same
     * absolute path was reached through both sources.
     */
    private suspend fun mergeWithCustomFolders(
        mediaStoreTracks: List<AudioTrack>,
        includeSafFolders: Boolean
    ): List<AudioTrack> {
        val excludedPaths = musicFolderRepository.getExcludedPathsSync()
        val filteredMediaStore = if (excludedPaths.isEmpty()) {
            mediaStoreTracks
        } else {
            mediaStoreTracks.filterNot { FolderExclusionRules.isTrackExcluded(it.path, excludedPaths) }
        }

        if (!includeSafFolders) return filteredMediaStore

        val includedFolders = musicFolderRepository.getIncludedFoldersSync()
        if (includedFolders.isEmpty()) return filteredMediaStore

        val safTracks = includedFolders.flatMap { folder ->
            try {
                safFolderScanner.scan(folder.uriString.toUri(), excludedPaths)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scan custom folder ${folder.displayName}: ${e.message}")
                emptyList()
            }
        }

        val mediaStorePaths = filteredMediaStore.mapNotNullTo(HashSet()) { it.path }
        val dedupedSafTracks = safTracks.filterNot { it.path != null && it.path in mediaStorePaths }

        return filteredMediaStore + dedupedSafTracks
    }

    /**
     * THE ONE DIFF (issue #49)
     * Routes every local-cache reconciliation - the initial cold-start scan, a user-triggered
     * rescan, and an incremental MediaStore-change resync - through [LibraryCacheDiffer] and
     * writes only the resulting delta, never a full-table rewrite. Guarded by [localScanMutex]
     * so concurrent callers (e.g. a manual rescan racing an incremental ContentObserver resync)
     * can't interleave reads and writes against the cache.
     */
    private suspend fun applyLibraryDiff(scannedTracks: List<AudioTrack>, forceRefresh: Boolean) {
        localScanMutex.withLock {
            val cachedFingerprints = tigerDao.getCachedTrackFingerprints()
            val diff = LibraryCacheDiffer.diff(
                cached = cachedFingerprints,
                scanned = scannedTracks,
                forceUpsertAll = forceRefresh
            )

            if (diff.hasChanges) {
                tigerDao.applyCachedTracksDelta(
                    upserts = diff.upserts.map { it.toEntity() },
                    removedIds = diff.removedIds.toList()
                )
            }
        }
    }


    // --- Playlist Persistence & Reordering Operations ---

    fun getCustomPlaylists(): Flow<List<Playlist>> {
        return flow {
            ensureSingletonLikedPlaylist()
            emitAll(playlistDao.getPlaylistsWithCount())
        }.flowOn(ioDispatcher)
            .onEach { list ->
                if (com.tigerplayer.BuildConfig.DEBUG) {
                    Log.d(TAG, "Playlists loaded: ${list.size}")
                }
            }
    }

    suspend fun createPlaylist(name: String, id: Long? = null) {
        withContext(ioDispatcher) {
            val trimmed = name.trim()
            if (trimmed.equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)) {
                ensureSingletonLikedPlaylist()
                return@withContext
            }
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    playlistId = id ?: 0L,
                    name = name,
                    artworkUri = null,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun createPlaylistWithTracks(
        name: String,
        tracks: List<AudioTrack>,
        artworkUri: String? = null
    ): Long = withContext(ioDispatcher) {
        val trimmed = name.trim().ifEmpty { "Curated Playlist" }
        val resolvedName = resolveUniquePlaylistName(trimmed)
        val selectedArtwork = artworkUri ?: tracks.firstOrNull()?.artworkUri?.toString()
        playlistDao.createPlaylistWithTracks(
            name = resolvedName,
            trackIds = tracks.map { it.id },
            artworkUri = selectedArtwork
        )
    }

    private suspend fun resolveUniquePlaylistName(baseName: String): String {
        val existingPlaylists = playlistDao.getAllPlaylistsSync().map { it.name }
        if (!existingPlaylists.any { it.equals(baseName, ignoreCase = true) }) {
            return baseName
        }
        var counter = 2
        while (true) {
            val candidate = "$baseName ($counter)"
            if (!existingPlaylists.any { it.equals(candidate, ignoreCase = true) }) {
                return candidate
            }
            counter++
        }
    }

    suspend fun ensureSingletonLikedPlaylist(): Long {
        return withContext(ioDispatcher) {
            val trackIds = playlistDao.ensureSingletonLikedPlaylist()
            trackIds.forEach { trackId ->
                tigerDao.updateTrackLikeStatus(trackId, true)
            }
            PlaylistEntity.LIKED_SONGS_PLAYLIST_ID
        }
    }

    suspend fun updateTrackLikeStatus(trackId: String, isLiked: Boolean) {
        tigerDao.updateTrackLikeStatus(trackId, isLiked)
    }

    /**
     * Persists updated high-resolution artwork URI in Room and resets negative cache tracking.
     * Called when artwork is asynchronously fetched by MetadataEngine.
     */
    suspend fun updateTrackArtworkUri(trackId: String, newUri: String) {
        tigerDao.updateTrackArtworkUri(trackId, newUri)
    }

    suspend fun getCachedTrack(trackId: String): CachedTrackEntity? {
        return tigerDao.getCachedTrackById(trackId)
    }

    suspend fun recordArtworkLookupFailure(
        trackId: String,
        failedAt: Long = System.currentTimeMillis(),
        attempts: Int
    ) {
        tigerDao.updateArtworkLookupFailed(trackId, failedAt, attempts)
    }

    suspend fun updateHasEmbeddedArtwork(trackId: String, hasEmbedded: Boolean) {
        tigerDao.updateHasEmbeddedArtwork(trackId, hasEmbedded)
    }

    /**
     * Inspects whether the local track has embedded artwork in the underlying audio file.
     * Results are cached in Room so expensive MediaMetadataRetriever checks occur at most once.
     */
    suspend fun hasEmbeddedArtwork(track: AudioTrack): Boolean = withContext(ioDispatcher) {
        if (track.hasEmbeddedArtwork != null) {
            return@withContext track.hasEmbeddedArtwork
        }
        val cached = tigerDao.getCachedTrackById(track.id)
        if (cached?.hasEmbeddedArtwork != null) {
            return@withContext cached.hasEmbeddedArtwork
        }
        val detected = checkEmbeddedArtwork(track)
        tigerDao.updateHasEmbeddedArtwork(track.id, detected)
        detected
    }

    private fun checkEmbeddedArtwork(track: AudioTrack): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            if (!track.path.isNullOrBlank() && File(track.path).exists()) {
                retriever.setDataSource(track.path)
            } else {
                retriever.setDataSource(context, track.uri)
            }
            val picture = retriever.embeddedPicture
            picture != null && picture.isNotEmpty()
        } catch (e: Exception) {
            false
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        playlistDao.addTrackToPlaylist(playlistId, trackId)
    }
     fun getAllTracksStats() = tigerDao.getAllTracksStats()

    /**
     * Heavy Rotation is a displayed statistic, so it is floored at the stats epoch to exclude
     * pre-#42 inflated rows (issue #80).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getHeavyRotation(since: Long): Flow<List<TrackStats>> {
        return statsEpoch.effectiveStart(since).flatMapLatest { tigerDao.getHeavyRotation(it) }
    }


    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        playlistDao.removeTrackAndReorder(playlistId, trackId)


    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<AudioTrack>> {
        return getCachedLocalTracks().combine(playlistDao.getTrackIdsForPlaylist(playlistId)) { allTracks, trackIds ->
            trackIds.mapNotNull { id -> allTracks.find { it.id == id } }
        }
    }

    fun getNeonDaylistTracks(
        segment: String,
        sinceMillis: Long,
        limit: Int = 15
    ): Flow<List<AudioTrack>> {
        return tigerDao.getNeonDaylistTracks(
            segment = segment,
            sinceMillis = sinceMillis,
            limit = limit
        ).map { entities -> entities.map { it.toDomainModel() } }
    }

    fun getVaultDiscoveryTracks(
        sinceMillis: Long,
        limit: Int = 15
    ): Flow<List<AudioTrack>> {
        return tigerDao.getVaultDiscoveryTracks(
            sinceMillis = sinceMillis,
            limit = limit
        ).map { entities -> entities.map { it.toDomainModel() } }
    }

    fun getArtistCacheFlow(): Flow<Map<String, String?>> =
        tigerDao.getAllArtistCache().map { entities ->
            entities.associate { it.artistName to it.imageUrl }
        }

    // Inside LibraryEngine or AudioRepository
    suspend fun savePlaylistOrder(playlistId: Long, tracks: List<AudioTrack>) {
        // Invariant guard: synthetic playlist IDs (-1 for Liked Songs, -2 for Recently Added)
        // are computed dynamically and cannot have custom user-defined track orders persisted.
        if (playlistId <= 0) {
            Log.e(TAG, "Attempted to reorder synthetic or invalid playlist ID: $playlistId")
            return
        }

        val trackIds = tracks.map { it.id }
        tigerDao.savePlaylistOrder(playlistId, trackIds)
    }

    // --- MAPPING HELPERS ---

    private fun CachedTrackEntity.toDomainModel() = AudioTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        uri = uriString.toUri(),
        artworkUri = artworkUriString.toUri(),
        durationMs = durationMs,
        mimeType = mimeType,
        isLocal = true,
        bitrate = bitrate,
        sampleRate = sampleRate,
        trackNumber = trackNumber,
        path = path,
        year = year,
        dateAdded = dateAdded,
        isLiked = isLiked,
        replayGainTrackDb = replayGainTrackDb,
        replayGainAlbumDb = replayGainAlbumDb,
        replayGainTrackPeak = replayGainTrackPeak,
        replayGainAlbumPeak = replayGainAlbumPeak,
        dateModified = dateModified,
        artworkLookupFailedAt = artworkLookupFailedAt,
        artworkLookupAttempts = artworkLookupAttempts,
        hasEmbeddedArtwork = hasEmbeddedArtwork
    )

    private fun AudioTrack.toEntity() = CachedTrackEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        uriString = uri.toString(),
        artworkUriString = artworkUri.toString(),
        durationMs = durationMs,
        mimeType = mimeType,
        bitrate = bitrate,
        sampleRate = sampleRate,
        trackNumber = trackNumber,
        path = path,
        year = year,
        dateAdded = dateAdded,
        isLiked = isLiked,
        replayGainTrackDb = replayGainTrackDb,
        replayGainAlbumDb = replayGainAlbumDb,
        replayGainTrackPeak = replayGainTrackPeak,
        replayGainAlbumPeak = replayGainAlbumPeak,
        dateModified = dateModified,
        artworkLookupFailedAt = artworkLookupFailedAt,
        artworkLookupAttempts = artworkLookupAttempts,
        hasEmbeddedArtwork = hasEmbeddedArtwork
    )
}
