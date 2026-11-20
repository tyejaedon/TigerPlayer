package com.tigerplayer.data.repository

import com.tigerplayer.data.local.LastfmPrefs
import com.tigerplayer.data.local.entity.PendingScrobbleEntity
import com.tigerplayer.data.remote.api.LastFmApi
import com.tigerplayer.data.remote.model.LastFmNowPlayingResponse
import com.tigerplayer.data.remote.model.LastFmNowPlayingTrack
import com.tigerplayer.data.remote.model.LastFmScrobbleAttr
import com.tigerplayer.data.remote.model.LastFmScrobbleResponse
import com.tigerplayer.data.remote.model.LastFmScrobblesContainer
import com.tigerplayer.data.remote.model.LastFmSession
import com.tigerplayer.data.remote.model.LastFmSessionResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastfmRepositoryTest {

    private lateinit var api: LastFmApi
    private lateinit var prefs: LastfmPrefs
    private lateinit var repository: LastFmRepository

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        every { prefs.isConnected } returns MutableStateFlow(true)
        every { prefs.username } returns MutableStateFlow("tigeruser")
        every { prefs.sessionKeySnapshot() } returns "secret_session_key_123"
        repository = LastFmRepository(
            lastFmApi = api,
            lastfmPrefs = prefs,
            ioDispatcher = UnconfinedTestDispatcher(),
            apiKey = "test_api_key",
            apiSecret = "test_api_secret"
        )
    }

    @Test
    fun `completeAuth saves session when configured and API succeeds`() = runTest {
        coEvery { api.getSession(any(), any(), any()) } returns Response.success(
            LastFmSessionResponse(LastFmSession(name = "tigeruser", key = "sk_abc", subscriber = 0))
        )

        val result = repository.completeAuth("tok_xyz")

        assertTrue(result.isSuccess)
        assertEquals("tigeruser", result.getOrNull())
        coVerify(exactly = 1) { prefs.saveSession("sk_abc", "tigeruser") }
    }

    @Test
    fun `scrobbleBatch formats indexed batch params and returns Success`() = runTest {
        val paramsSlot = slot<Map<String, String>>()
        coEvery { api.scrobble(capture(paramsSlot)) } returns Response.success(
            LastFmScrobbleResponse(LastFmScrobblesContainer(LastFmScrobbleAttr(accepted = 2, ignored = 0)))
        )

        val items = listOf(
            PendingScrobbleEntity(id = 1, trackId = "t1", artist = "Artist A", track = "Track 1", album = "Album 1", durationSec = 200, timestampSec = 1000L),
            PendingScrobbleEntity(id = 2, trackId = "t2", artist = "Artist B", track = "Track 2", album = null, durationSec = 180, timestampSec = 1200L)
        )

        val outcome = repository.scrobbleBatch(items)

        assertEquals(ScrobbleOutcome.Success, outcome)
        val captured = paramsSlot.captured
        assertEquals("track.scrobble", captured["method"])
        assertEquals("Artist A", captured["artist[0]"])
        assertEquals("Track 1", captured["track[0]"])
        assertEquals("1000", captured["timestamp[0]"])
        assertEquals("Album 1", captured["album[0]"])
        assertEquals("Artist B", captured["artist[1]"])
        assertEquals("Track 2", captured["track[1]"])
        assertEquals("1200", captured["timestamp[1]"])
        assertTrue(captured.containsKey("api_sig"))
    }

    @Test
    fun `scrobbleBatch returns AuthInvalid and clears session on Lastfm error 9`() = runTest {
        val errorJson = """{"error":9,"message":"Invalid session key - Please re-authenticate"}"""
        coEvery { api.scrobble(any()) } returns Response.error(
            403,
            errorJson.toResponseBody("application/json".toMediaType())
        )

        val item = PendingScrobbleEntity(id = 1, trackId = "t1", artist = "Artist", track = "Track", durationSec = 180, timestampSec = 1000L)
        val outcome = repository.scrobbleBatch(listOf(item))

        assertTrue(outcome is ScrobbleOutcome.AuthInvalid)
        coVerify(exactly = 1) { prefs.clearSession() }
    }

    @Test
    fun `updateNowPlaying returns TransientFailure on IOException`() = runTest {
        coEvery { api.updateNowPlaying(any()) } throws IOException("No network")

        val outcome = repository.updateNowPlaying("Artist", "Track", "Album", 210)

        assertTrue(outcome is ScrobbleOutcome.TransientFailure)
    }

    @Test
    fun `updateNowPlaying returns Success when API responds 200`() = runTest {
        coEvery { api.updateNowPlaying(any()) } returns Response.success(
            LastFmNowPlayingResponse(LastFmNowPlayingTrack(null, null))
        )

        val outcome = repository.updateNowPlaying("Artist", "Track", "Album", 210)

        assertEquals(ScrobbleOutcome.Success, outcome)
    }
}
