package com.tigerplayer.engine

import android.util.Log
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.remote.model.SpotifyTrack
import com.tigerplayer.data.repository.SpotifyPlaybackState
import com.tigerplayer.data.repository.SpotifyRepository
import com.tigerplayer.service.MediaControllerManager
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
    val sleepTimerState: StateFlow<SleepTimerState> = mediaControllerManager.sleepTimerState
    val spotifyPlaybackState: Flow<SpotifyPlaybackState?> = spotifyRepository.spotifyPlaybackState
    val spotifyReauthRequired: StateFlow<Boolean> = spotifyRepository.reauthRequired
    init {
        mediaControllerManager.onSleepTimerExpired = { pauseActiveTransport() }
    }
    private fun pauseActiveTransport() {
        val spotifyActive = spotifyRepository.spotifyPlaybackState.value?.isPlaying == true
        if (spotifyActive) spotifyRepository.pause() else mediaControllerManager.fadeOutAndPause()
    }

    fun setSleepTimerDuration(minutes: Int, fadeOutEnabled: Boolean = true) {
        mediaControllerManager.setSleepTimerDuration(minutes * 60_000L, fadeOutEnabled)
    }
    fun setSleepTimerEndOfTrack() = mediaControllerManager.setSleepTimerEndOfTrack()
    fun setSleepTimerEndOfQueue() = mediaControllerManager.setSleepTimerEndOfQueue()
    fun cancelSleepTimer() = mediaControllerManager.cancelSleepTimer()

    /**
     * Opens the Spotify App Remote session so [SpotifyRepository.isConnected] reflects reality.
     * Safe to call repeatedly - [SpotifyRepository.connect] is a no-op while already connected.
     */
    fun connectSpotifyRemote() = spotifyRepository.connect()

    /**
     * Opens the Spotify App Remote session only if a token is already present, for the cold-start
     * case where the user authenticated in a previous app session.
     */
    fun connectSpotifyRemoteIfAuthenticated() {
        if (spotifyRepository.isAuthenticated.value) spotifyRepository.connect()
    }

    /** Tears down the Spotify App Remote session and any cached remote playback state. */
    fun disconnectSpotifyRemote() = spotifyRepository.disconnect()


    // Resolve queue directly from MediaController so queue state is not coupled to library filtering.
    fun getQueueFlow(): Flow<List<AudioTrack>> {
        return mediaControllerManager.getQueueFlow()
    }

    fun getQueueSnapshotFlow(): Flow<MediaControllerManager.QueueSnapshot> {
        return mediaControllerManager.getQueueSnapshotFlow()
    }

    val spotifyRemoteTrack: Flow<AudioTrack?> = spotifyPlaybackState.map { it?.track }

    fun playTrack(track: AudioTrack, libraryTracks: List<AudioTrack>) {
        val isSpotifyTrack = track.id.startsWith("spotify:")
        if (isSpotifyTrack) {
            mediaControllerManager.pause()
            // Seed the placeholder with the metadata we already hold so the player never shows a
            // raw Spotify id at 0:00 while App Remote connects (issue #169).
            spotifyRepository.playUri(track.id, track)
        } else {
            spotifyRepository.pause()
            val startIndex = libraryTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            mediaControllerManager.setPlaylistAndPlay(
                libraryTracks,
                startIndex
            )
        }
    }

    /** Plays a Spotify track whose full metadata is already known (e.g. a playlist listing row). */
    fun playSpotifyTrack(track: SpotifyTrack) {
        mediaControllerManager.pause()
        spotifyRepository.playTrack(track)
    }

    /** Plays a Spotify playlist/album URI, using [displayName] for the interim placeholder. */
    fun playSpotifyCollection(uri: String, displayName: String) {
        mediaControllerManager.pause()
        spotifyRepository.playCollection(uri, displayName)
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
