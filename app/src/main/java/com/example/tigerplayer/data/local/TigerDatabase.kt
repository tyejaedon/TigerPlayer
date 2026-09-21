package com.tigerplayer.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tigerplayer.data.local.dao.MusicFolderDao
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.ArtistCacheEntity
import com.tigerplayer.data.local.entity.CachedTrackEntity // NEW
import com.tigerplayer.data.local.entity.MusicFolderEntity
import com.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.tigerplayer.data.local.entity.LyricsCacheEntity
import com.tigerplayer.data.local.entity.WaveformCacheEntity

@Database(
    entities = [
        PlaybackHistoryEntity::class,
        ArtistCacheEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        WaveformCacheEntity::class,
        CachedTrackEntity::class, // <-- Added
        LyricsCacheEntity::class, // 2. Add it to the array
        MusicFolderEntity::class // <-- Added for custom music folders (issue #50)
    ],
    version = 14, // <-- Bumped to 14 for MusicFolderEntity (issue #50)
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14)
    ]
)
@TypeConverters(Converters::class)
abstract class TigerDatabase : RoomDatabase() {
    abstract fun tigerDao(): TigerDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun musicFolderDao(): MusicFolderDao
}