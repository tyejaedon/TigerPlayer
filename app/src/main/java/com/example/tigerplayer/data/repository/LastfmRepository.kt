package com.example.tigerplayer.data.repository

import android.util.Log
import com.example.tigerplayer.data.remote.api.LastFmApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 1. Ensure your existing ArtistDetails is imported (if it's in a different package)
// import com.example.tigerplayer.data.repository.ArtistDetails

@Singleton
class LastFmRepository @Inject constructor(
    private val lastFmApi: LastFmApi
) {
    // 2. Change the return type to ArtistDetails?
    suspend fun fetchArtistProfile(artistName: String): ArtistDetails? = withContext(Dispatchers.IO) {
        try {
            val response = lastFmApi.getArtistInfo(artistName = artistName)
            if (response.isSuccessful) {
                val artistData = response.body()?.artist

                if (artistData != null) {
                    val rawBio = artistData.bio?.summary ?: "The archives hold no lore for this artist."
                    val cleanBio = rawBio.substringBefore("<a href").trim()
                    val genres = artistData.tags?.tag?.map { it.name } ?: emptyList()
                    val listeners = artistData.stats?.listeners?.toIntOrNull() ?: 0

                    Log.d("LastFm", "Successfully forged lore for $artistName")

                    // 3. Return your actual ArtistDetails object
                    return@withContext ArtistDetails(
                        name = artistData.name,
                        bio = cleanBio,
                        genres = genres,
                        popularity = listeners,
                        imageUrl = null
                    )
                }
            } else {
                Log.e("LastFm", "API rejected the request: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("LastFm", "Ritual failed: ${e.message}")
        }
        return@withContext null
    }
}