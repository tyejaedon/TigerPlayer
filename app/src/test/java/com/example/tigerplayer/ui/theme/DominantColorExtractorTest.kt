package com.example.tigerplayer.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DominantColorExtractorTest {

    private val neonSet = setOf(
        TigerCyberCyan,
        TigerToxicLime,
        TigerHotPink,
        TigerElectricAmber,
        TigerNeonOrange,
        TigerSpectralViolet
    )

    @Test
    fun snaps_cyan_family_to_cyber_cyan() {
        val snapped = DominantColorExtractor.snapToNearestNeon(Color(0xFF00D0FF))
        assertEquals(TigerCyberCyan, snapped)
    }

    @Test
    fun snaps_lime_family_to_toxic_lime() {
        val snapped = DominantColorExtractor.snapToNearestNeon(Color(0xFF58FF1A))
        assertEquals(TigerToxicLime, snapped)
    }

    @Test
    fun snaps_pink_family_to_hot_pink() {
        val snapped = DominantColorExtractor.snapToNearestNeon(Color(0xFFFF2AAB))
        assertEquals(TigerHotPink, snapped)
    }

    @Test
    fun snaps_orange_family_to_neon_orange() {
        val snapped = DominantColorExtractor.snapToNearestNeon(Color(0xFFFF7F22))
        assertEquals(TigerNeonOrange, snapped)
    }

    @Test
    fun snaps_low_saturation_gray_to_valid_neon_token() {
        val snapped = DominantColorExtractor.snapToNearestNeon(Color(0xFF777A7C))
        assertTrue(snapped in neonSet)
        assertTrue("Expected high saturation neon token", saturation(snapped) > 0.55f)
    }

    @Test
    fun snaps_muddy_olive_deterministically() {
        val source = Color(0xFF6B7046)
        val first = DominantColorExtractor.snapToNearestNeon(source)
        val second = DominantColorExtractor.snapToNearestNeon(source)
        assertEquals(first, second)
        assertTrue(first in neonSet)
    }

    @Test
    fun snaps_dark_neutral_to_valid_neon_token() {
        val snapped = DominantColorExtractor.snapToNearestNeon(Color(0xFF222426))
        assertTrue(snapped in neonSet)
    }

    private fun saturation(color: Color): Float {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        return if (max == 0f) 0f else delta / max
    }
}
