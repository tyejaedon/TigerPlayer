package com.example.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waveform_cache")
data class WaveformCacheEntity(
    @PrimaryKey val trackId: String,

    // Stored as a comma-separated string of normalized Float amplitudes
    val amplitudes: String
)