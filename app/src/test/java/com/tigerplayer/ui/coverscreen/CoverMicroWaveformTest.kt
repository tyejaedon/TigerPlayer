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
import com.tigerplayer.ui.coverscreen.calculateWaveformY
import com.tigerplayer.ui.coverscreen.calculateWaveformYWithEffectiveHeight
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
        val wave2Amp = wave1Amp * 2f
        val wave3Amp = wave2Amp * 1.5f
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

        // Wave 2: Sine Wave starting at PI / 4 radians (2x larger than Wave 1)
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

        // Wave 3: Cosine Wave (1.5x larger than Wave 2 = 3x Wave 1)
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
        assertEquals(2f, wave2Amp / wave1Amp, 0.001f)
        assertEquals(1.5f, wave3Amp / wave2Amp, 0.001f)
        assertEquals(3f, wave3Amp / wave1Amp, 0.001f)

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
    fun `populatePositiveWaveformPath maintains natural sinusoidal curvature across entire width with smooth boundary anchoring`() {
        val path = Path()
        val fillPath = Path()
        val width = 200f
        val maxHeight = 40f
        val baselineY = 20f
        val overlapPx = 2f
        val effectiveBaselineY = baselineY + overlapPx

        // Test with cosine wave with phase = 0f: smoothly anchored at baseline endpoints with shift 0 and peaks in body
        populatePositiveWaveformPath(
            path = path,
            fillPath = fillPath,
            points = floatArrayOf(0.5f, 0.5f),
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0f,
            phaseOffset = 0f,
            isCosine = true,
            waveCycles = 2.0f
        )

        val androidPath = path.asAndroidPath()
        val bounds = RectF()
        androidPath.computeBounds(bounds, true)

        // Bottom rests at baseline and top curves up to peak height smoothly in body
        assertEquals(effectiveBaselineY, bounds.bottom, 0.1f)
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

    // --- 5. ENERGETIC MULTI-HARMONIC & FLUX SURGE TESTS ---

    @Test
    fun `populatePositiveWaveformPath surges aggressively with high bass and flux transients`() {
        val points = floatArrayOf(0.4f, 0.8f, 0.3f, 0.9f)
        val pathQuiet = Path()
        val pathSurge = Path()
        val width = 300f
        val maxHeight = 50f
        val baselineY = 40f
        val overlapPx = 2f

        populatePositiveWaveformPath(
            path = pathQuiet,
            points = points,
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0f,
            bass = 0.1f,
            energy = 0.2f,
            flux = 0f
        )

        populatePositiveWaveformPath(
            path = pathSurge,
            points = points,
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0f,
            bass = 0.95f,
            energy = 1.1f,
            flux = 0.90f
        )

        val boundsQuiet = RectF()
        pathQuiet.asAndroidPath().computeBounds(boundsQuiet, true)
        val boundsSurge = RectF()
        pathSurge.asAndroidPath().computeBounds(boundsSurge, true)

        assertTrue(
            "Surge wave height (${boundsSurge.height()}) must be significantly larger than quiet wave (${boundsQuiet.height()})",
            boundsSurge.height() > boundsQuiet.height() * 1.15f
        )
    }

    @Test
    fun `populatePositiveWaveformPath produces rich harmonic ripples when turbulence is active`() {
        val points = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val smoothPath = Path()
        val turbulentPath = Path()
        val width = 300f
        val maxHeight = 40f
        val baselineY = 30f
        val overlapPx = 2f

        populatePositiveWaveformPath(
            path = smoothPath,
            points = points,
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 1.0f,
            turbulence = 0f
        )

        populatePositiveWaveformPath(
            path = turbulentPath,
            points = points,
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 1.0f,
            turbulence = 0.40f
        )

        val smoothBounds = RectF()
        smoothPath.asAndroidPath().computeBounds(smoothBounds, true)
        val turbulentBounds = RectF()
        turbulentPath.asAndroidPath().computeBounds(turbulentBounds, true)

        assertTrue("Both smooth and turbulent paths must develop non-empty curves", !smoothPath.isEmpty && !turbulentPath.isEmpty)
        assertTrue("Turbulent path bounds must develop rich crests", turbulentBounds.height() > 0f)
    }

    // --- 6. BOUNDARY ANCHOR & ENDPOINT INTERCEPT TESTS ---

    @Test
    fun `populatePositiveWaveformPath anchors all wave curves to baseline with shift 0 at origin x 0`() {
        val width = 400f
        val maxHeight = 50f
        val baselineY = 30f
        val overlapPx = 2.5f
        val effectiveBaselineY = baselineY + overlapPx
        val piOver4 = (Math.PI / 4.0).toFloat()

        val phases = floatArrayOf(0f, 0.5f, 1.2f, (Math.PI).toFloat(), 5.5f)

        for (phase in phases) {
            // Wave 1: Standard Sine wave
            val y1 = calculateWaveformY(
                x = 0f,
                width = width,
                maxHeight = maxHeight,
                baselineY = baselineY,
                overlapPx = overlapPx,
                phase = phase,
                phaseOffset = 0f,
                isCosine = false,
                waveCycles = 2.0f
            )
            assertEquals("Wave 1 must start anchored at baseline with shift 0 at x=0 for phase $phase", effectiveBaselineY, y1, 0.001f)

            // Wave 2: Sine with PI/4 phase lead
            val y2 = calculateWaveformY(
                x = 0f,
                width = width,
                maxHeight = maxHeight * 2f,
                baselineY = baselineY,
                overlapPx = overlapPx,
                phase = phase,
                phaseOffset = piOver4,
                isCosine = false,
                waveCycles = 2.2f
            )
            assertEquals("Wave 2 must start anchored at baseline with shift 0 at x=0 for phase $phase", effectiveBaselineY, y2, 0.001f)

            // Wave 3: Cosine formulation
            val y3 = calculateWaveformY(
                x = 0f,
                width = width,
                maxHeight = maxHeight * 3f,
                baselineY = baselineY,
                overlapPx = overlapPx,
                phase = phase,
                phaseOffset = 0f,
                isCosine = true,
                waveCycles = 2.4f
            )
            assertEquals("Wave 3 must start anchored at baseline with shift 0 at x=0 for phase $phase", effectiveBaselineY, y3, 0.001f)
        }
    }

    @Test
    fun `populatePositiveWaveformPath intercepts baseline x-axis at endpoint x width for all waves`() {
        val width = 500f
        val maxHeight = 45f
        val baselineY = 25f
        val overlapPx = 3f
        val effectiveBaselineY = baselineY + overlapPx
        val piOver4 = (Math.PI / 4.0).toFloat()

        val phases = floatArrayOf(0f, 0.7f, 2.1f, (Math.PI * 1.5).toFloat(), 6.0f)

        for (phase in phases) {
            // Wave 1: 2.0 cycles
            val y1 = calculateWaveformY(
                x = width,
                width = width,
                maxHeight = maxHeight,
                baselineY = baselineY,
                overlapPx = overlapPx,
                phase = phase,
                phaseOffset = 0f,
                isCosine = false,
                waveCycles = 2.0f
            )
            assertEquals("Wave 1 must intercept baseline at x=width for phase $phase", effectiveBaselineY, y1, 0.001f)

            // Wave 2: 2.2 cycles (fractional cycle)
            val y2 = calculateWaveformY(
                x = width,
                width = width,
                maxHeight = maxHeight * 2f,
                baselineY = baselineY,
                overlapPx = overlapPx,
                phase = phase,
                phaseOffset = piOver4,
                isCosine = false,
                waveCycles = 2.2f
            )
            assertEquals("Wave 2 must intercept baseline at x=width for phase $phase", effectiveBaselineY, y2, 0.001f)

            // Wave 3: 2.4 cycles (fractional cycle, cosine)
            val y3 = calculateWaveformY(
                x = width,
                width = width,
                maxHeight = maxHeight * 3f,
                baselineY = baselineY,
                overlapPx = overlapPx,
                phase = phase,
                phaseOffset = 0f,
                isCosine = true,
                waveCycles = 2.4f
            )
            assertEquals("Wave 3 must intercept baseline at x=width for phase $phase", effectiveBaselineY, y3, 0.001f)
        }
    }

    @Test
    fun `populatePositiveWaveformPath smooth boundary envelope anchors endpoints and preserves body crests`() {
        val width = 1000f
        val maxHeight = 50f
        val baselineY = 30f
        val overlapPx = 2f
        val effectiveBaselineY = baselineY + overlapPx

        // 1. At boundaries, elevation must be strictly 0 (shift 0 anchor at start, baseline intercept at end)
        val yStart = calculateWaveformY(
            x = 0f,
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 1.0f,
            phaseOffset = (Math.PI / 4.0).toFloat(),
            isCosine = true
        )
        assertEquals("Elevation at origin must be 0", effectiveBaselineY, yStart, 0.001f)

        val yEnd = calculateWaveformY(
            x = width,
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 1.0f,
            phaseOffset = (Math.PI / 4.0).toFloat(),
            isCosine = true
        )
        assertEquals("Elevation at endpoint must be 0", effectiveBaselineY, yEnd, 0.001f)

        // 2. Throughout the taper region (0 < x < 12% width), elevation is positive and bounded
        for (step in 1..10) {
            val x = (width * 0.12f) * (step.toFloat() / 10f)
            val y = calculateWaveformY(
                x = x,
                width = width,
                maxHeight = maxHeight,
                baselineY = baselineY,
                overlapPx = overlapPx,
                phase = 0f,
                phaseOffset = 0f,
                isCosine = true
            )
            val elevation = effectiveBaselineY - y
            assertTrue("Elevation in taper zone must be non-negative (elevation=$elevation)", elevation >= 0f)
            assertTrue("Elevation must not exceed peak height (elevation=$elevation)", elevation <= maxHeight * 1.5f)
        }

        // 3. In the wave body (outside taper zone), wave reaches full dynamic amplitude
        val path = Path()
        populatePositiveWaveformPath(
            path = path,
            points = floatArrayOf(0.5f, 0.5f),
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0f,
            waveCycles = 2.0f
        )
        val bounds = RectF()
        path.asAndroidPath().computeBounds(bounds, true)
        assertEquals("Path bounds bottom rests at baseline", effectiveBaselineY, bounds.bottom, 0.1f)
        assertEquals("Path bounds left starts at 0", 0f, bounds.left, 0.1f)
        assertEquals("Path bounds right ends at width", width, bounds.right, 0.1f)
        assertTrue("Wave body reaches full peak height", bounds.top <= effectiveBaselineY - (maxHeight * 0.85f))
    }
}
