package com.tigerplayer.utils

import android.net.Uri
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.M3uEntry
import com.tigerplayer.data.model.M3uPathMode
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

class M3uParserTest {

    private fun createTestTrack(
        id: String,
        title: String,
        artist: String,
        path: String?,
        durationMs: Long = 180000L
    ): AudioTrack {
        val dummyUri = mockk<Uri>(relaxed = true)
        return AudioTrack(
            id = id,
            title = title,
            artist = artist,
            album = "Test Album",
            uri = dummyUri,
            artworkUri = dummyUri,
            durationMs = durationMs,
            mimeType = "audio/mpeg",
            isLocal = true,
            path = path
        )
    }

    @Test
    fun `parse basic extended M3U file with header and EXTINF directives`() {
        val content = """
            #EXTM3U
            #EXTINF:215,Pink Floyd - Time
            /storage/emulated/0/Music/Pink Floyd/Time.mp3
            #EXTINF:320,Led Zeppelin - Kashmir
            /storage/emulated/0/Music/Led Zeppelin/Kashmir.flac
        """.trimIndent()

        val playlist = M3uParser.parse(content, "Rock Classics")

        assertEquals("Rock Classics", playlist.name)
        assertEquals(2, playlist.entries.size)

        val entry1 = playlist.entries[0]
        assertEquals("/storage/emulated/0/Music/Pink Floyd/Time.mp3", entry1.pathOrUri)
        assertEquals(215L, entry1.durationSeconds)
        assertEquals("Pink Floyd - Time", entry1.title)

        val entry2 = playlist.entries[1]
        assertEquals("/storage/emulated/0/Music/Led Zeppelin/Kashmir.flac", entry2.pathOrUri)
        assertEquals(320L, entry2.durationSeconds)
        assertEquals("Led Zeppelin - Kashmir", entry2.title)
    }

    @Test
    fun `parse handles UTF-8 BOM, comments, and empty lines gracefully`() {
        val content = "\uFEFF#EXTM3U\n" +
            "# This is a comment\n\n" +
            "#EXTINF:180,Artist - Song\n" +
            "Songs/Song.mp3\n"

        val playlist = M3uParser.parse(content, "BOM Playlist")
        assertEquals(1, playlist.entries.size)
        assertEquals("Songs/Song.mp3", playlist.entries[0].pathOrUri)
        assertEquals(180L, playlist.entries[0].durationSeconds)
        assertEquals("Artist - Song", playlist.entries[0].title)
    }

    @Test
    fun `parse handles simple M3U without EXTINF directives`() {
        val content = """
            Music/Track1.mp3
            Music/Track2.flac
        """.trimIndent()

        val playlist = M3uParser.parse(content, "Simple")
        assertEquals(2, playlist.entries.size)
        assertEquals("Music/Track1.mp3", playlist.entries[0].pathOrUri)
        assertEquals(-1L, playlist.entries[0].durationSeconds)
        assertNull(playlist.entries[0].title)
        assertEquals("Music/Track2.flac", playlist.entries[1].pathOrUri)
    }

    @Test
    fun `write playlist in absolute path mode outputs valid M3U8 UTF-8`() {
        val track1 = createTestTrack("1", "Track One", "Artist A", "/storage/emulated/0/Music/Track1.mp3", 125000L)
        val track2 = createTestTrack("2", "Track Two", "Artist B", "/storage/emulated/0/Music/Track2.mp3", 240000L)

        val output = ByteArrayOutputStream()
        M3uParser.writePlaylist(
            outputStream = output,
            tracks = listOf(track1, track2),
            playlistBaseDir = "/storage/emulated/0/Playlists",
            pathMode = M3uPathMode.ABSOLUTE
        )

        val written = output.toString(StandardCharsets.UTF_8.name())
        assertTrue(written.startsWith("#EXTM3U\n") || written.startsWith("#EXTM3U\r\n"))
        assertTrue(written.contains("#EXTINF:125,Artist A - Track One"))
        assertTrue(written.contains("/storage/emulated/0/Music/Track1.mp3"))
        assertTrue(written.contains("#EXTINF:240,Artist B - Track Two"))
        assertTrue(written.contains("/storage/emulated/0/Music/Track2.mp3"))
    }

    @Test
    fun `write playlist in relative path mode correctly computes relative paths`() {
        val baseDir = "/storage/emulated/0/Music"
        val track = createTestTrack(
            id = "1",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            path = "/storage/emulated/0/Music/Queen/Bohemian Rhapsody.mp3",
            durationMs = 354000L
        )

        val relativePath = M3uParser.formatTrackPath(track, baseDir, M3uPathMode.RELATIVE)
        assertEquals("Queen/Bohemian Rhapsody.mp3", relativePath)
    }

    @Test
    fun `non-ASCII titles and paths survive round-trip losslessly`() {
        val unicodeTitle = "夜に駆ける (Racing into the Night) ★ 🎵"
        val unicodeArtist = "YOASOBI"
        val unicodePath = "/storage/emulated/0/Music/J-Pop/夜に駆ける.flac"

        val track = createTestTrack("1", unicodeTitle, unicodeArtist, unicodePath, 261000L)

        val output = ByteArrayOutputStream()
        M3uParser.writePlaylist(
            outputStream = output,
            tracks = listOf(track),
            playlistBaseDir = null,
            pathMode = M3uPathMode.ABSOLUTE
        )

        val exportedBytes = output.toByteArray()
        val parsedPlaylist = M3uParser.parseStream(
            inputStream = ByteArrayInputStream(exportedBytes),
            defaultName = "Unicode Test"
        )

        assertEquals(1, parsedPlaylist.entries.size)
        val entry = parsedPlaylist.entries[0]
        assertEquals(unicodePath, entry.pathOrUri)
        assertEquals("YOASOBI - $unicodeTitle", entry.title)
        assertEquals(261L, entry.durationSeconds)

        val resolved = M3uParser.resolveTrack(entry.pathOrUri, null, listOf(track))
        assertNotNull(resolved)
        assertEquals("1", resolved?.id)
        assertEquals(unicodeTitle, resolved?.title)
    }

    @Test
    fun `resolveTrack matches absolute paths with forward or backslashes`() {
        val track = createTestTrack("t1", "Song", "Artist", "/storage/emulated/0/Music/Song.mp3")
        val tracks = listOf(track)

        val match1 = M3uParser.resolveTrack("/storage/emulated/0/Music/Song.mp3", null, tracks)
        assertEquals(track, match1)

        val match2 = M3uParser.resolveTrack("\\storage\\emulated\\0\\Music\\Song.mp3", null, tracks)
        assertEquals(track, match2)
    }

    @Test
    fun `resolveTrack matches relative path using playlistBaseDir`() {
        val track = createTestTrack("t1", "Song", "Artist", "/storage/emulated/0/Music/Rock/Song.mp3")
        val tracks = listOf(track)

        val match = M3uParser.resolveTrack("Rock/Song.mp3", "/storage/emulated/0/Music", tracks)
        assertEquals(track, match)
    }

    @Test
    fun `resolveTrack matches suffix path when baseDir is unknown`() {
        val track = createTestTrack("t1", "Song", "Artist", "/storage/emulated/0/Music/Rock/Song.mp3")
        val tracks = listOf(track)

        val match = M3uParser.resolveTrack("Music/Rock/Song.mp3", null, tracks)
        assertEquals(track, match)
    }

    @Test
    fun `resolveTrack falls back to unique filename match`() {
        val track = createTestTrack("t1", "UniqueSong", "Artist", "/storage/emulated/0/Music/UniqueSong.mp3")
        val tracks = listOf(track)

        val match = M3uParser.resolveTrack("AnyFolder/UniqueSong.mp3", null, tracks)
        assertEquals(track, match)
    }

    @Test
    fun `resolveTrack returns null when no matching track found in library`() {
        val track = createTestTrack("t1", "Song", "Artist", "/storage/emulated/0/Music/Song.mp3")
        val tracks = listOf(track)

        val match = M3uParser.resolveTrack("DifferentSong.mp3", null, tracks)
        assertNull(match)
    }

    @Test
    fun `parse conforms to industry standard with PLAYLIST header, inline attributes, quotes and mixed line endings`() {
        val standardM3u = "#EXTM3U\r\n" +
            "#PLAYLIST:Classic Rock Archive\r\n" +
            "#EXTINF:245 tvg-id=\"rock1\" group-title=\"Rock\",Led Zeppelin - Stairway to Heaven\r\n" +
            "\"/storage/emulated/0/Music/Led Zeppelin/Stairway to Heaven.mp3\"\r" +
            "# Unknown comment line that should be safely ignored\r\n" +
            "#EXTINF:180,Queen - Bohemian Rhapsody\n" +
            "Music/Queen/Bohemian Rhapsody.mp3\n"

        val playlist = M3uParser.parse(standardM3u, "Fallback Name")
        assertEquals("Classic Rock Archive", playlist.name)
        assertEquals(2, playlist.entries.size)

        val entry1 = playlist.entries[0]
        assertEquals("/storage/emulated/0/Music/Led Zeppelin/Stairway to Heaven.mp3", entry1.pathOrUri)
        assertEquals(245L, entry1.durationSeconds)
        assertEquals("Led Zeppelin - Stairway to Heaven", entry1.title)

        val entry2 = playlist.entries[1]
        assertEquals("Music/Queen/Bohemian Rhapsody.mp3", entry2.pathOrUri)
        assertEquals(180L, entry2.durationSeconds)
        assertEquals("Queen - Bohemian Rhapsody", entry2.title)
    }

    @Test
    fun `parseStream falls back to ISO-8859-1 gracefully when bytes contain invalid UTF-8 sequences`() {
        // Construct bytes with an ISO-8859-1 accented character (e.g. 0xE9 for 'é' in René)
        val isoHeader = "#EXTM3U\n#EXTINF:120,Ren".toByteArray(StandardCharsets.ISO_8859_1)
        val accentedE = byteArrayOf(0xE9.toByte())
        val isoRest = " - Song\n/path/song.mp3\n".toByteArray(StandardCharsets.ISO_8859_1)
        val rawBytes = isoHeader + accentedE + isoRest

        val playlist = M3uParser.parseStream(ByteArrayInputStream(rawBytes), "ISO Fallback")
        assertEquals(1, playlist.entries.size)
        assertEquals("/path/song.mp3", playlist.entries[0].pathOrUri)
        assertEquals("René - Song", playlist.entries[0].title)
    }
}
