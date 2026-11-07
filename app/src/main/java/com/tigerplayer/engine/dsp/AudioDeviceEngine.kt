package com.tigerplayer.engine

import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.entity.AudioDeviceEntity
import com.tigerplayer.data.local.entity.DevicePresetEntity
import com.tigerplayer.data.model.AudioOutputDevice
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.data.model.HeadphoneSpecs
import com.tigerplayer.data.repository.AudioDeviceRepository
import com.tigerplayer.data.repository.AutoEqRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain engine orchestrating audio routing status, auto-detection of AutoEq profiles,
 * and automated application of per-device equalization curves.
 */
@Singleton
class AudioDeviceEngine @Inject constructor(
    private val audioDeviceRepository: AudioDeviceRepository,
    private val autoEqRepository: AutoEqRepository,
    private val settingsDataStore: SettingsDataStore
) {

    val activeDevice: StateFlow<AudioOutputDevice> = audioDeviceRepository.currentActiveDevice
    val activePreset: StateFlow<DevicePresetEntity?> = audioDeviceRepository.currentActivePreset
    val knownDevicesFlow: Flow<List<AudioOutputDevice>> = audioDeviceRepository.knownDevicesFlow
    val settingsFlow: Flow<TigerSettingsState> = settingsDataStore.settingsFlow

    suspend fun searchHeadphones(query: String): List<HeadphoneSearchResult> {
        return autoEqRepository.searchHeadphones(query)
    }

    suspend fun fetchHeadphoneSpecs(item: HeadphoneSearchResult): Result<HeadphoneSpecs> {
        return autoEqRepository.fetchHeadphoneSpecs(item)
    }

    suspend fun applyAutoEqToDevice(
        deviceId: String,
        item: HeadphoneSearchResult
    ): Result<DevicePresetEntity> {
        val specsResult = autoEqRepository.fetchHeadphoneSpecs(item)
        if (specsResult.isFailure) {
            return Result.failure(specsResult.exceptionOrNull() ?: RuntimeException("Unable to fetch specs"))
        }

        val specs = specsResult.getOrThrow()
        val preset = specs.toDevicePreset(deviceId = deviceId)
        audioDeviceRepository.savePreset(preset)
        audioDeviceRepository.attachAutoEqToDevice(
            deviceId = deviceId,
            modelName = specs.modelName,
            source = specs.source,
            targetCurve = specs.targetCurve,
            formFactor = specs.formFactor,
            presetId = preset.presetId
        )
        return Result.success(preset)
    }

    suspend fun assignPresetToDevice(deviceId: String, presetId: String?) {
        audioDeviceRepository.assignPresetToDevice(deviceId, presetId)
    }

    suspend fun createNamedWiredProfile(
        name: String,
        autoEqModel: String? = null,
        targetCurve: String? = null,
        formFactor: String? = null
    ): AudioDeviceEntity {
        return audioDeviceRepository.createNamedWiredProfile(name, autoEqModel, targetCurve, formFactor)
    }

    fun getPresetsForDeviceFlow(deviceId: String): Flow<List<DevicePresetEntity>> {
        return audioDeviceRepository.getPresetsForDeviceFlow(deviceId)
    }

    suspend fun saveCustomPreset(preset: DevicePresetEntity) {
        audioDeviceRepository.savePreset(preset)
    }

    suspend fun deletePreset(presetId: String) {
        audioDeviceRepository.deletePreset(presetId)
    }

    suspend fun deleteDevice(deviceId: String) {
        audioDeviceRepository.deleteDevice(deviceId)
    }

    suspend fun setAutoApplyEnabled(enabled: Boolean) {
        settingsDataStore.setAutoApplyDeviceEq(enabled)
    }

    suspend fun setAutoDetectEnabled(enabled: Boolean) {
        settingsDataStore.setAutoDetectAutoEq(enabled)
    }

    suspend fun setPreferredTargetCurve(curve: String) {
        settingsDataStore.setPreferredTargetCurve(curve)
    }
}
