package com.tigerplayer.data.repository

import com.tigerplayer.data.local.entity.AudioDeviceEntity
import com.tigerplayer.data.local.entity.DevicePresetEntity
import com.tigerplayer.data.model.AudioDeviceType
import com.tigerplayer.data.model.AudioOutputDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AudioDeviceRepository {
    val currentActiveDevice: StateFlow<AudioOutputDevice>
    val currentActivePreset: StateFlow<DevicePresetEntity?>
    val knownDevicesFlow: Flow<List<AudioOutputDevice>>

    suspend fun recordDeviceConnected(
        deviceId: String,
        displayName: String,
        type: AudioDeviceType,
        codec: String? = null,
        batteryLevel: Int = -1
    )

    suspend fun recordDeviceDisconnected(deviceId: String)

    suspend fun assignPresetToDevice(deviceId: String, presetId: String?)
    suspend fun attachAutoEqToDevice(
        deviceId: String,
        modelName: String,
        source: String,
        targetCurve: String,
        formFactor: String,
        presetId: String
    )

    suspend fun createNamedWiredProfile(
        profileName: String,
        matchedAutoEqModel: String? = null,
        targetCurve: String? = null,
        formFactor: String? = null
    ): AudioDeviceEntity

    fun getPresetsForDeviceFlow(deviceId: String): Flow<List<DevicePresetEntity>>
    fun getAllPresetsFlow(): Flow<List<DevicePresetEntity>>
    suspend fun savePreset(preset: DevicePresetEntity)
    suspend fun deletePreset(presetId: String)
    suspend fun deleteDevice(deviceId: String)
}
