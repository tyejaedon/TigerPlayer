package com.tigerplayer.data.repository

import android.util.Log
import com.tigerplayer.BuildConfig
import com.tigerplayer.data.local.SpotifyPrefs
import com.tigerplayer.data.remote.api.SpotifyAuthApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class SpotifyAuthManager @Inject constructor(
    private val spotifyPrefs: SpotifyPrefs,
    private val spotifyAuthApi: SpotifyAuthApi
) {
    // --- USER AUTH STATE ---
    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()
    private var tokenTimestamp: Long = 0L
    private var expiresInMs: Long = 3600_000L
    private var refreshToken: String = ""

    // Public client identifier only — no secret. PKCE requires no client authentication.
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            val cachedToken = spotifyPrefs.accessToken.firstOrNull()
            val cachedTimestamp = spotifyPrefs.tokenTimestamp.firstOrNull()
            if (cachedToken != null && cachedTimestamp != null) {
                _token.value = cachedToken
                tokenTimestamp = cachedTimestamp
            }
            refreshToken = spotifyPrefs.refreshToken.firstOrNull() ?: ""
        }
    }

    /**
     * Returns a valid access token, transparently refreshing via the stored refresh token
     * if the cached access token has expired. Returns "" if the user must re-authenticate.
     */
    suspend fun getValidToken(): String = withContext(Dispatchers.IO) {
        if (_token.value.isNotEmpty() && !isTokenExpired(tokenTimestamp)) {
            return@withContext _token.value
        }

        if (refreshToken.isNotEmpty()) {
            val refreshed = refreshAccessToken()
            if (refreshed.isNotEmpty()) return@withContext refreshed
        }

        ""
    }

    /**
     * Returns only a valid user token for user-scoped endpoints (for example /v1/me).
     * Behaves identically to [getValidToken] now that the client-credentials fallback is gone.
     */
    suspend fun getValidUserToken(): String = getValidToken()

    private suspend fun refreshAccessToken(): String {
        return try {
            val response = spotifyAuthApi.refreshToken(
                clientId = clientId,
                refreshToken = refreshToken
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    // Spotify may or may not rotate the refresh token on refresh.
                    val newRefreshToken = body.refreshToken ?: refreshToken
                    updateToken(body.accessToken, body.expiresIn, newRefreshToken)
                    Log.d("SpotifyAuth", "Access token refreshed successfully.")
                    body.accessToken
                } else {
                    Log.e("SpotifyAuth", "Refresh failed: response body was empty.")
                    ""
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("SpotifyAuth", "Refresh failed with code ${response.code()}: $errorBody")
                if (response.code() == 400 || response.code() == 401) {
                    // Refresh token itself is dead — force the user to log in again.
                    logout()
                }
                ""
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SpotifyAuth", "Exception during refresh: ${e.message}")
            ""
        }
    }

    private fun updateToken(newToken: String, expiresInSeconds: Int, newRefreshToken: String) {
        _token.value = newToken
        tokenTimestamp = System.currentTimeMillis()
        expiresInMs = expiresInSeconds * 1000L
        refreshToken = newRefreshToken
        scope.launch {
            spotifyPrefs.saveToken(newToken, tokenTimestamp, newRefreshToken)
        }
    }

    fun getToken(): String = _token.value

    fun isTokenExpired(timestamp: Long): Boolean {
        if (timestamp == 0L) return true
        val bufferMs = 300_000L // 5 min buffer
        return System.currentTimeMillis() - timestamp > (expiresInMs - bufferMs)
    }

    fun logout() {
        _token.value = ""
        tokenTimestamp = 0L
        refreshToken = ""
        scope.launch {
            spotifyPrefs.clearToken()
        }
    }

    /**
     * Exchanges the temporary Authorization Code for an Access Token using PKCE.
     * No client secret is required or sent — [codeVerifier] proves possession of the
     * original code_challenge instead.
     */
    suspend fun exchangeCodeForToken(
        authCode: String?,
        redirectUri: String,
        codeVerifier: String
    ): String {
        return try {
            val response = spotifyAuthApi.getUserToken(
                clientId = clientId,
                code = authCode,
                redirectUri = redirectUri,
                codeVerifier = codeVerifier
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    updateToken(body.accessToken, body.expiresIn, body.refreshToken.orEmpty())
                    Log.d("SpotifyAuth", "Token forged successfully from Code!")
                    body.accessToken
                } else {
                    Log.e("SpotifyAuth", "Exchange failed: response body was empty.")
                    ""
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("SpotifyAuth", "Swap failed with code ${response.code()}: $errorBody")
                ""
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SpotifyAuth", "Exception during ritual: ${e.message}")
            ""
        }
    }
}
