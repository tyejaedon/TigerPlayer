package com.example.tigerplayer.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {
    @GET("rest/getMusicFolders")
    suspend fun getMusicFolders(
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "TigerPlayer",
        @Query("f") format: String = "json"
    ): Any // Placeholder for actual response model

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "TigerPlayer",
        @Query("f") format: String = "json"
    ): Any // Placeholder for actual response model
}
