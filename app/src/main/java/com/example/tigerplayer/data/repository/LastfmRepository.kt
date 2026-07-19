package com.example.tigerplayer.data.repository

import android.util.Log
import com.example.tigerplayer.data.remote.api.LastFmApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LastFmRepository @Inject constructor(
    private val lastFmApi: LastFmApi
) {
    suspend fun getSimilarArtistNames(artistName: String, limit: Int = 8): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = lastFmApi.getSimilarArtists(artistName = artistName, limit = limit)
            if (!response.isSuccessful) return@withContext emptyList()

            response.body()
                ?.similarArtists
                ?.artists
                .orEmpty()
                .mapNotNull { it.name?.trim() }
                .filter { it.isNotEmpty() && !it.equals(artistName, ignoreCase = true) }
                .distinct()
                .take(limit)
        } catch (e: Exception) {
            Log.e("LastFm", "Similar artist fetch failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchArtistProfile(artistName: String): ArtistDetails? = withContext(Dispatchers.IO) {
        try {
            val response = lastFmApi.getArtistInfo(artistName = artistName)
            if (response.isSuccessful) {
                val artistData = response.body()?.artist

                if (artistData != null) {
                    val rawBio = artistData.bio?.summary ?: "The archives hold no lore for this artist."
                    val cleanBio = rawBio.substringBefore("<a href").trim()

                    // THE FIX: Purifying the genre list
                    val genres = artistData.tags?.tag?.mapNotNull { it.name } ?: emptyList()

                    val listeners = artistData.stats?.listeners?.toIntOrNull() ?: 0

                    // Extracting the high-res image
                    val bestImageUrl =
                        artistData.image?.lastOrNull { !it.url.isNullOrBlank() } // Take the highest resolution available (usually 'mega' or 'extralarge')
                        ?.url

                    Log.d("LastFm", "Successfully forged lore and image for $artistName")

                    return@withContext ArtistDetails(
                        name = artistData.name,
                        bio = cleanBio,
                        genres = genres, // Now perfectly matches List<String>
                        popularity = listeners,
                        imageUrl = bestImageUrl
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