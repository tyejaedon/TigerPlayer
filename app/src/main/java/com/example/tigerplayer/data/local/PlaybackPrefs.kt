package com.example.tigerplayer.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore by preferencesDataStore(name = "playback_prefs")

@Singleton
class PlaybackPrefs @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore = context.playbackDataStore

    companion object {
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val LAST_POSITION = longPreferencesKey("last_position")
        val LAST_QUEUE_IDS = stringPreferencesKey("last_queue_ids")
        val SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
    }

    val lastTrackId: Flow<String?> = dataStore.data.map { it[LAST_TRACK_ID] }
    val lastPosition: Flow<Long> = dataStore.data.map { it[LAST_POSITION] ?: 0L }
    val lastQueueIds: Flow<List<String>> = dataStore.data.map { 
        it[LAST_QUEUE_IDS]?.split(",")?.filter { id -> id.isNotEmpty() } ?: emptyList() 
    }
    val shuffleMode: Flow<Boolean> = dataStore.data.map { it[SHUFFLE_MODE] ?: false }
    val repeatMode: Flow<Int> = dataStore.data.map { it[REPEAT_MODE] ?: 0 } // Player.REPEAT_MODE_OFF

    suspend fun savePlaybackState(trackId: String?, position: Long, queueIds: List<String>) {
        dataStore.edit { prefs ->
            if (trackId != null) prefs[LAST_TRACK_ID] = trackId
            prefs[LAST_POSITION] = position
            prefs[LAST_QUEUE_IDS] = queueIds.joinToString(",")
        }
    }

    suspend fun savePosition(position: Long) {
        dataStore.edit { it[LAST_POSITION] = position }
    }

    suspend fun saveShuffleMode(enabled: Boolean) {
        dataStore.edit { it[SHUFFLE_MODE] = enabled }
    }

    suspend fun saveRepeatMode(mode: Int) {
        dataStore.edit { it[REPEAT_MODE] = mode }
    }
}