package com.tigerplayer.engine

import com.tigerplayer.data.local.NavidromePrefs
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.repository.NavidromeRepository
import com.tigerplayer.data.repository.SpotifyAuthManager
import com.tigerplayer.di.SubsonicHostManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for issue #47.
 *
 * `ensureValidUrl()` previously defaulted any scheme-less server address to `http://` with no
 * warning, silently sending Navidrome credentials in the clear. `connectToNavidrome` now defaults
 * bare hosts to `https://`, and additionally refuses an explicit `http://` address unless the
 * caller passes `allowCleartext = true` (the UI-level user acknowledgement gate).
 */
class NetworkEngineTest {

    private val navidromePrefs = mockk<NavidromePrefs>(relaxed = true)
    private val hostManager = SubsonicHostManager()
    private val navidromeRepository = mockk<NavidromeRepository>()
    private val audioRepository = mockk<AudioRepository>(relaxed = true)
    private val authManager = mockk<SpotifyAuthManager>(relaxed = true)

    private lateinit var engine: NetworkEngine

    @Before
    fun setUp() {
        engine = NetworkEngine(navidromePrefs, hostManager, navidromeRepository, audioRepository, authManager)
        coEvery { navidromeRepository.pingServer(any(), any()) } returns Result.success(true)
    }

    @Test
    fun `a bare host with no scheme is treated as https`() = runTest {
        engine.connectToNavidrome("myserver.example.com", "user", "pass")

        assertEquals("https://myserver.example.com/", hostManager.currentBaseUrl)
        coVerify { navidromePrefs.saveCredentials("https://myserver.example.com/", "user", "pass") }
    }

    @Test
    fun `an explicit https url is always accepted regardless of allowCleartext`() = runTest {
        val result = engine.connectToNavidrome("https://myserver.example.com", "user", "pass", allowCleartext = false)

        assertTrue(result.isSuccess)
        assertEquals("https://myserver.example.com/", hostManager.currentBaseUrl)
    }

    @Test
    fun `an explicit http url is rejected without acknowledgement`() = runTest {
        val result = engine.connectToNavidrome("http://192.168.1.100:4533", "user", "pass", allowCleartext = false)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CleartextNotAcknowledgedException)
        // Must not have proceeded to ping/save credentials for the un-acknowledged cleartext host.
        coVerify(exactly = 0) { navidromeRepository.pingServer(any(), any()) }
        coVerify(exactly = 0) { navidromePrefs.saveCredentials(any(), any(), any()) }
    }

    @Test
    fun `an explicit http url proceeds once cleartext is acknowledged`() = runTest {
        val result = engine.connectToNavidrome("http://192.168.1.100:4533", "user", "pass", allowCleartext = true)

        assertTrue(result.isSuccess)
        assertEquals("http://192.168.1.100:4533/", hostManager.currentBaseUrl)
        coVerify { navidromePrefs.saveCredentials("http://192.168.1.100:4533/", "user", "pass") }
    }

    @Test
    fun `trailing slash is normalized exactly once`() = runTest {
        engine.connectToNavidrome("https://myserver.example.com/", "user", "pass")

        assertEquals("https://myserver.example.com/", hostManager.currentBaseUrl)
    }
}

