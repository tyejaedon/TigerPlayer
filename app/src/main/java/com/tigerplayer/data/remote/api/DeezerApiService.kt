package com.tigerplayer.data.remote.api

import com.tigerplayer.data.remote.model.DeezerAlbumDto
import com.tigerplayer.data.remote.model.DeezerArtistDto
import com.tigerplayer.data.remote.model.DeezerChartResponse
import com.tigerplayer.data.remote.model.DeezerSearchResponse
import com.tigerplayer.data.remote.model.DeezerTrackDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the public, unauthenticated Deezer Simple API (`https://api.deezer.com/`).
 */
interface DeezerApiService {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("index") index: Int = 0
    ): Response<DeezerSearchResponse<DeezerTrackDto>>

    @GET("search/track")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("index") index: Int = 0
    ): Response<DeezerSearchResponse<DeezerTrackDto>>

    @GET("search/artist")
    suspend fun searchArtists(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("index") index: Int = 0
    ): Response<DeezerSearchResponse<DeezerArtistDto>>

    @GET("search/album")
    suspend fun searchAlbums(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("index") index: Int = 0
    ): Response<DeezerSearchResponse<DeezerAlbumDto>>

    @GET("track/{id}")
    suspend fun getTrack(
        @Path("id") id: Long
    ): Response<DeezerTrackDto>

    @GET("album/{id}")
    suspend fun getAlbum(
        @Path("id") id: Long
    ): Response<DeezerAlbumDto>

    @GET("artist/{id}")
    suspend fun getArtist(
        @Path("id") id: Long
    ): Response<DeezerArtistDto>

    @GET("artist/{id}/top")
    suspend fun getArtistTopTracks(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 20
    ): Response<DeezerSearchResponse<DeezerTrackDto>>

    @GET("chart/0/tracks")
    suspend fun getChartTracks(
        @Query("limit") limit: Int = 25
    ): Response<DeezerSearchResponse<DeezerTrackDto>>

    @GET("chart/0/artists")
    suspend fun getChartArtists(
        @Query("limit") limit: Int = 15
    ): Response<DeezerSearchResponse<DeezerArtistDto>>

    @GET("chart")
    suspend fun getChart(): Response<DeezerChartResponse>
}
