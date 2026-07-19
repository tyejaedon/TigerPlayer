package com.example.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waveform_cache")
data class WaveformCacheEntity(
    @PrimaryKey val trackId: String,

    /**
     * AMBIENT BLOB STORAGE
     * Amplitudes (0..1) quantized to bytes (0..255).
     * 100 bars = 100 bytes. High efficiency for Studio-Grade visuals.
     */
    val amplitudesBlob: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WaveformCacheEntity
        if (trackId != other.trackId) return false
        if (!amplitudesBlob.contentEquals(other.amplitudesBlob)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = trackId.hashCode()
        result = 31 * result + amplitudesBlob.contentHashCode()
        return result
    }
}
