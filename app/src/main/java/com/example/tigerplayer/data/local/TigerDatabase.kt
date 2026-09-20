package com.example.tigerplayer.data.local

import androidx.room.AutoMigration
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
import com.example.tigerplayer.data.local.entity.LyricsCacheEntity
import com.example.tigerplayer.data.local.entity.WaveformCacheEntity

@Database(
    entities = [
        PlaybackHistoryEntity::class,
        ArtistCacheEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        WaveformCacheEntity::class,
        CachedTrackEntity::class, // <-- Added
        LyricsCacheEntity::class // 2. Add it to the array
    ],
    version = 13, // <-- Bumped to 13 for the dateModified fingerprint column (issue #49)
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13)
    ]
)
@TypeConverters(Converters::class)
abstract class TigerDatabase : RoomDatabase() {
    abstract fun tigerDao(): TigerDao
    abstract fun playlistDao(): PlaylistDao
}