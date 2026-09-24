package com.tigerplayer.ui.player

import androidx.media3.common.Player
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.remote.api.YouTubeRepository
import com.tigerplayer.data.repository.ArtistDetails
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.engine.AdaptiveDspEngine
import com.tigerplayer.engine.AudioReactiveFrame
import com.tigerplayer.engine.LibraryEngine
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.engine.NetworkEngine
import com.tigerplayer.engine.PlaybackEngine
import com.tigerplayer.engine.StatsEngine
import com.tigerplayer.engine.WaveformEngine
import com.tigerplayer.service.MediaControllerManager
import com.tigerplayer.ui.home.HomeUiState
import com.tigerplayer.utils.BluetoothDeviceInfo
import com.tigerplayer.utils.BluetoothDeviceManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val playbackEngine = mockk<PlaybackEngine>(relaxed = true)
    private val mediaControllerManager = mockk<MediaControllerManager>(relaxed = true)
    private val metadataEngine = mockk<MetadataEngine>(relaxed = true)
    private val statsEngine = mockk<StatsEngine>(relaxed = true)
    private val libraryEngine = mockk<LibraryEngine>(relaxed = true)
    private val networkEngine = mockk<NetworkEngine>(relaxed = true)
    private val waveformEngine = mockk<WaveformEngine>(relaxed = true)
    private val adaptiveDspEngine = mockk<AdaptiveDspEngine>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val playbackPrefs = mockk<PlaybackPrefs>(relaxed = true)
    private val audioRepository = mockk<AudioRepository>(relaxed = true)
    private val youtubeRepository = mockk<YouTubeRepository>(relaxed = true)
    private val bluetoothDeviceManager = mockk<BluetoothDeviceManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { playbackEngine.spotifyPlaybackState } returns MutableStateFlow(null)
        every { playbackEngine.getQueueSnapshotFlow() } returns flowOf(MediaControllerManager.QueueSnapshot())

        every { mediaControllerManager.isPlaying } returns MutableStateFlow(false)
        every { mediaControllerManager.currentPosition } returns MutableStateFlow(0L)
        every { mediaControllerManager.shuffleModeEnabled } returns MutableStateFlow(false)
        every { mediaControllerManager.repeatMode } returns MutableStateFlow(Player.REPEAT_MODE_OFF)
        every { mediaControllerManager.playbackSpeed } returns MutableStateFlow(1f)
        every { mediaControllerManager.pitch } returns MutableStateFlow(1f)

        every { metadataEngine.artistDetails } returns MutableStateFlow<Map<String, ArtistDetails>>(emptyMap())
        every { metadataEngine.currentLyrics } returns MutableStateFlow(null)
        every { metadataEngine.currentArtistImageUrl } returns MutableStateFlow(null)

        every { libraryEngine.getHomeUiStateFlow(any()) } returns flowOf(HomeUiState())
        every { libraryEngine.getCustomPlaylists() } returns flowOf(emptyList())
        every {
            libraryEngine.getAggregatedLibraryFlow(any(), any(), any())
        } returns flowOf(
            LibraryEngine.LibraryAggregation(
                tracks = emptyList(),
                filteredTracks = emptyList(),
                artists = emptyList(),
                albums = emptyList()
            )
        )

        every { statsEngine.getDetailedStatsFlow(any(), any()) } returns flowOf(DetailedStatsUiState())
        every { adaptiveDspEngine.audioReactiveFrame } returns MutableStateFlow(AudioReactiveFrame())
        every { settingsDataStore.settingsFlow } returns MutableStateFlow(TigerSettingsState())
        every { bluetoothDeviceManager.connectedDevice } returns MutableStateFlow(BluetoothDeviceInfo())

        coEvery { networkEngine.getUnifiedLibraryFlow() } returns flowOf(emptyList<AudioTrack>())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): PlayerViewModel = PlayerViewModel(
        playbackEngine = playbackEngine,
        mediaControllerManager = mediaControllerManager,
        metadataEngine = metadataEngine,
        statsEngine = statsEngine,
        libraryEngine = libraryEngine,
        networkEngine = networkEngine,
        waveformEngine = waveformEngine,
        adaptiveDspEngine = adaptiveDspEngine,
        settingsDataStore = settingsDataStore,
        playbackPrefs = playbackPrefs,
        audioRepository = audioRepository,
        youtubeRepository = youtubeRepository,
        bluetoothDeviceManager = bluetoothDeviceManager
    )

    @Test
    fun `auth success persists the token without eagerly opening spotify app remote`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAuthSuccess("token-123")

        verify(exactly = 1) { networkEngine.onAuthSuccess("token-123") }
        verify(exactly = 0) { playbackEngine.connectSpotifyRemote() }
    }
}

