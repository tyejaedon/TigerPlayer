package com.example.tigerplayer.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

enum class PrismSpectralAnalysis {
    BANDPASS,
    FFT
}

enum class AudioReactiveHapticsProfile {
    SUBTLE,
    BALANCED,
    AGGRESSIVE
}

data class TigerSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureAmoledBlack: Boolean = false,
    val disablePip: Boolean = false,
    val accentStyle: TigerAccentStyle = TigerAccentStyle.NEON_ORANGE,
    val defaultPlayerView: DefaultPlayerView = DefaultPlayerView.ARTWORK_3D,
    val crossfadeDurationSec: Int = 0,
    val gaplessPlayback: Boolean = true,
    val audioReactiveHaptics: Boolean = false,
    val audioReactiveHapticsProfile: AudioReactiveHapticsProfile = AudioReactiveHapticsProfile.BALANCED,
    val skipShortAudio: SkipShortAudio = SkipShortAudio.OFF,
    val routeToSystemDecoderDsp: Boolean = true,
    val resumeOnBluetoothConnect: Boolean = true,
    val resumeOnWiredHeadsetConnect: Boolean = false,
    val prismEnabled: Boolean = false,
    val prismVocals: Float = 1f,
    val prismBeats: Float = 1f,
    val prismInstruments: Float = 1f,
    val prismSpectralAnalysis: PrismSpectralAnalysis = PrismSpectralAnalysis.FFT
)

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val settingsFlow: Flow<TigerSettingsState> = dataStore.data.map { prefs ->
        TigerSettingsState(
            themeMode = enumOrDefault(prefs[THEME_MODE], ThemeMode.SYSTEM),
            pureAmoledBlack = prefs[PURE_AMOLED_BLACK] ?: false,
            disablePip = prefs[DISABLE_PIP] ?: false,
            accentStyle = enumOrDefault(prefs[ACCENT_STYLE], TigerAccentStyle.NEON_ORANGE),
            defaultPlayerView = enumOrDefault(prefs[DEFAULT_PLAYER_VIEW], DefaultPlayerView.ARTWORK_3D),
            crossfadeDurationSec = (prefs[CROSSFADE_DURATION_SEC] ?: 0).coerceIn(0, 12),
            gaplessPlayback = prefs[GAPLESS_PLAYBACK] ?: true,
            audioReactiveHaptics = prefs[AUDIO_REACTIVE_HAPTICS] ?: false,
            audioReactiveHapticsProfile = enumOrDefault(
                prefs[AUDIO_REACTIVE_HAPTICS_PROFILE],
                AudioReactiveHapticsProfile.BALANCED
            ),
            skipShortAudio = enumOrDefault(prefs[SKIP_SHORT_AUDIO], SkipShortAudio.OFF),
            routeToSystemDecoderDsp = prefs[ROUTE_TO_SYSTEM_DECODER_DSP] ?: true,
            resumeOnBluetoothConnect = prefs[RESUME_ON_BLUETOOTH_CONNECT] ?: true,
            resumeOnWiredHeadsetConnect = prefs[RESUME_ON_WIRED_CONNECT] ?: false,
            prismEnabled = prefs[PRISM_ENABLED] ?: false,
            prismVocals = (prefs[PRISM_VOCALS] ?: 1f).coerceIn(0f, 1f),
            prismBeats = (prefs[PRISM_BEATS] ?: 1f).coerceIn(0f, 1f),
            prismInstruments = (prefs[PRISM_INSTRUMENTS] ?: 1f).coerceIn(0f, 1f),
            prismSpectralAnalysis = enumOrDefault(prefs[PRISM_SPECTRAL_ANALYSIS], PrismSpectralAnalysis.FFT)
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setPureAmoledBlack(enabled: Boolean) {
        dataStore.edit { it[PURE_AMOLED_BLACK] = enabled }
    }

    suspend fun setDisablePip(disabled: Boolean) {
        dataStore.edit { it[DISABLE_PIP] = disabled }
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

    suspend fun setAudioReactiveHapticsProfile(profile: AudioReactiveHapticsProfile) {
        dataStore.edit { it[AUDIO_REACTIVE_HAPTICS_PROFILE] = profile.name }
    }

    suspend fun setSkipShortAudio(option: SkipShortAudio) {
        dataStore.edit { it[SKIP_SHORT_AUDIO] = option.name }
    }

    suspend fun setRouteToSystemDecoderDsp(enabled: Boolean) {
        dataStore.edit { it[ROUTE_TO_SYSTEM_DECODER_DSP] = enabled }
    }

    suspend fun setResumeOnBluetoothConnect(enabled: Boolean) {
        dataStore.edit { it[RESUME_ON_BLUETOOTH_CONNECT] = enabled }
    }

    suspend fun setResumeOnWiredHeadsetConnect(enabled: Boolean) {
        dataStore.edit { it[RESUME_ON_WIRED_CONNECT] = enabled }
    }

    suspend fun setPrismEnabled(enabled: Boolean) {
        dataStore.edit { it[PRISM_ENABLED] = enabled }
    }

    suspend fun setPrismMix(vocals: Float, beats: Float, instruments: Float) {
        dataStore.edit {
            it[PRISM_VOCALS] = vocals.coerceIn(0f, 1f)
            it[PRISM_BEATS] = beats.coerceIn(0f, 1f)
            it[PRISM_INSTRUMENTS] = instruments.coerceIn(0f, 1f)
        }
    }

    suspend fun setPrismSpectralAnalysis(mode: PrismSpectralAnalysis) {
        dataStore.edit { it[PRISM_SPECTRAL_ANALYSIS] = mode.name }
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
        val DISABLE_PIP = booleanPreferencesKey("disable_pip")
        val ACCENT_STYLE = stringPreferencesKey("accent_style")
        val DEFAULT_PLAYER_VIEW = stringPreferencesKey("default_player_view")
        val CROSSFADE_DURATION_SEC = intPreferencesKey("crossfade_duration_sec")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        val AUDIO_REACTIVE_HAPTICS = booleanPreferencesKey("audio_reactive_haptics")
        val AUDIO_REACTIVE_HAPTICS_PROFILE = stringPreferencesKey("audio_reactive_haptics_profile")
        val SKIP_SHORT_AUDIO = stringPreferencesKey("skip_short_audio")
        val ROUTE_TO_SYSTEM_DECODER_DSP = booleanPreferencesKey("route_to_system_decoder_dsp")
        val RESUME_ON_BLUETOOTH_CONNECT = booleanPreferencesKey("resume_on_bluetooth_connect")
        val RESUME_ON_WIRED_CONNECT = booleanPreferencesKey("resume_on_wired_connect")
        val PRISM_ENABLED = booleanPreferencesKey("prism_enabled")
        val PRISM_VOCALS = floatPreferencesKey("prism_vocals")
        val PRISM_BEATS = floatPreferencesKey("prism_beats")
        val PRISM_INSTRUMENTS = floatPreferencesKey("prism_instruments")
        val PRISM_SPECTRAL_ANALYSIS = stringPreferencesKey("prism_spectral_analysis")
    }
}

