package com.example.tigerplayer.data.remote

import android.net.Uri
import com.example.tigerplayer.data.local.NavidromePrefs
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Coverage for issue #44.
 *
 * Persisted Navidrome URIs must carry no credentials, and signing must happen per request so a
 * restored queue still plays after the original salted token has rotated.
 */
@RunWith(RobolectricTestRunner::class)
class NavidromeUriTest {

    private val prefs = mockk<NavidromePrefs>(relaxed = true)

    private fun signer(
        base: String? = "https://music.example.com/",
        user: String? = "tyej",
        pass: String? = "hunter2"
    ): NavidromeUrlSigner {
        every { prefs.serverUrlSnapshot() } returns base
        every { prefs.usernameSnapshot() } returns user
        every { prefs.passwordSnapshot() } returns pass
        return NavidromeUrlSigner(prefs)
    }

    // --- opaque URIs ---

    @Test
    fun `a stream uri carries the id and nothing else`() {
        val uri = NavidromeUri.stream("track-123")

        assertEquals("navidrome://stream/track-123", uri.toString())
        assertEquals("track-123", NavidromeUri.resourceId(uri))
        assertTrue(NavidromeUri.isStream(uri))
        assertFalse(NavidromeUri.isArt(uri))
    }

    @Test
    fun `an art uri is distinguishable from a stream uri`() {
        val uri = NavidromeUri.art("album-9")

        assertEquals("navidrome://art/album-9", uri.toString())
        assertTrue(NavidromeUri.isArt(uri))
        assertFalse(NavidromeUri.isStream(uri))
    }

    @Test
    fun `an opaque uri never contains credential parameters`() {
        val stream = NavidromeUri.stream("abc").toString()
        val art = NavidromeUri.art("abc").toString()

        listOf(stream, art).forEach { value ->
            assertFalse("uri must not carry a username: $value", value.contains("u="))
            assertFalse("uri must not carry a token: $value", value.contains("t="))
            assertFalse("uri must not carry a salt: $value", value.contains("s="))
        }
    }

    @Test
    fun `a non navidrome uri is not claimed`() {
        val http = Uri.parse("https://example.com/song.flac")

        assertFalse(NavidromeUri.isNavidrome(http))
        assertNull(NavidromeUri.resourceId(http))
    }

    // --- signing ---

    @Test
    fun `signing a stream uri produces exactly one of each auth parameter`() {
        val signed = signer().sign(NavidromeUri.stream("track-123"))!!

        assertEquals("track-123", signed.getQueryParameter("id"))
        assertEquals("tyej", signed.getQueryParameter("u"))
        assertEquals(1, signed.getQueryParameters("u").size)
        assertEquals(1, signed.getQueryParameters("t").size)
        assertEquals(1, signed.getQueryParameters("s").size)
        assertTrue(signed.toString().startsWith("https://music.example.com/rest/stream.view?"))
    }

    @Test
    fun `signing an art uri targets the cover art endpoint and requests a size`() {
        val signed = signer().sign(NavidromeUri.art("album-9"))!!

        assertTrue(signed.toString().contains("rest/getCoverArt.view"))
        assertEquals("500", signed.getQueryParameter("size"))
    }

    @Test
    fun `a stream url omits format and bitrate so the original file is served`() {
        val signed = signer().sign(NavidromeUri.stream("track-123"))!!

        assertNull(signed.getQueryParameter("format"))
        assertNull(signed.getQueryParameter("maxBitRate"))
    }

    @Test
    fun `a base url without a trailing slash is normalized`() {
        val signed = signer(base = "https://music.example.com").sign(NavidromeUri.stream("x"))!!

        assertTrue(signed.toString().startsWith("https://music.example.com/rest/stream.view?"))
        assertFalse(signed.toString().contains("com//rest"))
    }

    @Test
    fun `each signature uses a fresh salt and token`() {
        val s = signer()
        val first = s.sign(NavidromeUri.stream("track-123"))!!
        val second = s.sign(NavidromeUri.stream("track-123"))!!

        assertTrue(
            "salt must rotate per request",
            first.getQueryParameter("s") != second.getQueryParameter("s")
        )
        assertTrue(
            "token must rotate with the salt",
            first.getQueryParameter("t") != second.getQueryParameter("t")
        )
    }

    @Test
    fun `signing returns null when credentials are absent`() {
        assertNull(signer(base = null).sign(NavidromeUri.stream("x")))
        assertNull(signer(user = null).sign(NavidromeUri.stream("x")))
        assertNull(signer(pass = null).sign(NavidromeUri.stream("x")))
        assertNull(signer(user = "").sign(NavidromeUri.stream("x")))
    }

    @Test
    fun `signing ignores uris that are not ours`() {
        assertNull(signer().sign(Uri.parse("https://example.com/song.flac")))
        assertNull(signer().sign(Uri.parse("content://media/external/audio/media/12")))
    }

    @Test
    fun `an unknown navidrome host is rejected rather than guessed`() {
        val odd = Uri.parse("navidrome://unknown/track-1")

        assertNull(signer().sign(odd))
    }
}

