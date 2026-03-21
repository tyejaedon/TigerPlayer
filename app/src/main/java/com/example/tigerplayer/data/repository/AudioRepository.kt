package com.example.tigerplayer.data.repository

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepository @Inject constructor(
    private val localAudioDataSource: LocalAudioDataSource,
    private val playlistDao: PlaylistDao,
    private val tigerDao: TigerDao, // <-- Injected the DAO for caching
    private val navidromeRepository: NavidromeRepository
) {

    /**
     * The Master Archive: Combines local FLACs and Navidrome streams.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
    fun getUnifiedTracks(
        user: String?,
        pass: String?,
        baseUrl: String?,
        forceRefresh: Boolean = false // <-- Added so UI can trigger a scan
    ): Flow<List<AudioTrack>> = combine(
        getLocalTracks(forceRefresh),
        flow {
            if (user != null && pass != null && baseUrl != null) {
                // If you add a local database cache for Navidrome later, it would go here too
                val remoteResult = navidromeRepository.getAllRemoteTracks(user, pass)
                val remoteTracks = remoteResult.getOrDefault(emptyList())
                emit(remoteTracks.map { it.toAudioTrack(baseUrl, user, pass) })
            } else {
                emit(emptyList<AudioTrack>())
            }
        }
    ) { local, remote ->
        (local + remote).sortedBy { it.title.lowercase() }
    }

    /**
     * Extension to transform Navidrome's RemoteTrack into our Unified AudioTrack
     */
    private fun RemoteTrack.toAudioTrack(baseUrl: String, u: String, p: String): AudioTrack {
        val salt = UUID.randomUUID().toString().substring(0, 8)
        val auth = NavidromeSecurity.generateAuthPayload(u, p)

        val authQuery = "u=$u&t=${auth.t}&s=$salt&v=1.16.1&c=TigerPlayer"

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
            serverPath = id
        )
    }

    /**
     * Handles the local track caching logic.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
    fun getLocalTracks(forceRefresh: Boolean = false): Flow<List<AudioTrack>> = flow {
        if (forceRefresh) {
            tigerDao.clearTrackCache()
        }

        // 1. Check the local Room cache first
        val cachedEntities = tigerDao.getCachedTracks()

        if (cachedEntities.isNotEmpty()) {
            // Map Entity back to the Domain Model
            val cachedTracks = cachedEntities.map { entity ->
                AudioTrack(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    album = entity.album,
                    uri = entity.uriString.toUri(),
                    artworkUri = entity.artworkUriString.toUri(),
                    durationMs = entity.durationMs,
                    mimeType = entity.mimeType,
                    isLocal = true,
                    bitrate = entity.bitrate,
                    sampleRate = entity.sampleRate,
                    trackNumber = entity.trackNumber
                )
            }
            emit(cachedTracks)
        } else {
            // 2. If cache is empty, do the heavy MediaStore scan
            val scannedTracks = localAudioDataSource.getLocalAudioFiles()

            // 3. Save the newly scanned tracks into Room for next time
            val entitiesToCache = scannedTracks.map { track ->
                CachedTrackEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    uriString = track.uri.toString(),
                    artworkUriString = track.artworkUri.toString(),
                    durationMs = track.durationMs,
                    mimeType = track.mimeType,
                    bitrate = track.bitrate,
                    sampleRate = track.sampleRate,
                    trackNumber = track.trackNumber
                )
            }
            if (entitiesToCache.isNotEmpty()) {
                tigerDao.insertCachedTracks(entitiesToCache)
            }

            // 4. Emit the scanned tracks
            emit(scannedTracks)
        }
    }

    // --- PLAYLIST LOGIC ---

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

    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<AudioTrack>> {
        return getLocalTracks().combine(playlistDao.getTrackIdsForPlaylist(playlistId)) { allTracks, trackIds ->
            allTracks.filter { trackIds.contains(it.id) }
        }
    }
}