package com.example.tigerplayer.data.repository

import android.util.Log
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.api.WikipediaApi
import com.example.tigerplayer.data.remote.model.SpotifyArtistDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

data class ArtistDetails(
    val name: String?,
    val imageUrl: String?,
    val bio: String?,
    val genres: List<String> = emptyList(),
    val popularity: Int = 0
)

@Singleton
class MediaDataRepository @Inject constructor(
    private val tigerDao: TigerDao,
    private val spotifyApiService: SpotifyApiService,
    private val authManager: SpotifyAuthManager
) {

    /**
     * PRIORITY: Cache. Fallback: Spotify API.
     * This ritual ensures the UI gets the full ArtistDetails object every time.
     */
    fun getArtistDetails(artistName: String): Flow<ArtistDetails> = flow {
        // 1. CHECK THE ARCHIVES (Database)
        val cachedData = tigerDao.getArtistCache(artistName)

        if (cachedData != null && !cachedData.imageUrl.isNullOrBlank()) {
            Log.d("MediaRepo", "Cache Hit for $artistName. Restoring metadata.")
            emit(ArtistDetails(
                name = artistName,
                imageUrl = cachedData.imageUrl,
                bio = cachedData.bio,
                // If your CacheEntity doesn't store genres/pop yet, we default them here
                genres = emptyList(),
                popularity = 0
            ))
            return@flow
        }

        // 2. CACHE MISS: CONSULT THE SPOTIFY ORACLE
        Log.d("MediaRepo", "Cache Miss for $artistName. Hunting Spotify API...")
        try {
            val token = authManager.getToken()
            val response = spotifyApiService.searchArtist("Bearer $token", artistName)

            if (response.isSuccessful) {
                val detail = response.body()?.artists?.items?.firstOrNull()

                if (detail != null) {
                    // Extract the essence
                    val imageUrl = if ((detail.images?.size ?: 0) > 1) detail.images?.get(1)?.url else detail.images?.firstOrNull()?.url
                    val generatedBio = buildSyntheticBio(detail)

                    val freshDetails = ArtistDetails(
                        name = detail.name,
                        imageUrl = imageUrl,
                        bio = generatedBio,
                        genres = detail.genres ?: emptyList(),
                        popularity = detail.popularity ?: 0
                    )

                    // 3. STORE THE LOOT
                    // NOTE: Update your ArtistCacheEntity to include bio if it's missing!
                    tigerDao.insertArtistCache(ArtistCacheEntity(artistName, imageUrl, generatedBio))

                    // 4. EMIT FRESH DATA
                    emit(freshDetails)
                } else {
                    emit(ArtistDetails(artistName, null, "No records found in the Spotify archives."))
                }
            } else {
                emit(ArtistDetails(artistName, null, "The connection to the cloud was severed."))
            }
        } catch (e: Exception) {
            Log.e("MediaRepo", "API Hunt failed for $artistName: ${e.message}")
            emit(ArtistDetails(artistName, null, "Ritual failed: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * High-Res Album Art Ritual
     */
    fun getHighResAlbumArt(albumName: String, artistName: String): Flow<String?> = flow {
        val token = authManager.getToken()
        if (token.isEmpty()) {
            emit(null)
            return@flow
        }

        try {
            val query = "album:\"$albumName\" artist:\"$artistName\""
            val response = spotifyApiService.searchAlbum("Bearer $token", query)

            if (response.isSuccessful) {
                val albumItems = response.body()?.albums?.items
                // Index 0 is the 640x640 high-fidelity variant
                val highResUrl = albumItems?.firstOrNull()?.images?.firstOrNull()?.url
                emit(highResUrl)
            } else {
                emit(null)
            }
        } catch (e: Exception) {
            Log.e("MediaRepo", "Album Art Hunt failed: ${e.message}")
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildSyntheticBio(artist: SpotifyArtistDetail): String {
        val genreText = artist.genres?.take(3)?.joinToString(", ")?.lowercase() ?: "various styles"
        val renown = when {
            (artist.popularity ?: 0) > 80 -> "a legendary figure"
            (artist.popularity ?: 0) > 50 -> "a renowned master"
            else -> "an emerging force"
        }
        return "Known in the archives as $renown of $genreText. Their influence across the cloud is currently marked at a potency of ${artist.popularity ?: 0}/100."
    }
}