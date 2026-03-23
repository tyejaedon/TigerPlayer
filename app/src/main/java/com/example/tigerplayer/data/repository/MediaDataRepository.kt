package com.example.tigerplayer.data.repository

import android.util.Log
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.api.WikipediaApi
import com.example.tigerplayer.data.remote.model.SpotifyArtistDetail
import com.example.tigerplayer.data.remote.api.LastFmApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val authManager: SpotifyAuthManager,
    private val lastFmApi: LastFmApi

) {

    /**
     * PRIORITY: Cache. Fallback: Spotify API.
     * This ritual ensures the UI gets the full ArtistDetails object every time.
     */


// ... (Make sure private val lastFmApi: LastFmApi is injected in MediaDataRepository's constructor)

    fun getArtistDetails(artistName: String): Flow<ArtistDetails> = flow {
        // 1. CHECK THE ARCHIVES (Database)
        val cachedData = tigerDao.getArtistCache(artistName)

        // Ensure we actually have the bio cached before skipping the API hunt
        if (cachedData != null && !cachedData.imageUrl.isNullOrBlank() && !cachedData.bio.isNullOrBlank()) {
            Log.d("MediaRepo", "Cache Hit for $artistName. Restoring metadata.")
            emit(ArtistDetails(
                name = artistName,
                imageUrl = cachedData.imageUrl,
                bio = cachedData.bio,
                genres = emptyList(), // Ideally load from cache if your entity supports it
                popularity = 0
            ))
            return@flow
        }

        // 2. CACHE MISS: CONSULT BOTH ORACLES (Spotify + Last.fm)
        Log.d("MediaRepo", "Cache Miss for $artistName. Hunting Spotify and Last.fm APIs...")
        try {
            coroutineScope {
                // A. Fetch Spotify Essence (Image, Genres, Popularity)
                val spotifyDeferred = async {
                    val token = authManager.getValidToken()
                    if (token.isNotEmpty()) {
                        val response = spotifyApiService.searchArtist("Bearer $token", artistName)
                        if (response.isSuccessful) response.body()?.artists?.items?.firstOrNull() else null
                    } else null
                }

                // B. Fetch Last.fm Lore (Biography)
                val lastFmDeferred = async {
                    try {
                        val response = lastFmApi.getArtistInfo(artistName = artistName)
                        if (response.isSuccessful) response.body()?.artist else null
                    } catch (e: Exception) {
                        Log.e("MediaRepo", "Last.fm fetch error: ${e.message}")
                        null // Prevent one failing API from crashing the other
                    }
                }

                // Await both results
                val spotifyDetail = spotifyDeferred.await()
                val lastFmArtist = lastFmDeferred.await()

                if (spotifyDetail != null || lastFmArtist != null) {
                    // Extract Image from Spotify
                    val imageUrl = if ((spotifyDetail?.images?.size ?: 0) > 1)
                        spotifyDetail?.images?.get(1)?.url
                    else
                        spotifyDetail?.images?.firstOrNull()?.url

                    // Extract and clean Bio from Last.fm
                    val rawBio = lastFmArtist?.bio?.summary
                    val finalBio = if (!rawBio.isNullOrBlank()) {
                        // Strip the messy HTML "Read more on Last.fm" link
                        rawBio.substringBefore("<a href").trim()
                    } else {
                        // Fallback to our synthetic bio if Last.fm is empty
                        spotifyDetail?.let { buildSyntheticBio(it) } ?: "No historical records found."
                    }

                    val freshDetails = ArtistDetails(
                        name = spotifyDetail?.name ?: lastFmArtist?.name ?: artistName,
                        imageUrl = imageUrl,
                        bio = finalBio,
                        genres = spotifyDetail?.genres ?: emptyList(),
                        popularity = spotifyDetail?.popularity ?: 0
                    )

                    // 3. STORE THE LOOT
                    tigerDao.insertArtistCache(ArtistCacheEntity(artistName, imageUrl, finalBio))

                    // 4. EMIT FRESH DATA
                    emit(freshDetails)
                } else {
                    // Neither oracle had answers, but we EMIT a non-null string to kill the loading bar!
                    emit(ArtistDetails(artistName, null, "No records found in the archives."))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaRepo", "API Hunt failed for $artistName: ${e.message}")
            // EMIT error string to kill the loading bar on crash
            emit(ArtistDetails(artistName, null, "Ritual failed: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)
    /**
     * High-Res Album Art Ritual
     */
    fun getHighResAlbumArt(albumName: String, artistName: String): Flow<String?> = flow {
        val token = authManager.getValidToken()
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
    suspend fun clearArtistCache() {
        tigerDao.clearArtistCache()
    }}