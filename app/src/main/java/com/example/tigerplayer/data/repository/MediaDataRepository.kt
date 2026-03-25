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

    fun getArtistDetails(artistName: String): Flow<ArtistDetails> = flow {
        val cleanArtist = artistName.trim()

        // 1. THE FAST-FAIL: Do not bother the Oracles with empty questions
        if (cleanArtist.isBlank() || cleanArtist.equals("<unknown>", ignoreCase = true)) {
            emit(ArtistDetails(cleanArtist, null, "Unknown entity."))
            return@flow
        }

        // 2. THE ARCHIVE CHECK (Cache)
        val cachedData = tigerDao.getArtistCache(cleanArtist)

        // THE FIX: We accept the cache hit even if bio/image is null.
        // This prevents re-querying artists we already know don't exist online.
        if (cachedData != null) {
            Log.d("MediaRepo", "Cache Hit: $cleanArtist. Metadata restored.")
            emit(ArtistDetails(
                name = cleanArtist,
                imageUrl = cachedData.imageUrl,
                bio = cachedData.bio,
                genres = cachedData.genres?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            ))
            return@flow
        }

        // 3. THE API RITUAL (Cache Miss)
        try {
            coroutineScope {
                val spotifyDeferred = async {
                    val token = authManager.getValidToken()
                    if (token.isNotEmpty()) {
                        val response = spotifyApiService.searchArtist("Bearer $token", cleanArtist)
                        if (response.isSuccessful) response.body()?.artists?.items?.firstOrNull() else null
                    } else null
                }

                val lastFmDeferred = async {
                    try {
                        val response = lastFmApi.getArtistInfo(artistName = cleanArtist)
                        if (response.isSuccessful) response.body()?.artist else null
                    } catch (e: Exception) { null }
                }

                val spotifyDetail = spotifyDeferred.await()
                val lastFmArtist = lastFmDeferred.await()

                if (spotifyDetail != null || lastFmArtist != null) {
                    var imageUrl = spotifyDetail?.images?.firstOrNull()?.url

                    if (imageUrl.isNullOrBlank()) {
                        imageUrl = lastFmArtist?.image?.getBestImage()
                    }

                    val genreList = spotifyDetail?.genres?.takeIf { it.isNotEmpty() }
                        ?: lastFmArtist?.tags?.tag?.mapNotNull { it.name }
                        ?: emptyList()

                    val finalBio = lastFmArtist?.bio?.summary?.let {
                        if (it.isNotBlank()) it.substringBefore("<a href").trim() else null
                    } ?: spotifyDetail?.let { buildSyntheticBio(it) } ?: "No records found."

                    val freshDetails = ArtistDetails(
                        name = spotifyDetail?.name ?: lastFmArtist?.name ?: cleanArtist,
                        imageUrl = imageUrl,
                        bio = finalBio,
                        genres = genreList,
                        popularity = spotifyDetail?.popularity ?: 0
                    )

                    // SECURING THE LOOT
                    tigerDao.insertArtistCache(
                        ArtistCacheEntity(
                            artistName = cleanArtist,
                            imageUrl = imageUrl,
                            bio = finalBio,
                            genres = genreList.joinToString(",")
                        )
                    )

                    emit(freshDetails)
                } else {
                    // THE FIX: Cache the negative result (The Void)
                    val voidBio = "Lore not found in the grand archives."
                    tigerDao.insertArtistCache(
                        ArtistCacheEntity(
                            artistName = cleanArtist,
                            imageUrl = null,
                            bio = voidBio,
                            genres = ""
                        )
                    )
                    emit(ArtistDetails(cleanArtist, null, voidBio))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaRepo", "Ritual failed: ${e.message}")
            // Do not cache network failures, so it can retry later
            emit(ArtistDetails(cleanArtist, null, "Connection to oracles lost."))
        }
    }.flowOn(Dispatchers.IO)

    private fun List<LastFmImage>.getBestImage(): String? {
        return this.find { it.size == "mega" }?.url
            ?: this.find { it.size == "extralarge" }?.url
            ?: this.find { it.size == "large" }?.url
            ?: this.firstOrNull()?.url
    }

    private fun buildSyntheticBio(artist: SpotifyArtistDetail): String {
        val genreText = artist.genres?.take(2)?.joinToString(", ")?.uppercase() ?: "VARIOUS STYLES"
        val renown = when {
            (artist.popularity ?: 0) > 80 -> "A LEGENDARY FIGURE"
            (artist.popularity ?: 0) > 50 -> "A RENOWNED MASTER"
            else -> "AN EMERGING FORCE"
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