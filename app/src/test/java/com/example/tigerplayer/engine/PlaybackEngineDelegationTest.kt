package com.example.tigerplayer.engine

import android.util.Log
import androidx.media3.common.Player
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.SpotifyRepository
import com.example.tigerplayer.service.MediaControllerManager
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.After

class PlaybackEngineDelegationTest {

    private lateinit var mediaControllerManager: MediaControllerManager
    private lateinit var spotifyRepository: SpotifyRepository
    private lateinit var playbackEngine: PlaybackEngine

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0

        mediaControllerManager = mockk(relaxed = true)
        spotifyRepository = mockk(relaxed = true)

        every { mediaControllerManager.isPlaying } returns MutableStateFlow(false)
        every { mediaControllerManager.currentPosition } returns MutableStateFlow(0L)
        every { mediaControllerManager.currentMediaId } returns MutableStateFlow("")
        every { mediaControllerManager.shuffleModeEnabled } returns MutableStateFlow(false)
        every { mediaControllerManager.repeatMode } returns MutableStateFlow(Player.REPEAT_MODE_OFF)
        every { mediaControllerManager.mediaControllerState } returns MutableSharedFlow(extraBufferCapacity = 1)
        every { spotifyRepository.currentSpotifyTrack } returns MutableStateFlow("Not Playing")

        playbackEngine = PlaybackEngine(
            mediaControllerManager = mediaControllerManager,
            spotifyRepository = spotifyRepository
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun playTrack_routes_spotify_to_spotifyRepository_and_pauses_local() {
        val spotifyTrack = track(id = "spotify:track:42")

        playbackEngine.playTrack(spotifyTrack, libraryTracks = emptyList())

        verify(exactly = 1) { mediaControllerManager.pause() }
        verify(exactly = 1) { spotifyRepository.playUri("spotify:track:42") }
        verify(exactly = 0) { spotifyRepository.pause() }
        verify(exactly = 0) { mediaControllerManager.setPlaylistAndPlay(any(), any()) }
    }

    @Test
    fun playTrack_routes_local_to_mediaController_and_pauses_spotify() {
        val first = track(id = "local_1")
        val second = track(id = "local_2")

        playbackEngine.playTrack(second, libraryTracks = listOf(first, second))

        verify(exactly = 1) { spotifyRepository.pause() }
        verify(exactly = 1) { mediaControllerManager.setPlaylistAndPlay(listOf(first, second), 1) }
        verify(exactly = 0) { mediaControllerManager.pause() }
        verify(exactly = 0) { spotifyRepository.playUri(any()) }
    }

    @Test
    fun toggleShuffle_routes_by_track_target() {
        val spotifyTrack = track(id = "spotify:track:1")
        val localTrack = track(id = "local_1")

        playbackEngine.toggleShuffle(spotifyTrack)
        playbackEngine.toggleShuffle(localTrack)

        verify(exactly = 1) { spotifyRepository.toggleShuffle() }
        verify(exactly = 1) { mediaControllerManager.toggleShuffleMode() }
    }

    @Test
    fun toggleRepeat_routes_by_track_target() {
        val spotifyTrack = track(id = "spotify:track:1")
        val localTrack = track(id = "local_1")

        playbackEngine.toggleRepeat(spotifyTrack)
        playbackEngine.toggleRepeat(localTrack)

        verify(exactly = 1) { spotifyRepository.toggleRepeat() }
        verify(exactly = 1) { mediaControllerManager.toggleRepeatMode() }
    }

    @Test
    fun seekTo_routes_by_track_target() {
        val spotifyTrack = track(id = "spotify:track:1")
        val localTrack = track(id = "local_1")

        playbackEngine.seekTo(55_000L, spotifyTrack)
        playbackEngine.seekTo(10_000L, localTrack)

        verify(exactly = 1) { spotifyRepository.seekTo(55_000L) }
        verify(exactly = 1) { mediaControllerManager.seekTo(10_000L) }
    }

    @Test
    fun togglePlayPause_routes_by_track_target() {
        val spotifyTrack = track(id = "spotify:track:1")
        val localTrack = track(id = "local_1")

        playbackEngine.togglePlayPause(spotifyTrack, isCurrentlyPlaying = true)
        playbackEngine.togglePlayPause(localTrack, isCurrentlyPlaying = false)

        verify(exactly = 1) { spotifyRepository.pause() }
        verify(exactly = 1) { mediaControllerManager.resume() }
    }

    @Test
    fun addToQueue_ignores_spotify_tracks() {
        val spotifyTrack = track(id = "spotify:track:1")

        playbackEngine.addToQueue(spotifyTrack)

        verify(exactly = 0) { mediaControllerManager.addToQueue(any()) }
    }

    @Test
    fun addToQueue_routes_local_tracks_to_media_controller() {
        val localTrack = track(id = "local_1")

        playbackEngine.addToQueue(localTrack)

        verify(exactly = 1) { mediaControllerManager.addToQueue(localTrack) }
    }

    @Test
    fun addNextToQueue_ignores_spotify_tracks() {
        val spotifyTrack = track(id = "spotify:track:1")

        playbackEngine.addNextToQueue(spotifyTrack)

        verify(exactly = 0) { mediaControllerManager.playNext(any()) }
    }

    @Test
    fun addNextToQueue_routes_local_tracks_to_media_controller() {
        val localTrack = track(id = "local_1")

        playbackEngine.addNextToQueue(localTrack)

        verify(exactly = 1) { mediaControllerManager.playNext(localTrack) }
    }

    private fun track(id: String): AudioTrack {
        val track = mockk<AudioTrack>()
        every { track.id } returns id
        return track
    }
}





