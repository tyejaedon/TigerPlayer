package com.tigerplayer.ui.coverscreen

import android.net.Uri
import com.tigerplayer.data.backup.BackupManager
import com.tigerplayer.data.backup.BackupSummary
import com.tigerplayer.data.backup.RestoreStrategy
import com.tigerplayer.data.local.AudioReactiveHapticsProfile
import com.tigerplayer.data.local.DefaultPlayerView
import com.tigerplayer.data.local.NavidromePrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.SkipShortAudio
import com.tigerplayer.data.local.SpotifyPrefs
import com.tigerplayer.data.local.ThemeMode
import com.tigerplayer.data.local.TigerAccentStyle
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.model.MusicFolder
import com.tigerplayer.data.repository.SpotifyAuthManager
import com.tigerplayer.data.repository.SpotifyRepository
import com.tigerplayer.engine.LibraryEngine
import com.tigerplayer.service.HapticsDebugMonitor
import com.tigerplayer.service.HapticsDebugState
import com.tigerplayer.ui.settings.BackupUiEvent
import com.tigerplayer.ui.settings.SettingsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoverScreenSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val libraryEngine: LibraryEngine = mockk(relaxed = true)
    private val hapticsDebugMonitor: HapticsDebugMonitor = mockk(relaxed = true)
    private val backupManager: BackupManager = mockk(relaxed = true)
    private val spotifyPrefs: SpotifyPrefs = mockk(relaxed = true)
    private val spotifyRepository: SpotifyRepository = mockk(relaxed = true)
    private val navidromePrefs: NavidromePrefs = mockk(relaxed = true)

    private val settingsStateFlow = MutableStateFlow(TigerSettingsState())
    private val hapticsStateFlow = MutableStateFlow(HapticsDebugState())
    private val foldersFlow = MutableStateFlow<List<MusicFolder>>(emptyList())
    private val navidromeUrlFlow = MutableStateFlow<String?>(null)

    private lateinit var spotifyAuthManager: SpotifyAuthManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { settingsDataStore.settingsFlow } returns settingsStateFlow
        every { hapticsDebugMonitor.state } returns hapticsStateFlow
        every { libraryEngine.getMusicFolders() } returns foldersFlow
        every { spotifyPrefs.accessToken } returns flowOf("")
        every { spotifyPrefs.tokenTimestamp } returns flowOf(null)
        every { spotifyPrefs.refreshToken } returns flowOf(null)
        every { spotifyPrefs.grantedScope } returns flowOf(null)
        every { navidromePrefs.serverUrl } returns navidromeUrlFlow

        spotifyAuthManager = SpotifyAuthManager(
            spotifyPrefs = spotifyPrefs,
            spotifyAuthApi = mockk(relaxed = true),
            ioDispatcher = UnconfinedTestDispatcher()
        )

        viewModel = SettingsViewModel(
            settingsDataStore = settingsDataStore,
            libraryEngine = libraryEngine,
            hapticsDebugMonitor = hapticsDebugMonitor,
            backupManager = backupManager,
            spotifyAuthManager = spotifyAuthManager,
            spotifyRepository = spotifyRepository,
            navidromePrefs = navidromePrefs
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onlineMetadataAndLyricsTogglesMutateDataStore() = runTest {
        viewModel.setFetchOnlineArtwork(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setFetchOnlineArtwork(true) }

        viewModel.setFetchOnlineArtwork(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setFetchOnlineArtwork(false) }

        viewModel.setAutoDownloadLyrics(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setAutoDownloadLyrics(true) }

        viewModel.setAutoDownloadLyrics(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setAutoDownloadLyrics(false) }
    }

    @Test
    fun visualAndPlaybackSettingsMutateDataStore() = runTest {
        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setThemeMode(ThemeMode.DARK) }

        viewModel.setPureAmoledBlack(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setPureAmoledBlack(true) }

        viewModel.setAccentStyle(TigerAccentStyle.CYBER_CYAN)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setAccentStyle(TigerAccentStyle.CYBER_CYAN) }

        viewModel.setDefaultPlayerView(DefaultPlayerView.ARTWORK_3D)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setDefaultPlayerView(DefaultPlayerView.ARTWORK_3D) }

        viewModel.setCrossfadeDuration(6)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setCrossfadeDurationSec(6) }

        viewModel.setGaplessPlayback(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setGaplessPlayback(false) }

        viewModel.setCoverScreenPreviewMode(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setCoverScreenPreviewMode(true) }

        viewModel.setSkipShortAudio(SkipShortAudio.BELOW_30_SECONDS)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setSkipShortAudio(SkipShortAudio.BELOW_30_SECONDS) }
    }

    @Test
    fun audioReactiveHapticsTogglesDisableSystemDecoderDsp() = runTest {
        viewModel.setAudioReactiveHaptics(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setRouteToSystemDecoderDsp(false) }
        coVerify(exactly = 1) { settingsDataStore.setAudioReactiveHaptics(true) }

        viewModel.setAudioReactiveHapticsProfile(AudioReactiveHapticsProfile.AGGRESSIVE)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.setAudioReactiveHapticsProfile(AudioReactiveHapticsProfile.AGGRESSIVE) }
    }

    @Test
    fun musicFoldersOperationsDelegateToLibraryEngine() = runTest {
        val testUri = mockk<Uri>()
        coEvery { libraryEngine.addMusicFolder(testUri, false) } returns true
        coEvery { libraryEngine.addMusicFolder(testUri, true) } returns true
        every { libraryEngine.getLocalAudioScanFlow(forceRefresh = true) } returns flowOf()

        viewModel.addMusicFolder(testUri, isExcluded = false)
        advanceUntilIdle()
        coVerify(exactly = 1) { libraryEngine.addMusicFolder(testUri, false) }

        viewModel.addMusicFolder(testUri, isExcluded = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { libraryEngine.addMusicFolder(testUri, true) }

        viewModel.removeMusicFolder("content://test-folder")
        advanceUntilIdle()
        coVerify(exactly = 1) { libraryEngine.removeMusicFolder("content://test-folder") }
    }

    @Test
    fun backupExportAndImportEmitEventsAndAllowConsumption() = runTest {
        val exportUri = mockk<Uri>()
        val importUri = mockk<Uri>()
        coEvery { backupManager.exportTo(exportUri) } returns Result.success(BackupSummary(playlistCount = 5, trackRefCount = 20, historyCount = 10))
        coEvery { backupManager.importFrom(importUri, RestoreStrategy.MERGE) } returns Result.success(BackupSummary(playlistCount = 3, trackRefCount = 15, historyCount = 8))

        viewModel.exportBackup(exportUri)
        advanceUntilIdle()
        assertEquals(BackupUiEvent.ExportSucceeded(5, 10), viewModel.backupEvent.value)

        viewModel.consumeBackupEvent()
        assertNull(viewModel.backupEvent.value)

        viewModel.importBackup(importUri, RestoreStrategy.MERGE)
        advanceUntilIdle()
        assertEquals(BackupUiEvent.ImportSucceeded(3, 8), viewModel.backupEvent.value)

        viewModel.consumeBackupEvent()
        assertNull(viewModel.backupEvent.value)
    }

    @Test
    fun accountDisconnectOperationsClearSessionAndCredentials() = runTest {
        every { libraryEngine.getLocalAudioScanFlow(forceRefresh = true) } returns flowOf()

        viewModel.logoutSpotify()
        advanceUntilIdle()
        coVerify(exactly = 1) { spotifyRepository.disconnect() }
        coVerify(exactly = 1) { spotifyPrefs.clearToken() }
        assertTrue(spotifyAuthManager.token.value.isEmpty())

        viewModel.logoutNavidrome()
        advanceUntilIdle()
        coVerify(exactly = 1) { navidromePrefs.clearCredentials() }
    }

    @Test
    fun resetToDefaultsMutatesDataStore() = runTest {
        viewModel.resetToDefaults()
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsDataStore.resetToDefaults() }
    }
}
