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
    val minutesListened: Int = 0 // THE NEW METRIC ADDED
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
        val cacheKey = cleanArtist.lowercase() // Critical to prevent Tab-Switch flicker

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
            Log.d("MediaRepo", "Cache Hit: $cleanArtist. Metadata restored.")
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
        try {
            coroutineScope {
                // Hunt for the Face (Spotify)
                val spotifyDeferred = async {
                    val token = authManager.getValidToken()
                    if (token.isNotEmpty()) {
                        val response = spotifyApiService.searchArtist("Bearer $token", cleanArtist)
                        if (response.isSuccessful) response.body()?.artists?.items?.firstOrNull() else null
                    } else null
                }

                // Hunt for the Lore (Last.fm)
                val lastFmDeferred = async {
                    try {
                        val response = lastFmApi.getArtistInfo(artistName = cleanArtist)
                        if (response.isSuccessful) response.body()?.artist else null
                    } catch (e: Exception) { null }
                }

                val spotifyDetail = spotifyDeferred.await()
                val lastFmArtist = lastFmDeferred.await()

                if (spotifyDetail != null || lastFmArtist != null) {
                    // Priority 1: Spotify (Real Faces)
                    var imageUrl = spotifyDetail?.images?.firstOrNull()?.url

                    // Priority 2: Last.fm (Only if Spotify fails)
                    if (imageUrl.isNullOrBlank()) {
                        imageUrl = lastFmArtist?.image?.getBestImage()
                    }

                    // Priority 3: Local Vault Scavenge (If both APIs fail)
                    if (imageUrl.isNullOrBlank()) {
                        imageUrl = tigerDao.getLocalArtworkForArtist(cleanArtist)
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
                        popularity = spotifyDetail?.popularity ?: 0,
                        localPlayCount = localCount,
                        minutesListened = localMinutes
                    )

                    // SECURE THE LOOT: Save using the cacheKey so UI always finds it
                    tigerDao.insertArtistCache(
                        ArtistCacheEntity(
                            artistName = cacheKey,
                            imageUrl = imageUrl,
                            bio = finalBio,
                            genres = genreList.joinToString(",")
                        )
                    )

                    emit(freshDetails)
                } else {
                    // THE VOID
                    val voidBio = "Lore not found in the grand archives."
                    tigerDao.insertArtistCache(
                        ArtistCacheEntity(
                            artistName = cacheKey,
                            imageUrl = null,
                            bio = voidBio,
                            genres = ""
                        )
                    )
                    emit(ArtistDetails(
                        name = cleanArtist,
                        imageUrl = null,
                        bio = voidBio,
                        localPlayCount = localCount,
                        minutesListened = localMinutes
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaRepo", "Ritual failed: ${e.message}")
            emit(ArtistDetails(
                name = cleanArtist,
                imageUrl = null,
                bio = "Connection to oracles lost.",
                localPlayCount = localCount,
                minutesListened = localMinutes
            ))
        }
    }.flowOn(Dispatchers.IO)

    // Restored your flawless Last.fm image picker with a safely handled Star Banishment filter
    private fun List<LastFmImage>.getBestImage(): String? {
        // THE FIX: orEmpty() is safer than !! for handling potential null URLs
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

    // --- NEW: ADVANCED PLAYLIST OPERATIONS ---

    /**
     * Updates the custom sorting order of a playlist based on drag-and-drop UI.
     * 🔥 FIX: The looping logic has been migrated here from the DAO.
     */
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

    /**
     * Updates the custom user-selected artwork URI for a specific playlist.
     */
    suspend fun updatePlaylistArtwork(playlistId: Long, artworkUri: String): Boolean {
        return try {
            val rowsUpdated = tigerDao.updatePlaylistArtwork(playlistId, artworkUri)
            rowsUpdated > 0
        } catch (e: Exception) {
            Log.e("MediaRepo", "Failed to bind new sigil to grimoire: ${e.message}")
            false
        }
    }

    /**
     * Remove a single track from the playlist natively here.
     */
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Boolean {
        return try {
            tigerDao.removeTrackFromPlaylist(playlistId, trackId) > 0
        } catch (e: Exception) {
            Log.e("MediaRepo", "Failed to banish track from grimoire: ${e.message}")
            false
        }
    }

    /**
     * Batch add tracks (useful for importing playlists from Spotify/M3U)
     * 🔥 FIX: The mapping logic has been migrated here from the DAO.
     */
    suspend fun addMultipleTracksToPlaylist(playlistId: Long, trackIds: List<String>): Boolean {
        return try {
            // Map the simple List of IDs into the complex database Entities
            val crossRefs = trackIds.map { trackId ->
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    dateAdded = System.currentTimeMillis()
                )
            }
            // Send the mapped list directly to the primitive DAO insert function
            tigerDao.insertPlaylistTrackCrossRefs(crossRefs)
            true
        } catch (e: Exception) {
            Log.e("MediaRepo", "Failed to batch import chants: ${e.message}")
            false
        }
    }
}