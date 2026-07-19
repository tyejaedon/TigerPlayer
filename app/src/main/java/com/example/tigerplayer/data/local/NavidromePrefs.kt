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
class NavidromePrefs @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val PREF_FILE = "navidrome_secure_prefs"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEYSTORE_ALIAS = "tigerplayer_navidrome_key"
    }

    private val securePrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }
    private val cipher = SecurePrefsCipher(KEYSTORE_ALIAS)

    private fun readEncrypted(key: String): String? = cipher.decrypt(securePrefs.getString(key, null))

    private val _serverUrl = MutableStateFlow(readEncrypted(KEY_SERVER_URL))
    private val _username = MutableStateFlow(readEncrypted(KEY_USERNAME))
    private val _password = MutableStateFlow(readEncrypted(KEY_PASSWORD))

    val serverUrl: Flow<String?> = _serverUrl
    val username: Flow<String?> = _username
    val password: Flow<String?> = _password

    /**
     * Stores the credentials. This is called when you hit "Initiate Sync".
     */
    suspend fun saveCredentials(url: String, user: String, pass: String) {
        securePrefs.edit {
            putString(KEY_SERVER_URL, cipher.encrypt(url))
            putString(KEY_USERNAME, cipher.encrypt(user))
            putString(KEY_PASSWORD, cipher.encrypt(pass))
        }

        _serverUrl.value = url
        _username.value = user
        _password.value = pass
    }

    /**
     * Clears all server data. Perfect for a "Logout" or "De-link" ritual.
     */
    suspend fun clearCredentials() {
        securePrefs.edit { clear() }
        _serverUrl.value = null
        _username.value = null
        _password.value = null
    }
}