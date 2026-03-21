package com.example.tigerplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artist_cache")
data class ArtistCacheEntity(
    @PrimaryKey val artistName: String,
    val imageUrl: String?,
    val bio: String? // --- NEW ---
)