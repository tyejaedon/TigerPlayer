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
import com.tigerplayer.engine.FluidRenderer
import com.tigerplayer.engine.LibraryEngine
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.engine.NetworkEngine
import com.tigerplayer.engine.PlaybackEngine
import com.tigerplayer.engine.StatsEngine
import com.tigerplayer.engine.WaveformEngine
import com.tigerplayer.service.MediaControllerManager
import com.tigerplayer.ui.home.HomeUiState
import com.tigerplayer.ui.player.PlaybackState
import com.tigerplayer.ui.player.PlaybackStateManager
import com.tigerplayer.utils.BluetoothDeviceInfo
import com.tigerplayer.utils.BluetoothDeviceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val playbackStateManager = mockk<PlaybackStateManager>(relaxed = true)
    private val playbackEngine = mockk<PlaybackEngine>(relaxed = true)
    private val fluidRenderer = mockk<FluidRenderer>(relaxed = true)
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
        every { playbackEngine.spotifyReauthRequired } returns MutableStateFlow(false)
        every { playbackEngine.getQueueSnapshotFlow() } returns flowOf(MediaControllerManager.QueueSnapshot())
        every { playbackStateManager.playbackState } returns MutableStateFlow(PlaybackState())

        every { mediaControllerManager.isPlaying } returns MutableStateFlow(false)
        every { mediaControllerManager.currentPosition } returns MutableStateFlow(0L)
        every { mediaControllerManager.shuffleModeEnabled } returns MutableStateFlow(false)
        every { mediaControllerManager.repeatMode } returns MutableStateFlow(Player.REPEAT_MODE_OFF)
        every { mediaControllerManager.volume } returns MutableStateFlow(1f)
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
        playbackStateManager = playbackStateManager,
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
        bluetoothDeviceManager = bluetoothDeviceManager,
        fluidRenderer = fluidRenderer
    )

    @Test
    fun `auth success persists the token without eagerly opening spotify app remote`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAuthSuccess("token-123")

        verify(exactly = 1) { networkEngine.onAuthSuccess("token-123") }
        verify(exactly = 0) { playbackEngine.connectSpotifyRemote() }
    }

    @Test
    fun `sonic footprint open request is consumed after navigation`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.requestSonicFootprintOpen()
        assertEquals(true, viewModel.sonicFootprintOpenRequest.value)

        viewModel.consumeSonicFootprintOpenRequest()
        assertEquals(false, viewModel.sonicFootprintOpenRequest.value)
    }

    @Test
    fun `cover screen volume is normalized persisted and applied`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setCoverScreenVolume(0.42f)
        advanceUntilIdle()

        assertEquals(0.42f, viewModel.uiState.value.coverScreenVolume, 0.001f)
        verify(exactly = 1) { playbackStateManager.setCoverScreenVolume(0.42f) }

        viewModel.setCoverScreenVolume(Float.NaN)
        advanceUntilIdle()
        assertEquals(1f, viewModel.uiState.value.coverScreenVolume, 0f)
    }

    @Test
    fun `playAll initiates playback from first track when tracks are present`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val track1 = AudioTrack(
            id = "t1",
            title = "Track 1",
            artist = "Artist",
            album = "Album",
            uri = mockk(relaxed = true),
            artworkUri = mockk(relaxed = true),
            durationMs = 1000L,
            mimeType = "audio/mpeg"
        )
        val track2 = track1.copy(id = "t2", title = "Track 2")
        val tracks = listOf(track1, track2)

        viewModel.playAll(tracks)

        verify(exactly = 1) { playbackStateManager.playAll(tracks) }
    }

    @Test
    fun `playAll ignores empty track list`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.playAll(emptyList())

        verify(exactly = 0) { playbackStateManager.playAll(any()) }
    }

    @Test
    fun `onSearchQueryChanged updates rawSearchQuery immediately without updating uiState`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Kaer Morhen")

        assertEquals("Kaer Morhen", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)
        verify(exactly = 0) { libraryEngine.updateSearchQuery(any()) }
    }

    @Test
    fun `search query is debounced before updating uiState and libraryEngine`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Kaer")
        advanceTimeBy(100)
        runCurrent()
        assertEquals("Kaer", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)
        verify(exactly = 0) { libraryEngine.updateSearchQuery(any()) }

        viewModel.onSearchQueryChanged("Kaer Morhen")
        advanceTimeBy(200)
        runCurrent()
        assertEquals("Kaer Morhen", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)
        verify(exactly = 0) { libraryEngine.updateSearchQuery(any()) }

        // Advance the remaining time to pass the 250ms debounce threshold
        advanceTimeBy(50)
        runCurrent()
        assertEquals("Kaer Morhen", viewModel.rawSearchQuery.value)
        assertEquals("Kaer Morhen", viewModel.uiState.value.searchQuery)
        verify(exactly = 1) { libraryEngine.updateSearchQuery("Kaer Morhen") }
    }

    @Test
    fun `clearSearch immediately resets both rawSearchQuery and uiState searchQuery`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Witcher")
        advanceTimeBy(250)
        runCurrent()
        assertEquals("Witcher", viewModel.rawSearchQuery.value)
        assertEquals("Witcher", viewModel.uiState.value.searchQuery)

        viewModel.clearSearch()

        assertEquals("", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)
        verify(atLeast = 1) { libraryEngine.clearSearch() }
    }

    @Test
    fun `clearSearch immediately cancels in-flight debounced query`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Pending")
        assertEquals("Pending", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)

        viewModel.clearSearch()

        assertEquals("", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)
        verify(atLeast = 1) { libraryEngine.clearSearch() }

        // Even after debounce time expires, pending value is not emitted
        advanceTimeBy(300)
        runCurrent()
        assertEquals("", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `likeTrack delegates to libraryEngine and updates currentTrack like state`() = runTest(dispatcher) {
        val track = mockk<AudioTrack>(relaxed = true) {
            every { id } returns "track_like_1"
            every { isLiked } returns false
            every { copy(isLiked = true) } returns mockk {
                every { id } returns "track_like_1"
                every { isLiked } returns true
            }
        }
        coEvery { libraryEngine.likeTrack(track) } returns true

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.likeTrack(track)
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryEngine.likeTrack(track) }
        verify(atLeast = 1) { playbackStateManager.updateCurrentTrack(any()) }
    }
}
