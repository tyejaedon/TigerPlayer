package com.tigerplayer.data.repository

import com.tigerplayer.data.local.SpotifyPrefs
import com.tigerplayer.data.remote.api.SpotifyApiService
import com.tigerplayer.data.remote.api.SpotifyAuthApi
import com.tigerplayer.data.remote.model.PlaylistTrackInfo
import com.tigerplayer.data.remote.model.SpotifyImage
import com.tigerplayer.data.remote.model.SpotifyOwner
import com.tigerplayer.data.remote.model.SpotifyPaging
import com.tigerplayer.data.remote.model.SpotifyPlaylist
import com.tigerplayer.data.remote.model.SpotifyPlaylistResponse
import com.tigerplayer.data.remote.model.SpotifyTokenResponse
import com.tigerplayer.ui.coverscreen.CoverScreenLibraryTab
import com.tigerplayer.ui.coverscreen.CoverScreenTestTags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SpotifyRepositoryTest {

    private val apiService = mockk<SpotifyApiService>(relaxed = true)

    private fun samplePlaylist(id: String = "pl-1", name: String = "Indie Mix"): SpotifyPlaylist {
        return SpotifyPlaylist(
            id = id,
            name = name,
            uri = "spotify:playlist:$id",
            images = listOf(SpotifyImage("https://example.com/$id.png", 300, 300)),
            owner = SpotifyOwner(displayName = "Tiger User", id = "tiger_user"),
            trackInfo = PlaylistTrackInfo(total = 25, href = "https://api.spotify.com/v1/playlists/$id/tracks")
        )
    }

    private fun samplePaging(items: List<SpotifyPlaylist>): SpotifyPlaylistResponse {
        return SpotifyPaging(
            items = items,
            total = items.size,
            next = null,
            limit = 50,
            offset = 0
        )
    }

    private fun authManagerWithSession(
        accessToken: String = "valid-token"
    ): SpotifyAuthManager {
        val prefs = mockk<SpotifyPrefs>(relaxed = true)
        every { prefs.accessToken } returns flowOf(accessToken.ifEmpty { null })
        every { prefs.tokenTimestamp } returns flowOf(if (accessToken.isNotEmpty()) System.currentTimeMillis() else null)
        every { prefs.refreshToken } returns flowOf(null)
        every { prefs.grantedScope } returns flowOf("playlist-read-private")
        return SpotifyAuthManager(prefs, mockk(relaxed = true), UnconfinedTestDispatcher())
    }

    private fun createRepository(
        dispatcher: TestDispatcher,
        authManager: SpotifyAuthManager = authManagerWithSession()
    ): SpotifyRepository {
        val client = mockk<SpotifyAppRemoteClient>(relaxed = true)
        every { client.isSupported } returns true
        return SpotifyRepository(apiService, authManager, client, dispatcher)
    }

    // =========================================================================
    // 1. 15-Minute TTL Cache Behavior Tests
    // =========================================================================

    @Test
    fun `PLAYLISTS_CACHE_TTL_MS is 15 minutes`() {
        assertEquals(15 * 60 * 1000L, SpotifyRepository.PLAYLISTS_CACHE_TTL_MS)
    }

    @Test
    fun `fetchUserPlaylists returns cached playlists without re-querying API within 15 minutes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        val playlist = samplePlaylist("pl-1", "Daily Lift")

        coEvery { apiService.getUserPlaylists("Bearer valid-token", limit = 50, offset = 0) } returns
            Response.success(samplePaging(listOf(playlist)))

        // 1. First fetch: queries the API and sets userPlaylistsLastFetchedMs
        val initialResult = repository.fetchUserPlaylists("valid-token")
        assertEquals(1, initialResult.size)
        assertEquals("Daily Lift", initialResult.first().name)
        assertEquals(1, repository.userPlaylists.value.size)
        assertTrue(repository.userPlaylistsLastFetchedMs > 0L)
        coVerify(exactly = 1) { apiService.getUserPlaylists("Bearer valid-token", limit = 50, offset = 0) }

        // 2. Second fetch within 15 minutes: cache hit, no new API query
        val cachedResult = repository.fetchUserPlaylists("valid-token", forceRefresh = false)
        assertEquals(1, cachedResult.size)
        assertEquals("Daily Lift", cachedResult.first().name)
        coVerify(exactly = 1) { apiService.getUserPlaylists(any(), any(), any()) }
    }

    @Test
    fun `fetchUserPlaylists re-queries API after 15 minute TTL expires`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        val playlist1 = samplePlaylist("pl-1", "Playlist 1")
        val playlist2 = samplePlaylist("pl-2", "Playlist 2 (Updated)")

        coEvery { apiService.getUserPlaylists("Bearer valid-token", limit = 50, offset = 0) } returns
            Response.success(samplePaging(listOf(playlist1))) andThen
            Response.success(samplePaging(listOf(playlist2)))

        // First fetch
        val firstResult = repository.fetchUserPlaylists("valid-token")
        assertEquals("Playlist 1", firstResult.first().name)
        val firstTimestamp = repository.userPlaylistsLastFetchedMs
        assertTrue(firstTimestamp > 0L)

        // Simulate TTL expiration (> 15 minutes elapsed)
        repository.userPlaylistsLastFetchedMs = System.currentTimeMillis() - SpotifyRepository.PLAYLISTS_CACHE_TTL_MS - 1000L

        // Second fetch: should trigger re-query due to TTL expiration
        val expiredResult = repository.fetchUserPlaylists("valid-token", forceRefresh = false)
        assertEquals("Playlist 2 (Updated)", expiredResult.first().name)
        coVerify(exactly = 2) { apiService.getUserPlaylists("Bearer valid-token", limit = 50, offset = 0) }
        assertTrue(repository.userPlaylistsLastFetchedMs >= firstTimestamp)
    }

    @Test
    fun `fetchUserPlaylists with forceRefresh bypasses valid cache and re-queries API`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        val playlist1 = samplePlaylist("pl-1", "Initial")
        val playlist2 = samplePlaylist("pl-1", "Refreshed")

        coEvery { apiService.getUserPlaylists("Bearer valid-token", limit = 50, offset = 0) } returns
            Response.success(samplePaging(listOf(playlist1))) andThen
            Response.success(samplePaging(listOf(playlist2)))

        // Initial fetch
        repository.fetchUserPlaylists("valid-token")
        assertEquals("Initial", repository.userPlaylists.value.first().name)

        // Force refresh within TTL
        val refreshedResult = repository.fetchUserPlaylists("valid-token", forceRefresh = true)
        assertEquals("Refreshed", refreshedResult.first().name)
        coVerify(exactly = 2) { apiService.getUserPlaylists("Bearer valid-token", limit = 50, offset = 0) }
    }

    @Test
    fun `clearPlaylistsCache empties cached playlists and resets last fetched timestamp`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        coEvery { apiService.getUserPlaylists(any(), any(), any()) } returns
            Response.success(samplePaging(listOf(samplePlaylist())))

        repository.fetchUserPlaylists("valid-token")
        assertEquals(1, repository.userPlaylists.value.size)
        assertTrue(repository.userPlaylistsLastFetchedMs > 0L)

        repository.clearPlaylistsCache()
        assertTrue(repository.userPlaylists.value.isEmpty())
        assertEquals(0L, repository.userPlaylistsLastFetchedMs)
    }

    @Test
    fun `clears cached playlists when auth token becomes empty (logout)`() = runTest {
        val authManager = authManagerWithSession("valid-token")
        val dispatcher = UnconfinedTestDispatcher()
        val repository = createRepository(dispatcher, authManager)

        coEvery { apiService.getUserPlaylists(any(), any(), any()) } returns
            Response.success(samplePaging(listOf(samplePlaylist())))

        repository.fetchUserPlaylists("valid-token")
        assertEquals(1, repository.userPlaylists.value.size)

        // Simulate logout via authManager
        authManager.logout()

        assertTrue("User playlists should be empty on logout", repository.userPlaylists.value.isEmpty())
        assertEquals(0L, repository.userPlaylistsLastFetchedMs)
    }

    // =========================================================================
    // 2. Graceful Error Handling Tests (Network Failures & Invalid Tokens)
    // =========================================================================

    @Test
    fun `fetchUserPlaylists handles network exception gracefully without crashing and retains existing state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        val playlist = samplePlaylist("cached-1", "Cached Mix")

        // First successful fetch to populate cache
        coEvery { apiService.getUserPlaylists("Bearer valid-token", any(), any()) } returns
            Response.success(samplePaging(listOf(playlist)))
        repository.fetchUserPlaylists("valid-token")

        // Next fetch encounters network failure
        coEvery { apiService.getUserPlaylists("Bearer valid-token", any(), any()) } throws IOException("Socket timeout")

        val result = repository.fetchUserPlaylists("valid-token", forceRefresh = true)

        // Must not crash, should return previously cached playlists gracefully
        assertEquals(1, result.size)
        assertEquals("Cached Mix", result.first().name)
        assertEquals(1, repository.userPlaylists.value.size)
    }

    @Test
    fun `fetchUserPlaylists handles 401 Unauthorized invalid token response gracefully`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)

        val errorBody = "{\"error\": {\"status\": 401, \"message\": \"The access token expired\"}}"
            .toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { apiService.getUserPlaylists("Bearer expired-token", any(), any()) } returns
            Response.error(401, errorBody)

        val result = repository.fetchUserPlaylists("expired-token", forceRefresh = true)

        // Must not crash, should return empty list gracefully
        assertTrue(result.isEmpty())
        assertTrue(repository.userPlaylists.value.isEmpty())
    }

    @Test
    fun `fetchUserPlaylists with blank token returns current state without querying API`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)

        val result = repository.fetchUserPlaylists("   ")

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { apiService.getUserPlaylists(any(), any(), any()) }
    }

    @Test(expected = CancellationException::class)
    fun `fetchUserPlaylists re-throws CancellationException without swallowing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)

        coEvery { apiService.getUserPlaylists(any(), any(), any()) } throws CancellationException("Job was cancelled")

        repository.fetchUserPlaylists("valid-token", forceRefresh = true)
    }

    // =========================================================================
    // 3. Cover Screen Library Spotify Tab Integration & Auth Fallback Tests
    // =========================================================================

    @Test
    fun `CoverScreenLibraryTab contains SPOTIFY_PLAYLISTS tab with correct label`() {
        val tabs = CoverScreenLibraryTab.entries
        assertTrue(tabs.contains(CoverScreenLibraryTab.SPOTIFY_PLAYLISTS))
        assertEquals("Spotify Playlists", CoverScreenLibraryTab.SPOTIFY_PLAYLISTS.label)
    }

    @Test
    fun `CoverScreenTestTags defines constant for Spotify auth required fallback`() {
        assertEquals("cover_screen_spotify_auth_required", CoverScreenTestTags.LIBRARY_SPOTIFY_AUTH_REQUIRED)
    }

    @Test
    fun `CoverScreenTestTags generates consistent tag for spotify playlists`() {
        assertEquals("cover_screen_spotify_playlist_test-pl-123", CoverScreenTestTags.spotifyPlaylist("test-pl-123"))
    }

    @Test
    fun `authManager and repository isAuthenticated state maps correctly`() = runTest {
        val authApi = mockk<SpotifyAuthApi>()
        coEvery { authApi.getUserToken(any(), any(), any(), any(), any()) } returns
            Response.success(SpotifyTokenResponse("token-123", "Bearer", 3600, "refresh", "scope"))

        val prefs = mockk<SpotifyPrefs>(relaxed = true)
        every { prefs.accessToken } returns flowOf(null)
        every { prefs.tokenTimestamp } returns flowOf(null)
        every { prefs.refreshToken } returns flowOf(null)
        every { prefs.grantedScope } returns flowOf(null)

        val dispatcher = UnconfinedTestDispatcher()
        val authManager = SpotifyAuthManager(prefs, authApi, dispatcher)
        val repository = createRepository(dispatcher, authManager)

        // Initially logged out
        assertFalse(repository.isAuthenticated.value)

        // Logged in via exchangeCodeForToken
        authManager.exchangeCodeForToken("code", "uri", "verifier")
        assertTrue(repository.isAuthenticated.value)

        // Logged out again
        authManager.logout()
        assertFalse(repository.isAuthenticated.value)
    }
}
