package com.tigerplayer.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted credential store for Last.fm session key and username.
 * The raw session key is never exposed to UI state or logged.
 */
@Singleton
class LastfmPrefs @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val PREF_FILE = "lastfm_secure_prefs"
        const val SESSION_KEY = "session_key"
        const val USERNAME = "username"
        const val KEYSTORE_ALIAS = "tigerplayer_lastfm_key"
    }

    private val securePrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }
    private val cipher = SecurePrefsCipher(KEYSTORE_ALIAS)

    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    val usernameFlow: Flow<String?> = _username

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            val storedSessionKey = readEncrypted(SESSION_KEY)
            val storedUsername = readEncrypted(USERNAME)
            _username.value = storedUsername
            _isConnected.value = !storedSessionKey.isNullOrBlank()
        }
    }

    private fun readEncrypted(key: String): String? =
        cipher.decrypt(securePrefs.getString(key, null))

    /**
     * Reads the decrypted session key synchronously for API signing on a background thread.
     */
    fun sessionKeySnapshot(): String? = readEncrypted(SESSION_KEY)

    /**
     * Encrypts and persists the Last.fm [sessionKey] and [username].
     */
    suspend fun saveSession(sessionKey: String, username: String) = withContext(Dispatchers.IO) {
        securePrefs.edit {
            putString(SESSION_KEY, cipher.encrypt(sessionKey))
            putString(USERNAME, cipher.encrypt(username))
        }
        _username.value = username
        _isConnected.value = sessionKey.isNotBlank()
    }

    /**
     * Removes stored Last.fm credentials.
     */
    suspend fun clearSession() = withContext(Dispatchers.IO) {
        securePrefs.edit {
            remove(SESSION_KEY)
            remove(USERNAME)
        }
        _username.value = null
        _isConnected.value = false
    }
}
