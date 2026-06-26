package com.example.tigerplayer.data.repository

import android.util.Log
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.remote.api.LastFmApi
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.model.LastFmImage
import com.example.tigerplayer.data.remote.model.SpotifyArtistDetail
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.example.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
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
    private val lastFmApi: LastFmApi,
    private val audioRepository: AudioRepository
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getArtistDetails(artistName: String): Flow<ArtistDetails> {
        val cleanArtist = ArtistUtils.getBaseArtist(artistName).trim()
        val cacheKey = cleanArtist.lowercase()

        if (cleanArtist.isBlank() || cleanArtist.equals("<unknown>", ignoreCase = true)) {
            return flowOf(ArtistDetails(cleanArtist, null, "Unknown entity."))
        }

        return tigerDao.getArtistCache(cacheKey).flatMapLatest { cachedData ->
            flow {
                // Gather temporal stats directly from the local records
                val localCount = tigerDao.getArtistPlayCount(cleanArtist)
                val localMinutes = tigerDao.getArtistMinutesListened(cleanArtist)

                if (cachedData != null) {
                    emit(ArtistDetails(
                        name = cleanArtist,
                        imageUrl = cachedData.imageUrl,
                        bio = cachedData.bio,
                        genres = cachedData.genres?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                        localPlayCount = localCount,
                        minutesListened = localMinutes
                    ))
                }

                // If no cache or we want to refresh, do the Dual-Oracle Ritual
                // For simplicity, only fetch if no cache or image is missing
                if (cachedData == null || cachedData.imageUrl == null) {
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
                            } else if (cachedData == null) {
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
                            } else null
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }
                    freshDetails?.let { emit(it) }
                }
            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

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

    /**
     * Utility helper to strip bracketed clutter and trailing text (like "- Live")
     * while preserving the clean, exact core names for surgical search accuracy.
     */
    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("\\s*[(\\[](Explicit|Remastered|Deluxe|Live|O.S.T.|Original Motion Picture Soundtrack|Bonus Track|Mono|Stereo|Re-Recorded)[^\\])]*[\\])]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+-\\s+.*$"), "") // Removes trailing single separators like "- Single"
            .trim()
    }

    fun getHighResAlbumArt(title: String, artist: String, album: String): Flow<String?> = flow {
        val token = authManager.getValidToken()
        if (token.isEmpty()) {
            emit(null)
            return@flow
        }

        val highResUrl = try {
            val cleanTitle = cleanSearchTerm(title)
            val cleanArtist = cleanSearchTerm(artist)
            val cleanAlbum = cleanSearchTerm(album)

            // 1. SURGICAL SEARCH QUERY: track:"Title" artist:"Artist" album:"Album"
            // Double quotes inside the search query force Spotify to perform an EXACT match search.
            val strictQuery = "track:\"$cleanTitle\" artist:\"$cleanArtist\" album:\"$cleanAlbum\""

            // Note: Using a general track search is superior because Track items contain
            // direct high-resolution links to their parent Album image arrays.
            val response = spotifyApiService.searchTrack("Bearer $token", strictQuery)

            if (response.isSuccessful) {
                val trackItem = response.body()?.tracks?.items?.firstOrNull()

                // 2. THE SECURITY GUARD: Verify the search result against our clean criteria
                // to eliminate false positives entirely.
                val matchTitle = trackItem?.name?.equals(cleanTitle, ignoreCase = true) == true
                val matchArtist = trackItem?.artists?.any { it.name.equals(cleanArtist, ignoreCase = true) } == true

                if (matchTitle && matchArtist) {
                    trackItem.album?.images?.firstOrNull()?.url
                } else {
                    // FALLBACK QUERY: If the exact album metadata is too noisy, search strictly by Track & Artist.
                    val fallbackQuery = "track:\"$cleanTitle\" artist:\"$cleanArtist\""
                    val fallbackResponse = spotifyApiService.searchTrack("Bearer $token", fallbackQuery)

                    if (fallbackResponse.isSuccessful) {
                        val fallbackTrack = fallbackResponse.body()?.tracks?.items?.firstOrNull()
                        val fbMatchTitle = fallbackTrack?.name?.equals(cleanTitle, ignoreCase = true) == true
                        val fbMatchArtist = fallbackTrack?.artists?.any { it.name.equals(cleanArtist, ignoreCase = true) } == true

                        if (fbMatchTitle && fbMatchArtist) {
                            fallbackTrack.album?.images?.firstOrNull()?.url
                        } else null
                    } else null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MediaRepo", "Strict Album Art search failed: ${e.message}")
            null
        }

        emit(highResUrl)
    }.flowOn(Dispatchers.IO)

    suspend fun getInfinitePlayRecommendations(
        anchorTrack: AudioTrack,
        limit: Int = 12
    ): List<AudioTrack> = withContext(Dispatchers.IO) {
        val localTracks = audioRepository.getLocalTracks().firstOrNull().orEmpty()
        if (localTracks.isEmpty()) return@withContext emptyList()

        val trackLookup = localTracks.associateBy {
            recommendationKey(it.title, it.artist)
        }

        val spotifyCandidates = mutableListOf<SpotifyTrack>()

        try {
            val token = authManager.getValidToken()
            if (token.isNotBlank()) {
                val bearer = "Bearer $token"
                val query = "track:\"${cleanSearchTerm(anchorTrack.title)}\" artist:\"${cleanSearchTerm(anchorTrack.artist)}\""
                val seedResponse = spotifyApiService.searchTrack(
                    token = bearer,
                    query = query,
                    limit = 1
                )

                val seedTrack = seedResponse.body()?.tracks?.items?.firstOrNull()
                val seedArtistId = seedTrack?.artists?.firstOrNull()?.id
                    ?: spotifyApiService
                        .searchArtist(token = bearer, query = cleanSearchTerm(anchorTrack.artist), limit = 1)
                        .body()?.artists?.items?.firstOrNull()?.id

                val recommendationsResponse = spotifyApiService.getRecommendations(
                    bearerToken = bearer,
                    seedTracks = seedTrack?.id,
                    seedArtists = if (seedTrack == null) seedArtistId else null,
                    limit = (limit * 2).coerceAtMost(50)
                )
                spotifyCandidates += recommendationsResponse.body()?.tracks.orEmpty()

                if (spotifyCandidates.isEmpty() && seedArtistId != null) {
                    spotifyCandidates += spotifyApiService
                        .getArtistTopTracks(bearerToken = bearer, artistId = seedArtistId)
                        .body()?.tracks
                        .orEmpty()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MediaRepo", "Infinite Play Spotify fetch failed: ${e.message}")
        }

        val mappedSpotify = spotifyCandidates
            .mapNotNull { trackLookup[recommendationKey(it.name, it.artists.firstOrNull()?.name.orEmpty())] }
            .filterNot { it.id == anchorTrack.id }
            .distinctBy { it.id }

        if (mappedSpotify.isNotEmpty()) {
            return@withContext mappedSpotify.take(limit)
        }

        val lastFmMapped = try {
            lastFmApi.getSimilarTracks(
                trackName = cleanSearchTerm(anchorTrack.title),
                artistName = ArtistUtils.getBaseArtist(anchorTrack.artist),
                limit = (limit * 3).coerceAtMost(100)
            ).body()?.similarTracks?.track.orEmpty()
                .mapNotNull { similar ->
                    trackLookup[recommendationKey(similar.name.orEmpty(), similar.artist?.name.orEmpty())]
                }
                .filterNot { it.id == anchorTrack.id }
                .distinctBy { it.id }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MediaRepo", "Infinite Play Last.fm fetch failed: ${e.message}")
            emptyList()
        }

        if (lastFmMapped.isNotEmpty()) {
            lastFmMapped.take(limit)
        } else {
            // Final fallback: random nearby local catalog from same artist family.
            localTracks
                .asSequence()
                .filterNot { it.id == anchorTrack.id }
                .filter { ArtistUtils.getBaseArtist(it.artist).equals(ArtistUtils.getBaseArtist(anchorTrack.artist), ignoreCase = true) }
                .shuffled()
                .take(limit)
                .toList()
        }
    }

    private fun recommendationKey(title: String, artist: String): String {
        val cleanTitle = cleanSearchTerm(title).lowercase().trim()
        val cleanArtist = ArtistUtils.getBaseArtist(artist).lowercase().trim()
        return "$cleanArtist::$cleanTitle"
    }
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