package com.tigerplayer.data.remote.api

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class LastfmSignatureTest {

    @Test
    fun `sign sorts keys lexicographically, excludes format and callback, and appends secret`() {
        val params = linkedMapOf(
            "track" to "Believe",
            "format" to "json",
            "api_key" to "test_key",
            "callback" to "tigerplayer://callback/lastfm",
            "artist" to "Cher",
            "method" to "track.scrobble"
        )
        val secret = "test_secret"

        val expectedPayload = "api_keytest_keyartistChermethodtrack.scrobbletrackBelievetest_secret"
        val expectedMd5 = MessageDigest.getInstance("MD5")
            .digest(expectedPayload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expectedMd5, LastfmSignature.sign(params, secret))
    }

    @Test
    fun `signedParams preserves original params and appends api_sig`() {
        val params = mapOf(
            "api_key" to "k1",
            "method" to "auth.getSession",
            "token" to "tok123"
        )
        val signed = LastfmSignature.signedParams(params, "sec1")

        assertEquals(4, signed.size)
        assertEquals("k1", signed["api_key"])
        assertEquals("auth.getSession", signed["method"])
        assertEquals("tok123", signed["token"])
        assertEquals(LastfmSignature.sign(params, "sec1"), signed["api_sig"])
    }
}
