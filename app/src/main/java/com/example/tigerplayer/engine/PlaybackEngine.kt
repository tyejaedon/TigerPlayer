package com.example.tigerplayer.engine

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
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
        return mediaControllerManager.mediaControllerState
            .onStart { emit(Unit) }
            .map {
                val controller = mediaControllerManager.mediaController ?: return@map emptyList()
                List(controller.mediaItemCount) { i ->
                    fallbackTrackFromMediaItem(controller.getMediaItemAt(i))
                }
            }
    }

    private fun fallbackTrackFromMediaItem(mediaItem: MediaItem): AudioTrack {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val fallbackUri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY
        val mimeType = extras?.getString(MediaControllerManager.META_MIME_TYPE).orEmpty()

        return AudioTrack(
            id = mediaItem.mediaId,
            title = metadata.title?.toString() ?: "Unknown",
            artist = metadata.artist?.toString() ?: "Unknown Artist",
            album = metadata.albumTitle?.toString() ?: "Unknown Album",
            uri = fallbackUri,
            artworkUri = metadata.artworkUri ?: Uri.EMPTY,
            durationMs = extras?.getLong(MediaControllerManager.META_DURATION_MS, 0L) ?: 0L,
            mimeType = mimeType.ifBlank { "audio/unknown" },
            isLocal = extras?.getBoolean(MediaControllerManager.META_IS_LOCAL, false) ?: false,
            isRemote = extras?.getBoolean(MediaControllerManager.META_IS_REMOTE, fallbackUri != Uri.EMPTY)
                ?: (fallbackUri != Uri.EMPTY),
            bitrate = extras?.getInt(MediaControllerManager.META_BITRATE, 0) ?: 0,
            sampleRate = extras?.getInt(MediaControllerManager.META_SAMPLE_RATE, 0) ?: 0,
            trackNumber = extras?.getInt(MediaControllerManager.META_TRACK_NUMBER, 0) ?: 0,
            serverPath = extras?.getString(MediaControllerManager.META_SERVER_PATH),
            path = extras?.getString(MediaControllerManager.META_PATH),
            year = extras?.getString(MediaControllerManager.META_YEAR),
            dateAdded = extras?.getLong(MediaControllerManager.META_DATE_ADDED, 0L) ?: 0L,
            isLiked = extras?.getBoolean(MediaControllerManager.META_IS_LIKED, false) ?: false
        )
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
        val controller = mediaControllerManager.mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekTo(index, androidx.media3.common.C.TIME_UNSET)
            controller.play()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        mediaControllerManager.moveQueueItem(fromIndex, toIndex)
    }
}