package com.tigerplayer.ui.devices

import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.entity.DevicePresetEntity
import com.tigerplayer.data.model.AudioDeviceType
import com.tigerplayer.data.model.AudioOutputDevice
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.engine.AudioDeviceEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioDevicesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val engine = mockk<AudioDeviceEngine>(relaxed = true)

    private val testActiveDevice = AudioOutputDevice(
        deviceId = "bt:00:11:22:33:44:55",
        displayName = "Sony WH-1000XM4",
        type = AudioDeviceType.BLUETOOTH,
        isConnected = true,
        isActiveRoute = true,
        lastConnectedEpochMs = 1700000000000L,
        batteryLevel = 85,
        codec = "LDAC",
        matchedAutoEqModel = "Sony WH-1000XM4",
        targetCurve = "Harman Over-Ear 2018",
        formFactor = "Over-Ear"
    )

    private val activeDeviceFlow = MutableStateFlow(testActiveDevice)
    private val activePresetFlow = MutableStateFlow<DevicePresetEntity?>(null)
    private val knownDevicesFlow = MutableStateFlow<List<AudioOutputDevice>>(listOf(testActiveDevice))
    private val settingsFlow = MutableStateFlow(
        TigerSettingsState(
            autoApplyDeviceEq = true,
            autoDetectAutoEq = true,
            preferredTargetCurve = "Harman Over-Ear 2018"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { engine.activeDevice } returns activeDeviceFlow
        every { engine.activePreset } returns activePresetFlow
        every { engine.knownDevicesFlow } returns knownDevicesFlow
        every { engine.settingsFlow } returns settingsFlow
        every { engine.getPresetsForDeviceFlow(any()) } returns flowOf(emptyList())
        coEvery { engine.searchHeadphones(any()) } returns listOf(
            HeadphoneSearchResult(
                id = "sony_wh1000xm4",
                modelName = "Sony WH-1000XM4",
                source = "oratory1990",
                formFactor = "Over-Ear",
                targetCurve = "Harman Over-Ear 2018",
                path = "oratory1990/harman_over-ear_2018/Sony WH-1000XM4"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AudioDevicesViewModel {
        return AudioDevicesViewModel(engine)
    }

    @Test
    fun initialUiState_combinesActiveDeviceAndSettings() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Sony WH-1000XM4", state.activeDevice.displayName)
        assertEquals(true, state.autoApplyEnabled)
        assertEquals(true, state.autoDetectEnabled)
        assertEquals("Harman Over-Ear 2018", state.preferredTargetCurve)
        assertEquals(1, state.knownDevices.size)
        assertEquals(1, state.searchResults.size)
    }

    @Test
    fun onAutoApplyToggled_callsEngineSetAutoApplyEnabled() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAutoApplyToggled(false)
        advanceUntilIdle()

        coVerify { engine.setAutoApplyEnabled(false) }
    }

    @Test
    fun onAutoDetectToggled_callsEngineSetAutoDetectEnabled() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAutoDetectToggled(false)
        advanceUntilIdle()

        coVerify { engine.setAutoDetectEnabled(false) }
    }

    @Test
    fun onPreferredTargetCurveSelected_callsEngine() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onPreferredTargetCurveSelected("Diffuse Field")
        advanceUntilIdle()

        coVerify { engine.setPreferredTargetCurve("Diffuse Field") }
    }

    @Test
    fun onSearchQueryChanged_debouncesAndUpdatesSearchResults() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Sennheiser")
        assertEquals("Sennheiser", viewModel.uiState.value.searchQuery)
        assertTrue(viewModel.uiState.value.isSearching)

        advanceTimeBy(200)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSearching)
        coVerify { engine.searchHeadphones("Sennheiser") }
    }

    @Test
    fun openAutoEqDialog_setsSelectedDeviceAndShowsDialog() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openAutoEqDialog(testActiveDevice)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showAutoEqDialog)
        assertEquals(testActiveDevice, viewModel.uiState.value.selectedDeviceForAutoEq)

        viewModel.closeAutoEqDialog()
        assertFalse(viewModel.uiState.value.showAutoEqDialog)
        assertNull(viewModel.uiState.value.selectedDeviceForAutoEq)
    }

    @Test
    fun selectAutoEqModel_appliesPresetAndClosesDialog() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val selectedModel = HeadphoneSearchResult(
            id = "sony_wh1000xm4",
            modelName = "Sony WH-1000XM4",
            source = "oratory1990",
            formFactor = "Over-Ear",
            targetCurve = "Harman Over-Ear 2018",
            path = "oratory1990/harman_over-ear_2018/Sony WH-1000XM4"
        )
        viewModel.openAutoEqDialog(testActiveDevice)
        advanceUntilIdle()

        viewModel.selectAutoEqModel(selectedModel)
        advanceUntilIdle()

        coVerify { engine.applyAutoEqToDevice(testActiveDevice.deviceId, selectedModel) }
        assertFalse(viewModel.uiState.value.showAutoEqDialog)
    }

    @Test
    fun createNamedWiredProfile_callsEngineAndClosesDialog() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openCreateWiredDialog()
        assertTrue(viewModel.uiState.value.showCreateWiredDialog)

        viewModel.createNamedWiredProfile(
            name = "Sennheiser HD600",
            autoEqModel = "Sennheiser HD 600",
            targetCurve = "Harman Over-Ear 2018",
            formFactor = "Over-Ear"
        )
        advanceUntilIdle()

        coVerify {
            engine.createNamedWiredProfile(
                "Sennheiser HD600",
                "Sennheiser HD 600",
                "Harman Over-Ear 2018",
                "Over-Ear"
            )
        }
        assertFalse(viewModel.uiState.value.showCreateWiredDialog)
    }

    @Test
    fun assignPreset_callsEngine() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.assignPreset("test_device", "preset_123")
        advanceUntilIdle()

        coVerify { engine.assignPresetToDevice("test_device", "preset_123") }
    }

    @Test
    fun deleteDevice_callsEngine() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteDevice("test_device")
        advanceUntilIdle()

        coVerify { engine.deleteDevice("test_device") }
    }

    @Test
    fun deletePreset_callsEngine() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deletePreset("preset_123")
        advanceUntilIdle()

        coVerify { engine.deletePreset("preset_123") }
    }
}
