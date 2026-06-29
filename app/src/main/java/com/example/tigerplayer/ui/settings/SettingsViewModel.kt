package com.example.tigerplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.local.DefaultPlayerView
import com.example.tigerplayer.data.local.SettingsDataStore
import com.example.tigerplayer.data.local.SkipShortAudio
import com.example.tigerplayer.data.local.ThemeMode
import com.example.tigerplayer.data.local.TigerAccentStyle
import com.example.tigerplayer.data.local.TigerSettingsState
import com.example.tigerplayer.data.source.LocalAudioDataSource
import com.example.tigerplayer.engine.LibraryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryRescanState(
    val isRunning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val lastRunCompletedAtMs: Long? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val libraryEngine: LibraryEngine
) : ViewModel() {

    val settingsState: StateFlow<TigerSettingsState> = settingsDataStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = TigerSettingsState()
        )

    // Backward-compatible theme stream consumed by MainActivity.
    val themeMode: StateFlow<ThemeMode> = settingsState
        .map { it.themeMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThemeMode.SYSTEM
        )

    private val _libraryRescanState = MutableStateFlow(LibraryRescanState())
    val libraryRescanState: StateFlow<LibraryRescanState> = _libraryRescanState.asStateFlow()

    private var rescanJob: Job? = null

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setPureAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setPureAmoledBlack(enabled) }
    }

    fun setAccentStyle(style: TigerAccentStyle) {
        viewModelScope.launch { settingsDataStore.setAccentStyle(style) }
    }

    fun setDefaultPlayerView(view: DefaultPlayerView) {
        viewModelScope.launch { settingsDataStore.setDefaultPlayerView(view) }
    }

    fun setCrossfadeDuration(seconds: Int) {
        viewModelScope.launch { settingsDataStore.setCrossfadeDurationSec(seconds) }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setGaplessPlayback(enabled) }
    }

    fun setAudioReactiveHaptics(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAudioReactiveHaptics(enabled) }
    }

    fun setSkipShortAudio(option: SkipShortAudio) {
        viewModelScope.launch { settingsDataStore.setSkipShortAudio(option) }
    }

    fun setResumeOnBluetoothConnect(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setResumeOnBluetoothConnect(enabled) }
    }

    fun setResumeOnWiredHeadsetConnect(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setResumeOnWiredHeadsetConnect(enabled) }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsDataStore.resetToDefaults()
        }
    }

    fun triggerLibraryRescan() {
        if (rescanJob?.isActive == true) return
        
        rescanJob = viewModelScope.launch {
            libraryEngine.getLocalAudioScanFlow(forceRefresh = true).collect { status ->
                when (status) {
                    is LocalAudioDataSource.ScanStatus.Started -> {
                        _libraryRescanState.value = LibraryRescanState(
                            isRunning = true,
                            current = 0,
                            total = status.total,
                            lastRunCompletedAtMs = _libraryRescanState.value.lastRunCompletedAtMs
                        )
                    }
                    is LocalAudioDataSource.ScanStatus.InProgress -> {
                        _libraryRescanState.value = _libraryRescanState.value.copy(
                            isRunning = true,
                            current = status.current,
                            total = status.total
                        )
                    }
                    is LocalAudioDataSource.ScanStatus.Complete -> {
                        _libraryRescanState.value = _libraryRescanState.value.copy(
                            isRunning = false,
                            current = _libraryRescanState.value.total,
                            lastRunCompletedAtMs = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }
}
