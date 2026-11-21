package com.tigerplayer.ui.settings

import com.tigerplayer.data.backup.BackupManager
import com.tigerplayer.data.local.NavidromePrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.model.StorageBreakdown
import com.tigerplayer.data.model.StorageCategory
import com.tigerplayer.data.model.StorageCategoryInfo
import com.tigerplayer.data.repository.SpotifyAuthManager
import com.tigerplayer.data.repository.SpotifyRepository
import com.tigerplayer.engine.LibraryEngine
import com.tigerplayer.service.HapticsDebugMonitor
import com.tigerplayer.service.HapticsDebugState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelStorageTest {

    private val testDispatcher = StandardTestDispatcher()

    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val libraryEngine: LibraryEngine = mockk(relaxed = true)
    private val hapticsDebugMonitor: HapticsDebugMonitor = mockk(relaxed = true)
    private val backupManager: BackupManager = mockk(relaxed = true)
    private val spotifyPrefs: com.tigerplayer.data.local.SpotifyPrefs = mockk(relaxed = true)
    private val spotifyRepository: SpotifyRepository = mockk(relaxed = true)
    private val navidromePrefs: NavidromePrefs = mockk(relaxed = true)

    private val settingsStateFlow = MutableStateFlow(TigerSettingsState())
    private val hapticsStateFlow = MutableStateFlow(HapticsDebugState())
    private val navidromeServerUrlFlow = MutableStateFlow<String?>(null)

    private lateinit var spotifyAuthManager: SpotifyAuthManager
    private lateinit var viewModel: SettingsViewModel

    private val sampleBreakdown = StorageBreakdown(
        totalAppSizeBytes = 10_000_000L,
        totalCacheSizeBytes = 6_000_000L,
        artwork = StorageCategoryInfo(StorageCategory.ARTWORK, 4_000_000L, 20),
        lyrics = StorageCategoryInfo(StorageCategory.LYRICS, 1_000_000L, 200),
        artists = StorageCategoryInfo(StorageCategory.ARTISTS, 500_000L, 50),
        waveforms = StorageCategoryInfo(StorageCategory.WAVEFORMS, 500_000L, 100),
        internalData = StorageCategoryInfo(StorageCategory.DATABASE_INTERNAL, 4_000_000L, null, false)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { settingsDataStore.settingsFlow } returns settingsStateFlow
        every { hapticsDebugMonitor.state } returns hapticsStateFlow
        every { spotifyPrefs.accessToken } returns kotlinx.coroutines.flow.flowOf("")
        every { spotifyPrefs.tokenTimestamp } returns kotlinx.coroutines.flow.flowOf(null)
        every { spotifyPrefs.refreshToken } returns kotlinx.coroutines.flow.flowOf(null)
        every { spotifyPrefs.grantedScope } returns kotlinx.coroutines.flow.flowOf(null)
        every { navidromePrefs.serverUrl } returns navidromeServerUrlFlow
        every { libraryEngine.getMusicFolders() } returns MutableStateFlow(emptyList())

        spotifyAuthManager = SpotifyAuthManager(
            spotifyPrefs = spotifyPrefs,
            spotifyAuthApi = mockk(relaxed = true),
            ioDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testDispatcher.scheduler)
        )

        coEvery { libraryEngine.getStorageBreakdown() } returns sampleBreakdown

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
    fun initializationCalculatesStorageBreakdown() = runTest {
        advanceUntilIdle()

        val state = viewModel.storageBreakdown.value
        assertEquals(10_000_000L, state.totalAppSizeBytes)
        assertEquals(4_000_000L, state.artwork.sizeBytes)
        assertEquals(20, state.artwork.itemCount)
        assertEquals(200, state.lyrics.itemCount)
    }

    @Test
    fun clearArtworkCacheInvokesEngineAndRefreshes() = runTest {
        advanceUntilIdle()
        coEvery { libraryEngine.clearArtworkCache() } returns 4_000_000L

        viewModel.clearArtworkCache()
        advanceUntilIdle()

        coVerify(atLeast = 1) { libraryEngine.clearArtworkCache() }
        coVerify(atLeast = 2) { libraryEngine.getStorageBreakdown() }
        assertNotNull(viewModel.storageEventMessage.value)

        viewModel.consumeStorageEventMessage()
        assertNull(viewModel.storageEventMessage.value)
    }

    @Test
    fun clearLyricsCacheInvokesEngineAndRefreshes() = runTest {
        advanceUntilIdle()
        viewModel.clearLyricsCache()
        advanceUntilIdle()

        coVerify(atLeast = 1) { libraryEngine.clearLyricsCache() }
        coVerify(atLeast = 2) { libraryEngine.getStorageBreakdown() }
    }

    @Test
    fun clearArtistCacheInvokesEngineAndRefreshes() = runTest {
        advanceUntilIdle()
        viewModel.clearArtistCache()
        advanceUntilIdle()

        coVerify(atLeast = 1) { libraryEngine.clearArtistCache() }
        coVerify(atLeast = 2) { libraryEngine.getStorageBreakdown() }
    }

    @Test
    fun clearWaveformCacheInvokesEngineAndRefreshes() = runTest {
        advanceUntilIdle()
        viewModel.clearWaveformCache()
        advanceUntilIdle()

        coVerify(atLeast = 1) { libraryEngine.clearWaveformCache() }
        coVerify(atLeast = 2) { libraryEngine.getStorageBreakdown() }
    }

    @Test
    fun clearAllTemporaryCachesInvokesEngineAndNotifiesUser() = runTest {
        advanceUntilIdle()
        coEvery { libraryEngine.clearAllTemporaryCaches() } returns 6_000_000L

        viewModel.clearAllTemporaryCaches()
        advanceUntilIdle()

        coVerify(atLeast = 1) { libraryEngine.clearAllTemporaryCaches() }
        coVerify(atLeast = 2) { libraryEngine.getStorageBreakdown() }
        assertNotNull(viewModel.storageEventMessage.value)
    }
}
