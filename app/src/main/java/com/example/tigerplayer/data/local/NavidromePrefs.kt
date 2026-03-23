package com.example.tigerplayer.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// The actual DataStore instance delegated to the Context
private val Context.dataStore by preferencesDataStore(name = "navidrome_prefs")

@Singleton
class NavidromePrefs @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        // These are the labels for the "drawers" in your local storage
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
    }

    // These Flows act as "Observers" that tell the app whenever credentials change
    val serverUrl: Flow<String?> = dataStore.data.map { it[SERVER_URL] }
    val username: Flow<String?> = dataStore.data.map { it[USERNAME] }
    val password: Flow<String?> = dataStore.data.map { it[PASSWORD] }

    /**
     * Stores the credentials. This is called when you hit "Initiate Sync".
     */
    suspend fun saveCredentials(url: String, user: String, pass: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = url
            prefs[USERNAME] = user
            prefs[PASSWORD] = pass
        }
    }

    /**
     * Clears all server data. Perfect for a "Logout" or "De-link" ritual.
     */
    suspend fun clearCredentials() {
        dataStore.edit { it.clear() }
    }
}