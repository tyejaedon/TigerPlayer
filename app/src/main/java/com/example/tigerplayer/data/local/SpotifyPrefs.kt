package com.example.tigerplayer.data.local

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
        const val SERVICE_TOKEN = "service_token"
        const val SERVICE_TOKEN_TIMESTAMP = "service_token_timestamp"
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
    private val _serviceToken = MutableStateFlow(readEncrypted(SERVICE_TOKEN))
    private val _serviceTokenTimestamp = MutableStateFlow(readLongOrNull(SERVICE_TOKEN_TIMESTAMP))

    val accessToken: Flow<String?> = _accessToken
    val tokenTimestamp: Flow<Long?> = _tokenTimestamp
    val serviceToken: Flow<String?> = _serviceToken
    val serviceTokenTimestamp: Flow<Long?> = _serviceTokenTimestamp

    suspend fun saveToken(token: String, timestamp: Long) {
        securePrefs.edit {
            putString(ACCESS_TOKEN, cipher.encrypt(token))
            putString(TOKEN_TIMESTAMP, cipher.encrypt(timestamp.toString()))
        }
        _accessToken.value = token
        _tokenTimestamp.value = timestamp
    }

    suspend fun saveServiceToken(token: String, timestamp: Long) {
        securePrefs.edit {
            putString(SERVICE_TOKEN, cipher.encrypt(token))
            putString(SERVICE_TOKEN_TIMESTAMP, cipher.encrypt(timestamp.toString()))
        }
        _serviceToken.value = token
        _serviceTokenTimestamp.value = timestamp
    }

    suspend fun clearToken() {
        securePrefs.edit {
            remove(ACCESS_TOKEN)
            remove(TOKEN_TIMESTAMP)
        }
        _accessToken.value = null
        _tokenTimestamp.value = null
    }
}