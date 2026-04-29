package com.example.tigerplayer.ui.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.annotation.RequiresExtension
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import com.example.tigerplayer.data.repository.LyricsRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import com.example.tigerplayer.service.AudioPlayerService
import com.example.tigerplayer.service.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    private val mediaControllerManager: MediaControllerManager // 🔥 THE FIX: Direct bridge to ExoPlayer
) : ViewModel() {

    private val _cacheSizeFormatted = MutableStateFlow("Calculating...")
    val cacheSizeFormatted = _cacheSizeFormatted.asStateFlow()

    // --- AUDIO FIDELITY STATE ---
    val isBitPerfect: StateFlow<Boolean> = dataStore.data
        .map { it[BIT_PERFECT_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    @OptIn(UnstableApi::class)
    fun toggleBitPerfect() {
        viewModelScope.launch {
            val current = isBitPerfect.value
            dataStore.edit { it[BIT_PERFECT_KEY] = !current }

            // Dispatch the command instantly to the running Audio Service
            mediaControllerManager.mediaController?.sendCustomCommand(
                SessionCommand(AudioPlayerService.ACTION_TOGGLE_DSP, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
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
        private val BIT_PERFECT_KEY = booleanPreferencesKey("bit_perfect_mode")
    }
}