package com.example.tigerplayer.data.remote.api

import com.example.tigerplayer.data.remote.model.SpotifyAlbumSearchResponse
import com.example.tigerplayer.data.remote.model.SpotifyAlbumTrackResponse
import com.example.tigerplayer.data.remote.model.SpotifyArtistSearchResponse
import com.example.tigerplayer.data.remote.model.SpotifyPlaylistResponse
import com.example.tigerplayer.data.remote.model.SpotifyPlaylistTrackResponse
import com.example.tigerplayer.data.remote.model.SpotifySavedAlbumResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyApiService {

    /**
     * Fetches the current user's saved playlists.
     * Added 'limit' to ensure we get a good chunk of data at once.
     */
    @GET("v1/me/playlists")
    suspend fun getUserPlaylists(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyPlaylistResponse>

    @GET("playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") token: String,
        @Path("playlist_id") playlistId: String
    ): Response<SpotifyPlaylistTrackResponse>


    /**
     * Fetches the tracks for a specific playlist.
     * Spotify limits this to 100 tracks by default.
     */
    @GET("v1/playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") bearerToken: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100
    ): Response<SpotifyPlaylistTrackResponse>

    /**
     * The 'Liked Songs' Ritual - Every music player needs this.
     */
    @GET("v1/me/tracks")
    suspend fun getLikedSongs(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyPlaylistTrackResponse>

    @GET("v1/search")
    suspend fun searchArtist(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("type") type: String = "artist",
        @Query("limit") limit: Int = 1
    ): Response<SpotifyArtistSearchResponse>

@GET("v1/me/albums")
suspend fun getUserSavedAlbums(
    @Header("Authorization") bearerToken: String,
    @Query("limit") limit: Int = 50
): Response<SpotifySavedAlbumResponse>


    @GET("v1/search")
    suspend fun searchAlbum(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("type") type: String = "album",
        @Query("limit") limit: Int = 1
    ): Response<SpotifyAlbumSearchResponse>


    /**
     * Fetches the tracks belonging to a specific album.
     */
    @GET("v1/albums/{id}/tracks")
    suspend fun getAlbumTracks(
        @Header("Authorization") bearerToken: String,
        @Path("id") albumId: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyAlbumTrackResponse>
}


