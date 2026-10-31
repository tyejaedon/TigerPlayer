package com.tigerplayer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.M3uPathMode
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class M3uRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val audioRepository = mockk<AudioRepository>(relaxed = true)

    private lateinit var repository: M3uRepository

    private val track1Uri = mockk<Uri>(relaxed = true)
    private val track2Uri = mockk<Uri>(relaxed = true)

    private val track1 = AudioTrack(
        id = "t1",
        title = "Track 1",
        artist = "Artist A",
        album = "Album A",
        uri = track1Uri,
        artworkUri = track1Uri,
        durationMs = 180000L,
        mimeType = "audio/mpeg",
        isLocal = true,
        path = "/storage/emulated/0/Music/Track1.mp3"
    )

    private val track2 = AudioTrack(
        id = "t2",
        title = "Track 2",
        artist = "Artist B",
        album = "Album B",
        uri = track2Uri,
        artworkUri = track2Uri,
        durationMs = 240000L,
        mimeType = "audio/flac",
        isLocal = true,
        path = "/storage/emulated/0/Music/Track2.flac"
    )

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        repository = M3uRepository(context, playlistDao, audioRepository)
    }

    @Test
    fun `importPlaylist successfully imports matching tracks and reports unmatched count`() = runBlocking {
        val fileUri = mockk<Uri>(relaxed = true)
        val m3uContent = """
            #EXTM3U
            #EXTINF:180,Artist A - Track 1
            /storage/emulated/0/Music/Track1.mp3
            #EXTINF:999,Unknown Artist - Ghost
            /storage/emulated/0/Music/Ghost.mp3
        """.trimIndent()

        every { contentResolver.openInputStream(fileUri) } answers {
            ByteArrayInputStream(m3uContent.toByteArray())
        }

        // Return cursor with display name
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getString(0) } returns "MyClassicRock.m3u8"
        every {
            contentResolver.query(fileUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        } returns cursor

        every { audioRepository.getCachedLocalTracks() } returns flowOf(listOf(track1, track2))
        coEvery { playlistDao.insertPlaylistSafely(any()) } returns 42L

        val result = repository.importPlaylist(fileUri)

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()

        assertEquals(42L, summary.playlistId)
        assertEquals("MyClassicRock", summary.playlistName)
        assertEquals(1, summary.importedCount)
        assertEquals(1, summary.unmatchedCount)
        assertEquals(listOf("/storage/emulated/0/Music/Ghost.mp3"), summary.unmatchedEntries)

        // Verify insertion into playlistDao
        coVerify(exactly = 1) {
            playlistDao.insertTracksBatch(match { list ->
                list.size == 1 && list[0].playlistId == 42L && list[0].trackId == "t1" && list[0].position == 0
            })
        }
    }

    @Test
    fun `exportPlaylist writes tracks in correct order to destination stream`() = runBlocking {
        val destUri = mockk<Uri>(relaxed = true)
        val outputStream = ByteArrayOutputStream()

        val playlistEntity = PlaylistEntity(
            playlistId = 7L,
            name = "Party Mix",
            artworkUri = null,
            createdAt = 1000L
        )

        coEvery { playlistDao.getPlaylistById(7L) } returns playlistEntity
        every { audioRepository.getTracksForPlaylist(7L) } returns flowOf(listOf(track1, track2))
        every { contentResolver.openOutputStream(destUri, "wt") } returns outputStream

        val result = repository.exportPlaylist(
            playlistId = 7L,
            destinationUri = destUri,
            pathMode = M3uPathMode.ABSOLUTE
        )

        assertTrue(result.isSuccess)
        val exportSummary = result.getOrThrow()
        assertEquals(7L, exportSummary.playlistId)
        assertEquals("Party Mix", exportSummary.playlistName)
        assertEquals(2, exportSummary.trackCount)

        val exportedContent = outputStream.toString("UTF-8")
        assertTrue(exportedContent.startsWith("#EXTM3U"))
        assertTrue(exportedContent.contains("/storage/emulated/0/Music/Track1.mp3"))
        assertTrue(exportedContent.contains("/storage/emulated/0/Music/Track2.flac"))
    }
}
