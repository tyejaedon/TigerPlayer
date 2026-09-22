package com.tigerplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.tigerplayer.data.backup.BackupManager
import com.tigerplayer.data.backup.RestoreStrategy
import com.tigerplayer.data.local.AudioReactiveHapticsProfile
import com.tigerplayer.data.local.DefaultPlayerView
import com.tigerplayer.data.local.NavidromePrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.SkipShortAudio
import com.tigerplayer.data.local.ThemeMode
import com.tigerplayer.data.local.TigerAccentStyle
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.model.MusicFolder
import com.tigerplayer.data.repository.SpotifyAuthManager
import com.tigerplayer.data.repository.SpotifyRepository
import com.tigerplayer.data.source.LocalAudioDataSource
import com.tigerplayer.engine.LibraryEngine
import com.tigerplayer.service.HapticsDebugMonitor
import com.tigerplayer.service.HapticsDebugState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryRescanState(
    val isRunning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val lastRunCompletedAtMs: Long? = null
)

/** One-shot outcome of the last export/import, surfaced by the Settings screen then cleared. */
sealed interface BackupUiEvent {
    data class ExportSucceeded(val playlists: Int, val history: Int) : BackupUiEvent
    data class ImportSucceeded(val playlists: Int, val history: Int) : BackupUiEvent
    data class Failed(val message: String) : BackupUiEvent
}

/** Connection status for the third-party accounts surfaced in the Settings "Connected Accounts" section. */
data class ConnectedAccountsState(
    val isSpotifyConnected: Boolean = false,
    val navidromeServerUrl: String? = null
) {
    val isNavidromeConnected: Boolean get() = !navidromeServerUrl.isNullOrBlank()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val libraryEngine: LibraryEngine,
    private val hapticsDebugMonitor: HapticsDebugMonitor,
    private val backupManager: BackupManager,
    private val spotifyAuthManager: SpotifyAuthManager,
    private val spotifyRepository: SpotifyRepository,
    private val navidromePrefs: NavidromePrefs
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

    val hapticsDebugState: StateFlow<HapticsDebugState> = hapticsDebugMonitor.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HapticsDebugState()
        )

    val musicFolders: StateFlow<List<MusicFolder>> = libraryEngine.getMusicFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val connectedAccountsState: StateFlow<ConnectedAccountsState> = combine(
        spotifyAuthManager.token,
        navidromePrefs.serverUrl
    ) { token, serverUrl ->
        ConnectedAccountsState(
            isSpotifyConnected = token.isNotEmpty(),
            navidromeServerUrl = serverUrl
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ConnectedAccountsState()
    )

    private var rescanJob: Job? = null

    private val _backupEvent = MutableStateFlow<BackupUiEvent?>(null)
    val backupEvent: StateFlow<BackupUiEvent?> = _backupEvent.asStateFlow()

    /** Export playlists, history, and settings as a JSON file at [destination] (from a SAF picker). */
    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            backupManager.exportTo(destination).fold(
                onSuccess = { summary ->
                    _backupEvent.value = BackupUiEvent.ExportSucceeded(summary.playlistCount, summary.historyCount)
                },
                onFailure = { error ->
                    _backupEvent.value = BackupUiEvent.Failed(error.message ?: "Export failed")
                }
            )
        }
    }

    /** Restore playlists, history, and settings from the JSON file at [source] (from a SAF picker). */
    fun importBackup(source: Uri, strategy: RestoreStrategy) {
        viewModelScope.launch {
            backupManager.importFrom(source, strategy).fold(
                onSuccess = { summary ->
                    _backupEvent.value = BackupUiEvent.ImportSucceeded(summary.playlistCount, summary.historyCount)
                },
                onFailure = { error ->
                    _backupEvent.value = BackupUiEvent.Failed(error.message ?: "Import failed")
                }
            )
        }
    }

    fun consumeBackupEvent() {
        _backupEvent.value = null
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setPureAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setPureAmoledBlack(enabled) }
    }

    fun setDisablePip(disabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDisablePip(disabled) }
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
        viewModelScope.launch {
            if (enabled) {
                // Kick-reactive haptics depend on the app DSP analysis path.
                settingsDataStore.setRouteToSystemDecoderDsp(false)
            }
            settingsDataStore.setAudioReactiveHaptics(enabled)
        }
    }

    fun setAudioReactiveHapticsProfile(profile: AudioReactiveHapticsProfile) {
        viewModelScope.launch { settingsDataStore.setAudioReactiveHapticsProfile(profile) }
    }

    fun setSkipShortAudio(option: SkipShortAudio) {
        viewModelScope.launch { settingsDataStore.setSkipShortAudio(option) }
    }

    fun setRouteToSystemDecoderDsp(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setRouteToSystemDecoderDsp(enabled) }
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

    /**
     * @param isExcluded true adds [treeUri] as an exclude entry (hidden from scan, search and
     * playback), false adds it as an include root scanned for tracks. Either way, a rescan is
     * triggered so the change takes effect immediately instead of waiting for the next cold start.
     */
    fun addMusicFolder(treeUri: Uri, isExcluded: Boolean) {
        viewModelScope.launch {
            if (libraryEngine.addMusicFolder(treeUri, isExcluded)) {
                triggerLibraryRescan()
            }
        }
    }

    fun removeMusicFolder(uriString: String) {
        viewModelScope.launch {
            libraryEngine.removeMusicFolder(uriString)
            triggerLibraryRescan()
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

    /** Signs out of Spotify: tears down the App Remote session, then clears the stored token. */
    fun logoutSpotify() {
        viewModelScope.launch {
            spotifyRepository.disconnect()
            spotifyAuthManager.logout()
        }
    }

    /**
     * Signs out of Navidrome: clears the stored server/credentials, then rescans the library so
     * unified-library tracks sourced from Navidrome disappear immediately rather than lingering
     * from a stale cache.
     */
    fun logoutNavidrome() {
        viewModelScope.launch {
            navidromePrefs.clearCredentials()
            triggerLibraryRescan()
        }
    }
}
