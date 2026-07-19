package com.example.tigerplayer.data.repository

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.example.tigerplayer.data.local.dao.PlaylistDao
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.dao.TrackStats
import com.example.tigerplayer.data.local.entity.CachedTrackEntity
import com.example.tigerplayer.data.local.entity.PlaylistEntity
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.data.remote.api.RemoteTrack
import com.example.tigerplayer.data.source.LocalAudioDataSource
import com.example.tigerplayer.utils.NavidromeSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepository @Inject constructor(
    private val localAudioDataSource: LocalAudioDataSource,
    private val playlistDao: PlaylistDao,
    private val tigerDao: TigerDao,
    private val navidromeRepository: NavidromeRepository
) {

    private var remoteCache: List<AudioTrack> = emptyList()
    private val hasPrimedLocalScan = AtomicBoolean(false)
    private val localScanMutex = Mutex()

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
    ): Flow<List<AudioTrack>> = combine(
        getLocalTracks(forceRefresh),
        flow {
            if (!user.isNullOrBlank() && !pass.isNullOrBlank() && !baseUrl.isNullOrBlank()) {
                if (remoteCache.isEmpty() || forceRefresh) {
                    val remoteResult = navidromeRepository.getAllRemoteTracks(user, pass)

                    remoteResult.onSuccess { remoteTracks ->
                        // 🔥 PERFORMANCE FIX: Generate salt/token ONCE per sync, not per track
                        val salt = UUID.randomUUID().toString().substring(0, 8)
                        val token = NavidromeSecurity.generateToken(pass, salt)
                        val authQuery = "u=$user&t=$token&s=$salt&v=1.16.1&c=TigerPlayer"

                        remoteCache = remoteTracks.map { it.toAudioTrack(baseUrl, authQuery,pass) }
                    }.onFailure { error ->
                        Log.e("AudioRepository", "Archive sync failed: ${error.message}")
                    }
                }
                emit(remoteCache)
            } else {
                emit(emptyList())
            }
        }
    ) { local, remote ->
        (local + remote).sortedBy { it.title.lowercase() }
    }

    /**
     * THE NAVIDROME RITUAL
     * Optimized for high-quality streaming.
     */
    private fun RemoteTrack.toAudioTrack(baseUrl: String, u: String, p: String): AudioTrack {
        val salt = UUID.randomUUID().toString().substring(0, 8)
        val token = NavidromeSecurity.generateToken(p, salt)
        val authQuery = "u=$u&t=$token&s=$salt&v=1.16.1&c=TigerPlayer"

        // Bit-Perfect Consideration: Navidrome stream.view returns the original file unless transcoding is forced.
        // We omit 'maxBitRate' and 'format' parameters to ensure we get the source file (FLAC/ALAC/High-VBR MP3).
        return AudioTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration.toLong() * 1000,
            artworkUri = Uri.parse("${baseUrl}rest/getCoverArt.view?id=$id&$authQuery"),
            uri = Uri.parse("${baseUrl}rest/stream.view?id=$id&$authQuery"),
            trackNumber = track,
            mimeType = "audio/$suffix",
            bitrate = bitRate * 1000,
            isRemote = true,
            serverPath = id,
            year = year?.toString()
        )
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
        val cachedEntities = tigerDao.getCachedTracksSync()
        val cachedTracks = cachedEntities.map { it.toDomainModel() }

        if (cachedTracks.isNotEmpty() && !forceRefresh) {
            emit(LocalAudioDataSource.ScanStatus.Complete(cachedTracks))
        }

        // Always scan to ensure the ledger is accurate, emitting progress to the UI
        localAudioDataSource.getLocalAudioFiles().collect { status ->
            emit(status)

            if (status is LocalAudioDataSource.ScanStatus.Complete) {
                val freshTracks = status.tracks
                hasPrimedLocalScan.set(true)
                val isArchiveOutdated = forceRefresh ||
                        cachedTracks.size != freshTracks.size ||
                        cachedTracks != freshTracks

                if (isArchiveOutdated) {
                    tigerDao.insertCachedTracksTransaction(freshTracks.map { it.toEntity() })
                }
            }
        }
    }

    private suspend fun refreshLocalCache(forceRefresh: Boolean) {
        localScanMutex.withLock {
            var freshTracks: List<AudioTrack>? = null
            localAudioDataSource.getLocalAudioFiles().collect { status ->
                if (status is LocalAudioDataSource.ScanStatus.Complete) {
                    freshTracks = status.tracks
                }
            }

            val scannedTracks = freshTracks ?: return
            val cachedTracks = tigerDao.getCachedTracksSync().map { it.toDomainModel() }
            val isArchiveOutdated = forceRefresh ||
                cachedTracks.size != scannedTracks.size ||
                cachedTracks != scannedTracks

            if (isArchiveOutdated) {
                tigerDao.insertCachedTracksTransaction(scannedTracks.map { it.toEntity() })
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

    fun getHeavyRotation(since: Long): Flow<List<TrackStats>> {
        return tigerDao.getHeavyRotation(since)
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
        isLiked = isLiked
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
        isLiked = isLiked
    )
}