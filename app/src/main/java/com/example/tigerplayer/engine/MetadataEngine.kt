package com.example.tigerplayer.engine

import android.net.Uri
import android.util.Log
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.data.repository.LyricsRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import androidx.core.net.toUri
import com.example.tigerplayer.data.local.dao.TigerDao
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class MetadataEngine @Inject constructor(
    private val mediaDataRepository: MediaDataRepository,
    private val lyricsRepository: LyricsRepository,
    private val tigerDao: TigerDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 🔥 THE FIX: artistDetails is now reactive to the database ground truth.
    // This ensures that images fetched on the Artist Screen appear in the Constellation instantly.
    val artistDetails: StateFlow<Map<String, ArtistDetails>> = tigerDao.getAllArtistCache()
        .map { list ->
            list.associate { entity ->
                entity.artistName to ArtistDetails(
                    name = entity.artistName,
                    imageUrl = entity.imageUrl,
                    bio = entity.bio,
                    genres = entity.genres?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()

    private val _currentArtistImageUrl = MutableStateFlow<String?>(null)
    val currentArtistImageUrl: StateFlow<String?> = _currentArtistImageUrl.asStateFlow()

    fun observeArtistProfile(artistName: String): Flow<ArtistDetails?> {
        val normalizedName = ArtistUtils.getBaseArtist(artistName).trim()
        val normalizedKey = normalizedName.lowercase()

        return combine(
            artistDetails.map { it[normalizedKey] },
            tigerDao.observeArtistStats(normalizedName),
            tigerDao.getTotalListeningTimeMs(0L)
        ) { cachedProfile, stats, lifetimeListeningMs ->
            if (cachedProfile == null && stats == null) {
                return@combine null
            }

            val artistListeningMs = (stats?.totalListeningMs ?: 0L).coerceAtLeast(0L)
            val minutesListened = (artistListeningMs / 60_000L).toInt()
            val listeningSharePercent = if (artistListeningMs > 0L && lifetimeListeningMs > 0L) {
                ((artistListeningMs.toFloat() / lifetimeListeningMs.toFloat()) * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }

            ArtistDetails(
                name = cachedProfile?.name ?: stats?.artistName ?: normalizedName,
                imageUrl = cachedProfile?.imageUrl ?: stats?.imageUrl,
                bio = cachedProfile?.bio,
                genres = cachedProfile?.genres ?: emptyList(),
                localPlayCount = stats?.playCount ?: 0,
                popularity = cachedProfile?.popularity ?: 0,
                minutesListened = minutesListened,
                listeningSharePercent = listeningSharePercent
            )
        }.distinctUntilChanged()
    }

    fun clearTrackMetadata() {
        _currentLyrics.value = null
        _currentArtistImageUrl.value = null
    }

    @OptIn(FlowPreview::class)
    suspend fun fetchTrackMetadata(track: AudioTrack) {
        coroutineScope {
            launch {
                // Observe for at least one emission that has an image, or time out.
                mediaDataRepository.getArtistDetails(track.artist)
                    .filter { it.imageUrl != null }
                    .take(1)
                    .timeout(3000.milliseconds)
                    .catch { 
                        // If timeout or no image, take whatever the first emission was (cache)
                        try {
                            emit(mediaDataRepository.getArtistDetails(track.artist).first())
                        } catch (e: Exception) { /* Silent fail */ }
                    }
                    .collect { details ->
                        _currentArtistImageUrl.value = details.imageUrl
                    }
            }

            launch {
                lyricsRepository.getLyrics(track).take(1).collect { lyrics ->
                    _currentLyrics.value = lyrics
                }
            }
        }
    }

    suspend fun fetchArtistProfile(artistName: String) {
        // We don't need to update a manual map anymore, the DB observer handles it.
        mediaDataRepository.getArtistDetails(artistName).take(2).collect()
    }

    /**
     * 🔥 THE FIX 2: Non-destructive refresh.
     * We no longer clear the whole cache. We just trigger fresh fetches for requested artists.
     */
    @OptIn(FlowPreview::class)
    suspend fun forceRefreshArtistProfiles(artistNames: List<String>) {
        val normalizedNames = artistNames
            .map { ArtistUtils.getBaseArtist(it).trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalizedNames.isEmpty()) return

        // Fetch in parallel for better constellation performance
        coroutineScope {
            normalizedNames.forEach { name ->
                launch {
                    mediaDataRepository.getArtistDetails(name)
                        .filter { it.imageUrl != null }
                        .take(1)
                        .timeout(4000.milliseconds)
                        .catch { 
                            try {
                                emit(mediaDataRepository.getArtistDetails(name).first())
                            } catch (e: Exception) { /* Silent fail */ }
                        }
                        .collect()
                }
            }
        }
    }

    suspend fun fetchSpotifyHighResArt(title: String, artist: String, album: String): Uri? {
        var highResUrl: String? = null
        try {
            mediaDataRepository.getHighResAlbumArt(title, artist, album).collect { url ->
                if (highResUrl == null) highResUrl = url
            }
        } catch (e: Exception) {
            Log.e("MetadataEngine", "Artwork fetch failed: ${e.message}")
        }
        return highResUrl?.toUri()
    }

    @OptIn(FlowPreview::class)
    suspend fun preSeedArtistCache(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val uniqueArtists = tracks.map { ArtistUtils.getBaseArtist(it.artist).trim() }.distinct()

        coroutineScope {
            uniqueArtists.forEach { name ->
                launch {
                    try {
                        // Just trigger the flow, DB updates will propagate via artistDetails StateFlow
                        mediaDataRepository.getArtistDetails(name)
                            .filter { it.imageUrl != null }
                            .take(1)
                            .timeout(2000.milliseconds)
                            .collect()
                    } catch (e: Exception) {
                        Log.w("MetadataEngine", "Pre-seed failed for $name: ${e.message}")
                    }
                }
            }
        }
    }
}
