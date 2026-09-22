package com.tigerplayer.data.backup

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.tigerplayer.data.local.entity.PlaybackHistoryEntity
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

        backupManager = BackupManager(context, playlistDao, tigerDao, settingsDataStore)
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
}

