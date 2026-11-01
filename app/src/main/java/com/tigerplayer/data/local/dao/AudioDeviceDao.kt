package com.tigerplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tigerplayer.data.local.entity.AudioDeviceEntity
import com.tigerplayer.data.local.entity.DevicePresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AudioDeviceDao {

    @Query("SELECT * FROM audio_devices ORDER BY lastConnectedEpochMs DESC")
    abstract fun getAllDevicesFlow(): Flow<List<AudioDeviceEntity>>

    @Query("SELECT * FROM audio_devices ORDER BY lastConnectedEpochMs DESC")
    abstract suspend fun getAllDevicesSync(): List<AudioDeviceEntity>

    @Query("SELECT * FROM audio_devices WHERE deviceId = :deviceId LIMIT 1")
    abstract suspend fun getDeviceById(deviceId: String): AudioDeviceEntity?

    @Query("SELECT * FROM audio_devices WHERE deviceId = :deviceId LIMIT 1")
    abstract fun getDeviceByIdFlow(deviceId: String): Flow<AudioDeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDevice(device: AudioDeviceEntity)

    @Query("UPDATE audio_devices SET lastConnectedEpochMs = :timestamp, lastKnownCodec = :codec, lastKnownBatteryLevel = :batteryLevel WHERE deviceId = :deviceId")
    abstract suspend fun updateLastConnected(deviceId: String, timestamp: Long, codec: String?, batteryLevel: Int)

    @Query("UPDATE audio_devices SET assignedPresetId = :presetId WHERE deviceId = :deviceId")
    abstract suspend fun updateAssignedPreset(deviceId: String, presetId: String?)

    @Query("DELETE FROM audio_devices WHERE deviceId = :deviceId")
    abstract suspend fun deleteDevice(deviceId: String)

    @Query("SELECT * FROM device_presets WHERE deviceId = :deviceId OR deviceId IS NULL ORDER BY updatedAtEpochMs DESC")
    abstract fun getPresetsForDeviceFlow(deviceId: String): Flow<List<DevicePresetEntity>>

    @Query("SELECT * FROM device_presets ORDER BY updatedAtEpochMs DESC")
    abstract fun getAllPresetsFlow(): Flow<List<DevicePresetEntity>>

    @Query("SELECT * FROM device_presets ORDER BY updatedAtEpochMs DESC")
    abstract suspend fun getAllPresetsSync(): List<DevicePresetEntity>

    @Query("SELECT * FROM device_presets WHERE presetId = :presetId LIMIT 1")
    abstract suspend fun getPresetById(presetId: String): DevicePresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPreset(preset: DevicePresetEntity)

    @Query("DELETE FROM device_presets WHERE presetId = :presetId")
    abstract suspend fun deletePreset(presetId: String)
}
