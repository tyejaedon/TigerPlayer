package com.tigerplayer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves that Room schema migrations preserve existing data on the JVM test pipeline (issue #74).
 * Runs on every PR without requiring an Android emulator or physical device.
 *
 * Uses the [File] + [BundledSQLiteDriver] + [KClass] constructor of [MigrationTestHelper].
 * Robolectric doesn't shadow android.database.sqlite in a way that's compatible with the legacy
 * `Class`-based constructor's default path under Room 2.8.x: that path configures its internal
 * `SupportSQLiteDriver` with the bare database name, but then opens the connection with the
 * absolute, Robolectric-sandboxed path, which throws
 * "This driver is configured to open a database named '<name>' but '<absolute-path>' was
 * requested" (verified against the room-testing 2.8.5 sources; still present upstream). Bundled
 * SQLite bypasses SupportSQLiteOpenHelper entirely and does not hit this bug. As a side effect,
 * this API shape returns [SQLiteConnection]/[SQLiteStatement] instead of
 * [androidx.sqlite.db.SupportSQLiteDatabase]/[android.database.Cursor], and
 * `runMigrationsAndValidate` no longer exposes a `validateDroppedTables` toggle (it is hardcoded
 * to `false` in this code path) - a minor loss of strictness versus the on-device instrumented
 * counterpart (`TigerDatabaseMigrationTest` under androidTest), which still uses the Class-based
 * constructor since real devices are unaffected by this bug.
 *
 * Covers:
 * - 11 -> 12: ReplayGain columns on CachedTrackEntity (issue #56)
 * - 12 -> 13: dateModified fingerprint column on CachedTrackEntity (issue #49)
 * - 13 -> 14: new music_folders table for custom directories (issue #50)
 */
@RunWith(RobolectricTestRunner::class)
class TigerDatabaseMigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(testDbName),
        driver = BundledSQLiteDriver(),
        databaseClass = TigerDatabase::class,
    )

    /** Mirrors Cursor's getColumnIndexOrThrow for the new [SQLiteStatement] API. */
    private fun SQLiteStatement.columnIndex(name: String): Int {
        for (i in 0 until getColumnCount()) {
            if (getColumnName(i) == name) return i
        }
        throw IllegalArgumentException("column '$name' does not exist in result set")
    }

    @Test
    fun migrate11To12_preservesCachedTrackRows_andBackfillsReplayGainColumnsAsNull() {
        // Arrange: create the DB at version 11 (pre-ReplayGain) and insert a representative row.
        var connection: SQLiteConnection = helper.createDatabase(11)
        connection.execSQL(
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
        connection.close()

        // Act: run the real 11 -> 12 migration path (validates against the committed schema JSON).
        connection = helper.runMigrationsAndValidate(12)

        // Assert: the pre-existing row survives, untouched, with the new columns defaulting to NULL.
        connection.prepare("SELECT * FROM cached_tracks WHERE id = 'local_track_001'").use { stmt ->
            assertTrue("expected the pre-migration row to still exist", stmt.step())
            assertEquals("Neon Drift", stmt.getText(stmt.columnIndex("title")))
            assertEquals("Tiger Unit", stmt.getText(stmt.columnIndex("artist")))
            assertEquals(243000L, stmt.getLong(stmt.columnIndex("durationMs")))
            assertEquals(1, stmt.getInt(stmt.columnIndex("isLiked")))
            assertEquals(
                "/storage/emulated/0/Music/Tiger/Neon Drift.flac",
                stmt.getText(stmt.columnIndex("path"))
            )

            assertTrue(
                "untagged rows must backfill to NULL, not 0.0",
                stmt.isNull(stmt.columnIndex("replayGainTrackDb"))
            )
            assertTrue(
                "untagged rows must backfill to NULL, not 0.0",
                stmt.isNull(stmt.columnIndex("replayGainAlbumDb"))
            )
            assertTrue(
                "untagged rows must backfill to NULL, not 0.0",
                stmt.isNull(stmt.columnIndex("replayGainTrackPeak"))
            )
            assertTrue(
                "untagged rows must backfill to NULL, not 0.0",
                stmt.isNull(stmt.columnIndex("replayGainAlbumPeak"))
            )
        }
        connection.close()
    }

    @Test
    fun migrate11To12_preservesPlaybackHistoryAndPlaylistOrdering() {
        // Arrange: seed irreplaceable history + user-authored playlist ordering at version 11.
        var connection: SQLiteConnection = helper.createDatabase(11)
        connection.execSQL(
            """
            INSERT INTO playback_history
                (trackId, title, artist, album, imageUrl, durationListenedMs, timestamp, source)
            VALUES
                ('local_track_001', 'Neon Drift', 'Tiger Unit', 'Midnight Circuit', NULL,
                 210000, 1719374500000, 'LOCAL')
            """.trimIndent()
        )
        connection.execSQL(
            "INSERT INTO playlists (playlistId, name, artworkUri, createdAt, position) " +
                "VALUES (1, 'Late Night Drive', NULL, 1719374000, 0)"
        )
        connection.execSQL(
            "INSERT INTO playlist_track_cross_ref (playlistId, trackId, dateAdded, position) " +
                "VALUES (1, 'local_track_001', 1719374100, 0)"
        )
        connection.close()

        // Act
        connection = helper.runMigrationsAndValidate(12)

        // Assert: history and ordering are untouched by the unrelated schema change.
        connection.prepare(
            "SELECT * FROM playback_history WHERE trackId = 'local_track_001'"
        ).use { stmt ->
            assertTrue("playback history must survive the migration", stmt.step())
            assertEquals(210000L, stmt.getLong(stmt.columnIndex("durationListenedMs")))
        }

        connection.prepare(
            "SELECT position FROM playlist_track_cross_ref WHERE playlistId = 1 AND trackId = 'local_track_001'"
        ).use { stmt ->
            assertTrue("playlist ordering must survive the migration", stmt.step())
            assertEquals(0, stmt.getInt(0))
        }
        connection.close()
    }

    @Test
    fun migrate12To13_preservesCachedTrackRows_andBackfillsDateModifiedAsZero() {
        // Arrange: create the DB at version 12 (pre-dateModified) and insert a representative row.
        var connection: SQLiteConnection = helper.createDatabase(12)
        connection.execSQL(
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
        connection.close()

        // Act: run the real 12 -> 13 migration path (validates against the committed schema JSON).
        connection = helper.runMigrationsAndValidate(13)

        // Assert: the pre-existing row survives, untouched, with dateModified defaulting to 0.
        connection.prepare("SELECT * FROM cached_tracks WHERE id = 'local_track_002'").use { stmt ->
            assertTrue("expected the pre-migration row to still exist", stmt.step())
            assertEquals("Chrome Static", stmt.getText(stmt.columnIndex("title")))
            assertEquals(198000L, stmt.getLong(stmt.columnIndex("durationMs")))
            assertEquals(-3.2, stmt.getDouble(stmt.columnIndex("replayGainTrackDb")), 0.0001)
            assertEquals(0L, stmt.getLong(stmt.columnIndex("dateModified")))
        }
        connection.close()
    }

    @Test
    fun migrate13To14_preservesCachedTrackRows_andCreatesMusicFoldersTable() {
        // Arrange: create the DB at version 13 (pre-music_folders) and insert a representative row.
        var connection: SQLiteConnection = helper.createDatabase(13)
        connection.execSQL(
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
        connection.close()

        // Act: run the real 13 -> 14 migration path (validates against the committed schema JSON).
        connection = helper.runMigrationsAndValidate(14)

        // Assert: the pre-existing cached track survives untouched.
        connection.prepare("SELECT * FROM cached_tracks WHERE id = 'local_track_003'").use { stmt ->
            assertTrue("expected the pre-migration row to still exist", stmt.step())
            assertEquals("Vault Signal", stmt.getText(stmt.columnIndex("title")))
        }

        // Assert: the new music_folders table exists and is usable.
        connection.execSQL(
            "INSERT INTO music_folders (uriString, displayName, path, isExcluded, addedAt) " +
                "VALUES ('content://tree/primary:Music', 'Music', '/storage/emulated/0/Music', 0, 1719374400)"
        )
        connection.prepare(
            "SELECT * FROM music_folders WHERE uriString = 'content://tree/primary:Music'"
        ).use { stmt ->
            assertTrue("music_folders table must exist and accept a row post-migration", stmt.step())
            assertEquals("Music", stmt.getText(stmt.columnIndex("displayName")))
            assertEquals(0, stmt.getInt(stmt.columnIndex("isExcluded")))
        }
        connection.close()
    }
}
