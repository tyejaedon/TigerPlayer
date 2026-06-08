package com.example.tigerplayer.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Query

interface OfficialYouTubeDataSource {
    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}

data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem>
)

data class YouTubeSearchItem(
    val id: YouTubeId,
    val snippet: YouTubeSnippet
)

data class YouTubeId(
    val videoId: String
)

data class YouTubeSnippet(
    val title: String,
    val channelTitle: String,
    val thumbnails: YouTubeThumbnails
)

data class YouTubeThumbnails(
    val default: YouTubeThumbnail,
    val medium: YouTubeThumbnail,
    val high: YouTubeThumbnail
)

data class YouTubeThumbnail(
    val url: String
)
