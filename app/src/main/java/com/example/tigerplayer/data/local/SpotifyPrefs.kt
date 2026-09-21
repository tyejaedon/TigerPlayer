package com.tigerplayer.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyPrefs @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val PREF_FILE = "spotify_secure_prefs"
        const val ACCESS_TOKEN = "access_token"
        const val TOKEN_TIMESTAMP = "token_timestamp"
        const val REFRESH_TOKEN = "refresh_token"
        const val KEYSTORE_ALIAS = "tigerplayer_spotify_key"
    }

    private val securePrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }
    private val cipher = SecurePrefsCipher(KEYSTORE_ALIAS)

    private fun readEncrypted(key: String): String? = cipher.decrypt(securePrefs.getString(key, null))

    private fun readLongOrNull(key: String): Long? {
        return readEncrypted(key)?.toLongOrNull()
    }

    private val _accessToken = MutableStateFlow(readEncrypted(ACCESS_TOKEN))
    private val _tokenTimestamp = MutableStateFlow(readLongOrNull(TOKEN_TIMESTAMP))
    private val _refreshToken = MutableStateFlow(readEncrypted(REFRESH_TOKEN))

    val accessToken: Flow<String?> = _accessToken
    val tokenTimestamp: Flow<Long?> = _tokenTimestamp
    val refreshToken: Flow<String?> = _refreshToken

    suspend fun saveToken(token: String, timestamp: Long, refreshToken: String? = null) {
        securePrefs.edit {
            putString(ACCESS_TOKEN, cipher.encrypt(token))
            putString(TOKEN_TIMESTAMP, cipher.encrypt(timestamp.toString()))
            if (refreshToken != null) {
                putString(REFRESH_TOKEN, cipher.encrypt(refreshToken))
            }
        }
        _accessToken.value = token
        _tokenTimestamp.value = timestamp
        if (refreshToken != null) {
            _refreshToken.value = refreshToken
        }
    }

    suspend fun clearToken() {
        securePrefs.edit {
            remove(ACCESS_TOKEN)
            remove(TOKEN_TIMESTAMP)
            remove(REFRESH_TOKEN)
        }
        _accessToken.value = null
        _tokenTimestamp.value = null
        _refreshToken.value = null
    }
}
