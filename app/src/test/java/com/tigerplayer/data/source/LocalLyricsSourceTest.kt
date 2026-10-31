package com.tigerplayer.data.source

import android.content.Context
import android.net.Uri
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.LrcLine
import com.tigerplayer.data.model.Lyrics
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalLyricsSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val localLyricsSource = LocalLyricsSource(context)

    @Test
    fun `getSidecarLyrics finds lrc matching audio file base name`() {
        val musicDir = tempFolder.newFolder("Music")
        val audioFile = File(musicDir, "Queen - Bohemian Rhapsody.mp3").apply { createNewFile() }
        val lrcFile = File(musicDir, "Queen - Bohemian Rhapsody.lrc").apply {
            writeText("[00:01.00]Mama, just killed a man\n[00:05.00]Put a gun against his head")
        }

        val track = AudioTrack(
            id = "test-track-1",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            uri = Uri.fromFile(audioFile),
            artworkUri = Uri.EMPTY,
            durationMs = 355_000L,
            mimeType = "audio/mp3",
            path = audioFile.absolutePath
        )

        val lyrics = localLyricsSource.getSidecarLyrics(track)

        assertTrue("Expected Synced lyrics, got: $lyrics", lyrics is Lyrics.Synced)
        val synced = lyrics as Lyrics.Synced
        assertEquals(2, synced.lines.size)
        assertEquals(LrcLine(1000L, "Mama, just killed a man"), synced.lines[0])
        assertEquals(LrcLine(5000L, "Put a gun against his head"), synced.lines[1])
    }

    @Test
    fun `getSidecarLyrics finds uppercase LRC matching track title`() {
        val musicDir = tempFolder.newFolder("AlbumDir")
        val audioFile = File(musicDir, "track_01.mp3").apply { createNewFile() }
        val lrcFile = File(musicDir, "Track Title.LRC").apply {
            writeText("Plain sidecar lyrics without tags")
        }

        val track = AudioTrack(
            id = "test-track-2",
            title = "Track Title",
            artist = "Artist",
            album = "Album",
            uri = Uri.fromFile(audioFile),
            artworkUri = Uri.EMPTY,
            durationMs = 180_000L,
            mimeType = "audio/mp3",
            path = audioFile.absolutePath
        )

        val lyrics = localLyricsSource.getSidecarLyrics(track)

        assertTrue("Expected Plain lyrics, got: $lyrics", lyrics is Lyrics.Plain)
        assertEquals("Plain sidecar lyrics without tags", (lyrics as Lyrics.Plain).text)
    }

    @Test
    fun `getSidecarLyrics returns None when no companion file exists`() {
        val musicDir = tempFolder.newFolder("SoloDir")
        val audioFile = File(musicDir, "song.mp3").apply { createNewFile() }

        val track = AudioTrack(
            id = "test-track-3",
            title = "Song",
            artist = "Artist",
            album = "Album",
            uri = Uri.fromFile(audioFile),
            artworkUri = Uri.EMPTY,
            durationMs = 180_000L,
            mimeType = "audio/mp3",
            path = audioFile.absolutePath
        )

        val lyrics = localLyricsSource.getSidecarLyrics(track)
        assertEquals(Lyrics.None, lyrics)
    }
}
