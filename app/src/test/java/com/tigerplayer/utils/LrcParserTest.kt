package com.tigerplayer.utils

import com.tigerplayer.data.model.LrcLine
import com.tigerplayer.data.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parse returns None when input is null or blank`() {
        assertEquals(Lyrics.None, LrcParser.parse(null))
        assertEquals(Lyrics.None, LrcParser.parse(""))
        assertEquals(Lyrics.None, LrcParser.parse("   \n\t  "))
    }

    @Test
    fun `parse returns None when input contains only metadata tags`() {
        val lrc = """
            [ti:Song Title]
            [ar:Artist Name]
            [al:Album Name]
            [by:Creator]
        """.trimIndent()
        assertEquals(Lyrics.None, LrcParser.parse(lrc))
    }

    @Test
    fun `parse returns Plain when input has no timestamp tags`() {
        val plain = "First line of song\nSecond line of song\nFinal line"
        val result = LrcParser.parse(plain)
        assertTrue(result is Lyrics.Plain)
        assertEquals(plain, (result as Lyrics.Plain).text)
    }

    @Test
    fun `parse returns Synced for standard timestamped lines`() {
        val lrc = """
            [00:01.00]First line
            [00:05.50]Second line
            [01:10.123]Third line
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        assertTrue(result is Lyrics.Synced)
        val synced = result as Lyrics.Synced
        assertEquals(3, synced.lines.size)
        assertEquals(LrcLine(1000L, "First line"), synced.lines[0])
        assertEquals(LrcLine(5500L, "Second line"), synced.lines[1])
        assertEquals(LrcLine(70123L, "Third line"), synced.lines[2])
        assertEquals(0L, synced.offsetMs)
    }

    @Test
    fun `parse handles multi-timestamp lines and chronological sorting`() {
        val lrc = """
            [00:10.00]Late line
            [00:01.00][00:05.00]Repeated chorus line
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        assertTrue(result is Lyrics.Synced)
        val synced = result as Lyrics.Synced
        assertEquals(3, synced.lines.size)
        assertEquals(LrcLine(1000L, "Repeated chorus line"), synced.lines[0])
        assertEquals(LrcLine(5000L, "Repeated chorus line"), synced.lines[1])
        assertEquals(LrcLine(10000L, "Late line"), synced.lines[2])
    }

    @Test
    fun `parse handles comma decimal separator and single digit tenths`() {
        val lrc = """
            [00:02,5]Tenths with comma
            [00:04.5]Tenths with dot
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        assertTrue(result is Lyrics.Synced)
        val synced = result as Lyrics.Synced
        assertEquals(LrcLine(2500L, "Tenths with comma"), synced.lines[0])
        assertEquals(LrcLine(4500L, "Tenths with dot"), synced.lines[1])
    }

    @Test
    fun `parse extracts global offset tag`() {
        val lrc = """
            [offset:+500]
            [00:01.00]First line
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        assertTrue(result is Lyrics.Synced)
        val synced = result as Lyrics.Synced
        assertEquals(500L, synced.offsetMs)
        assertEquals(1, synced.lines.size)
    }

    @Test
    fun `parse handles empty lyric lines as pauses`() {
        val lrc = """
            [00:01.00]Line 1
            [00:03.00]
            [00:05.00]Line 2
        """.trimIndent()

        val result = LrcParser.parse(lrc)
        assertTrue(result is Lyrics.Synced)
        val synced = result as Lyrics.Synced
        assertEquals(3, synced.lines.size)
        assertEquals("", synced.lines[1].text)
    }
}
