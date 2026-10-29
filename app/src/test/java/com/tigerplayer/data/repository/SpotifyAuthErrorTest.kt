package com.tigerplayer.data.repository

import com.tigerplayer.data.local.SpotifyPrefs
import com.tigerplayer.data.remote.api.SpotifyAuthApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyAuthErrorTest {

    @Test
    fun `missingClientId produces actionable repo setup guide`() {
        val error = SpotifyAuthError.missingClientId()
        assertEquals("Spotify Setup Required", error.title)
        assertEquals("MISSING_CLIENT_ID", error.rawErrorCode)
        assertTrue(error.description.contains("Spotify Client ID"))
        assertTrue(error.setupSteps.any { it.contains("secrets.properties") })
        assertTrue(error.setupSteps.any { it.contains("tigerplayer://callback") })
    }

    @Test
    fun `noInternet produces connectivity resolution steps`() {
        val error = SpotifyAuthError.noInternet()
        assertEquals("No Internet Connection", error.title)
        assertEquals("NETWORK_UNAVAILABLE", error.rawErrorCode)
        assertTrue(error.setupSteps.any { it.contains("Wi-Fi") })
    }

    @Test
    fun `fromCallback maps access_denied to authorization cancelled`() {
        val error = SpotifyAuthError.fromCallback("access_denied")
        assertEquals("Authorization Cancelled", error.title)
        assertEquals("access_denied", error.rawErrorCode)
        assertTrue(error.description.contains("cancelled") || error.description.contains("declined"))
    }

    @Test
    fun `fromCallback maps redirect_uri_mismatch to dashboard instructions`() {
        val error = SpotifyAuthError.fromCallback("redirect_uri_mismatch")
        assertEquals("Redirect URI Mismatch", error.title)
        assertEquals("redirect_uri_mismatch", error.rawErrorCode)
        assertTrue(error.setupSteps.any { it.contains("tigerplayer://callback") })
    }

    @Test
    fun `fromCallback maps invalid_client to client ID check`() {
        val error = SpotifyAuthError.fromCallback("invalid_client")
        assertEquals("Invalid Client ID", error.title)
        assertEquals("invalid_client", error.rawErrorCode)
        assertTrue(error.setupSteps.any { it.contains("secrets.properties") })
    }

    @Test
    fun `fromCallback maps unauthorized_client to permissions guide`() {
        val error = SpotifyAuthError.fromCallback("unauthorized_client")
        assertEquals("Client Not Authorized", error.title)
        assertEquals("unauthorized_client", error.rawErrorCode)
        assertTrue(error.setupSteps.any { it.contains("User Management") })
    }

    @Test
    fun `fromCallback handles arbitrary unknown error codes gracefully`() {
        val error = SpotifyAuthError.fromCallback("some_unforeseen_error")
        assertEquals("Spotify Login Failed", error.title)
        assertEquals("some_unforeseen_error", error.rawErrorCode)
    }

    @Test
    fun `tokenExchangeFailed formats details and retry steps`() {
        val error = SpotifyAuthError.tokenExchangeFailed("HTTP 400")
        assertEquals("Token Exchange Failed", error.title)
        assertEquals("HTTP 400", error.rawErrorCode)
        assertTrue(error.setupSteps.isNotEmpty())
    }

    @Test
    fun `emptyToken surfaces empty token diagnosis`() {
        val error = SpotifyAuthError.emptyToken()
        assertEquals("Empty Session Token", error.title)
        assertEquals("EMPTY_TOKEN", error.rawErrorCode)
    }

    @Test
    fun `SpotifyAuthManager manages authError lifecycle and clears on logout`() = runTest {
        val prefs = mockk<SpotifyPrefs>(relaxed = true) {
            every { accessToken } returns flowOf(null)
            every { tokenTimestamp } returns flowOf(null)
            every { refreshToken } returns flowOf(null)
            every { grantedScope } returns flowOf(null)
        }
        val api = mockk<SpotifyAuthApi>(relaxed = true)
        val dispatcher = UnconfinedTestDispatcher()
        val manager = SpotifyAuthManager(prefs, api, dispatcher)

        assertNull(manager.authError.value)

        val testError = SpotifyAuthError.missingClientId()
        manager.setAuthError(testError)
        assertEquals(testError, manager.authError.value)

        manager.clearAuthError()
        assertNull(manager.authError.value)

        manager.setAuthError(testError)
        assertNotNull(manager.authError.value)
        manager.logout()
        assertNull(manager.authError.value)
    }
}
