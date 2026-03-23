package com.example.tigerplayer.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.tigerplayer.data.repository.LyricsRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context, // THE FIX 1: Ensure "private val" is here
    private val spotifyAuthManager: SpotifyAuthManager,
    private val lyricsRepository: LyricsRepository, // THE NEW INJECTION
    private val mediaDataRepository: MediaDataRepository
) : ViewModel() {
    private val _cacheSizeFormatted = MutableStateFlow("Calculating...")
    val cacheSizeFormatted = _cacheSizeFormatted.asStateFlow()

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

    fun clearLyricsCache(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            lyricsRepository.clearLyricsCache()

            // Switch back to Main thread to update the UI
            launch(Dispatchers.Main) {
                onComplete()
            }
        }

    }
    fun clearTotalCache(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Purge the Physical Files (Coil Images, Temp Audio buffers)

            // 2. Purge the Database Archives
            lyricsRepository.clearLyricsCache()
            mediaDataRepository.clearArtistCache()

            // 3. Recalculate to show "0.00 KB" and notify UI
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
