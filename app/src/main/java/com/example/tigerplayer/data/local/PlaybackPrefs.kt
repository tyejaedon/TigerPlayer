package com.example.tigerplayer.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
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
        val ORIGINAL_QUEUE_IDS = stringPreferencesKey("original_queue_ids")
        val LAST_QUEUE_SNAPSHOT = stringPreferencesKey("last_queue_snapshot")
        val EQ_NODES_DATA = stringPreferencesKey("eq_nodes_data")
        val CURRENT_MOOD = stringPreferencesKey("current_mood")
        val ACOUSTIC_ENVIRONMENT_MODE = stringPreferencesKey("acoustic_environment_mode")
        val FLOW_STATE_ENABLED = booleanPreferencesKey("flow_state_enabled")
        val FLOW_STATE_WINDOW_MS = longPreferencesKey("flow_state_window_ms")
        val FLOW_STATE_TRUE_OVERLAP = booleanPreferencesKey("flow_state_true_overlap")
        val FULL_PLAYER_ACTIVE = booleanPreferencesKey("full_player_active")
    }

    val lastTrackId: Flow<String?> = dataStore.data.map { it[LAST_TRACK_ID] }
    val lastPosition: Flow<Long> = dataStore.data.map { it[LAST_POSITION] ?: 0L }
    val lastQueueIds: Flow<List<String>> = dataStore.data.map {
        it[LAST_QUEUE_IDS]?.split(",")?.filter { id -> id.isNotEmpty() } ?: emptyList()
    }
    val shuffleMode: Flow<Boolean> = dataStore.data.map { it[SHUFFLE_MODE] ?: false }
    val repeatMode: Flow<Int> = dataStore.data.map { it[REPEAT_MODE] ?: 0 }
    val originalQueueIds: Flow<List<String>> = dataStore.data.map {
        it[ORIGINAL_QUEUE_IDS]?.split(",")?.filter { id -> id.isNotEmpty() } ?: emptyList()
    }
    val lastQueueSnapshot: Flow<String?> = dataStore.data.map { it[LAST_QUEUE_SNAPSHOT] }
    val eqNodesData: Flow<String?> = dataStore.data.map { it[EQ_NODES_DATA] }
    val currentMood: Flow<String?> = dataStore.data.map { it[CURRENT_MOOD] }
    val acousticEnvironmentMode: Flow<String> = dataStore.data.map {
        it[ACOUSTIC_ENVIRONMENT_MODE] ?: "STUDIO"
    }
    val flowStateEnabled: Flow<Boolean> = dataStore.data.map {
        it[FLOW_STATE_ENABLED] ?: true
    }
    val flowStateWindowMs: Flow<Long> = dataStore.data.map {
        it[FLOW_STATE_WINDOW_MS] ?: 7000L
    }
    val flowStateTrueOverlap: Flow<Boolean> = dataStore.data.map {
        it[FLOW_STATE_TRUE_OVERLAP] ?: true
    }
    val fullPlayerActive: Flow<Boolean> = dataStore.data.map {
        it[FULL_PLAYER_ACTIVE] ?: false
    }

    fun getBtListeningTime(address: String): Flow<Long> = dataStore.data.map {
        it[longPreferencesKey("bt_time_$address")] ?: 0L
    }

    suspend fun saveBtListeningTime(address: String, timeMs: Long) {
        dataStore.edit { it[longPreferencesKey("bt_time_$address")] = timeMs }
    }

    suspend fun saveEqState(nodesData: String, mood: String) {
        dataStore.edit { prefs ->
            prefs[EQ_NODES_DATA] = nodesData
            prefs[CURRENT_MOOD] = mood
        }
    }

    suspend fun saveAcousticEnvironmentMode(modeName: String) {
        dataStore.edit { prefs ->
            prefs[ACOUSTIC_ENVIRONMENT_MODE] = modeName
        }
    }

    suspend fun saveFlowStateEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[FLOW_STATE_ENABLED] = enabled
        }
    }

    suspend fun saveFlowStateWindowMs(windowMs: Long) {
        dataStore.edit { prefs ->
            prefs[FLOW_STATE_WINDOW_MS] = windowMs.coerceIn(3000L, 12000L)
        }
    }

    suspend fun saveFlowStateTrueOverlap(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[FLOW_STATE_TRUE_OVERLAP] = enabled
        }
    }

    suspend fun saveFullPlayerActive(active: Boolean) {
        dataStore.edit { prefs ->
            prefs[FULL_PLAYER_ACTIVE] = active
        }
    }

    suspend fun savePlaybackState(
        trackId: String?,
        position: Long,
        queueIds: List<String>,
        originalQueueIds: List<String>? = null,
        queueSnapshot: String? = null
    ) {
        dataStore.edit { prefs ->
            if (trackId != null) {
                prefs[LAST_TRACK_ID] = trackId
            } else {
                prefs.remove(LAST_TRACK_ID)
            }
            prefs[LAST_POSITION] = position
            prefs[LAST_QUEUE_IDS] = queueIds.joinToString(",")
            if (originalQueueIds != null) {
                prefs[ORIGINAL_QUEUE_IDS] = originalQueueIds.joinToString(",")
            } else {
                prefs.remove(ORIGINAL_QUEUE_IDS)
            }
            if (queueSnapshot != null) {
                prefs[LAST_QUEUE_SNAPSHOT] = queueSnapshot
            } else {
                prefs.remove(LAST_QUEUE_SNAPSHOT)
            }
        }
    }

    suspend fun clearPlaybackState() {
        dataStore.edit { prefs ->
            prefs.remove(LAST_TRACK_ID)
            prefs[LAST_POSITION] = 0L
            prefs[LAST_QUEUE_IDS] = ""
            prefs.remove(ORIGINAL_QUEUE_IDS)
            prefs.remove(LAST_QUEUE_SNAPSHOT)
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