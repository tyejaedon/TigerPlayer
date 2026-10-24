package com.tigerplayer.engine

import android.net.Uri
import android.util.Log
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.ArtistDetails
import com.tigerplayer.data.repository.LyricsRepository
import com.tigerplayer.data.repository.MediaDataRepository
import com.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import androidx.core.net.toUri
import com.tigerplayer.data.local.dao.TigerDao
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal const val ARTIST_PRESEED_CONCURRENCY = 3
private val ARTIST_PRESEED_TIMEOUT = 5.seconds
private val ARTIST_PRESEED_STAGGER = 150.milliseconds

private const val TAG = "MetadataEngine"

/**
 * Manages fetching, caching, and observing artist metadata, album artwork, lyrics,
 * and profile pre-seeding for tracks and artists.
 *
 * `@Singleton` ensures artist cache observation and metadata requests share a consistent state.
 */
@Singleton
class MetadataEngine @Inject constructor(
    private val mediaDataRepository: MediaDataRepository,
    private val lyricsRepository: LyricsRepository,
    private val tigerDao: TigerDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Reactive state flow mapping normalized artist names to their cached [ArtistDetails] from Room. */
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
    /** Observable state flow representing the lyrics of the currently playing track. */
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()

    private val _currentArtistImageUrl = MutableStateFlow<String?>(null)
    /** Observable state flow representing the artist image URL for the currently playing track. */
    val currentArtistImageUrl: StateFlow<String?> = _currentArtistImageUrl.asStateFlow()

    /**
     * Observes detailed artist profile data including local play counts and listening share percentage.
     */
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
                minutesListened = minutesListened,
                listeningSharePercent = listeningSharePercent
            )
        }.distinctUntilChanged()
    }

    /** Clears active track metadata (lyrics and artist image URL). */
    fun clearTrackMetadata() {
        _currentLyrics.value = null
        _currentArtistImageUrl.value = null
    }

    /** Fetches and populates metadata (artist image and lyrics) for the given [track]. */
    @OptIn(FlowPreview::class)
    suspend fun fetchTrackMetadata(track: AudioTrack) {
        coroutineScope {
            launch {
                mediaDataRepository.getArtistDetails(track.artist)
                    .filter { it.imageUrl != null }
                    .take(1)
                    .timeout(3000.milliseconds)
                    .catch {
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

    /** Fetches an artist profile by name. */
    suspend fun fetchArtistProfile(artistName: String) {
        mediaDataRepository.getArtistDetails(artistName).take(2).collect()
    }

    /** Non-destructively refreshes artist profiles for the specified list of artist names. */
    @OptIn(FlowPreview::class)
    suspend fun forceRefreshArtistProfiles(artistNames: List<String>) {
        val normalizedNames = artistNames
            .map { ArtistUtils.getBaseArtist(it).trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalizedNames.isEmpty()) return

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

    /** Fetches high-resolution Spotify album artwork URI for a track. */
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

    /** Pre-seeds the artist cache in parallel with concurrency limiting for a list of tracks. */
    @OptIn(FlowPreview::class)
    suspend fun preSeedArtistCache(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val uniqueArtists = tracks
            .map { ArtistUtils.getBaseArtist(it.artist).trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val semaphore = Semaphore(ARTIST_PRESEED_CONCURRENCY)

        coroutineScope {
            uniqueArtists.forEachIndexed { index, name ->
                launch {
                    delay((index % ARTIST_PRESEED_CONCURRENCY) * ARTIST_PRESEED_STAGGER)
                    semaphore.withPermit {
                        try {
                            withTimeout(ARTIST_PRESEED_TIMEOUT) {
                                mediaDataRepository.getArtistDetails(name)
                                    .filter { it.imageUrl != null }
                                    .take(1)
                                    .collect()
                            }
                        } catch (e: TimeoutCancellationException) {
                            Log.w(TAG, "Pre-seed failed: ${e.message}")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Pre-seed failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
