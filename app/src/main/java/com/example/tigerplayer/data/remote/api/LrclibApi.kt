package com.example.tigerplayer.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// The data model for LRCLIB's response
data class LrclibResponse(
    @SerializedName("plainLyrics") val plainLyrics: String?,
    @SerializedName("syncedLyrics") val syncedLyrics: String?
)

interface LrclibApi {
    /**
     * LRCLIB is completely free and requires no API keys!
     */
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String
    ): Response<LrclibResponse>
}