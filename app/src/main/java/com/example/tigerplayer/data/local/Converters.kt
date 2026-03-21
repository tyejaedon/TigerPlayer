package com.example.tigerplayer.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMediaSource(value: MediaSource): String {
        return value.name
    }

    @TypeConverter
    fun toMediaSource(value: String): MediaSource {
        return MediaSource.valueOf(value)
    }
}
