package com.example.tigerplayer.data.repository

import android.util.Log
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.example.tigerplayer.data.remote.api.LastFmApi
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.model.LastFmImage
import com.example.tigerplayer.data.remote.model.SpotifyArtistDetail
import com.example.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.CancellationException
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
    val localPlayCount: Int = 0,
    val popularity: Int = 0,
    val minutesListened: Int = 0
)

@Singleton
class MediaDataRepository @Inject constructor(
    private val tigerDao: TigerDao,
    private val spotifyApiService: SpotifyApiService,
    private val authManager: SpotifyAuthManager,
    private val lastFmApi: LastFmApi
) {

    fun getArtistDetails(artistName: String): Flow<ArtistDetails> = flow {
        // 1. THE NORMALIZATION RITUAL
        val cleanArtist = ArtistUtils.getBaseArtist(artistName).trim()
        val cacheKey = cleanArtist.lowercase()

        if (cleanArtist.isBlank() || cleanArtist.equals("<unknown>", ignoreCase = true)) {
            emit(ArtistDetails(cleanArtist, null, "Unknown entity."))
            return@flow
        }

        // Gather temporal stats directly from the local records
        val localCount = tigerDao.getArtistPlayCount(cleanArtist)
        val localMinutes = tigerDao.getArtistMinutesListened(cleanArtist)

        // 2. THE ARCHIVE CHECK
        val cachedData = tigerDao.getArtistCache(cacheKey)

        if (cachedData != null) {
            emit(ArtistDetails(
                name = cleanArtist,
                imageUrl = cachedData.imageUrl,
                bio = cachedData.bio,
                genres = cachedData.genres?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                localPlayCount = localCount,
                minutesListened = localMinutes
            ))
            return@flow
        }

        // 3. THE RESTORED DUAL-ORACLE RITUAL
        // 🔥 THE FIX: Compute the data first, handle exceptions, THEN emit outside the block.
        val freshDetails = try {
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
                    if (imageUrl.isNullOrBlank()) imageUrl = lastFmArtist?.image?.getBestImage()
                    if (imageUrl.isNullOrBlank()) imageUrl = tigerDao.getLocalArtworkForArtist(cleanArtist)

                    val genreList = spotifyDetail?.genres?.takeIf { it.isNotEmpty() }
                        ?: lastFmArtist?.tags?.tag?.mapNotNull { it.name }
                        ?: emptyList()

                    val finalBio = lastFmArtist?.bio?.summary?.let {
                        if (it.isNotBlank()) it.substringBefore("<a href").trim() else null
                    } ?: spotifyDetail?.let { buildSyntheticBio(it) } ?: "No records found."

                    val details = ArtistDetails(
                        name = spotifyDetail?.name ?: lastFmArtist?.name ?: cleanArtist,
                        imageUrl = imageUrl,
                        bio = finalBio,
                        genres = genreList,
                        popularity = spotifyDetail?.popularity ?: 0,
                        localPlayCount = localCount,
                        minutesListened = localMinutes
                    )

                    tigerDao.insertArtistCache(
                        ArtistCacheEntity(
                            artistName = cacheKey,
                            imageUrl = imageUrl,
                            bio = finalBio,
                            genres = genreList.joinToString(",")
                        )
                    )
                    details
                } else {
                    val voidBio = "Lore not found in the grand archives."
                    tigerDao.insertArtistCache(
                        ArtistCacheEntity(
                            artistName = cacheKey,
                            imageUrl = null,
                            bio = voidBio,
                            genres = ""
                        )
                    )
                    ArtistDetails(
                        name = cleanArtist,
                        imageUrl = null,
                        bio = voidBio,
                        localPlayCount = localCount,
                        minutesListened = localMinutes
                    )
                }
            }
        } catch (e: Exception) {
            // Re-throw cancellations immediately so Coroutines can clean up memory safely
            if (e is CancellationException) throw e

            Log.e("MediaRepo", "Ritual failed: ${e.message}")
            ArtistDetails(
                name = cleanArtist,
                imageUrl = null,
                bio = "Connection to oracles lost.",
                localPlayCount = localCount,
                minutesListened = localMinutes
            )
        }

        // 🔥 THE FIX: Safely emit only after all try/catch blocks are resolved
        emit(freshDetails)

    }.flowOn(Dispatchers.IO)

    private fun List<LastFmImage>.getBestImage(): String? {
        return this.filter { !it.url.orEmpty().contains("2a96cbd8b46e442fc41c2b86b821562f") }
            .let { filtered ->
                filtered.find { it.size == "mega" }?.url
                    ?: filtered.find { it.size == "extralarge" }?.url
                    ?: filtered.find { it.size == "large" }?.url
                    ?: filtered.firstOrNull()?.url
            }
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

        // 🔥 THE FIX: Isolate the try-catch block from the emit function
        val highResUrl = try {
            val query = "album:\"$albumName\" artist:\"$artistName\""
            val response = spotifyApiService.searchAlbum("Bearer $token", query)

            if (response.isSuccessful) {
                response.body()?.albums?.items?.firstOrNull()?.images?.firstOrNull()?.url
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MediaRepo", "Album Art Hunt failed: ${e.message}")
            null
        }

        // Only emit once we are safely outside the exception hunting grounds
        emit(highResUrl)

    }.flowOn(Dispatchers.IO)

    // ==========================================
    // --- GRIMOIRE MANAGEMENT (Playlists) ---
    // ==========================================

    suspend fun deletePlaylist(playlistId: Long): Boolean {
        return try {
            val rowsDeleted = tigerDao.deletePlaylist(playlistId)
            rowsDeleted > 0
        } catch (e: Exception) {
            Log.e("MediaRepo", "Failed to destroy the grimoire: ${e.message}")
            false
        }
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String): Boolean {
        return try {
            val rowsUpdated = tigerDao.renamePlaylist(playlistId, newName)
            rowsUpdated > 0
        } catch (e: Exception) {
            Log.e("MediaRepo", "Failed to rename the grimoire: ${e.message}")
            false
        }
    }

    suspend fun updatePlaylistOrder(playlistId: Long, trackIdsInOrder: List<String>): Boolean {
        return try {
            trackIdsInOrder.forEachIndexed { index, trackId ->
                tigerDao.updatePlaylistTrackPosition(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = index
                )
            }
            true
        } catch (e: Exception) {
            Log.e("MediaRepo", "Failed to reorder the grimoire tracks: ${e.message}")
            false
        }
    }

    suspend fun updatePlaylistArtwork(playlistId: Long, artworkUri: String): Boolean {
        return try {
            val rowsUpdated = tigerDao.updatePlaylistArtwork(playlistId, artworkUri)
            rowsUpdated > 0
        } catch (e: Exception) {
            Log.e("MediaRepo", "Failed to bind new sigil to grimoire: ${e.message}")
            false
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Boolean {
        return try {
            tigerDao.removeTrackFromPlaylist(playlistId, trackId) > 0
        } catch (e: Exception) {
            Log.e("MediaRepo", "Failed to banish track from grimoire: ${e.message}")
            false
        }
    }

    suspend fun addMultipleTracksToPlaylist(playlistId: Long, trackIds: List<String>): Boolean {
        return try {
            val crossRefs = trackIds.map { trackId ->
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    dateAdded = System.currentTimeMillis()
                )
            }
            tigerDao.insertPlaylistTrackCrossRefs(crossRefs)
            true
        } catch (e: Exception) {
            Log.e("MediaRepo", "Failed to batch import chants: ${e.message}")
            false
        }
    }
}