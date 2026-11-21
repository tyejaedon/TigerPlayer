package com.tigerplayer.data.remote.api.deezer

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DeezerApiService {
    @GET("search")
    suspend fun search(@Query("q") query: String): DeezerSearchResponse<DeezerTrackDto>

    @GET("search/track")
    suspend fun searchTrack(@Query("q") query: String): DeezerSearchResponse<DeezerTrackDto>

    @GET("search/artist")
    suspend fun searchArtist(@Query("q") query: String): DeezerSearchResponse<DeezerArtistDto>

    @GET("search/album")
    suspend fun searchAlbum(@Query("q") query: String): DeezerSearchResponse<DeezerAlbumDto>

    @GET("track/{id}")
    suspend fun getTrack(@Path("id") id: Long): DeezerTrackDto

    @GET("album/{id}")
    suspend fun getAlbum(@Path("id") id: Long): DeezerAlbumDto

    @GET("artist/{id}")
    suspend fun getArtist(@Path("id") id: Long): DeezerArtistDto

    @GET("artist/{id}/top")
    suspend fun getArtistTopTracks(@Path("id") id: Long, @Query("limit") limit: Int = 10): DeezerSearchResponse<DeezerTrackDto>

    @GET("chart")
    suspend fun getChart(): DeezerChartResponse

    @GET("chart/0/tracks")
    suspend fun getChartTracks(): DeezerSearchResponse<DeezerTrackDto>
}
