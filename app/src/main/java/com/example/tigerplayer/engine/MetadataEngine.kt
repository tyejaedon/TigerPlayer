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
        val highResUrl = mediaDataRepository.getHighResAlbumArt(title, artist).firstOrNull()
        return highResUrl?.let { Uri.parse(it) }
    }

    suspend fun preSeedArtistCache(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val uniqueArtists = tracks.map { ArtistUtils.getBaseArtist(it.artist) }.distinct()
        uniqueArtists.forEach { name ->
            val details = mediaDataRepository.getArtistDetails(name).firstOrNull()
            if (details?.imageUrl != null) {
                _artistDetails.update { it + (name to details) }
            }
        }
    }


}