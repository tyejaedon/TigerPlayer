package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.local.SpotifyPrefs
import com.example.tigerplayer.data.remote.api.SpotifyAuthApi
import com.example.tigerplayer.data.remote.model.SpotifyTokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Coverage for issue #46 - migration from client-credentials/Basic-auth to
 * Authorization Code + PKCE, plus the refresh-token handling that previously didn't exist.
 *
 * Persistence side effects (`SpotifyPrefs.saveToken` / `clearToken`) are fired from an internal,
 * non-injectable `CoroutineScope(Dispatchers.IO)`, so those specific assertions use a bounded
 * `coVerify(timeout = ...)` rather than `runTest` virtual time. All token-state assertions
 * (`getToken()`, return values) are set synchronously and do not need it.
 */
class SpotifyAuthManagerTest {

    private val spotifyPrefs = mockk<SpotifyPrefs>(relaxed = true)
    private val spotifyAuthApi = mockk<SpotifyAuthApi>()

    private fun manager(): SpotifyAuthManager {
        every { spotifyPrefs.accessToken } returns flowOf(null)
        every { spotifyPrefs.tokenTimestamp } returns flowOf(null)
        every { spotifyPrefs.refreshToken } returns flowOf(null)
        return SpotifyAuthManager(spotifyPrefs, spotifyAuthApi)
    }

    private fun tokenResponse(
        accessToken: String,
        expiresIn: Int = 3600,
        refreshToken: String? = null
    ) = SpotifyTokenResponse(
        accessToken = accessToken,
        tokenType = "Bearer",
        expiresIn = expiresIn,
        refreshToken = refreshToken,
        scope = null
    )

    private fun errorResponse(code: Int): Response<SpotifyTokenResponse> =
        Response.error(code, "{}".toResponseBody("application/json".toMediaTypeOrNull()))

    @Before
    fun setUp() {
        // Nothing shared; each test builds its own manager so state never leaks across cases.
    }

    // --- exchangeCodeForToken (PKCE authorization_code exchange) ---

    @Test
    fun `exchangeCodeForToken sends the verifier and no secret, and returns the access token on success`() = runTest {
        val sut = manager()
        coEvery {
            spotifyAuthApi.getUserToken(
                clientId = any(),
                code = "auth-code",
                redirectUri = "tigerplayer://callback",
                codeVerifier = "the-verifier"
            )
        } returns Response.success(tokenResponse("access-1", refreshToken = "refresh-1"))

        val result = sut.exchangeCodeForToken("auth-code", "tigerplayer://callback", "the-verifier")

        assertEquals("access-1", result)
        assertEquals("access-1", sut.getToken())
        coVerify(timeout = 2_000) { spotifyPrefs.saveToken("access-1", any(), "refresh-1") }
    }

    @Test
    fun `exchangeCodeForToken returns empty string on an unsuccessful response`() = runTest {
        val sut = manager()
        coEvery {
            spotifyAuthApi.getUserToken(any(), any(), any(), any(), any())
        } returns errorResponse(400)

        val result = sut.exchangeCodeForToken("bad-code", "tigerplayer://callback", "verifier")

        assertEquals("", result)
        assertEquals("", sut.getToken())
    }

    @Test
    fun `exchangeCodeForToken swallows a generic exception and returns empty string`() = runTest {
        val sut = manager()
        coEvery {
            spotifyAuthApi.getUserToken(any(), any(), any(), any(), any())
        } throws RuntimeException("network down")

        val result = sut.exchangeCodeForToken("code", "tigerplayer://callback", "verifier")

        assertEquals("", result)
    }

    @Test
    fun `exchangeCodeForToken rethrows CancellationException instead of swallowing it`() = runTest {
        val sut = manager()
        coEvery {
            spotifyAuthApi.getUserToken(any(), any(), any(), any(), any())
        } throws CancellationException("scope cancelled")

        try {
            sut.exchangeCodeForToken("code", "tigerplayer://callback", "verifier")
            fail("Expected CancellationException to propagate")
        } catch (expected: CancellationException) {
            assertEquals("scope cancelled", expected.message)
        }
    }

    // --- getValidToken / refresh ---

    @Test
    fun `getValidToken returns the cached token without refreshing when not expired`() = runTest {
        val sut = manager()
        coEvery {
            spotifyAuthApi.getUserToken(any(), any(), any(), any(), any())
        } returns Response.success(tokenResponse("access-1", expiresIn = 3600, refreshToken = "refresh-1"))
        sut.exchangeCodeForToken("code", "tigerplayer://callback", "verifier")

        val result = sut.getValidToken()

        assertEquals("access-1", result)
        coVerify(exactly = 0) { spotifyAuthApi.refreshToken(any(), any(), any()) }
    }

    @Test
    fun `getValidToken refreshes an expired token using the stored refresh token`() = runTest {
        val sut = manager()
        // expiresIn = 0 guarantees isTokenExpired() is true almost immediately (elapsed > -bufferMs).
        coEvery {
            spotifyAuthApi.getUserToken(any(), any(), any(), any(), any())
        } returns Response.success(tokenResponse("access-1", expiresIn = 0, refreshToken = "refresh-1"))
        sut.exchangeCodeForToken("code", "tigerplayer://callback", "verifier")

        coEvery {
            spotifyAuthApi.refreshToken(clientId = any(), refreshToken = "refresh-1")
        } returns Response.success(tokenResponse("access-2", expiresIn = 3600, refreshToken = "refresh-2"))

        val result = sut.getValidToken()

        assertEquals("access-2", result)
        assertEquals("access-2", sut.getToken())
        coVerify(exactly = 1) { spotifyAuthApi.refreshToken(any(), any(), refreshToken = "refresh-1") }
    }

    @Test
    fun `getValidToken logs out and returns empty string when the refresh token is rejected with 400`() = runTest {
        val sut = manager()
        coEvery {
            spotifyAuthApi.getUserToken(any(), any(), any(), any(), any())
        } returns Response.success(tokenResponse("access-1", expiresIn = 0, refreshToken = "dead-refresh"))
        sut.exchangeCodeForToken("code", "tigerplayer://callback", "verifier")

        coEvery { spotifyAuthApi.refreshToken(any(), any(), any()) } returns errorResponse(400)

        val result = sut.getValidToken()

        assertEquals("", result)
        assertEquals("", sut.getToken())
        coVerify(timeout = 2_000) { spotifyPrefs.clearToken() }
    }

    @Test
    fun `getValidToken does not log out on a transient 500 refresh failure`() = runTest {
        val sut = manager()
        coEvery {
            spotifyAuthApi.getUserToken(any(), any(), any(), any(), any())
        } returns Response.success(tokenResponse("access-1", expiresIn = 0, refreshToken = "refresh-1"))
        sut.exchangeCodeForToken("code", "tigerplayer://callback", "verifier")

        coEvery { spotifyAuthApi.refreshToken(any(), any(), any()) } returns errorResponse(500)

        val result = sut.getValidToken()

        assertEquals("", result)
        // logout() was NOT triggered for a transient server error - only 400/401 clear the session.
        coVerify(exactly = 0) { spotifyPrefs.clearToken() }
    }

    @Test
    fun `getValidToken returns empty string when there is no cached token and no refresh token`() = runTest {
        val sut = manager()

        val result = sut.getValidToken()

        assertEquals("", result)
        coVerify(exactly = 0) { spotifyAuthApi.refreshToken(any(), any(), any()) }
    }

    // --- isTokenExpired boundary behavior ---

    @Test
    fun `isTokenExpired treats a zero timestamp as always expired`() {
        val sut = manager()

        assertTrue(sut.isTokenExpired(0L))
    }

    @Test
    fun `isTokenExpired uses the default one hour window before any token has been issued`() {
        val sut = manager()
        val now = System.currentTimeMillis()

        assertFalse(sut.isTokenExpired(now))
        // Comfortably inside the 1h-minus-5min-buffer boundary from the default expiresInMs.
        assertTrue(sut.isTokenExpired(now - 3_600_000L))
    }

    @Test
    fun `isTokenExpired treats a token shorter than the 5-minute buffer as immediately expired`() = runTest {
        val sut = manager()
        // A 60s lifetime is shorter than the 5-minute safety buffer, so the expiry threshold goes
        // negative and any elapsed time - including effectively zero - counts as expired.
        coEvery {
            spotifyAuthApi.getUserToken(any(), any(), any(), any(), any())
        } returns Response.success(tokenResponse("short-lived", expiresIn = 60, refreshToken = "refresh-1"))
        sut.exchangeCodeForToken("code", "tigerplayer://callback", "verifier")

        assertTrue(sut.isTokenExpired(System.currentTimeMillis()))
    }

    @Test
    fun `isTokenExpired recalculates its window from the last token's own expires_in`() = runTest {
        val sut = manager()
        coEvery {
            spotifyAuthApi.getUserToken(any(), any(), any(), any(), any())
        } returns Response.success(tokenResponse("long-lived", expiresIn = 7200, refreshToken = "refresh-1"))
        sut.exchangeCodeForToken("code", "tigerplayer://callback", "verifier")
        val now = System.currentTimeMillis()

        // Fresh 2h token: not expired now, and not expired at the 1h mark either - the previous
        // default-assumption test would have called this expired if expiresInMs hadn't updated.
        assertFalse(sut.isTokenExpired(now))
        assertFalse(sut.isTokenExpired(now - 3_600_000L))
        // But it is expired once elapsed time exceeds 2h minus the 5-minute buffer.
        assertTrue(sut.isTokenExpired(now - 7_200_000L))
    }
}

