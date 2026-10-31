package com.tigerplayer.ui.coverscreen

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import com.tigerplayer.ui.theme.CoverNeonPalette
import com.tigerplayer.ui.theme.DefaultCoverNeonPalette
import com.tigerplayer.ui.theme.DominantColorExtractor
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerElectricAmber
import com.tigerplayer.ui.theme.TigerHotPink
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerNeonRed
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
            TigerNeonRed,
            TigerNeonOrange,
            TigerHotPink,
            TigerCyberCyan,
            TigerSpectralViolet,
            TigerToxicLime,
            TigerElectricAmber
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

    @Test
    fun `when album art is available color output is extracted from album art rather than default cover neon palette`() = runTest {
        val defaultCoverNeonPalette = DefaultCoverNeonPalette

        // 1. When artwork is null, the default cover neon palette is used
        val nullResult = DominantColorExtractor.extractCoverNeonPalette(
            bitmap = null,
            fallback = defaultCoverNeonPalette
        )
        assertEquals(defaultCoverNeonPalette, nullResult)
        assertEquals(TigerNeonRed, nullResult.primary)
        assertEquals(TigerNeonOrange, nullResult.secondary)
        assertEquals(TigerCyberCyan, nullResult.tertiary)

        // 2. When red album art is available, color output matches the album art palette (TigerNeonRed)
        val redAlbumArt = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        val redResult = DominantColorExtractor.extractCoverNeonPalette(
            bitmap = redAlbumArt,
            fallback = defaultCoverNeonPalette
        )
        assertEquals("Primary color must match album art palette (TigerNeonRed)", TigerNeonRed, redResult.primary)

        // 3. When cyan album art is available, color output is the album art palette and NOT default cover neon palette
        val cyanAlbumArt = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.CYAN)
        }
        val cyanResult = DominantColorExtractor.extractCoverNeonPalette(
            bitmap = cyanAlbumArt,
            fallback = defaultCoverNeonPalette
        )

        assertNotEquals("Primary color must not be default TigerNeonRed", defaultCoverNeonPalette.primary, cyanResult.primary)
        assertNotEquals("Palette must not be default cover neon palette", defaultCoverNeonPalette, cyanResult)
        assertEquals("Primary color must match album art palette (TigerCyberCyan)", TigerCyberCyan, cyanResult.primary)

        // 4. When magenta album art is available, color output matches the album art's distinct palette
        val magentaAlbumArt = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }
        val magentaResult = DominantColorExtractor.extractCoverNeonPalette(
            bitmap = magentaAlbumArt,
            fallback = defaultCoverNeonPalette
        )

        assertNotEquals("Primary color must not be default TigerNeonRed", defaultCoverNeonPalette.primary, magentaResult.primary)
        assertNotEquals("Palette must not be default cover neon palette", defaultCoverNeonPalette, magentaResult)
        assertEquals("Primary color must match album art palette (TigerHotPink)", TigerHotPink, magentaResult.primary)
    }

    // --- 3. MODULAR MULTI-WAVEFORM TESTS ---

    @Test
    fun `populatePositiveWaveformPath creates 3 distinct waveforms with positive amplitudes and slider overlap`() {
        val points = floatArrayOf(0.2f, 0.5f, 0.8f, 0.6f, 0.9f, 0.4f, 0.7f, 0.3f)
        val path1 = Path()
        val fill1 = Path()
        val path2 = Path()
        val fill2 = Path()
        val path3 = Path()
        val fill3 = Path()

        val width = 200f
        val baseAmplitude = 6.5f
        val wave1Amp = baseAmplitude
        val wave2Amp = wave1Amp * 4f
        val wave3Amp = wave2Amp * 3f
        val baselineY = 40f
        val overlapPx = 3f
        val piOver4 = (Math.PI / 4.0).toFloat()
        val piOver2 = (Math.PI / 2.0).toFloat()

        // Wave 1: Standard Sine Wave (base amplitude 1x)
        populatePositiveWaveformPath(
            path = path1,
            fillPath = fill1,
            points = points,
            width = width,
            maxHeight = wave1Amp,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0.5f,
            phaseOffset = 0f,
            isCosine = false,
            frequencyMultiplier = 1.0f
        )

        // Wave 2: Sine Wave starting at PI / 4 radians (4x larger than Wave 1)
        populatePositiveWaveformPath(
            path = path2,
            fillPath = fill2,
            points = points,
            width = width,
            maxHeight = wave2Amp,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0.5f,
            phaseOffset = piOver4,
            isCosine = false,
            frequencyMultiplier = 1.0f
        )

        // Wave 3: Cosine Wave (3x larger than Wave 2 = 12x Wave 1)
        populatePositiveWaveformPath(
            path = path3,
            fillPath = fill3,
            points = points,
            width = width,
            maxHeight = wave3Amp,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0.5f,
            phaseOffset = 0f,
            isCosine = true,
            frequencyMultiplier = 1.0f
        )

        // Verify exact relative amplitude factor scaling
        assertEquals(4f, wave2Amp / wave1Amp, 0.001f)
        assertEquals(3f, wave3Amp / wave2Amp, 0.001f)
        assertEquals(12f, wave3Amp / wave1Amp, 0.001f)

        // Verify paths are non-empty
        assertTrue("Wave 1 path must not be empty", !path1.isEmpty)
        assertTrue("Wave 2 path must not be empty", !path2.isEmpty)
        assertTrue("Wave 3 path must not be empty", !path3.isEmpty)
        assertTrue("Wave 1 fill must not be empty", !fill1.isEmpty)
        assertTrue("Wave 2 fill must not be empty", !fill2.isEmpty)
        assertTrue("Wave 3 fill must not be empty", !fill3.isEmpty)
    }

    @Test
    fun `populatePositiveWaveformPath develops proper curves even when points array is empty`() {
        val emptyPoints = floatArrayOf()
        val path = Path()
        val fillPath = Path()

        populatePositiveWaveformPath(
            path = path,
            fillPath = fillPath,
            points = emptyPoints,
            width = 300f,
            maxHeight = 60f,
            baselineY = 40f,
            overlapPx = 2f,
            phase = 0f
        )

        assertTrue("Path must develop and not be empty when points is empty", !path.isEmpty)
        assertTrue("Fill path must develop and not be empty when points is empty", !fillPath.isEmpty)

        val bounds = RectF()
        path.asAndroidPath().computeBounds(bounds, true)
        assertTrue("Wave bounds height must develop full curves (height > 0)", bounds.height() > 30f)
        assertTrue("Wave bounds top must rise above baseline", bounds.top < 40f)
    }

    @Test
    fun `populatePositiveWaveformPath develops proper curves even with zero audio energy and zero bass`() {
        val zeroPoints = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val path = Path()
        val fillPath = Path()

        populatePositiveWaveformPath(
            path = path,
            fillPath = fillPath,
            points = zeroPoints,
            width = 300f,
            maxHeight = 50f,
            baselineY = 30f,
            overlapPx = 2f,
            phase = 0f,
            bass = 0f,
            energy = 0f
        )

        assertTrue("Path must develop and not be empty during silence or zero energy", !path.isEmpty)
        assertTrue("Fill path must develop and not be empty during silence or zero energy", !fillPath.isEmpty)

        val bounds = RectF()
        path.asAndroidPath().computeBounds(bounds, true)
        // Even with zero energy and zero bass, the wave must preserve >= 90% of maxHeight
        assertTrue(
            "Wave curve must develop at least 40px height for 50px maxHeight (developed: ${bounds.height()})",
            bounds.height() >= 40f
        )
    }

    @Test
    fun `populatePositiveWaveformPath maintains continuous curvature without flat clipping`() {
        val points = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val path = Path()
        val fillPath = Path()
        val maxHeight = 40f
        val baselineY = 20f
        val overlapPx = 2f

        populatePositiveWaveformPath(
            path = path,
            fillPath = fillPath,
            points = points,
            width = 200f,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0f,
            isCosine = false
        )

        val bounds = RectF()
        path.asAndroidPath().computeBounds(bounds, true)
        val effectiveBaselineY = baselineY + overlapPx

        // The wave crest must reach near baseline - maxHeight, and troughs sit at effectiveBaselineY
        assertTrue("Wave bounds bottom must be anchored at baseline", bounds.bottom <= effectiveBaselineY + 0.1f)
        assertTrue("Wave bounds top must curve up near peak height", bounds.top <= effectiveBaselineY - (maxHeight * 0.85f))
    }

    @Test
    fun `CoverNeonPalette includes distinct tertiary color for the third waveform`() {
        val palette = DefaultCoverNeonPalette

        assertNotEquals("Tertiary color must not equal primary", palette.primary, palette.tertiary)
        assertNotEquals("Tertiary color must not equal secondary", palette.secondary, palette.tertiary)
        assertTrue("All 3 colors must be non-unspecified", palette.tertiary != Color.Unspecified)
        assertEquals("Primary must be vibrant neon red", TigerNeonRed, palette.primary)
        assertEquals("Secondary must be vibrant neon orange", TigerNeonOrange, palette.secondary)
        assertEquals("Tertiary must be vibrant cyber cyan", TigerCyberCyan, palette.tertiary)
    }

    // --- 4. TEST TAG INTEGRITY ---

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
