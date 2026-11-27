package com.tigerplayer.data.backup

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.dao.AudioDeviceDao
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.AudioDeviceEntity
import com.tigerplayer.data.local.entity.DevicePresetEntity
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.tigerplayer.data.local.entity.SmartPlaylistEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the export/import contract of [BackupManager] end to end against fakes, since it's the
 * only place playlists + history + settings are combined into a single portable file.
 */
class BackupManagerTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val playlistDao = mockk<PlaylistDao>()
    private val tigerDao = mockk<TigerDao>()
    private val settingsDataStore = mockk<SettingsDataStore>(relaxUnitFun = true)
    private val audioDeviceDao = mockk<AudioDeviceDao>(relaxed = true)

    private lateinit var backupManager: BackupManager
    private val destination = mockk<Uri>()

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        val packageManager = mockk<PackageManager>()
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.tigerplayer"
        every { packageManager.getPackageInfo("com.tigerplayer", 0) } returns
            PackageInfo().apply { versionName = "2.0.0" }

        every { settingsDataStore.settingsFlow } returns flowOf(TigerSettingsState())
        coEvery { playlistDao.ensureSingletonLikedPlaylist(any(), any()) } returns emptyList()
        coEvery { tigerDao.getCachedTrackIdsSync() } returns emptyList()
        coEvery { audioDeviceDao.getAllDevicesSync() } returns emptyList()
        coEvery { audioDeviceDao.getAllPresetsSync() } returns emptyList()
        coEvery { playlistDao.getAllSmartPlaylistsSync() } returns emptyList()
        coEvery { playlistDao.clearAllSmartPlaylists() } returns 0
        coEvery { playlistDao.insertSmartPlaylist(any()) } returns 1L

        backupManager = BackupManager(context, playlistDao, tigerDao, settingsDataStore, audioDeviceDao)
    }

    @Test
    fun `export writes a JSON manifest that round-trips unicode playlist and track names`() = runTest {
        val playlist = PlaylistEntity(playlistId = 1L, name = "å¤œã®ãƒ‰ãƒ©ã‚¤ãƒ– \u001F mix", artworkUri = null, createdAt = 100L, position = 0)
        val crossRef = PlaylistTrackCrossRef(playlistId = 1L, trackId = "track-\u001E-1", dateAdded = 200L, position = 0)
        val history = PlaybackHistoryEntity(
            id = 5L,
            trackId = "track-1",
            title = "Título",
            artist = "Artist",
            album = "Album",
            imageUrl = null,
            durationListenedMs = 250_000L,
            timestamp = 300L,
            source = com.tigerplayer.data.local.MediaSource.LOCAL
        )

        coEvery { playlistDao.getAllPlaylistsSync() } returns listOf(playlist)
        coEvery { playlistDao.getCrossRefsForPlaylistSync(1L) } returns listOf(crossRef)
        coEvery { tigerDao.getAllHistorySync() } returns listOf(history)

        val output = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(destination, "wt") } returns output

        val result = backupManager.exportTo(destination)

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1, summary.playlistCount)
        assertEquals(1, summary.trackRefCount)
        assertEquals(1, summary.historyCount)

        // Re-parse what was actually written, proving it's valid, round-trippable JSON.
        val writtenJson = output.toString(Charsets.UTF_8.name())
        val reparsed = com.google.gson.Gson().fromJson(writtenJson, BackupManifest::class.java)
        assertEquals("å¤œã®ãƒ‰ãƒ©ã‚¤ãƒ– \u001F mix", reparsed.playlists.single().name)
        assertEquals("track-\u001E-1", reparsed.playlists.single().tracks.single().trackId)
        assertEquals("Título", reparsed.history.single().title)
    }

    @Test
    fun `export fails cleanly when the destination cannot be opened`() = runTest {
        coEvery { playlistDao.getAllPlaylistsSync() } returns emptyList()
        coEvery { tigerDao.getAllHistorySync() } returns emptyList()
        every { contentResolver.openOutputStream(destination, "wt") } returns null

        val result = backupManager.exportTo(destination)

        assertTrue(result.isFailure)
    }

    @Test
    fun `import with merge strategy appends without clearing existing playlists or history`() = runTest {
        val json = sampleBackupJson(schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION)
        every { contentResolver.openInputStream(destination) } returns ByteArrayInputStream(json.toByteArray())
        coEvery { playlistDao.insertPlaylist(any()) } returns 42L
        coEvery { playlistDao.insertTracksBatch(any()) } returns listOf(1L)
        coEvery { tigerDao.insertHistoryBatch(any()) } returns listOf(1L)

        val result = backupManager.importFrom(destination, RestoreStrategy.MERGE)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { playlistDao.replaceAllPlaylists() }
        coVerify(exactly = 0) { tigerDao.clearHistory() }
        coVerify(exactly = 1) { playlistDao.insertPlaylist(any()) }
        coVerify(exactly = 1) { tigerDao.insertHistoryBatch(any()) }
    }

    @Test
    fun `import with replace strategy clears existing playlists and history first`() = runTest {
        val json = sampleBackupJson(schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION)
        every { contentResolver.openInputStream(destination) } returns ByteArrayInputStream(json.toByteArray())
        coEvery { playlistDao.replaceAllPlaylists() } returns Unit
        coEvery { playlistDao.insertPlaylist(any()) } returns 1L
        coEvery { playlistDao.insertTracksBatch(any()) } returns listOf(1L)
        coEvery { tigerDao.clearHistory() } returns 0
        coEvery { tigerDao.insertHistoryBatch(any()) } returns listOf(1L)

        val result = backupManager.importFrom(destination, RestoreStrategy.REPLACE)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { playlistDao.replaceAllPlaylists() }
        coVerify(exactly = 1) { tigerDao.clearHistory() }
    }

    @Test
    fun `import rejects a manifest from a newer schema version instead of silently corrupting data`() = runTest {
        val json = sampleBackupJson(schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION + 1)
        every { contentResolver.openInputStream(destination) } returns ByteArrayInputStream(json.toByteArray())

        val result = backupManager.importFrom(destination, RestoreStrategy.MERGE)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { playlistDao.insertPlaylist(any()) }
    }

    @Test
    fun `import rejects malformed JSON rather than throwing`() = runTest {
        every { contentResolver.openInputStream(destination) } returns ByteArrayInputStream("{ not json".toByteArray())

        val result = backupManager.importFrom(destination, RestoreStrategy.MERGE)

        assertFalse(result.isSuccess)
    }

    @Test
    fun `backup models declare explicit serialized names for every persisted JSON field`() {
        assertSerializedNames(
            BackupManifest::class.java,
            "schemaVersion",
            "appVersionName",
            "createdAtEpochMs",
            "settings",
            "playlists",
            "history"
        )
        assertSerializedNames(
            SettingsBackup::class.java,
            "themeMode",
            "pureAmoledBlack",
            "disablePip",
            "accentStyle",
            "defaultPlayerView",
            "crossfadeDurationSec",
            "gaplessPlayback",
            "audioReactiveHaptics",
            "audioReactiveHapticsProfile",
            "skipShortAudio",
            "routeToSystemDecoderDsp",
            "resumeOnBluetoothConnect",
            "resumeOnWiredHeadsetConnect",
            "prismEnabled",
            "prismVocals",
            "prismBeats",
            "prismInstruments",
            "prismSpectralAnalysis"
        )
        assertSerializedNames(
            PlaylistBackup::class.java,
            "name",
            "artworkUri",
            "createdAt",
            "position",
            "tracks"
        )
        assertSerializedNames(
            PlaylistTrackBackup::class.java,
            "trackId",
            "dateAdded",
            "position"
        )
        assertSerializedNames(
            HistoryBackup::class.java,
            "trackId",
            "title",
            "artist",
            "album",
            "imageUrl",
            "durationListenedMs",
            "timestamp",
            "source"
        )
        assertSerializedNames(
            SmartPlaylistBackup::class.java,
            "name",
            "rulesJson",
            "sortBy",
            "sortDescending",
            "limitCount",
            "createdAt",
            "updatedAt"
        )
    }

    @Test
    fun `export and restore smart playlists round-trips cleanly under merge and replace`() = runTest {
        val smartEntity = SmartPlaylistEntity(
            id = 7L,
            name = "Liked & Forgotten",
            rulesJson = """{"type":"AND","children":[{"type":"LIKED","isLiked":true}]}""",
            sortBy = "PLAY_COUNT",
            sortDescending = true,
            limitCount = 50,
            createdAt = 1720000000L,
            updatedAt = 1720000500L
        )
        coEvery { playlistDao.getAllSmartPlaylistsSync() } returns listOf(smartEntity)
        coEvery { playlistDao.getAllPlaylistsSync() } returns emptyList()
        coEvery { tigerDao.getAllHistorySync() } returns emptyList()

        val output = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(destination, "wt") } returns output

        val exportResult = backupManager.exportTo(destination)
        assertTrue(exportResult.isSuccess)
        assertEquals(1, exportResult.getOrThrow().smartPlaylistCount)

        val writtenJson = output.toString(Charsets.UTF_8.name())
        val reparsed = com.google.gson.Gson().fromJson(writtenJson, BackupManifest::class.java)
        assertEquals(1, reparsed.smartPlaylists.size)
        assertEquals("Liked & Forgotten", reparsed.smartPlaylists.single().name)
        assertEquals(50, reparsed.smartPlaylists.single().limitCount)

        val source = mockk<Uri>()
        every { contentResolver.openInputStream(source) } returns ByteArrayInputStream(writtenJson.toByteArray(Charsets.UTF_8))
        coEvery { playlistDao.replaceAllPlaylists() } returns Unit
        coEvery { tigerDao.clearHistory() } returns 0

        val mergeResult = backupManager.importFrom(source, RestoreStrategy.MERGE)
        assertTrue(mergeResult.isSuccess)
        assertEquals(1, mergeResult.getOrThrow().smartPlaylistCount)
        coVerify(exactly = 0) { playlistDao.clearAllSmartPlaylists() }
        coVerify(exactly = 1) {
            playlistDao.insertSmartPlaylist(
                match {
                    it.id == 0L &&
                        it.name == "Liked & Forgotten" &&
                        it.rulesJson == smartEntity.rulesJson &&
                        it.limitCount == 50
                }
            )
        }

        every { contentResolver.openInputStream(source) } returns ByteArrayInputStream(writtenJson.toByteArray(Charsets.UTF_8))
        val replaceResult = backupManager.importFrom(source, RestoreStrategy.REPLACE)
        assertTrue(replaceResult.isSuccess)
        coVerify(exactly = 1) { playlistDao.clearAllSmartPlaylists() }
    }

    @Test
    fun `export and restore with audio devices and presets round-trips cleanly`() = runTest {
        val device = AudioDeviceEntity(
            deviceId = "AA:BB:CC:11:22:33",
            displayName = "Sony WH-1000XM4",
            deviceType = "BLUETOOTH",
            lastConnectedEpochMs = 1720000000L,
            firstSeenEpochMs = 1710000000L,
            assignedPresetId = "preset_1",
            matchedAutoEqModel = "Sony WH-1000XM4",
            matchedAutoEqSource = "oratory1990",
            targetCurve = "Harman Over-Ear 2018",
            formFactor = "Over-Ear"
        )
        val preset = DevicePresetEntity(
            presetId = "preset_1",
            name = "Harman Curve",
            deviceId = "AA:BB:CC:11:22:33",
            preampDb = -4.5f,
            bandsJson = "[{\"filterType\":\"PK\",\"frequencyHz\":100.0,\"gainDb\":2.0,\"q\":1.41}]",
            isAutoEq = true,
            autoEqModel = "Sony WH-1000XM4",
            autoEqSource = "oratory1990",
            targetCurve = "Harman Over-Ear 2018",
            formFactor = "Over-Ear"
        )

        coEvery { audioDeviceDao.getAllDevicesSync() } returns listOf(device)
        coEvery { audioDeviceDao.getAllPresetsSync() } returns listOf(preset)
        coEvery { playlistDao.getAllPlaylistsSync() } returns emptyList()
        coEvery { tigerDao.getAllHistorySync() } returns emptyList()

        val output = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(destination, "wt") } returns output

        val result = backupManager.exportTo(destination)
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1, summary.audioDeviceCount)
        assertEquals(1, summary.devicePresetCount)

        // Validate JSON
        val writtenJson = output.toString(Charsets.UTF_8.name())
        val reparsed = com.google.gson.Gson().fromJson(writtenJson, BackupManifest::class.java)
        assertEquals(1, reparsed.audioDevices.size)
        assertEquals("Sony WH-1000XM4", reparsed.audioDevices.first().displayName)
        assertEquals("Harman Over-Ear 2018", reparsed.audioDevices.first().targetCurve)
        assertEquals("Over-Ear", reparsed.audioDevices.first().formFactor)
        assertEquals(1, reparsed.devicePresets.size)
        assertEquals("Harman Curve", reparsed.devicePresets.first().name)

        // Now test restore
        val source = mockk<Uri>()
        every { contentResolver.openInputStream(source) } returns ByteArrayInputStream(writtenJson.toByteArray(Charsets.UTF_8))
        coEvery { playlistDao.insertPlaylist(any()) } returns 1L

        val importResult = backupManager.importFrom(source)
        assertTrue(importResult.isSuccess)
        val importSummary = importResult.getOrThrow()
        assertEquals(1, importSummary.audioDeviceCount)
        assertEquals(1, importSummary.devicePresetCount)

        coVerify { audioDeviceDao.upsertDevice(match { it.deviceId == "AA:BB:CC:11:22:33" && it.targetCurve == "Harman Over-Ear 2018" }) }
        coVerify { audioDeviceDao.upsertPreset(match { it.presetId == "preset_1" && it.preampDb == -4.5f }) }
    }

    @Test
    fun `import reports zero unresolved tracks when all restored track ids exist locally`() = runTest {
        val json = sampleBackupJson(schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION)
        every { contentResolver.openInputStream(destination) } returns ByteArrayInputStream(json.toByteArray())
        coEvery { playlistDao.insertPlaylist(any()) } returns 1L
        coEvery { playlistDao.insertTracksBatch(any()) } returns listOf(1L)
        coEvery { tigerDao.insertHistoryBatch(any()) } returns listOf(1L)
        coEvery { tigerDao.getCachedTrackIdsSync() } returns listOf("t1")

        val result = backupManager.importFrom(destination, RestoreStrategy.MERGE)

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(0, summary.unresolvedTrackCount)
        assertTrue(summary.unresolvedByPlaylist.isEmpty())
    }

    @Test
    fun `import succeeds and reports unresolved track counts when some track ids are missing locally`() = runTest {
        val manifest = BackupManifest(
            schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
            appVersionName = "2.0.0",
            createdAtEpochMs = 123L,
            settings = com.google.gson.Gson().fromJson(
                sampleBackupJson(BackupManifest.CURRENT_SCHEMA_VERSION),
                BackupManifest::class.java
            ).settings,
            playlists = listOf(
                PlaylistBackup(
                    name = "Road Trip",
                    artworkUri = null,
                    createdAt = 1L,
                    position = 0,
                    tracks = listOf(
                        PlaylistTrackBackup(trackId = "t1", dateAdded = 1L, position = 0),
                        PlaylistTrackBackup(trackId = "missing-1", dateAdded = 1L, position = 1),
                        PlaylistTrackBackup(trackId = "missing-2", dateAdded = 1L, position = 2)
                    )
                ),
                PlaylistBackup(
                    name = "Chill",
                    artworkUri = null,
                    createdAt = 2L,
                    position = 1,
                    tracks = listOf(
                        PlaylistTrackBackup(trackId = "t1", dateAdded = 2L, position = 0)
                    )
                )
            ),
            history = listOf(
                HistoryBackup("t1", "Song", "Artist", "Album", null, 1000L, 1L, "LOCAL"),
                HistoryBackup("missing-1", "Gone", "Artist", "Album", null, 1000L, 2L, "LOCAL"),
                HistoryBackup("missing-3", "Also Gone", "Artist", "Album", null, 1000L, 3L, "LOCAL")
            )
        )
        val json = com.google.gson.Gson().toJson(manifest)
        every { contentResolver.openInputStream(destination) } returns ByteArrayInputStream(json.toByteArray())
        coEvery { playlistDao.insertPlaylist(any()) } returnsMany listOf(1L, 2L)
        coEvery { playlistDao.insertTracksBatch(any()) } returns listOf(1L)
        coEvery { tigerDao.insertHistoryBatch(any()) } returns listOf(1L, 2L, 3L)
        coEvery { tigerDao.getCachedTrackIdsSync() } returns listOf("t1")

        val result = backupManager.importFrom(destination, RestoreStrategy.MERGE)

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(3, summary.unresolvedTrackCount)
        assertEquals(mapOf("Road Trip" to 2), summary.unresolvedByPlaylist)
    }

    @Test
    fun `import handles legacy backup JSON missing smartPlaylists, audioDevices, devicePresets, and newer settings`() = runTest {
        val legacyJson = """
            {
              "schemaVersion": 1,
              "appVersionName": "2.1.6",
              "createdAtEpochMs": 1700000000,
              "settings": {
                "themeMode": "DARK",
                "pureAmoledBlack": true
              },
              "playlists": [
                {
                  "name": "Legacy Mix",
                  "artworkUri": null,
                  "createdAt": 100,
                  "position": 0,
                  "tracks": [
                    { "trackId": "t1", "dateAdded": 100, "position": 0 }
                  ]
                }
              ],
              "history": [
                {
                  "trackId": "t1",
                  "title": "Song",
                  "artist": "Artist",
                  "album": "Album",
                  "durationListenedMs": 180000,
                  "timestamp": 200
                }
              ]
            }
        """.trimIndent()
        every { contentResolver.openInputStream(destination) } returns ByteArrayInputStream(legacyJson.toByteArray(Charsets.UTF_8))
        coEvery { playlistDao.insertPlaylist(any()) } returns 10L
        coEvery { playlistDao.insertTracksBatch(any()) } returns listOf(1L)
        coEvery { tigerDao.insertHistoryBatch(any()) } returns listOf(1L)

        val result = backupManager.importFrom(destination, RestoreStrategy.MERGE)

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1, summary.playlistCount)
        assertEquals(1, summary.trackRefCount)
        assertEquals(1, summary.historyCount)
        assertEquals(0, summary.smartPlaylistCount)
        assertEquals(0, summary.audioDeviceCount)
        assertEquals(0, summary.devicePresetCount)
        coVerify(exactly = 1) { settingsDataStore.setGaplessPlayback(true) }
        coVerify(exactly = 1) { settingsDataStore.setPureAmoledBlack(true) }
    }

    @Test
    fun `import handles UTF-8 BOM and null nested lists safely`() = runTest {
        val bomJson = "\uFEFF" + """
            {
              "schemaVersion": 1,
              "appVersionName": null,
              "settings": null,
              "playlists": [ { "name": null, "tracks": null } ],
              "history": null,
              "audioDevices": null,
              "devicePresets": null,
              "smartPlaylists": null
            }
        """.trimIndent()
        every { contentResolver.openInputStream(destination) } returns ByteArrayInputStream(bomJson.toByteArray(Charsets.UTF_8))
        coEvery { playlistDao.insertPlaylist(any()) } returns 11L

        val result = backupManager.importFrom(destination, RestoreStrategy.MERGE)

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1, summary.playlistCount)
        assertEquals(0, summary.trackRefCount)
        assertEquals(0, summary.historyCount)
    }

    @Test
    fun `import rejects non-object JSON such as an array`() = runTest {
        every { contentResolver.openInputStream(destination) } returns ByteArrayInputStream("[1, 2, 3]".toByteArray(Charsets.UTF_8))

        val result = backupManager.importFrom(destination, RestoreStrategy.MERGE)

        assertFalse(result.isSuccess)
    }

    @Test
    fun `export falls back to write mode when wt mode is unsupported`() = runTest {
        coEvery { playlistDao.getAllPlaylistsSync() } returns emptyList()
        coEvery { tigerDao.getAllHistorySync() } returns emptyList()
        val output = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(destination, "wt") } throws IllegalArgumentException("wt unsupported")
        every { contentResolver.openOutputStream(destination, "w") } returns output

        val result = backupManager.exportTo(destination)

        assertTrue(result.isSuccess)
        assertTrue(output.toString(Charsets.UTF_8.name()).contains("\"schemaVersion\""))
    }

    private fun sampleBackupJson(schemaVersion: Int): String {
        val manifest = BackupManifest(
            schemaVersion = schemaVersion,
            appVersionName = "2.0.0",
            createdAtEpochMs = 123L,
            settings = TigerSettingsState().let {
                SettingsBackup(
                    themeMode = it.themeMode.name,
                    pureAmoledBlack = it.pureAmoledBlack,
                    disablePip = it.disablePip,
                    accentStyle = it.accentStyle.name,
                    defaultPlayerView = it.defaultPlayerView.name,
                    crossfadeDurationSec = it.crossfadeDurationSec,
                    gaplessPlayback = it.gaplessPlayback,
                    audioReactiveHaptics = it.audioReactiveHaptics,
                    audioReactiveHapticsProfile = it.audioReactiveHapticsProfile.name,
                    skipShortAudio = it.skipShortAudio.name,
                    routeToSystemDecoderDsp = it.routeToSystemDecoderDsp,
                    resumeOnBluetoothConnect = it.resumeOnBluetoothConnect,
                    resumeOnWiredHeadsetConnect = it.resumeOnWiredHeadsetConnect,
                    prismEnabled = it.prismEnabled,
                    prismVocals = it.prismVocals,
                    prismBeats = it.prismBeats,
                    prismInstruments = it.prismInstruments,
                    prismSpectralAnalysis = it.prismSpectralAnalysis.name
                )
            },
            playlists = listOf(
                PlaylistBackup(
                    name = "Road Trip",
                    artworkUri = null,
                    createdAt = 1L,
                    position = 0,
                    tracks = listOf(PlaylistTrackBackup(trackId = "t1", dateAdded = 1L, position = 0))
                )
            ),
            history = listOf(
                HistoryBackup(
                    trackId = "t1",
                    title = "Song",
                    artist = "Artist",
                    album = "Album",
                    imageUrl = null,
                    durationListenedMs = 200_000L,
                    timestamp = 2L,
                    source = "LOCAL"
                )
            )
        )
        return com.google.gson.Gson().toJson(manifest)
    }

    private fun assertSerializedNames(clazz: Class<*>, vararg fieldNames: String) {
        fieldNames.forEach { fieldName ->
            val annotation = clazz.getDeclaredField(fieldName).getAnnotation(SerializedName::class.java)
            assertEquals(fieldName, annotation?.value)
        }
    }
}

