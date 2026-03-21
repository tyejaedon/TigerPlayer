package com.example.tigerplayer.data.repository

import android.util.Log
import com.example.tigerplayer.data.remote.api.NavidromeApiService
import com.example.tigerplayer.data.remote.api.RemotePlaylist
import com.example.tigerplayer.data.remote.api.RemoteTrack
import com.example.tigerplayer.utils.NavidromeSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavidromeRepository @Inject constructor(
    private val apiService: NavidromeApiService
) {

    private fun getAuthMap(username: String, pass: String): Map<String, String> {
        val payload = NavidromeSecurity.generateAuthPayload(username, pass)
        return mapOf(
            "u" to payload.u,
            "t" to payload.t,
            "s" to payload.s,
            "v" to payload.v,
            "c" to payload.c
        )
    }

    /**
     * 1. The Ping: Connection Ritual
     */
    suspend fun pingServer(username: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.ping(getAuthMap(username, pass))
            val subResponse = response.body()?.subsonicResponse

            if (response.isSuccessful && subResponse?.status == "ok") {
                true
            } else {
                throw Exception(subResponse?.error?.message ?: "Server rejected the ritual")
            }
        }
    }

    /**
     * 2. The Great Archive Fetch: Gets ALL songs for the Unified Library.
     * Uses 'search3' or 'getAlbumList2' logic, but for a simple
     * unified feel, we'll use a random or newest fetch of up to 500 songs.
     */
    suspend fun getAllRemoteTracks(username: String, pass: String): Result<List<RemoteTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            // We use getRandomSongs to populate the "Unified Library" quickly
            val response = apiService.getRandomSongs(getAuthMap(username, pass), size = 500)
            val data = response.body()?.subsonicResponse

            if (response.isSuccessful && data?.status == "ok") {
                data.randomSongs?.song ?: emptyList()
            } else {
                throw Exception("Could not reach the remote archives")
            }
        }
    }

    /**
     * 3. Fetch Remote Playlists
     */
    suspend fun getRemotePlaylists(username: String, pass: String): Result<List<RemotePlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getPlaylists(getAuthMap(username, pass))
            val data = response.body()?.subsonicResponse

            if (response.isSuccessful && data?.status == "ok") {
                data.playlists?.playlist ?: emptyList()
            } else {
                throw Exception("Remote collections are currently unreachable")
            }
        }
    }

    /**
     * 4. Fetch Tracks for a specific Playlist
     */
    suspend fun getRemotePlaylistTracks(playlistId: String, username: String, pass: String): Result<List<RemoteTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getPlaylistTracks(getAuthMap(username, pass), playlistId)
            val data = response.body()?.subsonicResponse

            if (response.isSuccessful && data?.status == "ok") {
                data.playlist?.entry ?: emptyList()
            } else {
                throw Exception("Failed to decrypt remote playlist entries")
            }
        }
    }
}