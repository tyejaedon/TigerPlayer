package com.example.tigerplayer.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class TigerAccentStyle {
    NEON_ORANGE,
    CYBER_CYAN,
    TOXIC_LIME,
    SPECTRAL_VIOLET
}

enum class DefaultPlayerView {
    ARTWORK_3D,
    FLUID_VORTEX,
    SONIC_PRISM
}

enum class SkipShortAudio {
    OFF,
    BELOW_30_SECONDS,
    BELOW_60_SECONDS;

    val minDurationMs: Long
        get() = when (this) {
            OFF -> 0L
            BELOW_30_SECONDS -> 30_000L
            BELOW_60_SECONDS -> 60_000L
        }
}

data class TigerSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureAmoledBlack: Boolean = false,
    val accentStyle: TigerAccentStyle = TigerAccentStyle.NEON_ORANGE,
    val defaultPlayerView: DefaultPlayerView = DefaultPlayerView.ARTWORK_3D,
    val crossfadeDurationSec: Int = 0,
    val gaplessPlayback: Boolean = true,
    val audioReactiveHaptics: Boolean = false,
    val skipShortAudio: SkipShortAudio = SkipShortAudio.OFF,
    val resumeOnBluetoothConnect: Boolean = true,
    val resumeOnWiredHeadsetConnect: Boolean = false
)

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val settingsFlow: Flow<TigerSettingsState> = dataStore.data.map { prefs ->
        TigerSettingsState(
            themeMode = enumOrDefault(prefs[THEME_MODE], ThemeMode.SYSTEM),
            pureAmoledBlack = prefs[PURE_AMOLED_BLACK] ?: false,
            accentStyle = enumOrDefault(prefs[ACCENT_STYLE], TigerAccentStyle.NEON_ORANGE),
            defaultPlayerView = enumOrDefault(prefs[DEFAULT_PLAYER_VIEW], DefaultPlayerView.ARTWORK_3D),
            crossfadeDurationSec = (prefs[CROSSFADE_DURATION_SEC] ?: 0).coerceIn(0, 12),
            gaplessPlayback = prefs[GAPLESS_PLAYBACK] ?: true,
            audioReactiveHaptics = prefs[AUDIO_REACTIVE_HAPTICS] ?: false,
            skipShortAudio = enumOrDefault(prefs[SKIP_SHORT_AUDIO], SkipShortAudio.OFF),
            resumeOnBluetoothConnect = prefs[RESUME_ON_BLUETOOTH_CONNECT] ?: true,
            resumeOnWiredHeadsetConnect = prefs[RESUME_ON_WIRED_CONNECT] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setPureAmoledBlack(enabled: Boolean) {
        dataStore.edit { it[PURE_AMOLED_BLACK] = enabled }
    }

    suspend fun setAccentStyle(style: TigerAccentStyle) {
        dataStore.edit { it[ACCENT_STYLE] = style.name }
    }

    suspend fun setDefaultPlayerView(view: DefaultPlayerView) {
        dataStore.edit { it[DEFAULT_PLAYER_VIEW] = view.name }
    }

    suspend fun setCrossfadeDurationSec(seconds: Int) {
        dataStore.edit { it[CROSSFADE_DURATION_SEC] = seconds.coerceIn(0, 12) }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        dataStore.edit { it[GAPLESS_PLAYBACK] = enabled }
    }

    suspend fun setAudioReactiveHaptics(enabled: Boolean) {
        dataStore.edit { it[AUDIO_REACTIVE_HAPTICS] = enabled }
    }

    suspend fun setSkipShortAudio(option: SkipShortAudio) {
        dataStore.edit { it[SKIP_SHORT_AUDIO] = option.name }
    }

    suspend fun setResumeOnBluetoothConnect(enabled: Boolean) {
        dataStore.edit { it[RESUME_ON_BLUETOOTH_CONNECT] = enabled }
    }

    suspend fun setResumeOnWiredHeadsetConnect(enabled: Boolean) {
        dataStore.edit { it[RESUME_ON_WIRED_CONNECT] = enabled }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T {
        if (value.isNullOrBlank()) return default
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PURE_AMOLED_BLACK = booleanPreferencesKey("pure_amoled_black")
        val ACCENT_STYLE = stringPreferencesKey("accent_style")
        val DEFAULT_PLAYER_VIEW = stringPreferencesKey("default_player_view")
        val CROSSFADE_DURATION_SEC = intPreferencesKey("crossfade_duration_sec")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        val AUDIO_REACTIVE_HAPTICS = booleanPreferencesKey("audio_reactive_haptics")
        val SKIP_SHORT_AUDIO = stringPreferencesKey("skip_short_audio")
        val RESUME_ON_BLUETOOTH_CONNECT = booleanPreferencesKey("resume_on_bluetooth_connect")
        val RESUME_ON_WIRED_CONNECT = booleanPreferencesKey("resume_on_wired_connect")
    }
}

