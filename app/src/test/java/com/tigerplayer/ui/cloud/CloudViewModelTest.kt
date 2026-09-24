package com.tigerplayer.ui.cloud

import com.tigerplayer.data.remote.api.SpotifyApiService
import com.tigerplayer.data.repository.SpotifyAuthManager
import com.tigerplayer.data.repository.SpotifyRepository
import com.tigerplayer.data.repository.SpotifyAppRemoteClient
import com.tigerplayer.data.local.SpotifyPrefs
import com.tigerplayer.engine.PlaybackEngine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for issue #201: Spotify reauthorization UI element visibility and state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CloudViewModelTest {

    private val apiService = mockk<SpotifyApiService>(relaxed = true)

    private fun authManagerWithSession(
        accessToken: String? = null,
        grantedScope: String? = null
    ): SpotifyAuthManager {
        val prefs = mockk<SpotifyPrefs>(relaxed = true)
        every { prefs.accessToken } returns flowOf(accessToken)
        every { prefs.tokenTimestamp } returns flowOf(accessToken?.let { System.currentTimeMillis() })
        every { prefs.refreshToken } returns flowOf(null)
        every { prefs.grantedScope } returns flowOf(grantedScope)
        return SpotifyAuthManager(prefs, mockk(relaxed = true), UnconfinedTestDispatcher())
    }

    private fun spotifyRepository(
        dispatcher: TestDispatcher,
        authManager: SpotifyAuthManager = authManagerWithSession()
    ): SpotifyRepository {
        val client = mockk<SpotifyAppRemoteClient>(relaxed = true)
        every { client.isSupported } returns true
        return SpotifyRepository(apiService, authManager, client, dispatcher)
    }

    @Test
    fun `CloudViewModel exposes reauthRequired state from repository`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authManager = authManagerWithSession(
            accessToken = "access-1",
            grantedScope = "playlist-read-private user-library-read" // missing app-remote-control
        )
        val repo = spotifyRepository(dispatcher, authManager)
        val playbackEngine = mockk<PlaybackEngine>(relaxed = true)
        every { playbackEngine.spotifyReauthRequired } returns repo.reauthRequired

        val viewModel = CloudViewModel(repo, authManager, playbackEngine)

        // Initially false
        assertFalse("reauthRequired should be false initially", viewModel.reauthRequired.value)
    }

    @Test
    fun `reauthRequired state reflects repository reauth requirement`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authManager = authManagerWithSession(
            accessToken = "access-1",
            grantedScope = "playlist-read-private"
        )
        val repo = spotifyRepository(dispatcher, authManager)
        val playbackEngine = mockk<PlaybackEngine>(relaxed = true)
        every { playbackEngine.spotifyReauthRequired } returns repo.reauthRequired

        val viewModel = CloudViewModel(repo, authManager, playbackEngine)

        // Simulate a playback attempt that requires reauth
        repo.playTrack(mockk {
            every { id } returns "spotify:track:123"
            every { name } returns "Test Track"
            every { uri } returns "spotify:track:123"
            every { durationMs } returns 180000
            every { popularity } returns 80
            every { artists } returns emptyList()
            every { album } returns null
        })

        assertTrue("reauthRequired should be true after reauth error", repo.reauthRequired.value)
        assertTrue("reauthRequired should be reflected in ViewModel", viewModel.reauthRequired.value)
    }
}

