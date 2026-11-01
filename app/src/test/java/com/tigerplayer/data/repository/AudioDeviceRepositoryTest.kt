package com.tigerplayer.data.repository

import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.dao.AudioDeviceDao
import com.tigerplayer.data.local.entity.AudioDeviceEntity
import com.tigerplayer.data.local.entity.DevicePresetEntity
import com.tigerplayer.data.model.AudioDeviceType
import com.tigerplayer.data.model.AudioOutputDevice
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.data.model.HeadphoneSpecs
import com.tigerplayer.utils.BluetoothDeviceInfo
import com.tigerplayer.utils.BluetoothDeviceManager
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AudioDeviceRepositoryTest {

    private val audioDeviceDao = mockk<AudioDeviceDao>(relaxed = true)
    private val bluetoothDeviceManager = mockk<BluetoothDeviceManager>(relaxed = true)
    private val autoEqRepository = mockk<AutoEqRepository>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val btStateFlow = MutableStateFlow(BluetoothDeviceInfo())
    private val settingsStateFlow = MutableStateFlow(TigerSettingsState())

    private lateinit var repository: AudioDeviceRepositoryImpl

    @Before
    fun setUp() {
        every { bluetoothDeviceManager.connectedDevice } returns btStateFlow
        every { settingsDataStore.settingsFlow } returns settingsStateFlow
        coEvery { audioDeviceDao.getAllDevicesFlow() } returns MutableStateFlow(emptyList())
        coEvery { audioDeviceDao.getDeviceById(any()) } returns null

        repository = AudioDeviceRepositoryImpl(
            audioDeviceDao = audioDeviceDao,
            bluetoothDeviceManager = bluetoothDeviceManager,
            autoEqRepository = autoEqRepository,
            settingsDataStore = settingsDataStore
        )
    }

    @Test
    fun `default active device is phone speaker`() {
        val active = repository.currentActiveDevice.value
        assertEquals(AudioOutputDevice.DEVICE_ID_SPEAKER, active.deviceId)
        assertEquals(AudioDeviceType.PHONE_SPEAKER, active.type)
        assertTrue(active.isConnected)
    }

    @Test
    fun `recordDeviceConnected updates active device and persists to dao`() = runTest {
        repository.recordDeviceConnected(
            deviceId = "AA:BB:CC:DD:EE:FF",
            displayName = "Sony WH-1000XM4",
            type = AudioDeviceType.BLUETOOTH,
            codec = "LDAC",
            batteryLevel = 90
        )

        val active = repository.currentActiveDevice.value
        assertEquals("AA:BB:CC:DD:EE:FF", active.deviceId)
        assertEquals("Sony WH-1000XM4", active.displayName)
        assertEquals(AudioDeviceType.BLUETOOTH, active.type)
        assertEquals("LDAC", active.codec)
        assertEquals(90, active.batteryLevel)

        coVerify {
            audioDeviceDao.upsertDevice(match {
                it.deviceId == "AA:BB:CC:DD:EE:FF" &&
                it.displayName == "Sony WH-1000XM4" &&
                it.lastKnownCodec == "LDAC" &&
                it.lastKnownBatteryLevel == 90
            })
        }
    }

    @Test
    fun `recordDeviceDisconnected resets active route to phone speaker`() = runTest {
        repository.recordDeviceConnected(
            deviceId = "AA:BB:CC:DD:EE:FF",
            displayName = "Sony WH-1000XM4",
            type = AudioDeviceType.BLUETOOTH
        )
        assertEquals("AA:BB:CC:DD:EE:FF", repository.currentActiveDevice.value.deviceId)

        repository.recordDeviceDisconnected("AA:BB:CC:DD:EE:FF")
        assertEquals(AudioOutputDevice.DEVICE_ID_SPEAKER, repository.currentActiveDevice.value.deviceId)
    }

    @Test
    fun `createNamedWiredProfile creates new wired device entity`() = runTest {
        val created = repository.createNamedWiredProfile(
            profileName = "Sennheiser HD 600",
            matchedAutoEqModel = "Sennheiser HD 600",
            targetCurve = "Harman Over-Ear 2018",
            formFactor = "Over-Ear"
        )

        assertEquals("wired:sennheiser_hd_600", created.deviceId)
        assertEquals("Sennheiser HD 600", created.displayName)
        assertEquals(AudioDeviceType.WIRED_HEADPHONES.name, created.deviceType)
        assertEquals("Harman Over-Ear 2018", created.targetCurve)
        assertEquals("Over-Ear", created.formFactor)

        coVerify {
            audioDeviceDao.upsertDevice(match {
                it.deviceId == "wired:sennheiser_hd_600" &&
                it.displayName == "Sennheiser HD 600"
            })
        }
    }

    @Test
    fun `attachAutoEqToDevice updates device entity and active state`() = runTest {
        val deviceEntity = AudioDeviceEntity(
            deviceId = "AA:BB:CC:DD:EE:FF",
            displayName = "Sony WH-1000XM4",
            deviceType = AudioDeviceType.BLUETOOTH.name,
            lastConnectedEpochMs = 1000L
        )
        coEvery { audioDeviceDao.getDeviceById("AA:BB:CC:DD:EE:FF") } returns deviceEntity

        repository.recordDeviceConnected("AA:BB:CC:DD:EE:FF", "Sony WH-1000XM4", AudioDeviceType.BLUETOOTH)

        repository.attachAutoEqToDevice(
            deviceId = "AA:BB:CC:DD:EE:FF",
            modelName = "Sony WH-1000XM4",
            source = "oratory1990",
            targetCurve = "Harman Over-Ear 2018",
            formFactor = "Over-Ear",
            presetId = "preset_xm4"
        )

        val active = repository.currentActiveDevice.value
        assertEquals("Sony WH-1000XM4", active.matchedAutoEqModel)
        assertEquals("oratory1990", active.matchedAutoEqSource)
        assertEquals("Harman Over-Ear 2018", active.targetCurve)
        assertEquals("Over-Ear", active.formFactor)
        assertEquals("preset_xm4", active.assignedPresetId)

        coVerify {
            audioDeviceDao.upsertDevice(match {
                it.deviceId == "AA:BB:CC:DD:EE:FF" &&
                it.matchedAutoEqModel == "Sony WH-1000XM4" &&
                it.targetCurve == "Harman Over-Ear 2018"
            })
        }
    }

    @Test
    fun `recordDeviceConnected auto-detects AutoEq curve when autoDetect is true`() = runTest {
        settingsStateFlow.value = TigerSettingsState(
            autoDetectAutoEq = true,
            autoApplyDeviceEq = true
        )

        val candidate = HeadphoneSearchResult(
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

        coEvery { autoEqRepository.findBestMatch("WH-1000XM4") } returns candidate
        coEvery { autoEqRepository.fetchHeadphoneSpecs(candidate) } returns Result.success(specs)

        val preset = DevicePresetEntity(
            presetId = "detected_preset_1",
            name = "Sony WH-1000XM4 (AutoEq)",
            deviceId = "11:22:33:44:55:66",
            preampDb = -4.5f,
            bandsJson = "[]",
            isAutoEq = true
        )
        coEvery { audioDeviceDao.getPresetById(any()) } returns preset

        repository.recordDeviceConnected(
            deviceId = "11:22:33:44:55:66",
            displayName = "WH-1000XM4",
            type = AudioDeviceType.BLUETOOTH
        )

        val active = repository.currentActiveDevice.value
        assertEquals("Sony WH-1000XM4", active.matchedAutoEqModel)
        assertEquals("Harman Over-Ear 2018", active.targetCurve)
        assertEquals("Over-Ear", active.formFactor)
        assertNotNull(active.assignedPresetId)

        // Verify active preset was auto-applied
        val activePreset = repository.currentActivePreset.value
        assertNotNull(activePreset)
        assertEquals(-4.5f, activePreset?.preampDb)
    }

    @Test
    fun `recordDeviceConnected does not auto-apply preset when autoApply is disabled`() = runTest {
        settingsStateFlow.value = TigerSettingsState(
            autoDetectAutoEq = false,
            autoApplyDeviceEq = false
        )

        val existing = AudioDeviceEntity(
            deviceId = "11:22:33:44:55:66",
            displayName = "Bose QC45",
            deviceType = AudioDeviceType.BLUETOOTH.name,
            lastConnectedEpochMs = 1000L,
            assignedPresetId = "bose_preset"
        )
        coEvery { audioDeviceDao.getDeviceById("11:22:33:44:55:66") } returns existing

        repository.recordDeviceConnected(
            deviceId = "11:22:33:44:55:66",
            displayName = "Bose QC45",
            type = AudioDeviceType.BLUETOOTH
        )

        val active = repository.currentActiveDevice.value
        assertEquals("bose_preset", active.assignedPresetId)
        // Auto-apply was disabled, so current active preset should be null
        assertNull(repository.currentActivePreset.value)
    }
}
