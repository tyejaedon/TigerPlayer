package com.example.tigerplayer.utils

import java.security.MessageDigest
import java.util.UUID

data class NavidromeAuth(
    val u: String,
    val t: String,
    val s: String,
    val v: String = "1.16.1",
    val c: String = "TigerPlayer"
)

object NavidromeSecurity {

    fun generateAuthPayload(username: String, pass: String): NavidromeAuth {
        val salt = UUID.randomUUID().toString().substring(0, 8)
        val token = md5(pass + salt)
        return NavidromeAuth(u = username, t = token, s = salt)
    }

    // This helper is used by AudioRepository to generate valid Stream URIs
    fun generateToken(pass: String, salt: String): String {
        return md5(pass + salt)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}