package com.example.tigerplayer.data.remote.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
interface WikipediaApi {
    @GET("w/api.php")
    suspend fun getArtistBio(
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("prop") prop: String = "extracts",
        @Query("exintro") exintro: Int = 1,      // 1 = true
        @Query("explaintext") explaintext: Int = 1, // 1 = true (Plain text, no HTML)
        @Query("redirects") redirects: Int = 1,    // Follow redirects!
        @Query("titles") artistName: String
    ): Response<JsonObject>
}