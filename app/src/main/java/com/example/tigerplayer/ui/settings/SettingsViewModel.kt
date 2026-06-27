package com.example.tigerplayer.ui.settings


import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import com.example.tigerplayer.engine.AcousticEnvironmentMode
import com.example.tigerplayer.data.repository.LyricsRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import com.example.tigerplayer.service.AudioPlayerService
import com.example.tigerplayer.service.MediaControllerManager
import com.example.tigerplayer.ui.theme.NeonContrastMode
import com.example.tigerplayer.ui.theme.NeonIntensityMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
    private val spotifyAuthManager: SpotifyAuthManager,
    private val lyricsRepository: LyricsRepository,
    private val mediaDataRepository: MediaDataRepository,
    private val mediaControllerManager: MediaControllerManager
) : ViewModel() {

    private val _cacheSizeFormatted = MutableStateFlow("Calculating...")
    val cacheSizeFormatted = _cacheSizeFormatted.asStateFlow()

    // --- AUDIO FIDELITY STATE ---
    val isBitPerfect: StateFlow<Boolean> = dataStore.data
        .map { it[BIT_PERFECT_KEY] ?: true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val acousticEnvironmentMode: StateFlow<AcousticEnvironmentMode> = dataStore.data
        .map { pref ->
            val raw = pref[ACOUSTIC_ENVIRONMENT_KEY] ?: AcousticEnvironmentMode.OFF.name
            runCatching { AcousticEnvironmentMode.valueOf(raw) }
                .getOrDefault(AcousticEnvironmentMode.OFF)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AcousticEnvironmentMode.OFF
        )

    val flowStateCrossfadeEnabled: StateFlow<Boolean> = dataStore.data
        .map { pref -> pref[FLOW_STATE_CROSSFADE_KEY] ?: true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val flowStateCrossfadeDurationSeconds: StateFlow<Int> = dataStore.data
        .map { pref -> (pref[FLOW_STATE_CROSSFADE_DURATION_SECONDS_KEY] ?: 7).coerceIn(0, 12) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 7
        )

    val neonContrastMode: StateFlow<NeonContrastMode> = dataStore.data
        .map { preferences ->
            val modeName = preferences[NEON_CONTRAST_MODE_KEY] ?: NeonContrastMode.BALANCED.name
            runCatching { NeonContrastMode.valueOf(modeName) }
                .getOrDefault(NeonContrastMode.BALANCED)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NeonContrastMode.BALANCED
        )

    val neonIntensityMode: StateFlow<NeonIntensityMode> = dataStore.data
        .map { preferences ->
            val modeName = preferences[NEON_INTENSITY_MODE_KEY] ?: NeonIntensityMode.BALANCED.name
            runCatching { NeonIntensityMode.valueOf(modeName) }
                .getOrDefault(NeonIntensityMode.BALANCED)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NeonIntensityMode.BALANCED
        )

    @OptIn(UnstableApi::class)
    fun toggleBitPerfect() {
        viewModelScope.launch {
            val current = isBitPerfect.value
            dataStore.edit { it[BIT_PERFECT_KEY] = !current }

            // Dispatch command instantly to the player service
            mediaControllerManager.mediaController?.sendCustomCommand(
                SessionCommand(AudioPlayerService.ACTION_TOGGLE_DSP, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
    }

    @OptIn(UnstableApi::class)
    fun setAcousticEnvironmentMode(mode: AcousticEnvironmentMode) {
        viewModelScope.launch {
            dataStore.edit { pref ->
                pref[ACOUSTIC_ENVIRONMENT_KEY] = mode.name
            }

            val args = Bundle().apply {
                putString(AudioPlayerService.EXTRA_ACOUSTIC_ENVIRONMENT_MODE, mode.name)
            }

            mediaControllerManager.mediaController?.sendCustomCommand(
                SessionCommand(AudioPlayerService.ACTION_SET_ACOUSTIC_ENVIRONMENT, Bundle.EMPTY),
                args
            )
        }
    }

    fun setFlowStateCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { pref ->
                pref[FLOW_STATE_CROSSFADE_KEY] = enabled
            }
            mediaControllerManager.setFlowStateCrossfadeEnabled(enabled)
        }
    }

    fun setFlowStateCrossfadeDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        viewModelScope.launch {
            dataStore.edit { pref ->
                pref[FLOW_STATE_CROSSFADE_DURATION_SECONDS_KEY] = clamped
            }
            mediaControllerManager.setFlowStateCrossfadeDurationSeconds(clamped)
        }
    }

    // --- APPEARANCE STATEFLOW (Optimized to avoid resource leak) ---
    val themeMode: StateFlow<ThemeMode> = dataStore.data
        .map { preferences ->
            val modeName = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            try {
                ThemeMode.valueOf(modeName)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[THEME_MODE_KEY] = mode.name
            }
        }
    }

    fun setNeonContrastMode(mode: NeonContrastMode) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[NEON_CONTRAST_MODE_KEY] = mode.name
            }
        }
    }

    fun setNeonIntensityMode(mode: NeonIntensityMode) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[NEON_INTENSITY_MODE_KEY] = mode.name
            }
        }
    }

    // --- CACHE & STORAGE ---
    init {
        calculateTotalCache()
        viewModelScope.launch {
            flowStateCrossfadeEnabled.collect { enabled ->
                mediaControllerManager.setFlowStateCrossfadeEnabled(enabled)
            }
        }
        viewModelScope.launch {
            flowStateCrossfadeDurationSeconds.collect { seconds ->
                mediaControllerManager.setFlowStateCrossfadeDurationSeconds(seconds)
            }
        }
    }

    private fun calculateTotalCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val internalCache = getDirSize(context.cacheDir)
            val externalCache = getDirSize(context.externalCacheDir)
            val totalBytes = internalCache + externalCache

            val kb = totalBytes / 1024f
            val mb = kb / 1024f

            val formatted = when {
                mb >= 1.0f -> String.format("%.2f MB", mb)
                kb > 0f -> String.format("%.2f KB", kb)
                else -> "0.00 KB"
            }
            _cacheSizeFormatted.value = formatted
        }
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    // --- PURGE ACTIONS ---
    fun clearTotalCache(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            lyricsRepository.clearLyricsCache()
            mediaDataRepository.clearArtistCache()
            calculateTotalCache()

            launch(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun logoutSpotify() {
        viewModelScope.launch {
            spotifyAuthManager.logout()
        }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val NEON_CONTRAST_MODE_KEY = stringPreferencesKey("neon_contrast_mode")
        private val NEON_INTENSITY_MODE_KEY = stringPreferencesKey("neon_intensity_mode")
        private val BIT_PERFECT_KEY = booleanPreferencesKey("bit_perfect_mode")
        private val ACOUSTIC_ENVIRONMENT_KEY = stringPreferencesKey("acoustic_environment_mode")
        private val FLOW_STATE_CROSSFADE_KEY = booleanPreferencesKey("flow_state_crossfade_enabled")
        private val FLOW_STATE_CROSSFADE_DURATION_SECONDS_KEY = intPreferencesKey("flow_state_crossfade_duration_seconds")
    }
}
