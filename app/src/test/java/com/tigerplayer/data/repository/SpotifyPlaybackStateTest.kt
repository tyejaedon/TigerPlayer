package com.tigerplayer.data.repository

import com.tigerplayer.data.remote.api.SpotifyApiService
import com.tigerplayer.data.remote.model.SpotifyAlbum
import com.tigerplayer.data.remote.model.SpotifyArtistSimplified
import com.tigerplayer.data.remote.model.SpotifyImage
import com.tigerplayer.data.remote.model.SpotifyTrack
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Coverage for issues #169 and #170.
 *
 * Requesting Spotify playback publishes an optimistic placeholder before the Spotify app confirms
 * anything. That placeholder must carry real metadata when the caller has it, must never echo a
 * raw base62 Spotify id at the user, and must never survive a failed or unconfirmed connection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SpotifyPlaybackStateTest {

    private val apiService = mockk<SpotifyApiService>(relaxed = true)

    // A real auth manager over relaxed prefs/api mocks — matching SpotifyAuthManagerTest. Mocking
    // the manager itself does not survive Robolectric's classloader. An UnconfinedTestDispatcher
    // runs the manager's internal persistence coroutine eagerly/synchronously, so no test in this
    // class races a real background dispatcher.
    private val authManager = SpotifyAuthManager(mockk(relaxed = true), mockk(relaxed = true), UnconfinedTestDispatcher())

    /** Records interactions and lets a test drive the App Remote callbacks by hand. */
    private class FakeAppRemoteClient(override val isSupported: Boolean = true) : SpotifyAppRemoteClient {
        var connectCalls = 0
        val playedUris = mutableListOf<String>()

        private var onConnectionChanged: ((Boolean) -> Unit)? = null
        private var onPlayerStateChanged: ((SpotifyRemotePlayerState?) -> Unit)? = null
        private var onConnectionFailed: ((Throwable) -> Unit)? = null

        /** When set, [connect] immediately reports failure, as a missing Spotify app would. */
        var failOnConnect: Throwable? = null

        override fun connect(
            clientId: String,
            redirectUri: String,
            onConnectionChanged: (Boolean) -> Unit,
            onPlayerStateChanged: (SpotifyRemotePlayerState?) -> Unit,
            onConnectionFailed: (Throwable) -> Unit
        ) {
            connectCalls++
            this.onConnectionChanged = onConnectionChanged
            this.onPlayerStateChanged = onPlayerStateChanged
            this.onConnectionFailed = onConnectionFailed
            failOnConnect?.let {
                onConnectionChanged(false)
                onConnectionFailed(it)
            }
        }

        fun completeConnection() = onConnectionChanged?.invoke(true)
        fun emitPlayerState(state: SpotifyRemotePlayerState?) = onPlayerStateChanged?.invoke(state)

        override fun disconnect() = Unit
        override fun play(uri: String) { playedUris += uri }
        override fun pause() = Unit
        override fun resume() = Unit
        override fun skipNext() = Unit
        override fun skipPrevious() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() = Unit
        override fun toggleRepeat() = Unit
    }

    private fun repository(client: SpotifyAppRemoteClient, dispatcher: TestDispatcher) =
        SpotifyRepository(apiService, authManager, client, dispatcher)

    private fun spotifyTrack(
        id: String = "6habFhsOp2NvshLv26DqMb",
        name: String = "Bohemian Rhapsody",
        durationMs: Long = 354_000L
    ) = SpotifyTrack(
        id = id,
        name = name,
        uri = "spotify:track:$id",
        durationMs = durationMs,
        popularity = 80,
        artists = listOf(SpotifyArtistSimplified("a1", "Queen", "spotify:artist:a1")),
        album = SpotifyAlbum(
            id = "al1",
            name = "A Night at the Opera",
            uri = "spotify:album:al1",
            images = listOf(SpotifyImage("https://img.example/cover.jpg", 640, 640)),
            artists = listOf(SpotifyArtistSimplified("a1", "Queen", "spotify:artist:a1")),
            totalTracks = 12,
            releaseDate = "1975-11-21"
        )
    )

    private fun remoteState(
        trackUri: String = "spotify:track:6habFhsOp2NvshLv26DqMb"
    ) = SpotifyRemotePlayerState(
        trackUri = trackUri,
        trackName = "Bohemian Rhapsody",
        artistName = "Queen",
        durationMs = 354_000L,
        positionMs = 1_200L,
        isPaused = false,
        isShuffling = false
    )

    // --- issue #169: the placeholder must carry real metadata ---

    @Test
    fun `playing a known track shows its real title artist and duration immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = repository(FakeAppRemoteClient(), dispatcher)

        repo.playTrack(spotifyTrack())

        val state = requireNotNull(repo.spotifyPlaybackState.value)
        assertEquals("Bohemian Rhapsody", state.track.title)
        assertEquals("Queen", state.track.artist)
        assertEquals("A Night at the Opera", state.track.album)
        assertEquals(354_000L, state.track.durationMs)
        assertTrue("state is not yet confirmed by the Spotify app", state.isOptimistic)
    }

    @Test
    fun `a placeholder never shows the raw spotify id as the title`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = repository(FakeAppRemoteClient(), dispatcher)

        repo.playUri("spotify:track:6habFhsOp2NvshLv26DqMb")

        val title = repo.spotifyPlaybackState.value!!.track.title
        assertEquals("Spotify track", title)
        assertFalse("must not echo the base62 id", title.contains("6habFhsOp2NvshLv26DqMb"))
    }

    @Test
    fun `a collection placeholder uses the display name it was given`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = repository(FakeAppRemoteClient(), dispatcher)

        repo.playCollection("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", "Today's Top Hits")

        assertEquals("Today's Top Hits", repo.spotifyPlaybackState.value!!.track.title)
    }

    @Test
    fun `a real player state event replaces the optimistic placeholder`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeAppRemoteClient()
        val repo = repository(client, dispatcher)

        repo.playTrack(spotifyTrack())
        client.completeConnection()
        client.emitPlayerState(remoteState())

        val state = repo.spotifyPlaybackState.value!!
        assertFalse(state.isOptimistic)
        assertEquals(1_200L, state.positionMs)
        assertEquals(354_000L, state.track.durationMs)
    }

    // --- issue #170: failures must surface and must not leave a stuck placeholder ---

    @Test
    fun `a failed connection reports an error and clears the stuck placeholder`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeAppRemoteClient().apply { failOnConnect = IllegalStateException("no app") }
        val repo = repository(client, dispatcher)

        repo.playTrack(spotifyTrack())

        assertNull("the placeholder must not linger at 0:00", repo.spotifyPlaybackState.value)
        assertNotNull(repo.connectionError.value)
        assertFalse(repo.isConnected.value)
    }

    @Test
    fun `playback that is never confirmed times out instead of sticking forever`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeAppRemoteClient()
        val repo = repository(client, dispatcher)

        repo.playTrack(spotifyTrack())
        client.completeConnection() // connects, but the Spotify app never emits a player state

        assertNotNull("still optimistic before the timeout", repo.spotifyPlaybackState.value)

        advanceTimeBy(20_000L)

        assertNull(repo.spotifyPlaybackState.value)
        assertNotNull(repo.connectionError.value)
    }

    @Test
    fun `confirmed playback is not torn down by the watchdog`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeAppRemoteClient()
        val repo = repository(client, dispatcher)

        repo.playTrack(spotifyTrack())
        client.completeConnection()
        client.emitPlayerState(remoteState())

        advanceTimeBy(60_000L)

        assertNotNull(repo.spotifyPlaybackState.value)
        assertNull(repo.connectionError.value)
    }

    @Test
    fun `a confirmed player state clears a previous failure message`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeAppRemoteClient()
        val repo = repository(client, dispatcher)

        repo.playTrack(spotifyTrack())
        advanceTimeBy(20_000L)
        assertNotNull(repo.connectionError.value)

        client.completeConnection()
        client.emitPlayerState(remoteState())

        assertNull(repo.connectionError.value)
    }

    @Test
    fun `a build without app remote support reports it rather than showing a placeholder`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeAppRemoteClient(isSupported = false)
        val repo = repository(client, dispatcher)

        repo.playTrack(spotifyTrack())

        assertNull(repo.spotifyPlaybackState.value)
        assertNotNull(repo.connectionError.value)
        assertTrue("no connection should be attempted", client.connectCalls == 0)
    }

    @Test
    fun `a queued uri is played once the connection completes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeAppRemoteClient()
        val repo = repository(client, dispatcher)

        repo.playTrack(spotifyTrack())
        assertTrue("nothing can be played before connecting", client.playedUris.isEmpty())

        client.completeConnection()

        assertEquals(listOf("spotify:track:6habFhsOp2NvshLv26DqMb"), client.playedUris)
    }

    @Test
    fun `clearing the error acknowledges it so it is not shown twice`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeAppRemoteClient().apply { failOnConnect = IllegalStateException("no app") }
        val repo = repository(client, dispatcher)

        repo.playTrack(spotifyTrack())
        assertNotNull(repo.connectionError.value)

        repo.clearConnectionError()

        assertNull(repo.connectionError.value)
    }

    @Test
    fun `disconnecting drops any optimistic state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeAppRemoteClient()
        val repo = repository(client, dispatcher)

        repo.playTrack(spotifyTrack())
        repo.disconnect()

        assertNull(repo.spotifyPlaybackState.value)
        assertFalse(repo.isConnected.value)
    }
}
