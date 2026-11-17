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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val _serverUrl = MutableStateFlow<String?>(null)
    private val _username = MutableStateFlow<String?>(null)
    private val _password = MutableStateFlow<String?>(null)

    val serverUrl: Flow<String?> = _serverUrl
    val username: Flow<String?> = _username
    val password: Flow<String?> = _password

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            val initServerUrl = readEncrypted(KEY_SERVER_URL)
            val initUsername = readEncrypted(KEY_USERNAME)
            val initPassword = readEncrypted(KEY_PASSWORD)

            _serverUrl.value = initServerUrl
            _username.value = initUsername
            _password.value = initPassword
        }
    }

    // Synchronous snapshots for callers that cannot suspend - notably the Media3 data-source
    // resolver and the Coil interceptor, which sign requests on a loading thread (issue #44).
    // Backed by the same in-memory StateFlow, so no disk or crypto work happens here.
    fun serverUrlSnapshot(): String? = _serverUrl.value

    fun usernameSnapshot(): String? = _username.value

    fun passwordSnapshot(): String? = _password.value

    /**
     * Stores the credentials. This is called when you hit "Initiate Sync".
     */
    suspend fun saveCredentials(url: String, user: String, pass: String) = withContext(Dispatchers.IO) {
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
    suspend fun clearCredentials() = withContext(Dispatchers.IO) {
        securePrefs.edit { clear() }
        _serverUrl.value = null
        _username.value = null
        _password.value = null
    }
}
