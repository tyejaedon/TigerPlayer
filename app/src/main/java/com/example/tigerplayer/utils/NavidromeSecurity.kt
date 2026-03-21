package com.example.tigerplayer.utils

import java.security.MessageDigest
import java.util.UUID

object NavidromeSecurity {

    // The payload we will inject into our Retrofit API calls
    data class AuthPayload(
        val u: String,     // Username
        val t: String,     // Token (MD5 Hash)
        val s: String,     // Salt
        val v: String = "1.16.1", // Subsonic API version
        val c: String = "TigerPlayer" // Client name
    )

    /**
     * Forges the secure token required by the Subsonic API.
     */
    fun generateAuthPayload(username: String, password: String): AuthPayload {
        val salt = generateSalt()
        val token = md5(password + salt)

        return AuthPayload(
            u = username,
            t = token,
            s = salt
        )
    }

    private fun generateSalt(): String {
        // A random 8-character string is perfect for the Subsonic salt
        return UUID.randomUUID().toString().substring(0, 8)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())

        // Convert the byte array into a clean hex string
        return digested.joinToString("") {
            String.format("%02x", it)
        }
    }
}