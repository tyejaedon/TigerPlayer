package com.example.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val trackId: String,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val lastAccessed: Long = System.currentTimeMillis() // Crucial for LRU
)