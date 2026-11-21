package com.tigerplayer.engine

import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.entity.AudioDeviceEntity
import com.tigerplayer.data.local.entity.DevicePresetEntity
import com.tigerplayer.data.model.AudioDeviceType
import com.tigerplayer.data.model.AudioOutputDevice
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.data.model.HeadphoneSpecs
import com.tigerplayer.data.repository.AudioDeviceRepository
import com.tigerplayer.data.repository.AutoEqRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AudioDeviceEngineTest {

    private val audioDeviceRepository = mockk<AudioDeviceRepository>(relaxed = true)
    private val autoEqRepository = mockk<AutoEqRepository>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)

    private val activeDeviceFlow = MutableStateFlow(
        AudioOutputDevice(
            deviceId = "test_device",
            displayName = "Sony WH-1000XM4",
            type = AudioDeviceType.BLUETOOTH,
            isConnected = true,
            isActiveRoute = true,
            lastConnectedEpochMs = 1000L
        )
    )
    private val activePresetFlow = MutableStateFlow<DevicePresetEntity?>(null)
    private val settingsFlow = MutableStateFlow(TigerSettingsState())

    private lateinit var engine: AudioDeviceEngine

    @Before
    fun setUp() {
        every { audioDeviceRepository.currentActiveDevice } returns activeDeviceFlow
        every { audioDeviceRepository.currentActivePreset } returns activePresetFlow
        every { audioDeviceRepository.knownDevicesFlow } returns MutableStateFlow(emptyList())
        every { settingsDataStore.settingsFlow } returns settingsFlow

        engine = AudioDeviceEngine(
            audioDeviceRepository = audioDeviceRepository,
            autoEqRepository = autoEqRepository,
            settingsDataStore = settingsDataStore
        )
    }

    @Test
    fun `applyAutoEqToDevice fetches specs and attaches to device`() = runTest {
        val searchItem = HeadphoneSearchResult(
            id = "sony_wh_1000xm4_oratory",
            modelName = "Sony WH-1000XM4",
            source = "oratory1990",
            formFactor = "Over-Ear",
            targetCurve = "Harman Over-Ear 2018",
            path = "oratory1990/harman_over-ear_2018/Sony WH-1000XM4"
        )
        val specs = HeadphoneSpecs(
            modelName = "Sony WH-1000XM4",
            source = "oratory1990",
            targetCurve = "Harman Over-Ear 2018",
            formFactor = "Over-Ear",
            preampDb = -4.5f,
            bands = emptyList()
        )

        coEvery { autoEqRepository.fetchHeadphoneSpecs(searchItem) } returns Result.success(specs)

        val result = engine.applyAutoEqToDevice("test_device", searchItem)
        assertTrue(result.isSuccess)

        val preset = result.getOrThrow()
        assertEquals(-4.5f, preset.preampDb)
        assertEquals("Sony WH-1000XM4", preset.autoEqModel)

        coVerify {
            audioDeviceRepository.savePreset(any())
            audioDeviceRepository.attachAutoEqToDevice(
                deviceId = "test_device",
                modelName = "Sony WH-1000XM4",
                source = "oratory1990",
                targetCurve = "Harman Over-Ear 2018",
                formFactor = "Over-Ear",
                presetId = preset.presetId
            )
        }
    }

    @Test
    fun `settings update delegates to settingsDataStore`() = runTest {
        engine.setAutoApplyEnabled(true)
        coVerify { settingsDataStore.setAutoApplyDeviceEq(true) }

        engine.setAutoDetectEnabled(false)
        coVerify { settingsDataStore.setAutoDetectAutoEq(false) }

        engine.setPreferredTargetCurve("Harman In-Ear 2019v2")
        coVerify { settingsDataStore.setPreferredTargetCurve("Harman In-Ear 2019v2") }
    }
}
