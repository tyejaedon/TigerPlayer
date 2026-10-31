package com.tigerplayer.utils

import com.tigerplayer.engine.AcousticNode
import com.tigerplayer.engine.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadDesignerTest {

    private val sampleRate = 48000f

    @Test
    fun calculateCompositePeakGainDb_withEmptyNodes_returnsZero() {
        val peak = BiquadDesigner.calculateCompositePeakGainDb(emptyList(), sampleRate)
        assertEquals(0f, peak, 0.001f)
    }

    @Test
    fun calculateCompositePeakGainDb_withFlatNodes_returnsZero() {
        val flatNodes = listOf(
            AcousticNode("1", "Flat Sub", FilterType.PEAKING, 60f, 0f, 1.0f),
            AcousticNode("2", "Flat Air", FilterType.PEAKING, 12000f, 0f, 1.0f)
        )
        val peak = BiquadDesigner.calculateCompositePeakGainDb(flatNodes, sampleRate)
        assertEquals(0f, peak, 0.001f)
    }

    @Test
    fun calculateCompositePeakGainDb_withNegativeCutNodes_returnsZero() {
        val cutNodes = listOf(
            AcousticNode("cut1", "Bass Cut", FilterType.LOW_SHELF, 100f, -4f, 0.707f),
            AcousticNode("cut2", "Treble Cut", FilterType.HIGH_SHELF, 8000f, -6f, 0.707f)
        )
        val peak = BiquadDesigner.calculateCompositePeakGainDb(cutNodes, sampleRate)
        assertEquals(0f, peak, 0.001f)
    }

    @Test
    fun calculateCompositePeakGainDb_withSinglePeakingFilter_returnsAccurateGain() {
        val node = AcousticNode("mid", "Mid Boost", FilterType.PEAKING, 1000f, 6.0f, 1.0f)
        val peak = BiquadDesigner.calculateCompositePeakGainDb(listOf(node), sampleRate)
        assertEquals(6.0f, peak, 0.05f)
    }

    @Test
    fun calculateCompositePeakGainDb_withNonOverlappingBoosts_doesNotAlgebraicallySum() {
        // Issue #289 core defect scenario: +5 dB at 60 Hz and +4 dB at 12 kHz.
        // Linear algebraic summation yields 5 + 4 = 9 dB (-9 dB attenuation).
        // True composite peak should equal ~5.0 dB (-5 dB attenuation).
        val nodes = listOf(
            AcousticNode("sub", "Sub Bass", FilterType.PEAKING, 60f, 5.0f, 1.0f),
            AcousticNode("air", "Air", FilterType.PEAKING, 12000f, 4.0f, 1.0f)
        )

        val peak = BiquadDesigner.calculateCompositePeakGainDb(nodes, sampleRate)

        // Peak must match the highest individual band (~5.0 dB) and must NOT compound to 9.0 dB
        assertTrue("Composite peak should be close to 5.0 dB (actual=$peak)", peak in 4.95f..5.10f)
        assertTrue("Composite peak must NOT sum algebraically to 9.0 dB", peak < 5.5f)
    }

    @Test
    fun calculateCompositePeakGainDb_withOverlappingBoosts_compoundsAccurately() {
        // Adjacent overlapping boosts at 1000 Hz and 1200 Hz with Q=1.0
        val nodes = listOf(
            AcousticNode("band1", "Mid 1", FilterType.PEAKING, 1000f, 4.0f, 1.0f),
            AcousticNode("band2", "Mid 2", FilterType.PEAKING, 1200f, 4.0f, 1.0f)
        )

        val peak = BiquadDesigner.calculateCompositePeakGainDb(nodes, sampleRate)

        // Compounded peak between bands must exceed individual 4 dB boost, but not exceed algebraic 8 dB
        assertTrue("Compounded peak must be greater than individual 4.0 dB boost (actual=$peak)", peak > 6.5f)
        assertTrue("Compounded peak must not exceed theoretical 8.0 dB maximum (actual=$peak)", peak < 8.0f)
        assertEquals(7.73f, peak, 0.1f)
    }

    @Test
    fun calculateCompositePeakGainDb_withCancelingBoostAndCut_returnsZero() {
        // Identical boost (+6 dB) and cut (-6 dB) at same center frequency and Q cancel out
        val nodes = listOf(
            AcousticNode("boost", "Boost", FilterType.PEAKING, 1000f, 6.0f, 1.0f),
            AcousticNode("cut", "Cut", FilterType.PEAKING, 1000f, -6.0f, 1.0f)
        )

        val peak = BiquadDesigner.calculateCompositePeakGainDb(nodes, sampleRate)
        assertEquals(0.0f, peak, 0.05f)
    }

    @Test
    fun calculateCompositePeakGainDb_withHighQNarrowPeak_detectsPeakAccurately() {
        val node = AcousticNode("notch", "Narrow Boost", FilterType.PEAKING, 2500f, 10.0f, 8.0f)
        val peak = BiquadDesigner.calculateCompositePeakGainDb(listOf(node), sampleRate)
        assertEquals(10.0f, peak, 0.05f)
    }

    @Test
    fun calculateCompositePeakGainDb_withShelvingFilters_calculatesPeakAccurately() {
        val nodes = listOf(
            AcousticNode("low", "Low Shelf", FilterType.LOW_SHELF, 100f, 3.5f, 0.707f),
            AcousticNode("high", "High Shelf", FilterType.HIGH_SHELF, 10000f, 2.0f, 0.707f)
        )

        val peak = BiquadDesigner.calculateCompositePeakGainDb(nodes, sampleRate)
        assertTrue("Shelf peak should be around 3.5 dB (actual=$peak)", peak in 3.4f..3.6f)
    }
}
