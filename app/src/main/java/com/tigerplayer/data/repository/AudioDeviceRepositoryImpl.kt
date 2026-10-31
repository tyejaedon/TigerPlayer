package com.tigerplayer.data.repository

import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.dao.AudioDeviceDao
import com.tigerplayer.data.local.entity.AudioDeviceEntity
import com.tigerplayer.data.local.entity.DevicePresetEntity
import com.tigerplayer.data.model.AudioDeviceType
import com.tigerplayer.data.model.AudioOutputDevice
import com.tigerplayer.utils.BluetoothDeviceManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioDeviceRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAudioDeviceRepository(
        impl: AudioDeviceRepositoryImpl
    ): AudioDeviceRepository

    @Binds
    @Singleton
    abstract fun bindAutoEqRepository(
        impl: AutoEqRepositoryImpl
    ): AutoEqRepository
}

@Singleton
class AudioDeviceRepositoryImpl @Inject constructor(
    private val audioDeviceDao: AudioDeviceDao,
    private val bluetoothDeviceManager: BluetoothDeviceManager,
    private val autoEqRepository: AutoEqRepository,
    private val settingsDataStore: SettingsDataStore
) : AudioDeviceRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val defaultSpeakerDevice = AudioOutputDevice(
        deviceId = AudioOutputDevice.DEVICE_ID_SPEAKER,
        displayName = "Phone Speaker",
        type = AudioDeviceType.PHONE_SPEAKER,
        isConnected = true,
        isActiveRoute = true,
        lastConnectedEpochMs = System.currentTimeMillis()
    )

    private val _currentActiveDevice = MutableStateFlow(defaultSpeakerDevice)
    override val currentActiveDevice: StateFlow<AudioOutputDevice> = _currentActiveDevice.asStateFlow()

    private val _currentActivePreset = MutableStateFlow<DevicePresetEntity?>(null)
    override val currentActivePreset: StateFlow<DevicePresetEntity?> = _currentActivePreset.asStateFlow()

    override val knownDevicesFlow: Flow<List<AudioOutputDevice>> = audioDeviceDao.getAllDevicesFlow()
        .map { entities ->
            val active = _currentActiveDevice.value
            entities.map { entity ->
                val isConnected = (entity.deviceId == active.deviceId && active.isConnected)
                AudioOutputDevice(
                    deviceId = entity.deviceId,
                    displayName = entity.displayName,
                    type = AudioDeviceType.fromString(entity.deviceType),
                    isConnected = isConnected,
                    isActiveRoute = entity.deviceId == active.deviceId,
                    lastConnectedEpochMs = entity.lastConnectedEpochMs,
                    codec = if (entity.deviceId == active.deviceId) active.codec else (entity.lastKnownCodec ?: "N/A"),
                    batteryLevel = if (entity.deviceId == active.deviceId) active.batteryLevel else entity.lastKnownBatteryLevel,
                    assignedPresetId = entity.assignedPresetId,
                    matchedAutoEqModel = entity.matchedAutoEqModel,
                    matchedAutoEqSource = entity.matchedAutoEqSource,
                    targetCurve = entity.targetCurve,
                    formFactor = entity.formFactor,
                    listeningTimeMs = entity.totalListeningTimeMs
                )
            }
        }
        .flowOn(Dispatchers.IO)

    init {
        repositoryScope.launch {
            ensureDefaultDevices()
            observeBluetoothConnections()
            observeSettings()
        }
    }

    private suspend fun ensureDefaultDevices() {
        val existingSpeaker = audioDeviceDao.getDeviceById(AudioOutputDevice.DEVICE_ID_SPEAKER)
        if (existingSpeaker == null) {
            audioDeviceDao.upsertDevice(
                AudioDeviceEntity(
                    deviceId = AudioOutputDevice.DEVICE_ID_SPEAKER,
                    displayName = "Phone Speaker",
                    deviceType = AudioDeviceType.PHONE_SPEAKER.name,
                    lastConnectedEpochMs = System.currentTimeMillis(),
                    formFactor = "Speaker"
                )
            )
        }
        val existingWired = audioDeviceDao.getDeviceById(AudioOutputDevice.DEVICE_ID_WIRED)
        if (existingWired == null) {
            audioDeviceDao.upsertDevice(
                AudioDeviceEntity(
                    deviceId = AudioOutputDevice.DEVICE_ID_WIRED,
                    displayName = "Wired Earphones",
                    deviceType = AudioDeviceType.WIRED_HEADPHONES.name,
                    lastConnectedEpochMs = 0L,
                    formFactor = "In-Ear"
                )
            )
        }
    }

    private fun observeBluetoothConnections() {
        repositoryScope.launch {
            bluetoothDeviceManager.connectedDevice.collect { btInfo ->
                if (btInfo.isConnected && btInfo.address.isNotBlank()) {
                    recordDeviceConnected(
                        deviceId = btInfo.address,
                        displayName = btInfo.name,
                        type = AudioDeviceType.BLUETOOTH,
                        codec = btInfo.codec,
                        batteryLevel = btInfo.batteryLevel
                    )
                } else if (!btInfo.isConnected && _currentActiveDevice.value.type == AudioDeviceType.BLUETOOTH) {
                    recordDeviceDisconnected(_currentActiveDevice.value.deviceId)
                }
            }
        }
    }

    private fun observeSettings() {
        repositoryScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                refreshActivePreset(settings.autoApplyDeviceEq)
            }
        }
    }

    private suspend fun refreshActivePreset(autoApplyEnabled: Boolean) {
        val active = _currentActiveDevice.value
        if (!autoApplyEnabled) {
            _currentActivePreset.value = null
            return
        }
        val presetId = active.assignedPresetId
        if (presetId != null) {
            _currentActivePreset.value = audioDeviceDao.getPresetById(presetId)
        } else {
            _currentActivePreset.value = null
        }
    }

    override suspend fun recordDeviceConnected(
        deviceId: String,
        displayName: String,
        type: AudioDeviceType,
        codec: String?,
        batteryLevel: Int
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val settings = settingsDataStore.settingsFlow.first()
        val existing = audioDeviceDao.getDeviceById(deviceId)
        var entity = existing?.copy(
            displayName = if (displayName.isNotBlank()) displayName else existing.displayName,
            lastConnectedEpochMs = now,
            lastKnownCodec = codec ?: existing.lastKnownCodec,
            lastKnownBatteryLevel = if (batteryLevel >= 0) batteryLevel else existing.lastKnownBatteryLevel
        ) ?: AudioDeviceEntity(
            deviceId = deviceId,
            displayName = displayName,
            deviceType = type.name,
            lastConnectedEpochMs = now,
            firstSeenEpochMs = now,
            lastKnownCodec = codec,
            lastKnownBatteryLevel = batteryLevel
        )

        // Auto-detection: attempt to resolve and bind AutoEq curve if none assigned yet
        if (settings.autoDetectAutoEq && entity.matchedAutoEqModel == null && displayName.isNotBlank()) {
            try {
                val candidate = autoEqRepository.findBestMatch(displayName)
                if (candidate != null) {
                    val specsResult = autoEqRepository.fetchHeadphoneSpecs(candidate)
                    if (specsResult.isSuccess) {
                        val specs = specsResult.getOrThrow()
                        val preset = com.tigerplayer.data.local.entity.DevicePresetEntity(
                            presetId = java.util.UUID.randomUUID().toString(),
                            name = "${specs.modelName} (AutoEq)",
                            deviceId = deviceId,
                            preampDb = specs.preampDb,
                            bandsJson = com.google.gson.Gson().toJson(specs.bands),
                            isAutoEq = true,
                            autoEqModel = specs.modelName,
                            autoEqSource = specs.source,
                            targetCurve = specs.targetCurve,
                            formFactor = specs.formFactor,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now
                        )
                        audioDeviceDao.upsertPreset(preset)
                        entity = entity.copy(
                            matchedAutoEqModel = specs.modelName,
                            matchedAutoEqSource = specs.source,
                            targetCurve = specs.targetCurve,
                            formFactor = specs.formFactor,
                            assignedPresetId = preset.presetId
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        audioDeviceDao.upsertDevice(entity)

        val activeDevice = AudioOutputDevice(
            deviceId = entity.deviceId,
            displayName = entity.displayName,
            type = type,
            isConnected = true,
            isActiveRoute = true,
            lastConnectedEpochMs = now,
            codec = codec ?: entity.lastKnownCodec ?: "N/A",
            batteryLevel = if (batteryLevel >= 0) batteryLevel else entity.lastKnownBatteryLevel,
            assignedPresetId = entity.assignedPresetId,
            matchedAutoEqModel = entity.matchedAutoEqModel,
            matchedAutoEqSource = entity.matchedAutoEqSource,
            targetCurve = entity.targetCurve,
            formFactor = entity.formFactor,
            listeningTimeMs = entity.totalListeningTimeMs
        )
        _currentActiveDevice.value = activeDevice

        // Auto-apply logic
        val presetId = entity.assignedPresetId
        if (settings.autoApplyDeviceEq && presetId != null) {
            _currentActivePreset.value = audioDeviceDao.getPresetById(presetId)
        } else {
            _currentActivePreset.value = null
        }
    }

    override suspend fun recordDeviceDisconnected(deviceId: String) = withContext(Dispatchers.IO) {
        if (_currentActiveDevice.value.deviceId == deviceId) {
            _currentActiveDevice.value = defaultSpeakerDevice
            val settings = settingsDataStore.settingsFlow.first()
            val speakerPresetId = defaultSpeakerDevice.assignedPresetId
            if (settings.autoApplyDeviceEq && speakerPresetId != null) {
                _currentActivePreset.value = audioDeviceDao.getPresetById(speakerPresetId)
            } else {
                _currentActivePreset.value = null
            }
        }
    }

    override suspend fun assignPresetToDevice(deviceId: String, presetId: String?) = withContext(Dispatchers.IO) {
        audioDeviceDao.updateAssignedPreset(deviceId, presetId)
        if (_currentActiveDevice.value.deviceId == deviceId) {
            _currentActiveDevice.value = _currentActiveDevice.value.copy(assignedPresetId = presetId)
            val settings = settingsDataStore.settingsFlow.first()
            if (settings.autoApplyDeviceEq && presetId != null) {
                _currentActivePreset.value = audioDeviceDao.getPresetById(presetId)
            } else {
                _currentActivePreset.value = null
            }
        }
    }

    override suspend fun attachAutoEqToDevice(
        deviceId: String,
        modelName: String,
        source: String,
        targetCurve: String,
        formFactor: String,
        presetId: String
    ) = withContext(Dispatchers.IO) {
        val existing = audioDeviceDao.getDeviceById(deviceId) ?: return@withContext
        val updated = existing.copy(
            matchedAutoEqModel = modelName,
            matchedAutoEqSource = source,
            targetCurve = targetCurve,
            formFactor = formFactor,
            assignedPresetId = presetId
        )
        audioDeviceDao.upsertDevice(updated)

        if (_currentActiveDevice.value.deviceId == deviceId) {
            _currentActiveDevice.value = _currentActiveDevice.value.copy(
                matchedAutoEqModel = modelName,
                matchedAutoEqSource = source,
                targetCurve = targetCurve,
                formFactor = formFactor,
                assignedPresetId = presetId
            )
            val settings = settingsDataStore.settingsFlow.first()
            if (settings.autoApplyDeviceEq) {
                _currentActivePreset.value = audioDeviceDao.getPresetById(presetId)
            }
        }
    }

    override suspend fun createNamedWiredProfile(
        profileName: String,
        matchedAutoEqModel: String?,
        targetCurve: String?,
        formFactor: String?
    ): AudioDeviceEntity = withContext(Dispatchers.IO) {
        val slug = profileName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val deviceId = "wired:$slug"
        val now = System.currentTimeMillis()

        val entity = AudioDeviceEntity(
            deviceId = deviceId,
            displayName = profileName,
            deviceType = AudioDeviceType.WIRED_HEADPHONES.name,
            lastConnectedEpochMs = now,
            firstSeenEpochMs = now,
            matchedAutoEqModel = matchedAutoEqModel,
            targetCurve = targetCurve,
            formFactor = formFactor ?: "In-Ear"
        )
        audioDeviceDao.upsertDevice(entity)
        entity
    }

    override fun getPresetsForDeviceFlow(deviceId: String): Flow<List<DevicePresetEntity>> {
        return audioDeviceDao.getPresetsForDeviceFlow(deviceId).flowOn(Dispatchers.IO)
    }

    override fun getAllPresetsFlow(): Flow<List<DevicePresetEntity>> {
        return audioDeviceDao.getAllPresetsFlow().flowOn(Dispatchers.IO)
    }

    override suspend fun savePreset(preset: DevicePresetEntity) = withContext(Dispatchers.IO) {
        audioDeviceDao.upsertPreset(preset)
    }

    override suspend fun deletePreset(presetId: String) = withContext(Dispatchers.IO) {
        audioDeviceDao.deletePreset(presetId)
    }

    override suspend fun deleteDevice(deviceId: String) = withContext(Dispatchers.IO) {
        audioDeviceDao.deleteDevice(deviceId)
    }
}
