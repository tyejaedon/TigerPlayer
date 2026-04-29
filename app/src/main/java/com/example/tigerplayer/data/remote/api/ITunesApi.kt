package com.example.tigerplayer.data.remote.api

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

}