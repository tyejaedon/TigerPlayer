package com.tigerplayer.engine

import android.util.Log
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.remote.model.SpotifyTrack
import com.tigerplayer.data.repository.SpotifyPlaybackState
import com.tigerplayer.data.repository.SpotifyRepository
import com.tigerplayer.service.MediaControllerManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Orchestrates audio playback control across local playback (via [MediaControllerManager])
 * and Spotify remote playback (via [SpotifyRepository]).
 *
 * Exposes observable flows for playback state, current position, queue, and sleep timer.
 */
class PlaybackEngine @Inject constructor(
    private val mediaControllerManager: MediaControllerManager,
    private val spotifyRepository: SpotifyRepository
) {

    /** Observable flow indicating whether media is currently playing. */
    val isPlaying: Flow<Boolean> = mediaControllerManager.isPlaying
    /** Observable flow representing the current playback position in milliseconds. */
    val currentPosition: Flow<Long> = mediaControllerManager.currentPosition
    /** Observable flow representing the media ID of the currently playing track. */
    val currentMediaId: Flow<String> = mediaControllerManager.currentMediaId
    /** Observable flow indicating whether shuffle mode is enabled. */
    val shuffleModeEnabled: Flow<Boolean> = mediaControllerManager.shuffleModeEnabled
    /** Observable flow representing the repeat mode. */
    val repeatMode: Flow<Int> = mediaControllerManager.repeatMode
    /** State flow representing the current sleep timer state. */
    val sleepTimerState: StateFlow<SleepTimerState> = mediaControllerManager.sleepTimerState
    /** Observable flow representing Spotify playback state. */
    val spotifyPlaybackState: Flow<SpotifyPlaybackState?> = spotifyRepository.spotifyPlaybackState
    /** State flow indicating whether Spotify re-authentication is required. */
    val spotifyReauthRequired: StateFlow<Boolean> = spotifyRepository.reauthRequired

    init {
        mediaControllerManager.onSleepTimerExpired = { pauseActiveTransport() }
    }

    /** Pauses the active transport (Spotify or local media controller). */
    private fun pauseActiveTransport() {
        val spotifyActive = spotifyRepository.spotifyPlaybackState.value?.isPlaying == true
        if (spotifyActive) spotifyRepository.pause() else mediaControllerManager.fadeOutAndPause()
    }

    /** Sets sleep timer duration in minutes with optional fade out. */
    fun setSleepTimerDuration(minutes: Int, fadeOutEnabled: Boolean = true) {
        mediaControllerManager.setSleepTimerDuration(minutes * 60_000L, fadeOutEnabled)
    }

    /** Arms the sleep timer to trigger at the end of the current track. */
    fun setSleepTimerEndOfTrack() = mediaControllerManager.setSleepTimerEndOfTrack()

    /** Arms the sleep timer to trigger at the end of the queue. */
    fun setSleepTimerEndOfQueue() = mediaControllerManager.setSleepTimerEndOfQueue()

    /** Cancels the active sleep timer. */
    fun cancelSleepTimer() = mediaControllerManager.cancelSleepTimer()

    /** Opens the Spotify App Remote session. */
    fun connectSpotifyRemote() = spotifyRepository.connect()

    /** Opens the Spotify App Remote session if a valid token is already present. */
    fun connectSpotifyRemoteIfAuthenticated() {
        if (spotifyRepository.isAuthenticated.value) spotifyRepository.connect()
    }

    /** Disconnects the Spotify App Remote session. */
    fun disconnectSpotifyRemote() = spotifyRepository.disconnect()

    /** Returns the playback queue flow from the media controller. */
    fun getQueueFlow(): Flow<List<AudioTrack>> {
        return mediaControllerManager.getQueueFlow()
    }

    /** Returns the queue snapshot flow from the media controller. */
    fun getQueueSnapshotFlow(): Flow<MediaControllerManager.QueueSnapshot> {
        return mediaControllerManager.getQueueSnapshotFlow()
    }

    /** Observable flow of the current Spotify remote track. */
    val spotifyRemoteTrack: Flow<AudioTrack?> = spotifyPlaybackState.map { it?.track }

    /** Plays an audio track, routing to Spotify or local media controller based on ID. */
    fun playTrack(track: AudioTrack, libraryTracks: List<AudioTrack>) {
        val isSpotifyTrack = track.id.startsWith("spotify:")
        if (isSpotifyTrack) {
            mediaControllerManager.pause()
            spotifyRepository.playUri(track.id, track)
        } else {
            mediaControllerManager.pause()
            val startIndex = libraryTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            mediaControllerManager.setPlaylistAndPlay(
                libraryTracks,
                startIndex
            )
        }
    }

    /** Plays a Spotify track whose metadata is fully known. */
    fun playSpotifyTrack(track: SpotifyTrack) {
        mediaControllerManager.pause()
        spotifyRepository.playTrack(track)
    }

    /** Plays a Spotify collection URI. */
    fun playSpotifyCollection(uri: String, displayName: String) {
        mediaControllerManager.pause()
        spotifyRepository.playCollection(uri, displayName)
    }

    /** Toggles play/pause state for the active transport. */
    fun togglePlayPause(currentTrack: AudioTrack?, isCurrentlyPlaying: Boolean) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            if (isCurrentlyPlaying) spotifyRepository.pause() else spotifyRepository.resume()
        } else {
            if (isCurrentlyPlaying) mediaControllerManager.pause() else mediaControllerManager.resume()
        }
    }

    /** Seeks to the specified position in milliseconds. */
    fun seekTo(position: Long, currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.seekTo(position) else mediaControllerManager.seekTo(position)
    }

    /** Toggles shuffle mode. */
    fun toggleShuffle(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.toggleShuffle()
        } else {
            mediaControllerManager.toggleShuffleMode()
        }
    }

    /** Toggles repeat mode. */
    fun toggleRepeat(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) {
            spotifyRepository.toggleRepeat()
        } else {
            mediaControllerManager.toggleRepeatMode()
        }
    }

    /** Sets the active playlist and starts playback from [startIndex]. */
    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int = 0) {
        mediaControllerManager.setPlaylistAndPlay(tracks, startIndex)
    }

    /** Skips to the next track in the queue. */
    fun skipToNext(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.skipNext() else mediaControllerManager.skipToNext()
    }

    /** Skips to the previous track in the queue. */
    fun skipToPrevious(currentTrack: AudioTrack?) {
        val isSpotify = currentTrack?.id?.startsWith("spotify:") == true
        if (isSpotify) spotifyRepository.skipPrevious() else mediaControllerManager.skipToPrevious()
    }

    /** Inserts a track to play next in the local queue. */
    fun playNext(track: AudioTrack) {
        val isSpotify = track.id.startsWith("spotify:")
        if (isSpotify) {
            Log.w("TigerPlayer", "Spotify queueing requires extended API access.")
        } else {
            mediaControllerManager.playNext(track)
        }
    }

    /** Adds a track to the end of the local queue. */
    fun addToQueue(track: AudioTrack) {
        val isSpotify = track.id.startsWith("spotify:")
        if (isSpotify) {
            Log.w("TigerPlayer", "Spotify queueing requires extended API access.")
        } else {
            mediaControllerManager.addToQueue(track)
        }
    }

    /** Removes a queue item at the specified [index]. */
    fun removeFromQueue(index: Int) {
        mediaControllerManager.removeFromQueueAt(index)
    }

    /** Clears the playback queue. */
    fun clearQueue() {
        mediaControllerManager.clearQueue()
    }

    /** Plays a specific queue item by index. */
    fun playQueueItem(index: Int) {
        mediaControllerManager.playQueueItem(index)
    }

    /** Moves a queue item from [fromIndex] to [toIndex]. */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        mediaControllerManager.moveQueueItem(fromIndex, toIndex)
    }
}
