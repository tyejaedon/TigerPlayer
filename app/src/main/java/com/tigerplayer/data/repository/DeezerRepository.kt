package com.tigerplayer.data.repository

import android.util.Log
import androidx.core.net.toUri
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.remote.api.DeezerApiService
import com.tigerplayer.data.remote.model.DeezerAlbumDto
import com.tigerplayer.data.remote.model.DeezerArtistDto
import com.tigerplayer.data.remote.model.DeezerTrackDto
import com.tigerplayer.utils.ArtistUtils
import com.tigerplayer.utils.MusicMetadataSearch.cleanSearchTerm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class DeezerArtist(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val fanCount: Int = 0,
    val albumCount: Int = 0
)

data class DeezerAlbum(
    val id: Long,
    val title: String,
    val artistName: String,
    val coverUrl: String?,
    val releaseDate: String? = null,
    val trackCount: Int = 0
)

/**
 * Repository for the unauthenticated Deezer Simple API.
 *
 * Provides zero-auth discovery (charts, search), high-resolution 1000x1000 artwork/artist
 * fallback, and 30-second audio preview tracks with in-memory TTL caching.
 */
@Singleton
class DeezerRepository @Inject constructor(
    private val deezerApiService: DeezerApiService
) {
    private data class CacheEntry<T>(val value: T, val timestampMs: Long)

    private val chartTracksCache = ConcurrentHashMap<Int, CacheEntry<List<AudioTrack>>>()
    private val chartArtistsCache = ConcurrentHashMap<Int, CacheEntry<List<DeezerArtist>>>()
    private val trackSearchCache = ConcurrentHashMap<String, CacheEntry<List<AudioTrack>>>()
    private val artistSearchCache = ConcurrentHashMap<String, CacheEntry<List<DeezerArtist>>>()
    private val albumSearchCache = ConcurrentHashMap<String, CacheEntry<List<DeezerAlbum>>>()
    private val albumTracksCache = ConcurrentHashMap<Long, CacheEntry<List<AudioTrack>>>()
    private val artistTopTracksCache = ConcurrentHashMap<Long, CacheEntry<List<AudioTrack>>>()
    private val artLookupCache = ConcurrentHashMap<String, CacheEntry<String?>>()
    private val artistInfoCache = ConcurrentHashMap<String, CacheEntry<DeezerArtist?>>()

    private fun <K, V> getCached(cache: ConcurrentHashMap<K, CacheEntry<V>>, key: K): V? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestampMs > CACHE_TTL_MS) {
            cache.remove(key)
            return null
        }
        return entry.value
    }

    private fun <K, V> putCached(cache: ConcurrentHashMap<K, CacheEntry<V>>, key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }

    fun getChartTracks(limit: Int = 25): Flow<Result<List<AudioTrack>>> = flow {
        getCached(chartTracksCache, limit)?.let {
            emit(Result.success(it))
            return@flow
        }
        try {
            val response = deezerApiService.getChartTracks(limit = limit)
            if (response.isSuccessful) {
                val tracks = response.body()?.data.orEmpty().map { it.toAudioTrack() }
                putCached(chartTracksCache, limit, tracks)
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Deezer chart tracks failed: HTTP ${response.code()}")))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to fetch chart tracks: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getChartArtists(limit: Int = 15): Flow<Result<List<DeezerArtist>>> = flow {
        getCached(chartArtistsCache, limit)?.let {
            emit(Result.success(it))
            return@flow
        }
        try {
            val response = deezerApiService.getChartArtists(limit = limit)
            if (response.isSuccessful) {
                val artists = response.body()?.data.orEmpty().map { it.toDomain() }
                putCached(chartArtistsCache, limit, artists)
                emit(Result.success(artists))
            } else {
                emit(Result.failure(Exception("Deezer chart artists failed: HTTP ${response.code()}")))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to fetch chart artists: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun searchTracks(query: String, limit: Int = 25): Flow<Result<List<AudioTrack>>> = flow {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            emit(Result.success(emptyList()))
            return@flow
        }
        val cacheKey = "${trimmed.lowercase()}::$limit"
        getCached(trackSearchCache, cacheKey)?.let {
            emit(Result.success(it))
            return@flow
        }
        try {
            val response = deezerApiService.searchTracks(query = trimmed, limit = limit)
            if (response.isSuccessful) {
                val tracks = response.body()?.data.orEmpty().map { it.toAudioTrack() }
                putCached(trackSearchCache, cacheKey, tracks)
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Deezer track search failed: HTTP ${response.code()}")))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Track search failed: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun searchArtists(query: String, limit: Int = 20): Flow<Result<List<DeezerArtist>>> = flow {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            emit(Result.success(emptyList()))
            return@flow
        }
        val cacheKey = "${trimmed.lowercase()}::$limit"
        getCached(artistSearchCache, cacheKey)?.let {
            emit(Result.success(it))
            return@flow
        }
        try {
            val response = deezerApiService.searchArtists(query = trimmed, limit = limit)
            if (response.isSuccessful) {
                val artists = response.body()?.data.orEmpty().map { it.toDomain() }
                putCached(artistSearchCache, cacheKey, artists)
                emit(Result.success(artists))
            } else {
                emit(Result.failure(Exception("Deezer artist search failed: HTTP ${response.code()}")))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Artist search failed: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun searchAlbums(query: String, limit: Int = 20): Flow<Result<List<DeezerAlbum>>> = flow {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            emit(Result.success(emptyList()))
            return@flow
        }
        val cacheKey = "${trimmed.lowercase()}::$limit"
        getCached(albumSearchCache, cacheKey)?.let {
            emit(Result.success(it))
            return@flow
        }
        try {
            val response = deezerApiService.searchAlbums(query = trimmed, limit = limit)
            if (response.isSuccessful) {
                val albums = response.body()?.data.orEmpty().map { it.toDomain() }
                putCached(albumSearchCache, cacheKey, albums)
                emit(Result.success(albums))
            } else {
                emit(Result.failure(Exception("Deezer album search failed: HTTP ${response.code()}")))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Album search failed: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getAlbumTracks(albumId: Long): Flow<Result<List<AudioTrack>>> = flow {
        getCached(albumTracksCache, albumId)?.let {
            emit(Result.success(it))
            return@flow
        }
        try {
            val response = deezerApiService.getAlbum(albumId)
            if (response.isSuccessful) {
                val albumDto = response.body()
                val tracks = albumDto?.tracks?.data.orEmpty().map { track ->
                    val enriched = if (track.album == null) track.copy(album = albumDto) else track
                    enriched.toAudioTrack()
                }
                putCached(albumTracksCache, albumId, tracks)
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Deezer album tracks failed: HTTP ${response.code()}")))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Album tracks fetch failed: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getArtistTopTracks(artistId: Long, limit: Int = 20): Flow<Result<List<AudioTrack>>> = flow {
        getCached(artistTopTracksCache, artistId)?.let {
            emit(Result.success(it))
            return@flow
        }
        try {
            val response = deezerApiService.getArtistTopTracks(id = artistId, limit = limit)
            if (response.isSuccessful) {
                val tracks = response.body()?.data.orEmpty().map { it.toAudioTrack() }
                putCached(artistTopTracksCache, artistId, tracks)
                emit(Result.success(tracks))
            } else {
                emit(Result.failure(Exception("Deezer artist top tracks failed: HTTP ${response.code()}")))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Artist top tracks fetch failed: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Zero-auth high-resolution (1000x1000) album artwork lookup via Deezer track search.
     */
    suspend fun findHighResAlbumArt(
        title: String,
        artist: String,
        album: String
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSearchTerm(title)
        val cleanArtist = ArtistUtils.getBaseArtist(cleanSearchTerm(artist)).trim()
        val cleanAlbum = cleanSearchTerm(album)
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        val cacheKey = "${cleanArtist.lowercase()}::${cleanTitle.lowercase()}::${cleanAlbum.lowercase()}"
        if (artLookupCache.containsKey(cacheKey)) {
            val cached = getCached(artLookupCache, cacheKey)
            if (cached != null || artLookupCache.containsKey(cacheKey)) {
                return@withContext cached
            }
        }

        val resolvedUrl = try {
            val strictQuery = if (cleanAlbum.isNotBlank()) {
                "artist:\"$cleanArtist\" track:\"$cleanTitle\" album:\"$cleanAlbum\""
            } else {
                "artist:\"$cleanArtist\" track:\"$cleanTitle\""
            }
            val strictResponse = deezerApiService.searchTracks(query = strictQuery, limit = 5)
            val strictMatch = if (strictResponse.isSuccessful) {
                strictResponse.body()?.data.orEmpty()
                    .firstOrNull { isMatchingTrack(it, cleanTitle, cleanArtist) }
                    ?.album?.bestCoverUrl
            } else null

            strictMatch ?: run {
                val fallbackQuery = "artist:\"$cleanArtist\" track:\"$cleanTitle\""
                val fallbackResponse = deezerApiService.searchTracks(query = fallbackQuery, limit = 5)
                if (fallbackResponse.isSuccessful) {
                    fallbackResponse.body()?.data.orEmpty()
                        .firstOrNull { isMatchingTrack(it, cleanTitle, cleanArtist) }
                        ?.album?.bestCoverUrl
                } else null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "High-res album art lookup failed: ${e.message}")
            null
        }

        putCached(artLookupCache, cacheKey, resolvedUrl)
        resolvedUrl
    }

    /**
     * Zero-auth artist profile lookup returning 1000x1000 `picture_xl` when available.
     */
    suspend fun findArtistInfo(artistName: String): DeezerArtist? = withContext(Dispatchers.IO) {
        val cleanArtist = ArtistUtils.getBaseArtist(artistName).trim()
        if (cleanArtist.isBlank() || cleanArtist.equals("<unknown>", ignoreCase = true)) {
            return@withContext null
        }

        val cacheKey = cleanArtist.lowercase()
        if (artistInfoCache.containsKey(cacheKey)) {
            val cached = getCached(artistInfoCache, cacheKey)
            if (cached != null || artistInfoCache.containsKey(cacheKey)) {
                return@withContext cached
            }
        }

        val resolved = try {
            val response = deezerApiService.searchArtists(query = cleanArtist, limit = 5)
            if (response.isSuccessful) {
                response.body()?.data.orEmpty()
                    .firstOrNull { isSameName(it.name, cleanArtist) }
                    ?.toDomain()
            } else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Artist info lookup failed: ${e.message}")
            null
        }

        putCached(artistInfoCache, cacheKey, resolved)
        resolved
    }

    /**
     * Fetches top tracks for [artistName] to serve as zero-auth Infinite Play recommendation seeds.
     */
    suspend fun findArtistTopTracksByName(
        artistName: String,
        limit: Int = 15
    ): List<DeezerTrackDto> = withContext(Dispatchers.IO) {
        val artist = findArtistInfo(artistName) ?: return@withContext emptyList()
        try {
            val response = deezerApiService.getArtistTopTracks(id = artist.id, limit = limit)
            if (response.isSuccessful) response.body()?.data.orEmpty() else emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Artist top tracks by name failed: ${e.message}")
            emptyList()
        }
    }

    fun DeezerTrackDto.toAudioTrack(): AudioTrack = AudioTrack(
        id = "$DEEZER_ID_PREFIX$id",
        title = title,
        artist = artist?.name?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
        album = album?.title?.takeIf { it.isNotBlank() } ?: "Single",
        uri = preview.orEmpty().toUri(),
        artworkUri = album?.bestCoverUrl.orEmpty().toUri(),
        durationMs = (duration.toLong() * 1000L).coerceAtLeast(0L),
        mimeType = "audio/mpeg",
        isLocal = false,
        isRemote = true,
        replayGainTrackDb = gain?.toDouble()
    )

    private fun DeezerArtistDto.toDomain(): DeezerArtist = DeezerArtist(
        id = id,
        name = name,
        imageUrl = bestPictureUrl,
        fanCount = nbFan ?: 0,
        albumCount = nbAlbum ?: 0
    )

    private fun DeezerAlbumDto.toDomain(): DeezerAlbum = DeezerAlbum(
        id = id,
        title = title,
        artistName = artist?.name?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
        coverUrl = bestCoverUrl,
        releaseDate = releaseDate,
        trackCount = nbTracks ?: 0
    )

    private fun isMatchingTrack(track: DeezerTrackDto, cleanTitle: String, cleanArtist: String): Boolean {
        val candidateTitle = normalize(cleanSearchTerm(track.title))
        val targetTitle = normalize(cleanTitle)
        val candidateArtist = normalize(ArtistUtils.getBaseArtist(track.artist?.name.orEmpty()))
        val targetArtist = normalize(cleanArtist)
        return candidateTitle.isNotEmpty() &&
            candidateArtist.isNotEmpty() &&
            candidateTitle == targetTitle &&
            candidateArtist == targetArtist
    }

    private fun isSameName(candidate: String?, requested: String): Boolean {
        val normCandidate = normalize(candidate)
        val normRequested = normalize(requested)
        return normCandidate.isNotEmpty() && normCandidate == normRequested
    }

    private fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .lowercase()
            .replace(NON_ALPHANUMERIC_REGEX, "")
    }

    companion object {
        private const val TAG = "DeezerRepository"
        const val DEEZER_ID_PREFIX = "deezer:"
        private const val CACHE_TTL_MS = 10 * 60_000L
        private val DIACRITICS_REGEX = Regex("\\p{Mn}+")
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]")
    }
}
