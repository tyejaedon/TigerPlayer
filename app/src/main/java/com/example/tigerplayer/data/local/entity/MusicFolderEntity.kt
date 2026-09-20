package com.example.tigerplayer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-selected music directory (issue #50), added via `ACTION_OPEN_DOCUMENT_TREE` (SAF).
 *
 * [isExcluded] distinguishes two roles:
 * - `false`: an "include" root - [SafFolderScanner] walks it as an additional source of tracks,
 *   alongside the MediaStore fast path.
 * - `true`: an exclude entry - tracks whose resolved path falls under [path] are dropped from the
 *   library everywhere (scan, search, playback), regardless of which source found them.
 */
@Entity(tableName = "music_folders")
data class MusicFolderEntity(
    @PrimaryKey val uriString: String,
    val displayName: String,
    @ColumnInfo(defaultValue = "")
    val path: String,
    val isExcluded: Boolean,
    val addedAt: Long = System.currentTimeMillis()
)
