package com.example.tigerplayer.data.remote.api

import com.example.tigerplayer.BuildConfig
import com.example.tigerplayer.data.remote.model.LastFmResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LastFmApi {
    @GET("2.0/")
    suspend fun getArtistInfo(
        @Query("method") method: String = "artist.getinfo",
        @Query("artist") artistName: String,
        @Query("api_key") apiKey: String = BuildConfig.LASTFM_API_KEY,
        @Query("format") format: String = "json",
        @Query("autocorrect") autocorrect: Int = 1
    ): Response<LastFmResponse>
}