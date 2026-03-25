package com.example.tigerplayer.data.remote.api

import com.example.tigerplayer.BuildConfig
import com.example.tigerplayer.data.remote.model.LastFmResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LastFmApi {

    // THE FIX: Bake the static parameters directly into the endpoint path.
    // This keeps the function signature clean and prevents accidental overrides.
    @GET("2.0/?method=artist.getinfo&format=json&autocorrect=1")
    suspend fun getArtistInfo(
        @Query("artist") artistName: String,
        @Query("api_key") apiKey: String = BuildConfig.LASTFM_API_KEY
    ): Response<LastFmResponse>
}