package com.tigerplayer.data.repository.deezer

import android.util.Log
import android.util.LruCache
import com.tigerplayer.BuildConfig
import com.tigerplayer.data.remote.api.deezer.DeezerApiService
import com.tigerplayer.data.remote.api.deezer.DeezerTrackDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeezerRepository"
private const val CACHE_MAX_ENTRIES = 100

/**
 * Repository for zero-auth Deezer public API lookups with bounded in-memory caching,
 * main-safe IO dispatching, and detection of Deezer HTTP 200 error payloads.
 */
@Singleton
class DeezerRepository @Inject constructor(
    private val api: DeezerApiService
) {
    private val trackCache = LruCache<Long, DeezerTrackDto>(CACHE_MAX_ENTRIES)
    private val albumArtCache = LruCache<String, String>(CACHE_MAX_ENTRIES)
    private val artistPictureCache = LruCache<String, String>(CACHE_MAX_ENTRIES)
    private val artistTopTracksCache = LruCache<String, List<DeezerTrackDto>>(CACHE_MAX_ENTRIES)

    suspend fun searchTracks(query: String): List<DeezerTrackDto> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()
        try {
            val response = api.searchTrack(cleanQuery)
            if (response.error != null) {
                logDebug("searchTracks API error (${response.error.code}): ${response.error.message}")
                return@withContext emptyList()
            }
            response.data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logDebug("searchTracks failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getTrack(id: Long): DeezerTrackDto? = withContext(Dispatchers.IO) {
        trackCache.get(id)?.let { return@withContext it }
        try {
            val track = api.getTrack(id)
            if (track.error != null || track.id == 0L) {
                logDebug("getTrack($id) API error (${track.error?.code}): ${track.error?.message}")
                return@withContext null
            }
            trackCache.put(id, track)
            track
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logDebug("getTrack($id) failed: ${e.message}")
            null
        }
    }

    suspend fun getAlbumArt(albumName: String, artistName: String): String? = withContext(Dispatchers.IO) {
        val cleanAlbum = sanitizeQueryPart(albumName)
        val cleanArtist = sanitizeQueryPart(artistName)
        if (cleanAlbum.isEmpty() && cleanArtist.isEmpty()) return@withContext null

        val cacheKey = "${cleanAlbum.lowercase()}|${cleanArtist.lowercase()}"
        albumArtCache.get(cacheKey)?.let { return@withContext it }

        try {
            val strictQuery = "album:\"$cleanAlbum\" artist:\"$cleanArtist\""
            val strictResponse = api.searchAlbum(strictQuery)
            if (strictResponse.error != null) {
                logDebug("getAlbumArt API error (${strictResponse.error.code}): ${strictResponse.error.message}")
                return@withContext null
            }
            var cover = strictResponse.data.firstOrNull()?.coverXl
            if (cover == null) {
                val fallbackResponse = api.searchAlbum("$cleanAlbum $cleanArtist".trim())
                if (fallbackResponse.error != null) {
                    logDebug("getAlbumArt fallback API error (${fallbackResponse.error.code}): ${fallbackResponse.error.message}")
                    return@withContext null
                }
                cover = fallbackResponse.data.firstOrNull()?.coverXl
            }
            if (!cover.isNullOrBlank()) {
                albumArtCache.put(cacheKey, cover)
            }
            cover
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logDebug("getAlbumArt failed: ${e.message}")
            null
        }
    }

    suspend fun getArtistPicture(artistName: String): String? = withContext(Dispatchers.IO) {
        val cleanArtist = sanitizeQueryPart(artistName)
        if (cleanArtist.isEmpty()) return@withContext null

        val cacheKey = cleanArtist.lowercase()
        artistPictureCache.get(cacheKey)?.let { return@withContext it }

        try {
            val response = api.searchArtist(cleanArtist)
            if (response.error != null) {
                logDebug("getArtistPicture API error (${response.error.code}): ${response.error.message}")
                return@withContext null
            }
            val picture = response.data.firstOrNull()?.pictureXl
            if (!picture.isNullOrBlank()) {
                artistPictureCache.put(cacheKey, picture)
            }
            picture
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logDebug("getArtistPicture failed: ${e.message}")
            null
        }
    }

    suspend fun getArtistTopTracks(artistName: String): List<DeezerTrackDto> = withContext(Dispatchers.IO) {
        val cleanArtist = sanitizeQueryPart(artistName)
        if (cleanArtist.isEmpty()) return@withContext emptyList()

        val cacheKey = cleanArtist.lowercase()
        artistTopTracksCache.get(cacheKey)?.let { return@withContext it }

        try {
            val artistResponse = api.searchArtist(cleanArtist)
            if (artistResponse.error != null) {
                logDebug("getArtistTopTracks search API error (${artistResponse.error.code}): ${artistResponse.error.message}")
                return@withContext emptyList()
            }
            val artist = artistResponse.data.firstOrNull() ?: return@withContext emptyList()
            val topResponse = api.getArtistTopTracks(artist.id)
            if (topResponse.error != null) {
                logDebug("getArtistTopTracks API error (${topResponse.error.code}): ${topResponse.error.message}")
                return@withContext emptyList()
            }
            if (topResponse.data.isNotEmpty()) {
                artistTopTracksCache.put(cacheKey, topResponse.data)
            }
            topResponse.data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logDebug("getArtistTopTracks failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getChartTracks(): List<DeezerTrackDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getChartTracks()
            if (response.error != null) {
                logDebug("getChartTracks API error (${response.error.code}): ${response.error.message}")
                return@withContext emptyList()
            }
            response.data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logDebug("getChartTracks failed: ${e.message}")
            emptyList()
        }
    }

    private fun sanitizeQueryPart(input: String): String =
        input.replace("\"", " ").trim()

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
