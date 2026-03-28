package com.example.tigerplayer.data.repository

import android.net.Uri
import androidx.core.net.toUri
import com.example.tigerplayer.data.local.dao.PlaylistDao
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.CachedTrackEntity
import com.example.tigerplayer.data.local.entity.PlaylistEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.data.remote.api.RemoteTrack
import com.example.tigerplayer.data.source.LocalAudioDataSource
import com.example.tigerplayer.utils.NavidromeSecurity
import kotlinx.coroutines.flow.*
import java.util.UUID
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
                    val remoteTracks = remoteResult.getOrDefault(emptyList())
                    // High-Quality: We ensure suffix is handled properly for codec selection
                    remoteCache = remoteTracks.map { it.toAudioTrack(baseUrl, user, pass) }
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
     * Retrieves bit-perfect local files from the internal vault.
     */
    /**
     * LOCAL CACHE LOGIC
     * Emits the zero-latency cache instantly, then silently patrols
     * the device storage for newly forged or banished tracks.
     */
    fun getLocalTracks(forceRefresh: Boolean = false): Flow<List<AudioTrack>> = flow {
        // 1. THE FAST PATH: Instantly load the archive from Room
        val cachedEntities = tigerDao.getCachedTracks()
        val cachedTracks = cachedEntities.map { it.toDomainModel() }

        if (cachedTracks.isNotEmpty() && !forceRefresh) {
            emit(cachedTracks)
        }

        // 2. THE SILENT PATROL: Scan the device in the background
        localAudioDataSource.getLocalAudioFiles().collect { status ->
            if (status is LocalAudioDataSource.ScanStatus.Complete) {
                val freshTracks = status.tracks

                // 3. THE RECONCILIATION: Check if the user downloaded or deleted music
                val isArchiveOutdated = forceRefresh ||
                        cachedTracks.size != freshTracks.size ||
                        cachedTracks != freshTracks

                if (isArchiveOutdated) {
                    // Purge the stale ledger and secure the new loot
                    tigerDao.clearTrackCache()
                    if (freshTracks.isNotEmpty()) {
                        tigerDao.insertCachedTracks(freshTracks.map { it.toEntity() })
                    }
                    // Emit the freshly forged tracks so the UI updates seamlessly
                    emit(freshTracks)
                }
            }
        }
    }

    /**
     * Used specifically when the UI needs to display the ScanningOverlay.
     */
    fun getLocalTracksWithProgress(forceRefresh: Boolean = false): Flow<LocalAudioDataSource.ScanStatus> = flow {
        val cachedEntities = tigerDao.getCachedTracks()
        val cachedTracks = cachedEntities.map { it.toDomainModel() }

        if (cachedTracks.isNotEmpty() && !forceRefresh) {
            emit(LocalAudioDataSource.ScanStatus.Complete(cachedTracks))
        }

        // Always scan to ensure the ledger is accurate, emitting progress to the UI
        localAudioDataSource.getLocalAudioFiles().collect { status ->
            emit(status)

            if (status is LocalAudioDataSource.ScanStatus.Complete) {
                val freshTracks = status.tracks
                val isArchiveOutdated = forceRefresh ||
                        cachedTracks.size != freshTracks.size ||
                        cachedTracks != freshTracks

                if (isArchiveOutdated) {
                    tigerDao.clearTrackCache()
                    if (freshTracks.isNotEmpty()) {
                        tigerDao.insertCachedTracks(freshTracks.map { it.toEntity() })
                    }
                }
            }
        }
    }


    fun getCustomPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { entities ->
            entities.map { entity ->
                Playlist(
                    id = entity.playlistId,
                    name = entity.name,
                    trackCount = 0,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    suspend fun createPlaylist(name: String) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        playlistDao.insertTrackIntoPlaylist(
            PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId)
        )
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<AudioTrack>> {
        return getLocalTracks().combine(playlistDao.getTrackIdsForPlaylist(playlistId)) { allTracks, trackIds ->
            allTracks.filter { trackIds.contains(it.id) }
        }
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
        year = year
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
        year = year
    )
}