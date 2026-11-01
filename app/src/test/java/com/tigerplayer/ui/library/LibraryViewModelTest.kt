package com.tigerplayer.ui.library

import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.ArtistDetails
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.source.LocalAudioDataSource
import com.tigerplayer.engine.LibraryEngine
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.engine.NetworkEngine
import com.tigerplayer.ui.player.PlaybackStateManager
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
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val libraryEngine = mockk<LibraryEngine>(relaxed = true)
    private val networkEngine = mockk<NetworkEngine>(relaxed = true)
    private val metadataEngine = mockk<MetadataEngine>(relaxed = true)
    private val audioRepository = mockk<AudioRepository>(relaxed = true)
    private val playbackStateManager = mockk<PlaybackStateManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

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
        every { metadataEngine.artistDetails } returns MutableStateFlow<Map<String, ArtistDetails>>(emptyMap())
        coEvery { networkEngine.getUnifiedLibraryFlow() } returns flowOf(emptyList())

        every { playbackStateManager.currentTrack } returns MutableStateFlow(null)
        every { playbackStateManager.isPlaying } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel = LibraryViewModel(
        libraryEngine = libraryEngine,
        networkEngine = networkEngine,
        metadataEngine = metadataEngine,
        audioRepository = audioRepository,
        playbackStateManager = playbackStateManager
    )

    @Test
    fun `search query is debounced before updating uiState and calling libraryEngine`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Geralt")
        advanceTimeBy(100)
        runCurrent()
        assertEquals("Geralt", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)
        verify(exactly = 0) { libraryEngine.updateSearchQuery(any()) }

        viewModel.onSearchQueryChanged("Geralt of Rivia")
        advanceTimeBy(200)
        runCurrent()
        assertEquals("Geralt of Rivia", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)
        verify(exactly = 0) { libraryEngine.updateSearchQuery(any()) }

        // Complete the 250ms debounce
        advanceTimeBy(50)
        runCurrent()
        assertEquals("Geralt of Rivia", viewModel.rawSearchQuery.value)
        assertEquals("Geralt of Rivia", viewModel.uiState.value.searchQuery)
        verify(exactly = 1) { libraryEngine.updateSearchQuery("Geralt of Rivia") }
    }

    @Test
    fun `clearSearch immediately resets search query and cancels pending debounce`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Kaer Morhen")
        advanceTimeBy(250)
        runCurrent()
        assertEquals("Kaer Morhen", viewModel.uiState.value.searchQuery)

        viewModel.clearSearch()
        assertEquals("", viewModel.rawSearchQuery.value)
        assertEquals("", viewModel.uiState.value.searchQuery)
        verify(atLeast = 1) { libraryEngine.clearSearch() }
    }

    @Test
    fun `loadLocalAudio invokes getLocalAudioScanFlow and updates scanning state`() = runTest(dispatcher) {
        every { libraryEngine.getLocalAudioScanFlow(true) } returns flowOf<LocalAudioDataSource.ScanStatus>(
            LocalAudioDataSource.ScanStatus.Started(50),
            LocalAudioDataSource.ScanStatus.InProgress(25, 50),
            LocalAudioDataSource.ScanStatus.Complete(emptyList())
        )

        val viewModel = createViewModel()
        viewModel.loadLocalAudio(forceRefresh = true)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isScanning)
        verify(exactly = 1) { libraryEngine.getLocalAudioScanFlow(true) }
    }

    @Test
    fun `playback actions delegate to playbackStateManager`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val track = mockk<AudioTrack>(relaxed = true)
        val tracks = listOf(track)

        viewModel.playTrack(track)
        verify(exactly = 1) { playbackStateManager.playTrack(track, any()) }

        viewModel.playAll(tracks)
        verify(exactly = 1) { playbackStateManager.playAll(tracks) }

        viewModel.setPlaylistAndPlay(tracks, 1)
        verify(exactly = 1) { playbackStateManager.setPlaylistAndPlay(tracks, 1) }

        viewModel.togglePlayPause()
        verify(exactly = 1) { playbackStateManager.togglePlayPause() }

        viewModel.addToQueue(track)
        verify(exactly = 1) { playbackStateManager.addToQueue(track) }

        viewModel.addNextToQueue(track)
        verify(exactly = 1) { playbackStateManager.addNextToQueue(track) }
    }
}
