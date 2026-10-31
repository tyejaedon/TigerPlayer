package com.tigerplayer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists known audio output endpoints (Bluetooth, Phone Speaker, Wired Earphones / USB DAC).
 * Tracks connection history, friendly naming, and the assigned EQ preset binding.
 */
@Entity(tableName = "audio_devices")
data class AudioDeviceEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val deviceType: String,
    val lastConnectedEpochMs: Long,
    @ColumnInfo(defaultValue = "0")
    val firstSeenEpochMs: Long = System.currentTimeMillis(),
    val assignedPresetId: String? = null,
    val matchedAutoEqModel: String? = null,
    val matchedAutoEqSource: String? = null,
    val targetCurve: String? = null,
    val formFactor: String? = null,
    val lastKnownCodec: String? = null,
    @ColumnInfo(defaultValue = "-1")
    val lastKnownBatteryLevel: Int = -1,
    @ColumnInfo(defaultValue = "0")
    val totalListeningTimeMs: Long = 0L
)
