package com.example.tigerplayer.data.remote.api

import com.example.tigerplayer.data.remote.model.*
import retrofit2.Response
import retrofit2.http.*

interface SpotifyApiService {

    @GET("v1/search")
    suspend fun searchTrack(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 5
    ): Response<SpotifySearchResponse>

    @GET("v1/search")
    suspend fun searchArtist(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("type") type: String = "artist",
        @Query("limit") limit: Int = 1
    ): Response<SpotifySearchResponse>

    @GET("v1/search")
    suspend fun searchAlbum(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("type") type: String = "album",
        @Query("limit") limit: Int = 1
    ): Response<SpotifySearchResponse>

    @GET("v1/me/playlists")
    suspend fun getUserPlaylists(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyPlaylistResponse>

    @GET("v1/me/albums")
    suspend fun getUserSavedAlbums(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifySavedAlbumResponse>

    @GET("v1/playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") bearerToken: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100
    ): Response<SpotifyPaging<PlaylistTrackItem>>

    @GET("v1/albums/{id}/tracks")
    suspend fun getAlbumTracks(
        @Header("Authorization") bearerToken: String,
        @Path("id") albumId: String,
        @Query("limit") limit: Int = 50
    ): Response<SpotifyPaging<SpotifyTrack>>
}

interface SpotifyAuthApi {
    @POST("api/token")
    @FormUrlEncoded
    suspend fun getServiceToken(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): Response<SpotifyTokenResponse>

    @POST("api/token")
    @FormUrlEncoded
    suspend fun getUserToken(
        @Header("Authorization") authHeader: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String?,
        @Field("redirect_uri") redirectUri: String
    ): Response<SpotifyTokenResponse>
}