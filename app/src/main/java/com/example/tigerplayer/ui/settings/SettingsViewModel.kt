package com.example.tigerplayer.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.repository.LyricsRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import com.example.tigerplayer.engine.EqEngine
import com.example.tigerplayer.engine.EqUiState
import com.example.tigerplayer.service.PeqProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
    private val eqEngine: EqEngine // 🔥 NEW: Injected the sophisticated EqEngine
) : ViewModel() {

    private val _cacheSizeFormatted = MutableStateFlow("Calculating...")
    val cacheSizeFormatted = _cacheSizeFormatted.asStateFlow()

    // --- AUDIO FIDELITY DELEGATION ---
    val eqState: StateFlow<EqUiState> = eqEngine.uiState

    fun toggleBitPerfect() {
        eqEngine.toggleBitPerfect()
    }

    fun loadEqProfile(profile: PeqProfile) {
        eqEngine.loadProfile(profile)
    }

    // --- CACHE & STORAGE ---
    init {
        calculateTotalCache()
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

    // --- APPEARANCE ---
    val themeMode: Flow<ThemeMode> = dataStore.data
        .map { preferences ->
            val modeName = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            try {
                ThemeMode.valueOf(modeName)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
        }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[THEME_MODE_KEY] = mode.name
            }
        }
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
    }
}