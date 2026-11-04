package com.tigerplayer.ui.equalizer

import androidx.media3.common.util.UnstableApi
import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.model.AudioDeviceType
import com.tigerplayer.data.model.AudioOutputDevice
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.data.model.HeadphoneSpecs
import com.tigerplayer.data.repository.AutoEqRepository
import com.tigerplayer.engine.AcousticEnvironmentMode
import com.tigerplayer.engine.AdaptiveDspEngine
import com.tigerplayer.engine.AudioDeviceEngine
import com.tigerplayer.engine.AudioReactiveFrame
import com.tigerplayer.engine.FilterType
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerHubViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val adaptiveDspEngine: AdaptiveDspEngine = mockk(relaxed = true)
    private val audioDeviceEngine: AudioDeviceEngine = mockk(relaxed = true)
    private val autoEqRepository: AutoEqRepository = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val playbackPrefs: PlaybackPrefs = mockk(relaxed = true)

    private val testActiveDevice = AudioOutputDevice(
        deviceId = "speaker",
        displayName = "Phone Speaker",
        type = AudioDeviceType.PHONE_SPEAKER,
        isConnected = true,
        isActiveRoute = true,
        lastConnectedEpochMs = 1700000000000L
    )

    private val activeDeviceFlow = MutableStateFlow(testActiveDevice)
    private val envModeFlow = MutableStateFlow(AcousticEnvironmentMode.STUDIO.name)

    private lateinit var viewModel: EqualizerHubViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { adaptiveDspEngine.getSampleRate() } returns 48000
        every { adaptiveDspEngine.audioReactiveFrame } returns MutableStateFlow(AudioReactiveFrame())
        every { audioDeviceEngine.activeDevice } returns activeDeviceFlow
        every { playbackPrefs.acousticEnvironmentMode } returns envModeFlow
        every { settingsDataStore.settingsFlow } returns flowOf(TigerSettingsState())
        coEvery { autoEqRepository.searchHeadphones(any()) } returns emptyList()

        viewModel = EqualizerHubViewModel(
            adaptiveDspEngine = adaptiveDspEngine,
            audioDeviceEngine = audioDeviceEngine,
            autoEqRepository = autoEqRepository,
            settingsDataStore = settingsDataStore,
            playbackPrefs = playbackPrefs,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state defaults to 10-band ISO layout with master EQ enabled`() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertTrue(state.isEqEnabled)
        assertEquals(EqualizerBandMode.TEN_BAND, state.bandMode)
        assertEquals(10, state.bands.size)
        assertEquals(31f, state.bands[0].frequencyHz)
        assertEquals(16000f, state.bands[9].frequencyHz)
        assertEquals("Phone Speaker", state.connectedDeviceName)
        assertEquals(EqualizerPresets.FLAT, state.activePresetName)
    }

    @Test
    fun `toggleMasterEq dispatches empty acoustic nodes when disabled`() = runTest(testDispatcher) {
        viewModel.toggleMasterEq(false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEqEnabled)
        verify { adaptiveDspEngine.updateAcousticNodes(emptyList()) }

        viewModel.toggleMasterEq(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEqEnabled)
        verify(atLeast = 2) { adaptiveDspEngine.updateAcousticNodes(match { it.size == 10 }) }
    }

    @Test
    fun `switching to 5-band mode updates band layout and center frequencies`() = runTest(testDispatcher) {
        viewModel.setBandMode(EqualizerBandMode.FIVE_BAND)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EqualizerBandMode.FIVE_BAND, state.bandMode)
        assertEquals(5, state.bands.size)
        assertEquals("60 Hz", state.bands[0].label)
        assertEquals(60f, state.bands[0].frequencyHz)
        assertEquals(14000f, state.bands[4].frequencyHz)
    }

    @Test
    fun `updateBandGain clamps gain between -15 dB and +15 dB`() = runTest(testDispatcher) {
        viewModel.updateBandGain(0, 20f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(15.0f, viewModel.uiState.value.bands[0].gainDb, 0.01f)

        viewModel.updateBandGain(0, -30f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(-15.0f, viewModel.uiState.value.bands[0].gainDb, 0.01f)

        viewModel.stepBandGain(0, 0.5f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(-14.5f, viewModel.uiState.value.bands[0].gainDb, 0.01f)
    }

    @Test
    fun `applyPreset updates all band gains accurately`() = runTest(testDispatcher) {
        viewModel.applyPreset(EqualizerPresets.BASS_BOOST)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EqualizerPresets.BASS_BOOST, state.activePresetName)
        assertEquals(6.0f, state.bands[0].gainDb, 0.01f)
        assertEquals(5.0f, state.bands[1].gainDb, 0.01f)
        assertEquals(3.5f, state.bands[2].gainDb, 0.01f)

        viewModel.resetToFlat()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(EqualizerPresets.FLAT, viewModel.uiState.value.activePresetName)
        assertTrue(viewModel.uiState.value.bands.all { it.gainDb == 0f })
    }

    @Test
    fun `importRawAutoEq parses ParametricEQ text and sets active engine state`() = runTest(testDispatcher) {
        val sampleAutoEqText = """
            Preamp: -4.5 dB
            Filter 1: ON PK Fc 28 Hz Gain 6.2 dB Q 1.41
            Filter 2: ON LSC Fc 105 Hz Gain -2.0 dB Q 0.71
            Filter 3: ON HSC Fc 10000 Hz Gain 1.8 dB Q 0.71
        """.trimIndent()

        viewModel.importRawAutoEq(sampleAutoEqText, "Sennheiser HD 650")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EqualizerBandMode.AUTOEQ_PARAMETRIC, state.bandMode)
        assertEquals(3, state.bands.size)
        assertEquals(-4.5f, state.preampDb, 0.01f)
        assertEquals("Sennheiser HD 650", state.autoEqModel)

        assertEquals(28f, state.bands[0].frequencyHz, 0.01f)
        assertEquals(6.2f, state.bands[0].gainDb, 0.01f)
        assertEquals(FilterType.PEAKING, state.bands[0].filterType)

        assertEquals(105f, state.bands[1].frequencyHz, 0.01f)
        assertEquals(-2.0f, state.bands[1].gainDb, 0.01f)
        assertEquals(FilterType.LOW_SHELF, state.bands[1].filterType)

        assertEquals(10000f, state.bands[2].frequencyHz, 0.01f)
        assertEquals(1.8f, state.bands[2].gainDb, 0.01f)
        assertEquals(FilterType.HIGH_SHELF, state.bands[2].filterType)
    }

    @Test
    fun `setAcousticEnvironment persists mode and configures DSP`() = runTest(testDispatcher) {
        viewModel.setAcousticEnvironment(AcousticEnvironmentMode.VINYL_WARMTH)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AcousticEnvironmentMode.VINYL_WARMTH, viewModel.uiState.value.environmentMode)
        verify { adaptiveDspEngine.setAcousticEnvironmentMode(AcousticEnvironmentMode.VINYL_WARMTH) }
        coVerify { playbackPrefs.saveAcousticEnvironmentMode(AcousticEnvironmentMode.VINYL_WARMTH.name) }
    }

    @Test
    fun `selecting SPATIAL_NEXUS studio tab switches active engine source and dispatches 4 spatial nodes`() = runTest(testDispatcher) {
        viewModel.selectStudioTab(EqualizerStudioTab.SPATIAL_NEXUS)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EqualizerStudioTab.SPATIAL_NEXUS, state.studioTab)
        assertEquals(EqualizerEngineSource.SPATIAL_NODES, state.activeEngineSource)
        assertEquals(4, state.spatialNodes.size)
        verify { adaptiveDspEngine.updateAcousticNodes(match { it.size == 4 }) }
    }

    @Test
    fun `moveSpatialNode updates position and clamps coordinates within -1 and 1`() = runTest(testDispatcher) {
        viewModel.selectStudioTab(EqualizerStudioTab.SPATIAL_NEXUS)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveSpatialNode("sub", androidx.compose.ui.geometry.Offset(-1.5f, 0.5f))
        testDispatcher.scheduler.advanceUntilIdle()

        val subNode = viewModel.uiState.value.spatialNodes.first { it.id == "sub" }
        assertEquals(-1.0f, subNode.spatialPos.x, 0.01f)
        assertEquals(0.5f, subNode.spatialPos.y, 0.01f)
        assertEquals("Custom Shape", viewModel.uiState.value.spatialMood)
    }

    @Test
    fun `setSpatialMoodPreset applies predefined mood positions and persists`() = runTest(testDispatcher) {
        viewModel.setSpatialMoodPreset("Night Drive")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Night Drive", state.spatialMood)
        assertEquals(EqualizerEngineSource.SPATIAL_NODES, state.activeEngineSource)
        val sub = state.spatialNodes.first { it.id == "sub" }
        assertEquals(-0.6f, sub.spatialPos.x, 0.01f)
        assertEquals(-0.6f, sub.spatialPos.y, 0.01f)
        coVerify { settingsDataStore.setCoverScreenEqPreset(CoverScreenEqPreset.NIGHT_DRIVE) }
    }
}
