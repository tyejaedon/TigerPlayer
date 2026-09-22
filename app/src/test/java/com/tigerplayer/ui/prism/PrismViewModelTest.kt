package com.tigerplayer.ui.prism

import com.tigerplayer.data.local.PrismSpectralAnalysis
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.engine.AdaptiveDspEngine
import com.tigerplayer.engine.AudioReactiveFrame
import com.tigerplayer.engine.PrismMode
import com.tigerplayer.engine.SpectralAnalysisMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage for [PrismViewModel] hydration, debounced persistence, and preset wiring.
 *
 * Regression context: the ViewModel used to force `AdaptiveDspEngine.setPrismMode(BYPASS)` from an
 * `onCleared()` override, which silently disabled a user's active Sonic Prism mix whenever the
 * hosting screen's ViewModelStore was cleared (e.g. the Activity being destroyed by the system
 * while a foreground playback service kept running) - with no user action and no way to tell why
 * from the UI. That override has been removed; [AdaptiveDspEngine] state is now driven solely by
 * the persisted/observed [PrismUiState], never reset as a side effect of UI teardown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrismViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val adaptiveDspEngine = mockk<AdaptiveDspEngine>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { adaptiveDspEngine.audioReactiveFrame } returns MutableStateFlow(AudioReactiveFrame())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(initialSettings: TigerSettingsState) = run {
        every { settingsDataStore.settingsFlow } returns MutableStateFlow(initialSettings)
        coEvery { settingsDataStore.setPrismEnabled(any()) } returns Unit
        coEvery { settingsDataStore.setPrismMix(any(), any(), any()) } returns Unit
        coEvery { settingsDataStore.setPrismSpectralAnalysis(any()) } returns Unit
        PrismViewModel(adaptiveDspEngine, settingsDataStore)
    }

    @Test
    fun `hydrates persisted prism state into the engine exactly once on init`() = runTest(dispatcher) {
        val persisted = TigerSettingsState(
            prismEnabled = true,
            prismVocals = 0.4f,
            prismBeats = 0.9f,
            prismInstruments = 0.2f,
            prismSpectralAnalysis = PrismSpectralAnalysis.BANDPASS
        )

        val viewModel = viewModel(persisted)
        advanceUntilIdle()

        assertEquals(0.4f, viewModel.uiState.value.vocals)
        assertEquals(0.9f, viewModel.uiState.value.beats)
        assertEquals(0.2f, viewModel.uiState.value.instruments)
        assertTrue(viewModel.uiState.value.isPrismEnabled)

        verify { adaptiveDspEngine.setPrismMode(PrismMode.ISOLATION) }
        verify { adaptiveDspEngine.updatePrismMix(0.4f, 0.9f, 0.2f) }
        verify { adaptiveDspEngine.setSpectralAnalysisMode(SpectralAnalysisMode.BANDPASS) }
    }

    @Test
    fun `disabled and untouched engine stays bypassed after hydration`() = runTest(dispatcher) {
        val viewModel = viewModel(TigerSettingsState(prismEnabled = false))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPrismEnabled)
        verify { adaptiveDspEngine.setPrismMode(PrismMode.BYPASS) }
    }

    @Test
    fun `slider changes debounce then persist and apply to the engine`() = runTest(dispatcher) {
        val viewModel = viewModel(TigerSettingsState(prismEnabled = true))
        advanceUntilIdle()

        viewModel.updateVocals(0.3f)
        advanceUntilIdle()

        assertEquals(PrismPreset.CUSTOM, viewModel.uiState.value.preset)
        verify { adaptiveDspEngine.updatePrismMix(0.3f, 1f, 1f) }
        coVerify { settingsDataStore.setPrismMix(0.3f, 1f, 1f) }
    }

    @Test
    fun `applying a preset enables prism and sets the exact preset mix`() = runTest(dispatcher) {
        val viewModel = viewModel(TigerSettingsState(prismEnabled = false))
        advanceUntilIdle()

        viewModel.applyPreset(PrismPreset.VOCAL_FOCUS)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PrismPreset.VOCAL_FOCUS, state.preset)
        assertTrue(state.isPrismEnabled)
        assertEquals(PrismPreset.VOCAL_FOCUS.mix.vocals, state.vocals)
        assertEquals(PrismPreset.VOCAL_FOCUS.mix.beats, state.beats)
        assertEquals(PrismPreset.VOCAL_FOCUS.mix.instruments, state.instruments)

        verify { adaptiveDspEngine.setPrismMode(PrismMode.ISOLATION) }
        verify {
            adaptiveDspEngine.updatePrismMix(
                PrismPreset.VOCAL_FOCUS.mix.vocals,
                PrismPreset.VOCAL_FOCUS.mix.beats,
                PrismPreset.VOCAL_FOCUS.mix.instruments
            )
        }
    }

    @Test
    fun `resetMixToBalanced re-enables prism at the balanced preset`() = runTest(dispatcher) {
        val viewModel = viewModel(TigerSettingsState(prismEnabled = true, prismVocals = 0.1f))
        advanceUntilIdle()

        viewModel.resetMixToBalanced()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PrismPreset.BALANCED, state.preset)
        assertEquals(1f, state.vocals)
        assertEquals(1f, state.beats)
        assertEquals(1f, state.instruments)
    }
}
