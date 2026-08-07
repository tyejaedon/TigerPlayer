package com.example.tigerplayer.engine

import android.util.Log
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.SpotifyPlaybackState
import com.example.tigerplayer.data.repository.SpotifyRepository
import com.example.tigerplayer.service.MediaControllerManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class PlaybackEngine @Inject constructor(
    private val mediaControllerManager: MediaControllerManager,
    private val spotifyRepository: SpotifyRepository
) {

    val isPlaying: Flow<Boolean> = mediaControllerManager.isPlaying
    val currentPosition: Flow<Long> = mediaControllerManager.currentPosition
    val currentMediaId: Flow<String> = mediaControllerManager.currentMediaId
    val shuffleModeEnabled: Flow<Boolean> = mediaControllerManager.shuffleModeEnabled
    val repeatMode: Flow<Int> = mediaControllerManager.repeatMode
    val spotifyPlaybackState: Flow<SpotifyPlaybackState?> = spotifyRepository.spotifyPlaybackState

    // Resolve queue directly from MediaController so queue state is not coupled to library filtering.
    fun getQueueFlow(): Flow<List<AudioTrack>> {
        return mediaControllerManager.getQueueFlow()
    }

    val spotifyRemoteTrack: Flow<AudioTrack?> = spotifyPlaybackState.map { it?.track }

    fun playTrack(track: AudioTrack, libraryTracks: List<AudioTrack>) {
        val isSpotifyTrack = track.id.startsWith("spotify:")
        if (isSpotifyTrack) {
            mediaControllerManager.pause()
            spotifyRepository.playUri(track.id)
        } else {
            spotifyRepository.pause()
            val startIndex = libraryTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            mediaControllerManager.setPlaylistAndPlay(
                libraryTracks,
                startIndex
            )
        }
    }

    fun playSpotifyUri(uri: String) {
        mediaControllerManager.pause()
        spotifyRepository.playUri(uri)
    }

    fun togglePlayPause(currentTrack: AudioTrack?, isCurrentlyPlaying: Boolean) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            if (isCurrentlyPlaying) spotifyRepository.pause() else spotifyRepository.resume()
        } else {
            if (isCurrentlyPlaying) mediaControllerManager.pause() else mediaControllerManager.resume()
        }
    }

    fun seekTo(position: Long, currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.seekTo(position) else mediaControllerManager.seekTo(position)
    }

    fun toggleShuffle(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.toggleShuffle()
        } else {
            mediaControllerManager.toggleShuffleMode()
        }
    }

    fun toggleRepeat(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.toggleRepeat()
        } else {
            mediaControllerManager.toggleRepeatMode()
        }
    }

    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int = 0) {
        mediaControllerManager.setPlaylistAndPlay(tracks, startIndex)
    }

    fun skipToNext(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.skipNext() else mediaControllerManager.skipToNext()
    }

    fun skipToPrevious(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.skipPrevious() else mediaControllerManager.skipToPrevious()
    }

    fun playNext(track: AudioTrack) {
        val isSpotify = track.id.startsWith("spotify:")
        if (isSpotify) {
            Log.w("TigerPlayer", "Spotify queueing requires extended API access.")
        } else {
            mediaControllerManager.playNext(track)
        }
    }

    fun addToQueue(track: AudioTrack) {
        val isSpotify = track.id.startsWith("spotify:")
        if (isSpotify) {
            Log.w("TigerPlayer", "Spotify queueing requires extended API access.")
        } else {
            mediaControllerManager.addToQueue(track)
        }
    }

    fun removeFromQueue(index: Int) {
        mediaControllerManager.removeFromQueueAt(index)
    }

    fun playQueueItem(index: Int) {
        mediaControllerManager.playQueueItem(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        mediaControllerManager.moveQueueItem(fromIndex, toIndex)
    }
}