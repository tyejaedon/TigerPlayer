package com.tigerplayer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists equalizer presets associated with audio devices or standalone custom curves.
 */
@Entity(tableName = "device_presets")
data class DevicePresetEntity(
    @PrimaryKey val presetId: String,
    val name: String,
    val deviceId: String? = null,
    @ColumnInfo(defaultValue = "0.0")
    val preampDb: Float = 0.0f,
    val bandsJson: String,
    @ColumnInfo(defaultValue = "0")
    val isAutoEq: Boolean = false,
    val autoEqModel: String? = null,
    val autoEqSource: String? = null,
    val targetCurve: String? = null,
    val formFactor: String? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
