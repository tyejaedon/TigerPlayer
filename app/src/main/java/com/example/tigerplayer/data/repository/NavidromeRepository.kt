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
    // Cache the payload in memory so we don't recalculate MD5s constantly
    private var cachedAuthMap: Map<String, String>? = null
    private var currentUsername: String? = null

    private fun getAuthMap(username: String, pass: String): Map<String, String> {
        // Return cache if it exists for this user
        if (cachedAuthMap != null && currentUsername == username) {
            return cachedAuthMap!!
        }

        val payload = NavidromeSecurity.generateAuthPayload(username, pass)
        cachedAuthMap = mapOf(
            "u" to payload.u,
            "t" to payload.t,
            "s" to payload.s,
            "v" to payload.v,
            "c" to payload.c,
            "f" to "json"
        )
        currentUsername = username
        return cachedAuthMap!!
    }

    /**
     * 1. The Ping: Connection Ritual
     * Verifies if the server at the given URL is alive and accepts our credentials.
     */
    suspend fun pingServer(username: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.ping(getAuthMap(username, pass))
            val subResponse = response.body()?.subsonicResponse

            if (response.isSuccessful && subResponse?.status == "ok") {
                true
            } else {
                val error = subResponse?.error?.message ?: "Server rejected the ritual"
                throw Exception(error)
            }
        }
    }

    /**
     * 2. The Great Archive Fetch: Summons songs for the Unified Library.
     * Using 'getRandomSongs' is the fastest way to populate the library
     * with a large chunk of your remote archives.
     */
    suspend fun getAllRemoteTracks(username: String, pass: String): Result<List<RemoteTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            // Fetching up to 500 songs for a robust "Discover" experience
            val response = apiService.getRandomSongs(getAuthMap(username, pass), size = 500)
            val data = response.body()?.subsonicResponse

            if (response.isSuccessful && data?.status == "ok") {
                data.randomSongs?.song ?: emptyList()
            } else {
                Log.e("NavidromeRepo", "Failed to reach archives: ${response.code()}")
                throw Exception("Could not reach the remote archives")
            }
        }
    }

    /**
     * 3. Fetch Remote Playlists
     * Summons all playlists created by the user on the Navidrome server.
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
                // Navidrome wraps playlist tracks in an 'entry' list
                data.playlist?.entry ?: emptyList()
            } else {
                throw Exception("Failed to decrypt remote playlist entries")
            }
        }
    }
}