package com.tigerplayer.ui.coverscreen

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.tigerplayer.ui.theme.CoverNeonPalette
import com.tigerplayer.ui.theme.DominantColorExtractor
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerElectricAmber
import com.tigerplayer.ui.theme.TigerHotPink
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerSpectralViolet
import com.tigerplayer.ui.theme.TigerToxicLime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoverMicroWaveformTest {

    // --- 1. DURATION FORMATTING TESTS ---

    @Test
    fun `formatCoverDuration formats zero and negative milliseconds safely`() {
        assertEquals("00:00", formatCoverDuration(0L))
        assertEquals("00:00", formatCoverDuration(-500L))
        assertEquals("00:00", formatCoverDuration(Long.MIN_VALUE))
    }

    @Test
    fun `formatCoverDuration correctly formats standard minutes and seconds with padding`() {
        assertEquals("00:05", formatCoverDuration(5_000L))
        assertEquals("00:45", formatCoverDuration(45_000L))
        assertEquals("01:00", formatCoverDuration(60_000L))
        assertEquals("01:05", formatCoverDuration(65_000L))
        assertEquals("03:35", formatCoverDuration(215_000L))
        assertEquals("12:42", formatCoverDuration(762_000L))
    }

    // --- 2. NEON PALETTE HARMONY & EXTRACTION TESTS ---

    @Test
    fun `getHarmonizingNeon returns a distinct neon token from the neon palette`() {
        val tokens = listOf(
            TigerCyberCyan,
            TigerToxicLime,
            TigerHotPink,
            TigerElectricAmber,
            TigerNeonOrange,
            TigerSpectralViolet
        )

        for (token in tokens) {
            val harmonizing = DominantColorExtractor.getHarmonizingNeon(token)
            assertNotEquals("Harmonizing neon must not be identical to primary", token, harmonizing)
            assertTrue("Harmonizing neon must be one of the known neon tokens", tokens.contains(harmonizing))
        }
    }

    @Test
    fun `extractCoverNeonPalette falls back cleanly when bitmap is null or zero-sized`() = runTest {
        val fallback = CoverNeonPalette(primary = TigerHotPink, secondary = TigerCyberCyan)

        val nullResult = DominantColorExtractor.extractCoverNeonPalette(bitmap = null, fallback = fallback)
        assertEquals(fallback, nullResult)

        val zeroBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        // With a 1x1 bitmap, palette generation can succeed or fallback safely
        val result = DominantColorExtractor.extractCoverNeonPalette(bitmap = zeroBitmap, fallback = fallback)
        assertTrue(result.primary != Color.Unspecified)
        assertTrue(result.secondary != Color.Unspecified)
    }

    // --- 3. TEST TAG INTEGRITY ---

    @Test
    fun `cover micro waveform test tags are distinct and follow naming convention`() {
        assertEquals("cover_screen_micro_waveform", CoverScreenTestTags.MICRO_WAVEFORM)
        assertEquals("cover_screen_micro_waveform_current_time", CoverScreenTestTags.MICRO_WAVEFORM_CURRENT_TIME)
        assertEquals("cover_screen_micro_waveform_end_time", CoverScreenTestTags.MICRO_WAVEFORM_END_TIME)
        assertEquals("cover_screen_micro_waveform_slider", CoverScreenTestTags.MICRO_WAVEFORM_SLIDER)

        val tags = setOf(
            CoverScreenTestTags.MICRO_WAVEFORM,
            CoverScreenTestTags.MICRO_WAVEFORM_CURRENT_TIME,
            CoverScreenTestTags.MICRO_WAVEFORM_END_TIME,
            CoverScreenTestTags.MICRO_WAVEFORM_SLIDER
        )
        assertEquals("All test tags must be unique", 4, tags.size)
    }
}
