package com.tigerplayer.data.repository

import android.util.Log
import com.tigerplayer.BuildConfig
import com.tigerplayer.data.local.SpotifyPrefs
import com.tigerplayer.data.remote.api.SpotifyAuthApi
import com.tigerplayer.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class SpotifyAuthManager @Inject constructor(
    private val spotifyPrefs: SpotifyPrefs,
    private val spotifyAuthApi: SpotifyAuthApi,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        const val APP_REMOTE_CONTROL_SCOPE = "app-remote-control"

        val REQUESTED_SCOPES = listOf(
            APP_REMOTE_CONTROL_SCOPE,
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read",
            "user-read-private",
            "streaming"
        )
    }

    // --- USER AUTH STATE ---
    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()
    private val _grantedScope = MutableStateFlow<String?>(null)
    private var tokenTimestamp: Long = 0L
    private var expiresInMs: Long = 3600_000L
    private var refreshToken: String = ""

    // Public client identifier only — no secret. PKCE requires no client authentication.
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID

    // Uses the injected dispatcher (rather than a hardcoded Dispatchers.IO) so tests can supply a
    // TestDispatcher backed by the same TestScope scheduler as runTest, making the persistence
    // side effects (saveToken/clearToken) advance under virtual time instead of racing a real
    // background thread with a wall-clock coVerify(timeout = ...) (issue: flaky 400-refresh test).
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        scope.launch {
            val cachedToken = spotifyPrefs.accessToken.firstOrNull()
            val cachedTimestamp = spotifyPrefs.tokenTimestamp.firstOrNull()
            if (cachedToken != null && cachedTimestamp != null) {
                _token.value = cachedToken
                tokenTimestamp = cachedTimestamp
            }
            refreshToken = spotifyPrefs.refreshToken.firstOrNull() ?: ""
            _grantedScope.value = spotifyPrefs.grantedScope.firstOrNull()
        }
    }

    /**
     * Returns a valid access token, transparently refreshing via the stored refresh token
     * if the cached access token has expired. Returns "" if the user must re-authenticate.
     */
    suspend fun getValidToken(): String = withContext(ioDispatcher) {
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
                     updateToken(
                         newToken = body.accessToken,
                         expiresInSeconds = body.expiresIn,
                         newRefreshToken = newRefreshToken,
                         grantedScope = body.scope ?: _grantedScope.value
                     )
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
                     logoutSync()
                 }
                 ""
             }
         } catch (e: Exception) {
             if (e is CancellationException) throw e
             Log.e("SpotifyAuth", "Exception during refresh: ${e.message}")
             ""
         }
     }

    fun requiresAppRemoteReauth(): Boolean {
        val grantedScope = _grantedScope.value ?: return false
        return _token.value.isNotEmpty() && !containsScope(grantedScope, APP_REMOTE_CONTROL_SCOPE)
    }

    private fun containsScope(grantedScope: String, requiredScope: String): Boolean {
        return grantedScope
            .split(' ')
            .any { it.equals(requiredScope, ignoreCase = false) }
    }

    private fun updateToken(
        newToken: String,
        expiresInSeconds: Int,
        newRefreshToken: String,
        grantedScope: String? = _grantedScope.value
    ) {
        _token.value = newToken
        tokenTimestamp = System.currentTimeMillis()
        expiresInMs = expiresInSeconds * 1000L
        refreshToken = newRefreshToken
        if (grantedScope != null) {
            _grantedScope.value = grantedScope
        }
        scope.launch {
            spotifyPrefs.saveToken(
                token = newToken,
                timestamp = tokenTimestamp,
                refreshToken = newRefreshToken,
                grantedScope = grantedScope
            )
        }
    }

     fun getToken(): String = _token.value

     fun isTokenExpired(timestamp: Long): Boolean {
         if (timestamp == 0L) return true
         val bufferMs = 300_000L // 5 min buffer
         return System.currentTimeMillis() - timestamp > (expiresInMs - bufferMs)
     }

     /**
      * Synchronous logout used internally during token refresh failures.
      * Clears in-memory state immediately; persistence is async.
      */
     private fun logoutSync() {
         _token.value = ""
         _grantedScope.value = null
         tokenTimestamp = 0L
         refreshToken = ""
         scope.launch {
             spotifyPrefs.clearToken()
         }
     }

     /**
      * Public logout that suspends until token persistence is cleared.
      * This ensures callers wait for the full logout to complete, preventing race conditions.
      */
     suspend fun logout() {
         _token.value = ""
         _grantedScope.value = null
         tokenTimestamp = 0L
         refreshToken = ""
         spotifyPrefs.clearToken()
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
                    updateToken(
                        newToken = body.accessToken,
                        expiresInSeconds = body.expiresIn,
                        newRefreshToken = body.refreshToken.orEmpty(),
                        grantedScope = body.scope
                    )
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
