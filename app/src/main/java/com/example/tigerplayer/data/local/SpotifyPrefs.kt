package com.example.tigerplayer.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "spotify_prefs")

@Singleton
class SpotifyPrefs @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val TOKEN_TIMESTAMP = longPreferencesKey("token_timestamp")
        
        val SERVICE_TOKEN = stringPreferencesKey("service_token")
        val SERVICE_TOKEN_TIMESTAMP = longPreferencesKey("service_token_timestamp")
    }

    val accessToken: Flow<String?> = dataStore.data.map { it[ACCESS_TOKEN] }
    val tokenTimestamp: Flow<Long?> = dataStore.data.map { it[TOKEN_TIMESTAMP] }

    val serviceToken: Flow<String?> = dataStore.data.map { it[SERVICE_TOKEN] }
    val serviceTokenTimestamp: Flow<Long?> = dataStore.data.map { it[SERVICE_TOKEN_TIMESTAMP] }

    suspend fun saveToken(token: String, timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = token
            prefs[TOKEN_TIMESTAMP] = timestamp
        }
    }

    suspend fun saveServiceToken(token: String, timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[SERVICE_TOKEN] = token
            prefs[SERVICE_TOKEN_TIMESTAMP] = timestamp
        }
    }

    suspend fun clearToken() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(TOKEN_TIMESTAMP)
        }
    }
}