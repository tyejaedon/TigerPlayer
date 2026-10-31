package com.tigerplayer.utils

import com.tigerplayer.data.model.LrcLine
import com.tigerplayer.data.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class EmbeddedLyricsParserTest {

    @Test
    fun `parse returns None when stream is empty or non-audio`() {
        val emptyStream = ByteArrayInputStream(ByteArray(0))
        assertEquals(Lyrics.None, EmbeddedLyricsParser.parse(emptyStream))

        val randomStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        assertEquals(Lyrics.None, EmbeddedLyricsParser.parse(randomStream))
    }

    @Test
    fun `parse extracts USLT unsynchronized lyrics from ID3v2`() {
        val lyricsText = "Hello from embedded USLT\nSecond line"
        val id3Bytes = buildId3v23WithUslt(lyricsText)

        val result = EmbeddedLyricsParser.parse(ByteArrayInputStream(id3Bytes))

        assertTrue("Expected Plain lyrics, was: $result", result is Lyrics.Plain)
        assertEquals(lyricsText, (result as Lyrics.Plain).text)
    }

    @Test
    fun `parse extracts SYLT synchronized lyrics from ID3v2`() {
        val entries = listOf(
            1000L to "First synced line",
            3500L to "Second synced line"
        )
        val id3Bytes = buildId3v23WithSylt(entries)

        val result = EmbeddedLyricsParser.parse(ByteArrayInputStream(id3Bytes))

        assertTrue("Expected Synced lyrics, was: $result", result is Lyrics.Synced)
        val synced = result as Lyrics.Synced
        assertEquals(2, synced.lines.size)
        assertEquals(LrcLine(1000L, "First synced line"), synced.lines[0])
        assertEquals(LrcLine(3500L, "Second synced line"), synced.lines[1])
    }

    @Test
    fun `parse prefers SYLT over USLT when both are present`() {
        val entries = listOf(2000L to "Synced beat")
        val plainText = "Plain fallback"
        val id3Bytes = buildId3v23WithBoth(plainText, entries)

        val result = EmbeddedLyricsParser.parse(ByteArrayInputStream(id3Bytes))

        assertTrue("Expected Synced lyrics to take precedence, was: $result", result is Lyrics.Synced)
        val synced = result as Lyrics.Synced
        assertEquals(1, synced.lines.size)
        assertEquals(LrcLine(2000L, "Synced beat"), synced.lines[0])
    }

    @Test
    fun `parse extracts Vorbis comment lyrics from FLAC stream`() {
        val flacBytes = buildFlacWithVorbisComment("First line of FLAC\nSecond line")
        val result = EmbeddedLyricsParser.parse(ByteArrayInputStream(flacBytes))

        assertTrue("Expected Plain lyrics from FLAC, was: $result", result is Lyrics.Plain)
        val plain = result as Lyrics.Plain
        assertEquals("First line of FLAC\nSecond line", plain.text)
    }

    // Helper functions to construct binary tags for test coverage
    private fun buildId3v23WithUslt(lyrics: String): ByteArray {
        val framePayload = ByteArrayOutputStream().apply {
            write(0x03) // UTF-8 encoding
            write("eng".toByteArray(Charsets.US_ASCII))
            write(0x00) // Empty descriptor delimiter
            write(lyrics.toByteArray(Charsets.UTF_8))
        }.toByteArray()

        val frameBytes = ByteArrayOutputStream().apply {
            write("USLT".toByteArray(Charsets.US_ASCII))
            val sz = framePayload.size
            write(byteArrayOf((sz shr 24).toByte(), (sz shr 16).toByte(), (sz shr 8).toByte(), sz.toByte()))
            write(byteArrayOf(0x00, 0x00)) // Flags
            write(framePayload)
        }.toByteArray()

        return wrapId3v23(frameBytes)
    }

    private fun buildId3v23WithSylt(entries: List<Pair<Long, String>>): ByteArray {
        val framePayload = ByteArrayOutputStream().apply {
            write(0x03) // UTF-8 encoding
            write("eng".toByteArray(Charsets.US_ASCII))
            write(0x02) // Milliseconds timestamp format
            write(0x01) // Lyrics content type
            write(0x00) // Empty descriptor delimiter
            for ((timeMs, text) in entries) {
                write(text.toByteArray(Charsets.UTF_8))
                write(0x00) // null-terminated text
                write(byteArrayOf((timeMs shr 24).toByte(), (timeMs shr 16).toByte(), (timeMs shr 8).toByte(), timeMs.toByte()))
            }
        }.toByteArray()

        val frameBytes = ByteArrayOutputStream().apply {
            write("SYLT".toByteArray(Charsets.US_ASCII))
            val sz = framePayload.size
            write(byteArrayOf((sz shr 24).toByte(), (sz shr 16).toByte(), (sz shr 8).toByte(), sz.toByte()))
            write(byteArrayOf(0x00, 0x00)) // Flags
            write(framePayload)
        }.toByteArray()

        return wrapId3v23(frameBytes)
    }

    private fun buildId3v23WithBoth(plain: String, synced: List<Pair<Long, String>>): ByteArray {
        val usltPayload = ByteArrayOutputStream().apply {
            write(0x03)
            write("eng".toByteArray(Charsets.US_ASCII))
            write(0x00)
            write(plain.toByteArray(Charsets.UTF_8))
        }.toByteArray()
        val usltFrame = ByteArrayOutputStream().apply {
            write("USLT".toByteArray(Charsets.US_ASCII))
            val sz = usltPayload.size
            write(byteArrayOf((sz shr 24).toByte(), (sz shr 16).toByte(), (sz shr 8).toByte(), sz.toByte()))
            write(byteArrayOf(0x00, 0x00))
            write(usltPayload)
        }.toByteArray()

        val syltPayload = ByteArrayOutputStream().apply {
            write(0x03)
            write("eng".toByteArray(Charsets.US_ASCII))
            write(0x02)
            write(0x01)
            write(0x00)
            for ((timeMs, text) in synced) {
                write(text.toByteArray(Charsets.UTF_8))
                write(0x00)
                write(byteArrayOf((timeMs shr 24).toByte(), (timeMs shr 16).toByte(), (timeMs shr 8).toByte(), timeMs.toByte()))
            }
        }.toByteArray()
        val syltFrame = ByteArrayOutputStream().apply {
            write("SYLT".toByteArray(Charsets.US_ASCII))
            val sz = syltPayload.size
            write(byteArrayOf((sz shr 24).toByte(), (sz shr 16).toByte(), (sz shr 8).toByte(), sz.toByte()))
            write(byteArrayOf(0x00, 0x00))
            write(syltPayload)
        }.toByteArray()

        return wrapId3v23(usltFrame + syltFrame)
    }

    private fun wrapId3v23(frames: ByteArray): ByteArray {
        val tagSize = frames.size
        // Synchsafe 4 bytes
        val b0 = ((tagSize shr 21) and 0x7F).toByte()
        val b1 = ((tagSize shr 14) and 0x7F).toByte()
        val b2 = ((tagSize shr 7) and 0x7F).toByte()
        val b3 = (tagSize and 0x7F).toByte()

        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x03, 0x00, // ID3v2.3.0
            0x00,       // Flags
            b0, b1, b2, b3
        )
        return header + frames
    }

    private fun buildFlacWithVorbisComment(lyrics: String): ByteArray {
        val commentStr = "LYRICS=$lyrics"
        val commentBytes = commentStr.toByteArray(Charsets.UTF_8)
        val vendorBytes = "TigerTest".toByteArray(Charsets.UTF_8)

        val vorbisBlock = ByteArrayOutputStream().apply {
            // Vendor length (4 bytes LE)
            val vLen = vendorBytes.size
            write(byteArrayOf(vLen.toByte(), (vLen shr 8).toByte(), (vLen shr 16).toByte(), (vLen shr 24).toByte()))
            write(vendorBytes)
            // Comment count (1) (4 bytes LE)
            write(byteArrayOf(0x01, 0x00, 0x00, 0x00))
            // Comment 1 length (4 bytes LE)
            val cLen = commentBytes.size
            write(byteArrayOf(cLen.toByte(), (cLen shr 8).toByte(), (cLen shr 16).toByte(), (cLen shr 24).toByte()))
            write(commentBytes)
        }.toByteArray()

        val blockLen = vorbisBlock.size
        // Block header: 1 byte flagAndType (0x84 = last block + type 4 vorbis comment), 3 bytes length
        val blockHeader = byteArrayOf(
            0x84.toByte(),
            ((blockLen shr 16) and 0xFF).toByte(),
            ((blockLen shr 8) and 0xFF).toByte(),
            (blockLen and 0xFF).toByte()
        )

        val magic = "fLaC".toByteArray(Charsets.US_ASCII)
        return magic + blockHeader + vorbisBlock
    }
}
