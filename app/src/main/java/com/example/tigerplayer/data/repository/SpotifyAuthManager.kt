package com.example.tigerplayer.data.repository

import android.util.Base64
import android.util.Log
import com.example.tigerplayer.BuildConfig
import com.example.tigerplayer.data.local.SpotifyPrefs
import com.example.tigerplayer.data.remote.api.SpotifyAuthApi
import javax.inject.Inject
import javax.inject.Singleton
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

    // --- SERVICE AUTH STATE (Client Credentials) ---
    private var serviceToken: String = ""
    private var serviceTokenTimestamp: Long = 0L

    // SECURED: Values are now fetched from BuildConfig, which reads from secrets.properties
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            // Load User Token
            val cachedToken = spotifyPrefs.accessToken.firstOrNull()
            val cachedTimestamp = spotifyPrefs.tokenTimestamp.firstOrNull()
            if (cachedToken != null && cachedTimestamp != null) {
                _token.value = cachedToken
                tokenTimestamp = cachedTimestamp
            }

            // Load Service Token
            serviceToken = spotifyPrefs.serviceToken.firstOrNull() ?: ""
            serviceTokenTimestamp = spotifyPrefs.serviceTokenTimestamp.firstOrNull() ?: 0L
        }
    }

    /**
     * THE MASTER TOKEN RITUAL
     * Returns the user's token if logged in, otherwise fetches a generic Service Token.
     */
    suspend fun getValidToken(): String = withContext(Dispatchers.IO) {
        // 1. Priority: Valid User Token
        if (_token.value.isNotEmpty() && !isTokenExpired(tokenTimestamp)) {
            return@withContext _token.value
        }

        // 2. Fallback: Valid Service Token
        if (serviceToken.isNotEmpty() && !isTokenExpired(serviceTokenTimestamp)) {
            return@withContext serviceToken
        }

        // 3. Last Resort: Fetch Fresh Service Token
        if (clientSecret.isNotEmpty()) {
            return@withContext fetchFreshServiceToken()
        }

        ""
    }

    private suspend fun fetchFreshServiceToken(): String {
        try {
            val authString = "$clientId:$clientSecret"
            val encodedAuth = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
            val basicAuth = "Basic $encodedAuth"

            val response = spotifyAuthApi.getServiceToken(basicAuth)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    serviceToken = body.accessToken
                    serviceTokenTimestamp = System.currentTimeMillis()
                    spotifyPrefs.saveServiceToken(serviceToken, serviceTokenTimestamp)
                    Log.d("SpotifyAuth", "Service Token Refreshed successfully.")
                    return serviceToken
                }
            }
        } catch (e: Exception) {
            Log.e("SpotifyAuth", "Failed to summon Service Token: ${e.message}")
        }
        return ""
    }

    fun updateToken(newToken: String) {
        _token.value = newToken
        tokenTimestamp = System.currentTimeMillis()
        scope.launch {
            spotifyPrefs.saveToken(newToken, tokenTimestamp)
        }
    }

    fun getToken(): String = _token.value

    fun isTokenExpired(timestamp: Long): Boolean {
        if (timestamp == 0L) return true
        val hourInMs = 60 * 60 * 1000L
        return System.currentTimeMillis() - timestamp > (hourInMs - 300000L) // 5 min buffer
    }

    fun logout() {
        _token.value = ""
        tokenTimestamp = 0L
        scope.launch {
            spotifyPrefs.clearToken()
        }
    }

    /**
     * THE SWAP RITUAL
     * Takes the temporary Authorization Code and securely exchanges it for an Access Token.
     */
    suspend fun exchangeCodeForToken(authCode: String?, redirectUri: String): String {
        return try {
            // Use the Client ID and Secret to forge the Basic Auth header
            val authString = "$clientId:$clientSecret"
            val encodedAuth = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
            val basicAuth = "Basic $encodedAuth"

            val response = spotifyAuthApi.getUserToken(
                authHeader = basicAuth,
                grantType = "authorization_code", // Explicitly define the ritual type
                code = authCode,
                redirectUri = redirectUri
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val newToken = body.accessToken
                    updateToken(newToken)
                    Log.d("SpotifyAuth", "Token forged successfully from Code!")
                    newToken
                } else {
                    Log.e("SpotifyAuth", "Ritual failed: Response body was empty.")
                    ""
                }
            } else {
                // Log the error body to see exactly why Spotify rejected the swap
                val errorBody = response.errorBody()?.string()
                Log.e("SpotifyAuth", "Swap failed with code ${response.code()}: $errorBody")
                ""
            }
        } catch (e: Exception) {
            Log.e("SpotifyAuth", "Exception during ritual: ${e.message}")
            ""
        }
    }
}

