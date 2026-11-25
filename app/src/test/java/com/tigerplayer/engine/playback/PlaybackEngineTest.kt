package com.tigerplayer.engine

import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.SpotifyPlaybackState
import com.tigerplayer.data.repository.SpotifyRepository
import com.tigerplayer.service.MediaControllerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test

class PlaybackEngineTest {

    private val mediaControllerManager = mockk<MediaControllerManager>(relaxed = true)
    private val spotifyRepository = mockk<SpotifyRepository>(relaxed = true)
    private val spotifyStateFlow = MutableStateFlow<SpotifyPlaybackState?>(null)

    private lateinit var engine: PlaybackEngine

    @Before
    fun setUp() {
        every { spotifyRepository.spotifyPlaybackState } returns spotifyStateFlow
        engine = PlaybackEngine(mediaControllerManager, spotifyRepository)
    }

    private fun mockTrack(id: String): AudioTrack = mockk<AudioTrack>(relaxed = true).apply {
        every { this@apply.id } returns id
    }

    @Test
    fun `playTrack for local track delegates directly to setPlaylistAndPlay without redundant pause`() {
        val first = mockTrack("local-1")
        val second = mockTrack("local-2")
        val library = listOf(first, second)

        engine.playTrack(second, library)

        verify(exactly = 0) { mediaControllerManager.pause() }
        verify(exactly = 0) { spotifyRepository.pause() }
        verify(exactly = 1) { mediaControllerManager.setPlaylistAndPlay(library, 1) }

        val activeSpotifyTrack = mockTrack("spotify:track:99")
        spotifyStateFlow.value = SpotifyPlaybackState(
            track = activeSpotifyTrack,
            isPlaying = true,
            positionMs = 1_000L
        )

        engine.playTrack(first, library)

        verify(exactly = 0) { mediaControllerManager.pause() }
        verify(exactly = 1) { spotifyRepository.pause() }
        verify(exactly = 1) { mediaControllerManager.setPlaylistAndPlay(library, 0) }
    }

    @Test
    fun `playTrack for spotify track pauses MediaControllerManager and plays Spotify URI`() {
        val spotifyTrack = mockTrack("spotify:track:42")

        engine.playTrack(spotifyTrack, listOf(spotifyTrack))

        verify(exactly = 1) { mediaControllerManager.pause() }
        verify(exactly = 1) { spotifyRepository.playUri("spotify:track:42", spotifyTrack) }
        verify(exactly = 0) { mediaControllerManager.setPlaylistAndPlay(any(), any()) }
    }
}
