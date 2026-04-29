package com.example.tigerplayer.engine

import android.net.Uri
import android.util.Log
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.data.repository.LyricsRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import androidx.core.net.toUri

class MetadataEngine @Inject constructor(
    private val mediaDataRepository: MediaDataRepository,
    private val lyricsRepository: LyricsRepository
) {
    private val _artistDetails = MutableStateFlow<Map<String, ArtistDetails>>(emptyMap())
    val artistDetails: StateFlow<Map<String, ArtistDetails>> = _artistDetails.asStateFlow()

    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()

    private val _currentArtistImageUrl = MutableStateFlow<String?>(null)
    val currentArtistImageUrl: StateFlow<String?> = _currentArtistImageUrl.asStateFlow()

    fun clearTrackMetadata() {
        _currentLyrics.value = null
        _currentArtistImageUrl.value = null
    }

    suspend fun fetchTrackMetadata(track: AudioTrack) {
        val normalizedKey = ArtistUtils.getBaseArtist(track.artist).lowercase().trim()

        // Fetch Artist Info
        mediaDataRepository.getArtistDetails(track.artist).collect { details ->
            _artistDetails.update { it + (normalizedKey to details) }
            _currentArtistImageUrl.value = details.imageUrl
        }

        // Fetch Lyrics
        lyricsRepository.getLyrics(track).collect { lyrics ->
            _currentLyrics.value = lyrics
        }
    }

    suspend fun fetchArtistProfile(artistName: String) {
        val baseName = ArtistUtils.getBaseArtist(artistName).trim()
        val cacheKey = baseName.lowercase()

        if (_artistDetails.value.containsKey(cacheKey)) return

        mediaDataRepository.getArtistDetails(artistName).collect { details ->
            _artistDetails.update { currentMap ->
                currentMap + (cacheKey to details)
            }
        }
    }

    suspend fun fetchSpotifyHighResArt(title: String, artist: String): Uri? {
        var highResUrl: String? = null
        try {
            // 🔥 THE FIX: We use collect instead of firstOrNull() to prevent the Coroutine system
            // from throwing an AbortFlowException and crashing the Repository's try/catch block.
            mediaDataRepository.getHighResAlbumArt(title, artist).collect { url ->
                if (highResUrl == null) highResUrl = url
            }
        } catch (e: Exception) {
            Log.e("MetadataEngine", "Artwork fetch failed: ${e.message}")
        }
        return highResUrl?.toUri()
    }

    @Suppress("unused")
    suspend fun preSeedArtistCache(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val uniqueArtists = tracks.map { ArtistUtils.getBaseArtist(it.artist).trim() }.distinct()

        uniqueArtists.forEach { name ->
            val cacheKey = name.lowercase()

            if (!_artistDetails.value.containsKey(cacheKey)) {
                try {
                    // 🔥 THE FIX: Safe collection to prevent AbortFlowException crashes
                    var fetchedDetails: ArtistDetails? = null
                    mediaDataRepository.getArtistDetails(name).collect { d ->
                        if (fetchedDetails == null) fetchedDetails = d
                    }

                    if (fetchedDetails?.imageUrl != null) {
                        _artistDetails.update { it + (cacheKey to fetchedDetails!!) }
                    }
                } catch (e: Exception) {
                    Log.w("MetadataEngine", "Pre-seed failed for $name: ${e.message}")
                }
            }
        }
    }
}