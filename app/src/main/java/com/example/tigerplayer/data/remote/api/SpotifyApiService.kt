package com.example.tigerplayer.data.remote.api

import com.example.tigerplayer.data.remote.model.SpotifyAlbumSearchResponse
import com.example.tigerplayer.data.remote.model.SpotifyAlbumTrackResponse
import com.example.tigerplayer.data.remote.model.SpotifyArtistSearchResponse
import com.example.tigerplayer.data.remote.model.SpotifyPlaylistResponse
import com.example.tigerplayer.data.remote.model.SpotifyPlaylistTrackResponse
import com.example.tigerplayer.data.remote.model.SpotifySavedAlbumResponse
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

interface SpotifyApiService {

    @GET("playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") token: String,
        @Path("playlist_id") playlistId: String
    ): Response<SpotifyPlaylistTrackResponse>



    @GET("v1/playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") bearerToken: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100
    ): Response<SpotifyPlaylistTrackResponse>

    @GET("v1/me/playlists")
    suspend fun getUserPlaylists(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyPlaylistResponse>

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

    @GET("v1/albums/{id}/tracks")
    suspend fun getAlbumTracks(
        @Header("Authorization") bearerToken: String,
        @Path("id") albumId: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyAlbumTrackResponse>
}

// --- AUTH API ---

interface SpotifyAuthApi {
    @POST("api/token")
    @FormUrlEncoded
    suspend fun getServiceToken(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): Response<SpotifyTokenResponse>

    @FormUrlEncoded
    @POST("api/token")
    suspend fun getUserToken(
        @Header("Authorization") authHeader: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String?,
        @Field("redirect_uri") redirectUri: String
    ): Response<SpotifyTokenResponse> //
}


data class SpotifyTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: Int
)