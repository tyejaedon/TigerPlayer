package com.tigerplayer.ui.home

import com.tigerplayer.data.repository.ArtistDetails
import com.tigerplayer.engine.CoverScreenStatsSnapshot
import com.tigerplayer.engine.LibraryEngine
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.engine.NetworkEngine
import com.tigerplayer.engine.StatsEngine
import com.tigerplayer.ui.player.DetailedStatsUiState
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val statsEngine = mockk<StatsEngine>(relaxed = true)
    private val libraryEngine = mockk<LibraryEngine>(relaxed = true)
    private val networkEngine = mockk<NetworkEngine>(relaxed = true)
    private val metadataEngine = mockk<MetadataEngine>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { metadataEngine.artistDetails } returns MutableStateFlow<Map<String, ArtistDetails>>(emptyMap())
        coEvery { networkEngine.getUnifiedLibraryFlow() } returns flowOf(emptyList())
        every { libraryEngine.getHomeUiStateFlow(any()) } returns flowOf(HomeUiState())
        every { statsEngine.getDetailedStatsFlow(any(), any()) } returns flowOf(DetailedStatsUiState())
        every { statsEngine.getCoverScreenStatsSnapshotFlow() } returns flowOf(CoverScreenStatsSnapshot())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): StatsViewModel = StatsViewModel(
        statsEngine = statsEngine,
        libraryEngine = libraryEngine,
        networkEngine = networkEngine,
        metadataEngine = metadataEngine
    )

    @Test
    fun `updateStatsFilter delegates to statsEngine`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.updateStatsFilter("Last 30 Days")
        verify(exactly = 1) { statsEngine.updateStatsFilter("Last 30 Days") }
    }

    @Test
    fun `sonic footprint open request state transitions correctly`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestSonicFootprintOpen()
        assertEquals(true, viewModel.sonicFootprintOpenRequest.value)

        viewModel.consumeSonicFootprintOpenRequest()
        assertEquals(false, viewModel.sonicFootprintOpenRequest.value)
    }
}
