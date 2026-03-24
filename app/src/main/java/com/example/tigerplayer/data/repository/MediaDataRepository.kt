package com.example.tigerplayer.data.repository

import android.util.Log
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.api.LastFmApi
import com.example.tigerplayer.data.remote.model.LastFmImage
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
    private val authManager: SpotifyAuthManager,
    private val lastFmApi: LastFmApi
) {

    /**
     * THE ARTIST MANIFESTATION:
     * Orchestrates a parallel hunt across Spotify and Last.fm to retrieve
     * high-fidelity imagery and deep lore.
     */
    fun getArtistDetails(artistName: String): Flow<ArtistDetails> = flow {
        // 1. THE ARCHIVE CHECK (Cache)
        val cachedData = tigerDao.getArtistCache(artistName)

        if (cachedData != null && !cachedData.imageUrl.isNullOrBlank() && !cachedData.bio.isNullOrBlank()) {
            Log.d("MediaRepo", "Cache Hit: $artistName. Metadata restored.")
            emit(ArtistDetails(
                name = artistName,
                imageUrl = cachedData.imageUrl,
                bio = cachedData.bio,
                genres = cachedData.genres?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            ))
            return@flow
        }

        // 2. THE API RITUAL (Cache Miss)
        try {
            coroutineScope {
                val spotifyDeferred = async {
                    val token = authManager.getValidToken()
                    if (token.isNotEmpty()) {
                        val response = spotifyApiService.searchArtist("Bearer $token", artistName)
                        if (response.isSuccessful) response.body()?.artists?.items?.firstOrNull() else null
                    } else null
                }

                val lastFmDeferred = async {
                    try {
                        val response = lastFmApi.getArtistInfo(artistName = artistName)
                        if (response.isSuccessful) response.body()?.artist else null
                    } catch (e: Exception) { null }
                }

                val spotifyDetail = spotifyDeferred.await()
                val lastFmArtist = lastFmDeferred.await()

                if (spotifyDetail != null || lastFmArtist != null) {

                    // --- IMAGE LOGIC (High-Res Priority) ---
                    // 1. Spotify 640x640 is the Gold Standard
                    // 2. Fallback to Last.fm "Mega" or "Extralarge" via getBestImage()
                    var imageUrl = spotifyDetail?.images?.firstOrNull()?.url

                    if (imageUrl.isNullOrBlank()) {
                        // Using your new extraction logic for the Last.fm XML response
                        imageUrl = lastFmArtist?.image?.getBestImage()
                    }

                    // --- GENRE LOGIC ---
                    val genreList = spotifyDetail?.genres?.takeIf { it.isNotEmpty() }
                        ?: lastFmArtist?.tags?.tag?.mapNotNull { it.name }
                        ?: emptyList()

                    // --- BIO LOGIC ---
                    val finalBio = lastFmArtist?.bio?.summary?.let {
                        if (it.isNotBlank()) it.substringBefore("<a href").trim() else null
                    } ?: spotifyDetail?.let { buildSyntheticBio(it) } ?: "No records found."

                    val freshDetails = ArtistDetails(
                        name = spotifyDetail?.name ?: lastFmArtist?.name ?: artistName,
                        imageUrl = imageUrl,
                        bio = finalBio,
                        genres = genreList,
                        popularity = spotifyDetail?.popularity ?: 0
                    )

                    // 3. SECURING THE LOOT (Cache Storage)
                    tigerDao.insertArtistCache(
                        ArtistCacheEntity(
                            artistName = artistName,
                            imageUrl = imageUrl,
                            bio = finalBio,
                            genres = genreList.joinToString(",")
                        )
                    )

                    emit(freshDetails)
                } else {
                    emit(ArtistDetails(artistName, null, "Lore not found."))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaRepo", "Ritual failed: ${e.message}")
            emit(ArtistDetails(artistName, null, "Connection to oracles lost."))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Extension logic to navigate the Last.fm XML image nodes.
     */
    private fun List<LastFmImage>.getBestImage(): String? {
        return this.find { it.size == "mega" }?.url
            ?: this.find { it.size == "extralarge" }?.url
            ?: this.find { it.size == "large" }?.url
            ?: this.firstOrNull()?.url
    }

    private fun buildSyntheticBio(artist: SpotifyArtistDetail): String {
        val genreText = artist.genres?.take(2)?.joinToString(", ")?.lowercase() ?: "various styles"
        val renown = when {
            (artist.popularity ?: 0) > 80 -> "a legendary figure"
            (artist.popularity ?: 0) > 50 -> "a renowned master"
            else -> "an emerging force"
        }
        return "Known in the archives as $renown of $genreText. Their potency is marked at ${artist.popularity ?: 0}/100."
    }

    suspend fun clearArtistCache() {
        tigerDao.clearArtistCache()
    }

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
                val highResUrl = response.body()?.albums?.items?.firstOrNull()?.images?.firstOrNull()?.url
                emit(highResUrl)
            } else {
                emit(null)
            }
        } catch (e: Exception) {
            Log.e("MediaRepo", "Album Art Hunt failed: ${e.message}")
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

}