package com.example.tigerplayer.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface NavidromeApiService {

    // 1. The Ping: Ritual verification
    @GET("rest/ping.view")
    suspend fun ping(
        @QueryMap authParams: Map<String, String>,
        @Query("f") format: String = "json"
    ): Response<SubsonicResponseWrapper>

    // 2. The Great Archive Fetch: Grabs up to 500 songs for the Unified Library
    @GET("rest/getRandomSongs.view")
    suspend fun getRandomSongs(
        @QueryMap authParams: Map<String, String>,
        @Query("size") size: Int = 500,
        @Query("f") format: String = "json"
    ): Response<SubsonicResponseWrapper>

    // 3. Fetch all remote playlists
    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(
        @QueryMap authParams: Map<String, String>,
        @Query("f") format: String = "json"
    ): Response<SubsonicResponseWrapper>

    // 4. Fetch tracks inside a remote playlist
    @GET("rest/getPlaylist.view")
    suspend fun getPlaylistTracks(
        @QueryMap authParams: Map<String, String>,
        @Query("id") playlistId: String,
        @Query("f") format: String = "json"
    ): Response<SubsonicResponseWrapper>
}

// --- SUBSONIC JSON DATA MODELS ---

data class SubsonicResponseWrapper(
    @SerializedName("subsonic-response")
    val subsonicResponse: SubsonicResponseData
)

data class SubsonicResponseData(
    val status: String,
    val version: String,
    val playlists: SubsonicPlaylistsNode?,
    val playlist: SubsonicSinglePlaylistNode?,
    val randomSongs: SubsonicRandomSongsNode?, // <--- NEW: Added for bulk fetch
    val error: SubsonicError?
)

// NEW: Data node for getRandomSongs
data class SubsonicRandomSongsNode(
    val song: List<RemoteTrack>
)

data class SubsonicPlaylistsNode(
    val playlist: List<RemotePlaylist>
)

data class SubsonicSinglePlaylistNode(
    val entry: List<RemoteTrack>
)

data class RemotePlaylist(
    val id: String,
    val name: String,
    val songCount: Int,
    val duration: Int,
    val created: String
)

data class RemoteTrack(
    val id: String,
    val title: String,
    val album: String,
    val artist: String,
    val track: Int,
    val duration: Int,
    val suffix: String,
    val bitRate: Int
)

data class SubsonicError(
    val code: Int,
    val message: String
)