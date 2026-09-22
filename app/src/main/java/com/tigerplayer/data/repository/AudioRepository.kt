package com.tigerplayer.data.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.dao.TrackStats
import com.tigerplayer.data.local.entity.CachedTrackEntity
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.data.source.LocalAudioDataSource
import com.tigerplayer.data.source.SafFolderScanner
import com.tigerplayer.utils.NavidromeMapper.toAudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val safFolderScanner: SafFolderScanner
) {

    companion object {
        private const val TAG = "AudioRepository"

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
     * THE MASTER ARCHIVE
     * Combines local high-fidelity FLACs/MP3s and Navidrome remote streams.
     * Ensures local files are prioritized for bit-perfect output.
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
     * LOCAL CACHE LOGIC
     * retrieves bit-perfect local files from the internal vault.
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


    // ==========================================
    // --- GRIMOIRE (PLAYLIST) OPERATIONS ---
    // ==========================================

    fun getCustomPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getPlaylistsWithCount()
            .onEach { list ->
                Log.d("AudioRepository", "Archive Emission: Found ${list.size} playlists")
                list.forEach { Log.d("AudioRepository", "Playlist: ${it.name} ID: ${it.id}") }
            }
    }
    suspend fun createPlaylist(name: String, id: Long? = null) {
        playlistDao.insertPlaylist(
            PlaylistEntity(
                playlistId = id ?: 0L,
                name = name,
                artworkUri = null,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateTrackLikeStatus(trackId: String, isLiked: Boolean) {
        tigerDao.updateTrackLikeStatus(trackId, isLiked)
    }

    // 🔥 NEW: Persist the HD Artwork
    suspend fun updateTrackArtworkUri(trackId: String, newUri: String) {
        tigerDao.updateTrackArtworkUri(trackId, newUri)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        // AudioRepository.kt line 194
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
        // AudioRepository.kt line 211
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
        // 🛡️ STOP THE GHOST: Don't allow operations on ID -1 or 0
        if (playlistId <= 0) {
            Log.e("LibraryEngine", "Abort! Attempted to reorder invalid playlist ID: $playlistId")
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
        dateModified = dateModified
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
        dateModified = dateModified
    )
}
