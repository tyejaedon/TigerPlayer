package com.example.tigerplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.tigerplayer.data.local.dao.PlaylistDao
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.ArtistCacheEntity
import com.example.tigerplayer.data.local.entity.CachedTrackEntity // NEW
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.example.tigerplayer.data.local.entity.PlaylistEntity
import com.example.tigerplayer.data.local.entity.PlaylistTrackCrossRef

@Database(
    entities = [
        PlaybackHistoryEntity::class,
        ArtistCacheEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        CachedTrackEntity::class // <-- Added
    ],
    version = 4, // <-- Bumped to 4
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TigerDatabase : RoomDatabase() {
    abstract fun tigerDao(): TigerDao
    abstract fun playlistDao(): PlaylistDao
}