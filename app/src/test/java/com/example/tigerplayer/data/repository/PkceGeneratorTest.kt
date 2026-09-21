package com.tigerplayer.data.repository

import java.security.MessageDigest
import java.util.Base64 as JavaBase64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for issue #46 - PKCE replaces the client-secret Basic-auth flow.
 *
 * android.util.Base64 has no real implementation on the plain JVM, so this needs Robolectric
 * to exercise the actual code path used at runtime rather than re-implementing base64url by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PkceGeneratorTest {

    @Test
    fun `generateCodeVerifier produces a URL-safe unpadded string of sufficient length`() {
        val verifier = PkceGenerator.generateCodeVerifier()

        // RFC 7636 requires 43-128 characters; base64url(64 random bytes) lands well inside that.
        assertTrue("verifier length ${verifier.length} out of RFC 7636 bounds", verifier.length in 43..128)
        assertFalse("verifier must not contain padding", verifier.contains("="))
        assertTrue("verifier must be URL-safe base64", verifier.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun `generateCodeVerifier is not deterministic across calls`() {
        val first = PkceGenerator.generateCodeVerifier()
        val second = PkceGenerator.generateCodeVerifier()

        assertFalse(first == second)
    }

    @Test
    fun `generateCodeChallenge is deterministic base64url SHA-256 of the verifier with no padding`() {
        val verifier = "fixed-test-verifier-value-for-deterministic-hashing-1234567890"

        val challenge = PkceGenerator.generateCodeChallenge(verifier)

        val expectedDigest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val expectedChallenge = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(expectedDigest)

        assertEquals(expectedChallenge, challenge)
        assertFalse("challenge must not contain padding", challenge.contains("="))
    }

    @Test
    fun `generateCodeChallenge is stable for the same verifier`() {
        val verifier = PkceGenerator.generateCodeVerifier()

        val challengeA = PkceGenerator.generateCodeChallenge(verifier)
        val challengeB = PkceGenerator.generateCodeChallenge(verifier)

        assertEquals(challengeA, challengeB)
    }

    @Test
    fun `generateCodeChallenge differs for different verifiers`() {
        val verifierA = PkceGenerator.generateCodeVerifier()
        val verifierB = PkceGenerator.generateCodeVerifier()

        val challengeA = PkceGenerator.generateCodeChallenge(verifierA)
        val challengeB = PkceGenerator.generateCodeChallenge(verifierB)

        assertFalse(challengeA == challengeB)
    }
}

