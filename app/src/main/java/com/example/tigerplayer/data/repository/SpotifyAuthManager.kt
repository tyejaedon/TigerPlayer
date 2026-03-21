package com.example.tigerplayer.data.repository

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SpotifyAuthManager @Inject constructor() {
    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    private var tokenTimestamp: Long = 0L

    fun updateToken(newToken: String) {
        _token.value = newToken
        tokenTimestamp = System.currentTimeMillis()
    }

    fun getToken(): String = _token.value

    fun isTokenExpired(): Boolean {
        if (_token.value.isEmpty()) return true
        val hourInMs = 60 * 60 * 1000L
        return System.currentTimeMillis() - tokenTimestamp > (hourInMs - 300000L) // 5 min buffer
    }
}