package com.example.tigerplayer.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Query

// Wrapper for the iTunes JSON response
data class ITunesSearchResponse(
    val results: List<ITunesResult>
)

// Maps the fields iTunes sends back
data class ITunesResult(
    val artistName: String?,
    val collectionName: String?, // iTunes terminology for "Album"
    val artworkUrl100: String?,  // We upgrade this string to 1000x1000 in the Repository
    val artistLinkUrl: String?
)

interface ITunesApi {

    // 1. Search for the Artist (To get their profile picture)
    @GET("search")
    suspend fun searchArtist(
        @Query("term") artistName: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 1
    ): ITunesSearchResponse

    // 2. NEW: Search for the Album (To get High-Res Album Art)
    @GET("search")
    suspend fun searchAlbum(
        @Query("term") albumNameAndArtist: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 1
    ): ITunesSearchResponse
}