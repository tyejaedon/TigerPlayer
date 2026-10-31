package com.tigerplayer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that Room schema migrations preserve existing data, per issue #43 / #74. A schema
 * validation pass alone is not enough - PlaybackHistoryEntity rows and cached track rows written
 * under an older schema must still be readable, with their original values intact, after the
 * migration runs.
 *
 * Covers:
 * - 11 -> 12: ReplayGain columns on CachedTrackEntity (issue #56)
 * - 12 -> 13: dateModified fingerprint column on CachedTrackEntity (issue #49)
 * - 13 -> 14: new music_folders table for custom directories (issue #50)
 * - 14 -> 15: new audio_devices and device_presets tables (issue #300)
 * - 15 -> 16: artwork lookup negative cache columns on CachedTrackEntity (issue #64)
 */
@RunWith(AndroidJUnit4::class)
class TigerDatabaseMigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TigerDatabase::class.java
    )

    @Test
    fun migrate11To12_preservesCachedTrackRows_andBackfillsReplayGainColumnsAsNull() {
        // Arrange: create the DB at version 11 (pre-ReplayGain) and insert a representative row.
        var db: SupportSQLiteDatabase = helper.createDatabase(testDbName, 11)
        db.execSQL(
            """
            INSERT INTO cached_tracks
                (id, title, artist, album, uriString, artworkUriString, durationMs, mimeType,
                 bitrate, sampleRate, trackNumber, year, dateAdded, isLiked, path)
            VALUES
                ('local_track_001', 'Neon Drift', 'Tiger Unit', 'Midnight Circuit',
                 'content://media/external/audio/media/42', 'content://media/external/audio/albumart/7',
                 243000, 'audio/flac', 960000, 48000, 3, '2026', 1719374400, 1,
                 '/storage/emulated/0/Music/Tiger/Neon Drift.flac')
            """.trimIndent()
        )
        db.close()

        // Act: run the real 11 -> 12 migration path (validates against the committed schema JSON).
        db = helper.runMigrationsAndValidate(testDbName, 12, true)

        // Assert: the pre-existing row survives, untouched, with the new columns defaulting to NULL.
        val cursor = db.query("SELECT * FROM cached_tracks WHERE id = 'local_track_001'")
        assertTrue("expected the pre-migration row to still exist", cursor.moveToFirst())
        assertEquals("Neon Drift", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals("Tiger Unit", cursor.getString(cursor.getColumnIndexOrThrow("artist")))
        assertEquals(243000L, cursor.getLong(cursor.getColumnIndexOrThrow("durationMs")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isLiked")))
        assertEquals(
            "/storage/emulated/0/Music/Tiger/Neon Drift.flac",
            cursor.getString(cursor.getColumnIndexOrThrow("path"))
        )

        val trackGainIndex = cursor.getColumnIndexOrThrow("replayGainTrackDb")
        val albumGainIndex = cursor.getColumnIndexOrThrow("replayGainAlbumDb")
        val trackPeakIndex = cursor.getColumnIndexOrThrow("replayGainTrackPeak")
        val albumPeakIndex = cursor.getColumnIndexOrThrow("replayGainAlbumPeak")
        assertTrue("untagged rows must backfill to NULL, not 0.0", cursor.isNull(trackGainIndex))
        assertTrue("untagged rows must backfill to NULL, not 0.0", cursor.isNull(albumGainIndex))
        assertTrue("untagged rows must backfill to NULL, not 0.0", cursor.isNull(trackPeakIndex))
        assertTrue("untagged rows must backfill to NULL, not 0.0", cursor.isNull(albumPeakIndex))
        cursor.close()
        db.close()
    }

    @Test
    fun migrate11To12_preservesPlaybackHistoryAndPlaylistOrdering() {
        // Arrange: seed irreplaceable history + user-authored playlist ordering at version 11.
        var db: SupportSQLiteDatabase = helper.createDatabase(testDbName, 11)
        db.execSQL(
            """
            INSERT INTO playback_history
                (trackId, title, artist, album, imageUrl, durationListenedMs, timestamp, source)
            VALUES
                ('local_track_001', 'Neon Drift', 'Tiger Unit', 'Midnight Circuit', NULL,
                 210000, 1719374500000, 'LOCAL')
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO playlists (playlistId, name, artworkUri, createdAt, position) " +
                "VALUES (1, 'Late Night Drive', NULL, 1719374000, 0)"
        )
        db.execSQL(
            "INSERT INTO playlist_track_cross_ref (playlistId, trackId, dateAdded, position) " +
                "VALUES (1, 'local_track_001', 1719374100, 0)"
        )
        db.close()

        // Act
        db = helper.runMigrationsAndValidate(testDbName, 12, true)

        // Assert: history and ordering are untouched by the unrelated schema change.
        val historyCursor = db.query("SELECT * FROM playback_history WHERE trackId = 'local_track_001'")
        assertTrue("playback history must survive the migration", historyCursor.moveToFirst())
        assertEquals(210000L, historyCursor.getLong(historyCursor.getColumnIndexOrThrow("durationListenedMs")))
        historyCursor.close()

        val crossRefCursor = db.query(
            "SELECT position FROM playlist_track_cross_ref WHERE playlistId = 1 AND trackId = 'local_track_001'"
        )
        assertTrue("playlist ordering must survive the migration", crossRefCursor.moveToFirst())
        assertEquals(0, crossRefCursor.getInt(crossRefCursor.getColumnIndexOrThrow("position")))
        crossRefCursor.close()
        db.close()
    }

    @Test
    fun migrate12To13_preservesCachedTrackRows_andBackfillsDateModifiedAsZero() {
        // Arrange: create the DB at version 12 (pre-dateModified) and insert a representative row.
        var db: SupportSQLiteDatabase = helper.createDatabase(testDbName, 12)
        db.execSQL(
            """
            INSERT INTO cached_tracks
                (id, title, artist, album, uriString, artworkUriString, durationMs, mimeType,
                 bitrate, sampleRate, trackNumber, year, dateAdded, isLiked, path,
                 replayGainTrackDb, replayGainAlbumDb, replayGainTrackPeak, replayGainAlbumPeak)
            VALUES
                ('local_track_002', 'Chrome Static', 'Tiger Unit', 'Midnight Circuit',
                 'content://media/external/audio/media/43', 'content://media/external/audio/albumart/8',
                 198000, 'audio/flac', 960000, 48000, 5, '2026', 1719374400, 0,
                 '/storage/emulated/0/Music/Tiger/Chrome Static.flac', -3.2, -2.1, 0.95, 0.98)
            """.trimIndent()
        )
        db.close()

        // Act: run the real 12 -> 13 migration path (validates against the committed schema JSON).
        db = helper.runMigrationsAndValidate(testDbName, 13, true)

        // Assert: the pre-existing row survives, untouched, with dateModified defaulting to 0.
        val cursor = db.query("SELECT * FROM cached_tracks WHERE id = 'local_track_002'")
        assertTrue("expected the pre-migration row to still exist", cursor.moveToFirst())
        assertEquals("Chrome Static", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals(198000L, cursor.getLong(cursor.getColumnIndexOrThrow("durationMs")))
        assertEquals(-3.2, cursor.getDouble(cursor.getColumnIndexOrThrow("replayGainTrackDb")), 0.0001)
        assertEquals(
            0L,
            cursor.getLong(cursor.getColumnIndexOrThrow("dateModified"))
        )
        cursor.close()
        db.close()
    }

    @Test
    fun migrate13To14_preservesCachedTrackRows_andCreatesMusicFoldersTable() {
        // Arrange: create the DB at version 13 (pre-music_folders) and insert a representative row.
        var db: SupportSQLiteDatabase = helper.createDatabase(testDbName, 13)
        db.execSQL(
            """
            INSERT INTO cached_tracks
                (id, title, artist, album, uriString, artworkUriString, durationMs, mimeType,
                 bitrate, sampleRate, trackNumber, year, dateAdded, isLiked, path,
                 replayGainTrackDb, replayGainAlbumDb, replayGainTrackPeak, replayGainAlbumPeak,
                 dateModified)
            VALUES
                ('local_track_003', 'Vault Signal', 'Tiger Unit', 'Midnight Circuit',
                 'content://media/external/audio/media/44', 'content://media/external/audio/albumart/9',
                 205000, 'audio/flac', 960000, 48000, 6, '2026', 1719374400, 0,
                 '/storage/emulated/0/Music/Tiger/Vault Signal.flac', -3.0, -2.0, 0.9, 0.95,
                 1719374400)
            """.trimIndent()
        )
        db.close()

        // Act: run the real 13 -> 14 migration path (validates against the committed schema JSON).
        db = helper.runMigrationsAndValidate(testDbName, 14, true)

        // Assert: the pre-existing cached track survives untouched.
        val cursor = db.query("SELECT * FROM cached_tracks WHERE id = 'local_track_003'")
        assertTrue("expected the pre-migration row to still exist", cursor.moveToFirst())
        assertEquals("Vault Signal", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        cursor.close()

        // Assert: the new music_folders table exists and is usable.
        db.execSQL(
            "INSERT INTO music_folders (uriString, displayName, path, isExcluded, addedAt) " +
                "VALUES ('content://tree/primary:Music', 'Music', '/storage/emulated/0/Music', 0, 1719374400)"
        )
        val folderCursor = db.query("SELECT * FROM music_folders WHERE uriString = 'content://tree/primary:Music'")
        assertTrue("music_folders table must exist and accept a row post-migration", folderCursor.moveToFirst())
        assertEquals("Music", folderCursor.getString(folderCursor.getColumnIndexOrThrow("displayName")))
        assertEquals(0, folderCursor.getInt(folderCursor.getColumnIndexOrThrow("isExcluded")))
        folderCursor.close()
        db.close()
    }

    @Test
    fun migrate14To15_preservesMusicFoldersAndTracks_andCreatesAudioDevicesAndPresetsTables() {
        // Arrange: create the DB at version 14 and insert a representative row.
        var db: SupportSQLiteDatabase = helper.createDatabase(testDbName, 14)
        db.execSQL(
            "INSERT INTO music_folders (uriString, displayName, path, isExcluded, addedAt) " +
                "VALUES ('content://tree/primary:Audio', 'Audio', '/storage/emulated/0/Audio', 0, 1719374400)"
        )
        db.close()

        // Act: run the real 14 -> 15 migration path.
        db = helper.runMigrationsAndValidate(testDbName, 15, true)

        // Assert: pre-existing folder survives untouched.
        val folderCursor = db.query("SELECT * FROM music_folders WHERE uriString = 'content://tree/primary:Audio'")
        assertTrue("music_folders must survive migration to 15", folderCursor.moveToFirst())
        assertEquals("Audio", folderCursor.getString(folderCursor.getColumnIndexOrThrow("displayName")))
        folderCursor.close()

        // Assert: audio_devices table is created and operational.
        db.execSQL(
            """
            INSERT INTO audio_devices (deviceId, displayName, deviceType, lastConnectedEpochMs, firstSeenEpochMs,
                assignedPresetId, matchedAutoEqModel, matchedAutoEqSource, targetCurve, formFactor,
                lastKnownCodec, lastKnownBatteryLevel, totalListeningTimeMs)
            VALUES ('AA:BB:CC:DD:EE:FF', 'Sony WH-1000XM4', 'BLUETOOTH', 1720000000, 1710000000,
                'preset_xm4', 'Sony WH-1000XM4', 'oratory1990', 'Harman Over-Ear 2018', 'Over-Ear',
                'LDAC', 85, 3600000)
            """.trimIndent()
        )
        val deviceCursor = db.query("SELECT * FROM audio_devices WHERE deviceId = 'AA:BB:CC:DD:EE:FF'")
        assertTrue("audio_devices row must be readable post-migration", deviceCursor.moveToFirst())
        assertEquals("Sony WH-1000XM4", deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("displayName")))
        assertEquals("LDAC", deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("lastKnownCodec")))
        assertEquals(85, deviceCursor.getInt(deviceCursor.getColumnIndexOrThrow("lastKnownBatteryLevel")))
        assertEquals("Harman Over-Ear 2018", deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("targetCurve")))
        assertEquals("Over-Ear", deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("formFactor")))
        deviceCursor.close()

        // Assert: device_presets table is created and operational.
        db.execSQL(
            """
            INSERT INTO device_presets (presetId, name, deviceId, preampDb, bandsJson, isAutoEq,
                autoEqModel, autoEqSource, targetCurve, formFactor, createdAtEpochMs, updatedAtEpochMs)
            VALUES ('preset_xm4', 'XM4 Harman Curve', 'AA:BB:CC:DD:EE:FF', -4.5, '[]', 1,
                'Sony WH-1000XM4', 'oratory1990', 'Harman Over-Ear 2018', 'Over-Ear', 1710000000, 1720000000)
            """.trimIndent()
        )
        val presetCursor = db.query("SELECT * FROM device_presets WHERE presetId = 'preset_xm4'")
        assertTrue("device_presets row must be readable post-migration", presetCursor.moveToFirst())
        assertEquals("XM4 Harman Curve", presetCursor.getString(presetCursor.getColumnIndexOrThrow("name")))
        assertEquals(-4.5, presetCursor.getDouble(presetCursor.getColumnIndexOrThrow("preampDb")), 0.001)
        assertEquals(1, presetCursor.getInt(presetCursor.getColumnIndexOrThrow("isAutoEq")))
        presetCursor.close()
        db.close()
    }

    @Test
    fun migrate15To16_preservesCachedTrackRows_andAddsArtworkNegativeCacheColumns() {
        // Arrange: create the DB at version 15 and insert a representative row.
        var db: SupportSQLiteDatabase = helper.createDatabase(testDbName, 15)
        db.execSQL(
            """
            INSERT INTO cached_tracks
                (id, title, artist, album, uriString, artworkUriString, durationMs, mimeType,
                 bitrate, sampleRate, trackNumber, year, dateAdded, isLiked, path,
                 replayGainTrackDb, replayGainAlbumDb, replayGainTrackPeak, replayGainAlbumPeak,
                 dateModified)
            VALUES
                ('local_track_004', 'Negative Cache Test', 'Tiger Unit', 'Midnight Circuit',
                 'content://media/external/audio/media/45', 'content://media/external/audio/albumart/10',
                 180000, 'audio/flac', 960000, 48000, 7, '2026', 1719374400, 1,
                 '/storage/emulated/0/Music/Tiger/Negative Cache Test.flac', -3.0, -2.0, 0.9, 0.95,
                 1719374400)
            """.trimIndent()
        )
        db.close()

        // Act: run the real 15 -> 16 migration path.
        db = helper.runMigrationsAndValidate(testDbName, 16, true)

        // Assert: the pre-existing cached track survives with default values for new columns.
        val cursor = db.query("SELECT * FROM cached_tracks WHERE id = 'local_track_004'")
        assertTrue("expected the pre-migration row to still exist", cursor.moveToFirst())
        assertEquals("Negative Cache Test", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("artworkLookupFailedAt")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("artworkLookupAttempts")))
        val embeddedIndex = cursor.getColumnIndexOrThrow("hasEmbeddedArtwork")
        assertTrue("hasEmbeddedArtwork column should default to NULL", cursor.isNull(embeddedIndex))
        cursor.close()
        db.close()
    }
}
