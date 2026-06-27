package com.example.tigerplayer.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.remote.api.LastFmApi
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.model.LastFmImage
import com.example.tigerplayer.data.remote.model.SpotifyArtistDetail
import com.example.tigerplayer.R
import com.example.tigerplayer.utils.ArtistUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.*
import kotlin.random.Random
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
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {

    private data class GlobalTrendTrackSeed(
        val title: String,
        val artist: String,
        val genre: String? = null
    )

    private data class ScoredDiscoveryTrack(
        val track: AudioTrack,
        val genres: Set<String>,
        val score: Double
    )

    private val globalTrendCatalog: List<GlobalTrendTrackSeed> by lazy {
        loadGlobalTrendCatalog()
    }

    val lastDiscoveryGenerationAt: Flow<Long> = dataStore.data
        .map { prefs -> prefs[DISCOVERY_LAST_GENERATION_KEY] ?: 0L }

    suspend fun getLastDiscoveryGenerationTimestamp(): Long {
        return dataStore.data.first()[DISCOVERY_LAST_GENERATION_KEY] ?: 0L
    }

    suspend fun updateDiscoveryGenerationTimestamp(timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[DISCOVERY_LAST_GENERATION_KEY] = timestamp
        }
    }

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

    fun resolveGlobalTrendsetters(
        allAvailableTracks: List<AudioTrack>,
        limit: Int,
        seed: Long
    ): List<AudioTrack> {
        if (allAvailableTracks.isEmpty()) return emptyList()
        val random = Random(seed)
        val normalizedPool = allAvailableTracks.groupBy { track ->
            normalizeTrackLookupKey(track.title, track.artist)
        }

        val matched = globalTrendCatalog
            .shuffled(random)
            .flatMap { seedTrack ->
                normalizedPool[normalizeTrackLookupKey(seedTrack.title, seedTrack.artist)].orEmpty()
            }
            .distinctBy { it.id }

        if (matched.size >= limit) return matched.take(limit)

        val fallback = allAvailableTracks
            .asSequence()
            .filter { track -> track.id !in matched.map { it.id }.toSet() }
            .shuffled(random)
            .take(limit - matched.size)
            .toList()

        return (matched + fallback).take(limit)
    }

    fun inferGenresForTrack(track: AudioTrack): Set<String> {
        val normalizedText = listOf(track.title, track.artist, track.album)
            .joinToString(" ")
            .lowercase()
        return inferGenresFromText(normalizedText)
    }

    fun rankTracksForDiscovery(
        candidates: List<AudioTrack>,
        topGenres: Map<String, Double>,
        artistFamiliarity: Map<String, Double>,
        heardGenres: Set<String>,
        seed: Long,
        discoveryWeightMultiplier: Double = 1.0,
        limit: Int
    ): List<AudioTrack> {
        if (candidates.isEmpty()) return emptyList()

        val random = Random(seed)
        val normalizedTopGenres = normalizeWeights(topGenres)
        val normalizedArtistFamiliarity = normalizeWeights(artistFamiliarity)

        val scored = candidates.map { track ->
            val genres = inferGenresForTrack(track)
            val normalizedArtist = ArtistUtils.getBaseArtist(track.artist).trim().lowercase()

            val genreScore = if (genres.isEmpty()) {
                0.15
            } else {
                genres.maxOfOrNull { genre -> normalizedTopGenres[genre] ?: 0.0 } ?: 0.0
            }

            val artistScore = normalizedArtistFamiliarity[normalizedArtist] ?: 0.0
            val unseenGenreBoost = genres.any { it !in heardGenres }
            val discoveryScore = when {
                unseenGenreBoost -> 1.0
                artistScore == 0.0 -> 0.65
                else -> 0.25
            }

            val jitter = deterministicJitter(track.id, seed)
            val totalScore =
                (GENRE_WEIGHT * genreScore) +
                    (ARTIST_WEIGHT * artistScore) +
                    ((DISCOVERY_WEIGHT * discoveryWeightMultiplier) * discoveryScore) +
                    jitter

            ScoredDiscoveryTrack(
                track = track,
                genres = genres,
                score = totalScore
            )
        }.sortedByDescending { it.score }

        val unseenGenrePool = scored
            .filter { scoredTrack -> scoredTrack.genres.any { it !in heardGenres } }
            .map { it.track }
            .distinctBy { it.id }
            .shuffled(random)

        val injectionCount = when {
            limit <= 6 -> 2
            else -> 3
        }

        val injected = unseenGenrePool.take(injectionCount)
        val injectedIds = injected.map { it.id }.toSet()

        val ordered = buildList {
            addAll(injected)
            addAll(
                scored
                    .map { it.track }
                    .filter { track -> track.id !in injectedIds }
            )
        }

        return ordered
            .distinctBy { it.id }
            .take(limit)
    }

    private fun normalizeWeights(weights: Map<String, Double>): Map<String, Double> {
        if (weights.isEmpty()) return emptyMap()
        val maxValue = weights.values.maxOrNull()?.takeIf { it > 0.0 } ?: return emptyMap()
        return weights.mapValues { (_, value) -> (value / maxValue).coerceIn(0.0, 1.0) }
    }

    private fun inferGenresFromText(normalizedText: String): Set<String> {
        val inferred = buildSet {
            GENRE_KEYWORDS.forEach { (genre, keywords) ->
                if (keywords.any { keyword -> normalizedText.contains(keyword) }) {
                    add(genre)
                }
            }
        }
        return if (inferred.isEmpty()) setOf(DEFAULT_DISCOVERY_GENRE) else inferred
    }

    private fun deterministicJitter(trackId: String, seed: Long): Double {
        val combinedSeed = seed xor trackId.hashCode().toLong()
        val random = Random(combinedSeed)
        return random.nextDouble(-0.05, 0.05)
    }

    private fun loadGlobalTrendCatalog(): List<GlobalTrendTrackSeed> {
        return runCatching {
            val json = context.resources.openRawResource(R.raw.global_trending_tracks)
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<List<GlobalTrendTrackSeed>>() {}.type
            Gson().fromJson<List<GlobalTrendTrackSeed>>(json, type).orEmpty()
        }.getOrElse {
            Log.e("MediaRepo", "Failed to load global trend catalog: ${it.message}")
            emptyList()
        }
    }

    private fun normalizeTrackLookupKey(title: String, artist: String): String {
        return "${title.trim().lowercase()}::${ArtistUtils.getBaseArtist(artist).trim().lowercase()}"
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
                            fallbackTrack?.album?.images?.firstOrNull()?.url
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

    companion object {
        private const val DEFAULT_DISCOVERY_GENRE = "general"
        private const val GENRE_WEIGHT = 0.40
        private const val ARTIST_WEIGHT = 0.30
        private const val DISCOVERY_WEIGHT = 0.30

        val DISCOVERY_LAST_GENERATION_KEY = longPreferencesKey("discovery_last_generation_at")

        private val GENRE_KEYWORDS: Map<String, Set<String>> = mapOf(
            "hip hop" to setOf("hip hop", "rap", "drill", "trap", "808"),
            "electronic" to setOf("edm", "electro", "house", "techno", "trance", "synth"),
            "rock" to setOf("rock", "metal", "punk", "grunge", "alt rock"),
            "pop" to setOf("pop", "radio", "anthem"),
            "rnb" to setOf("rnb", "soul", "neo soul"),
            "afrobeats" to setOf("afro", "afrobeats", "amapiano"),
            "lofi" to setOf("lofi", "chill", "ambient", "study"),
            "acoustic" to setOf("acoustic", "unplugged", "folk", "singer songwriter")
        )
    }
}