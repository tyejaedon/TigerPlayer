package com.example.tigerplayer.data.remote.api

import android.util.Log
import com.example.tigerplayer.BuildConfig
import com.example.tigerplayer.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Clean domain model for the UI and Player to consume.
 */
data class YouTubeTrack(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String
)

interface YouTubeRepository {
    suspend fun searchMusic(query: String, limit: Int = 20): Result<List<YouTubeTrack>>
}

@Singleton
class YouTubeRepositoryImpl @Inject constructor(
    private val api: OfficialYouTubeDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : YouTubeRepository {

    // Simple in-memory cache to respect the 10,000-unit daily quota
    private val cache = mutableMapOf<String, List<YouTubeTrack>>()

    override suspend fun searchMusic(query: String, limit: Int): Result<List<YouTubeTrack>> =
        withContext(ioDispatcher) {
            try {
                if (cache.containsKey(query)) {
                    Log.d("YouTubeRepo", "Returning cached results for: $query")
                    return@withContext Result.success(cache[query]!!)
                }

                val response = api.searchVideos(
                    query = query,
                    maxResults = limit,
                    apiKey = BuildConfig.YOUTUBE_API_KEY
                )

                val tracks = response.items.map { item ->
                    YouTubeTrack(
                        videoId = item.id.videoId,
                        title = item.snippet.title,
                        author = item.snippet.channelTitle,
                        thumbnailUrl = item.snippet.thumbnails.high.url
                    )
                }

                cache[query] = tracks
                Result.success(tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("YouTubeRepo", "Official YouTube API search failed for query: $query", e)
                Result.failure(e)
            }
        }
}
