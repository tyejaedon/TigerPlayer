package com.tigerplayer.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.widgetPlaybackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tiger_widget_state"
)

/**
 * Snapshot of current playback metadata, progress, and snapped neon palette
 * persisted for Glance home screen and cover screen widgets.
 */
data class WidgetPlaybackState(
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val artworkPath: String? = null,
    val primaryNeonArgb: Int = DEFAULT_PRIMARY_NEON_ARGB,
    val secondaryNeonArgb: Int = DEFAULT_SECONDARY_NEON_ARGB,
    val updatedAtEpochMs: Long = 0L
) {
    val hasTrack: Boolean
        get() = title.isNotBlank() || trackId.isNotBlank()

    val progressFraction: Float
        get() = computeProgressFraction(positionMs, durationMs)

    companion object {
        const val DEFAULT_PRIMARY_NEON_ARGB: Int = 0xFFFF1744.toInt() // TigerNeonRed
        const val DEFAULT_SECONDARY_NEON_ARGB: Int = 0xFFFF7A1A.toInt() // TigerNeonOrange

        fun computeProgressFraction(positionMs: Long, durationMs: Long): Float {
            if (durationMs <= 0L || positionMs <= 0L) return 0f
            return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
    }
}

@Singleton
class WidgetStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TRACK_ID = stringPreferencesKey("track_id")
        val TITLE = stringPreferencesKey("title")
        val ARTIST = stringPreferencesKey("artist")
        val IS_PLAYING = booleanPreferencesKey("is_playing")
        val POSITION_MS = longPreferencesKey("position_ms")
        val DURATION_MS = longPreferencesKey("duration_ms")
        val ARTWORK_PATH = stringPreferencesKey("artwork_path")
        val PRIMARY_NEON_ARGB = intPreferencesKey("primary_neon_argb")
        val SECONDARY_NEON_ARGB = intPreferencesKey("secondary_neon_argb")
        val UPDATED_AT_EPOCH_MS = longPreferencesKey("updated_at_epoch_ms")
    }

    val stateFlow: Flow<WidgetPlaybackState> = context.widgetPlaybackDataStore.data.map { prefs ->
        prefs.toWidgetPlaybackState()
    }

    suspend fun read(): WidgetPlaybackState {
        return context.widgetPlaybackDataStore.data.first().toWidgetPlaybackState()
    }

    suspend fun write(state: WidgetPlaybackState) {
        context.widgetPlaybackDataStore.edit { prefs ->
            prefs[Keys.TRACK_ID] = state.trackId
            prefs[Keys.TITLE] = state.title
            prefs[Keys.ARTIST] = state.artist
            prefs[Keys.IS_PLAYING] = state.isPlaying
            prefs[Keys.POSITION_MS] = state.positionMs
            prefs[Keys.DURATION_MS] = state.durationMs
            if (state.artworkPath != null) {
                prefs[Keys.ARTWORK_PATH] = state.artworkPath
            } else {
                prefs.remove(Keys.ARTWORK_PATH)
            }
            prefs[Keys.PRIMARY_NEON_ARGB] = state.primaryNeonArgb
            prefs[Keys.SECONDARY_NEON_ARGB] = state.secondaryNeonArgb
            prefs[Keys.UPDATED_AT_EPOCH_MS] = state.updatedAtEpochMs
        }
    }

    private fun Preferences.toWidgetPlaybackState(): WidgetPlaybackState {
        return WidgetPlaybackState(
            trackId = this[Keys.TRACK_ID].orEmpty(),
            title = this[Keys.TITLE].orEmpty(),
            artist = this[Keys.ARTIST].orEmpty(),
            isPlaying = this[Keys.IS_PLAYING] ?: false,
            positionMs = this[Keys.POSITION_MS] ?: 0L,
            durationMs = this[Keys.DURATION_MS] ?: 0L,
            artworkPath = this[Keys.ARTWORK_PATH],
            primaryNeonArgb = this[Keys.PRIMARY_NEON_ARGB] ?: WidgetPlaybackState.DEFAULT_PRIMARY_NEON_ARGB,
            secondaryNeonArgb = this[Keys.SECONDARY_NEON_ARGB] ?: WidgetPlaybackState.DEFAULT_SECONDARY_NEON_ARGB,
            updatedAtEpochMs = this[Keys.UPDATED_AT_EPOCH_MS] ?: 0L
        )
    }
}
