package com.example.tigerplayer.engine

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresExtension
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.SpotifyRepository
import com.example.tigerplayer.service.MediaControllerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
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

    // Resolves the queue from the media controller

    fun getQueueFlow(libraryTracks: List<AudioTrack>): Flow<List<AudioTrack>> {
        return mediaControllerManager.mediaControllerState
            .onStart { emit(Unit) }
            .map {
                withContext(Dispatchers.Main) {
                    val controller = mediaControllerManager.mediaController ?: return@withContext emptyList()
                    (0 until controller.mediaItemCount).mapNotNull { i ->
                        val mediaId = controller.getMediaItemAt(i).mediaId
                        libraryTracks.find { it.id == mediaId }
                    }
                }
            }
    }

    // Listens for external Spotify playback to update UI with a remote track
    val spotifyRemoteTrack: Flow<AudioTrack?> = spotifyRepository.currentSpotifyTrack.map { spotifyInfo ->
        if (!spotifyInfo.isNullOrBlank() && spotifyInfo != "Not Playing" && spotifyInfo != "Paused / Stopped") {
            val parts = spotifyInfo.split(" • ")
            val title = parts.getOrNull(0) ?: "Unknown"
            val artist = parts.getOrNull(1) ?: "Spotify Cloud"

            AudioTrack(
                id = "spotify:remote",
                title = title,
                artist = artist,
                album = "Spotify Archive",
                durationMs = 0L,
                uri = Uri.EMPTY,
                trackNumber = 0,
                artworkUri = Uri.EMPTY,
                mimeType = "audio/spotify",
                isLocal = false,
                isRemote = true,
                bitrate = 0,
                sampleRate = 0,
                serverPath = null,
                path = null
            )
        } else {
            null
        }
    }

    fun playTrack(track: AudioTrack, libraryTracks: List<AudioTrack>) {
        val isSpotifyTrack = track.id.startsWith("spotify:")
        if (isSpotifyTrack) {
            mediaControllerManager.pause()
            spotifyRepository.playUri(track.id)
        } else {
            spotifyRepository.pause()
            mediaControllerManager.setPlaylistAndPlay(
                libraryTracks,
                libraryTracks.indexOf(track)
            )
        }
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
        val controller = mediaControllerManager.mediaController ?: return
        // Assuming createMediaItem is exposed by your MediaControllerManager or mapped here
        val mediaItems = tracks.map { mediaControllerManager.createMediaItem(it) }
        controller.setMediaItems(mediaItems, startIndex, androidx.media3.common.C.TIME_UNSET)
        controller.prepare()
        controller.play()
    }



    fun skipToNext(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.skipNext() else mediaControllerManager.skipToNext()
    }


    fun skipToPrevious(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.skipPrevious() else mediaControllerManager.skipToPrevious()
    }


    fun addToQueue(track: AudioTrack) {
        val isSpotify = track.id.startsWith("spotify:")
        if (isSpotify) {
            Log.w("TigerPlayer", "Spotify queueing requires extended API access.")
        } else {
            mediaControllerManager.addNextToQueue(track)
        }
    }


    fun removeFromQueue(track: AudioTrack) {
        mediaControllerManager.removeFromQueue(track.id)
    }


    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        mediaControllerManager.moveQueueItem(fromIndex, toIndex)
    }
}