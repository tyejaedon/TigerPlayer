package com.tigerplayer.engine

import com.tigerplayer.data.local.ReplayGainMode
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class ReplayGainCalculatorTest {

    private val epsilon = 1e-4f

    @Test
    fun modeOff_returnsUnityGain() {
        val multiplier = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.OFF,
            preampDb = 6.0f,
            preventClipping = true,
            trackGainDb = -6.0,
            albumGainDb = -8.0,
            trackPeak = 0.9,
            albumPeak = 0.9,
            isSequentialAlbumPlay = false,
            isBitPerfect = false
        )
        assertEquals(1.0f, multiplier, epsilon)
    }

    @Test
    fun bitPerfect_bypassesGain_returnsUnityGain() {
        val multiplier = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.TRACK,
            preampDb = 4.0f,
            preventClipping = true,
            trackGainDb = -6.0,
            albumGainDb = -8.0,
            trackPeak = 0.9,
            albumPeak = 0.9,
            isSequentialAlbumPlay = false,
            isBitPerfect = true
        )
        assertEquals(1.0f, multiplier, epsilon)
    }

    @Test
    fun modeTrack_appliesTrackGainAndPreamp() {
        // -6 dB track gain + 0 dB preamp = -6 dB -> 10^(-6/20) = 0.501187
        val multiplier = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.TRACK,
            preampDb = 0.0f,
            preventClipping = true,
            trackGainDb = -6.0,
            albumGainDb = -9.0,
            trackPeak = 0.5,
            albumPeak = 0.8,
            isSequentialAlbumPlay = false,
            isBitPerfect = false
        )
        val expected = 10.0.pow(-6.0 / 20.0).toFloat()
        assertEquals(expected, multiplier, epsilon)
    }

    @Test
    fun modeTrack_fallsBackToAlbumGain_whenTrackGainMissing() {
        val multiplier = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.TRACK,
            preampDb = 0.0f,
            preventClipping = true,
            trackGainDb = null,
            albumGainDb = -4.0,
            trackPeak = null,
            albumPeak = 0.5,
            isSequentialAlbumPlay = false,
            isBitPerfect = false
        )
        val expected = 10.0.pow(-4.0 / 20.0).toFloat()
        assertEquals(expected, multiplier, epsilon)
    }

    @Test
    fun modeAlbum_appliesAlbumGainAndPreamp() {
        // -3 dB album gain + 1 dB preamp = -2 dB -> 10^(-2/20) = 0.794328
        val multiplier = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.ALBUM,
            preampDb = 1.0f,
            preventClipping = true,
            trackGainDb = -10.0,
            albumGainDb = -3.0,
            trackPeak = 0.6,
            albumPeak = 0.7,
            isSequentialAlbumPlay = false,
            isBitPerfect = false
        )
        val expected = 10.0.pow(-2.0 / 20.0).toFloat()
        assertEquals(expected, multiplier, epsilon)
    }

    @Test
    fun modeAlbum_fallsBackToTrackGain_whenAlbumGainMissing() {
        val multiplier = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.ALBUM,
            preampDb = 0.0f,
            preventClipping = true,
            trackGainDb = -5.0,
            albumGainDb = null,
            trackPeak = 0.5,
            albumPeak = null,
            isSequentialAlbumPlay = false,
            isBitPerfect = false
        )
        val expected = 10.0.pow(-5.0 / 20.0).toFloat()
        assertEquals(expected, multiplier, epsilon)
    }

    @Test
    fun modeSmart_usesAlbumGainWhenSequential_andTrackGainOtherwise() {
        val sequentialMultiplier = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.SMART,
            preampDb = 0.0f,
            preventClipping = true,
            trackGainDb = -6.0,
            albumGainDb = -2.0,
            trackPeak = 0.8,
            albumPeak = 0.9,
            isSequentialAlbumPlay = true,
            isBitPerfect = false
        )
        assertEquals(10.0.pow(-2.0 / 20.0).toFloat(), sequentialMultiplier, epsilon)

        val nonSequentialMultiplier = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.SMART,
            preampDb = 0.0f,
            preventClipping = true,
            trackGainDb = -6.0,
            albumGainDb = -2.0,
            trackPeak = 0.8,
            albumPeak = 0.9,
            isSequentialAlbumPlay = false,
            isBitPerfect = false
        )
        assertEquals(10.0.pow(-6.0 / 20.0).toFloat(), nonSequentialMultiplier, epsilon)
    }

    @Test
    fun clippingProtection_clampsMultiplier_whenGainExceedsPeakHeadroom() {
        // +6 dB gain = 2.0 linear multiplier.
        // Peak is 0.8. 2.0 * 0.8 = 1.6 > 1.0 (clipping!)
        // With preventClipping=true, maximum allowed linear multiplier is 1.0 / 0.8 = 1.25.
        val clamped = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.TRACK,
            preampDb = 6.0f,
            preventClipping = true,
            trackGainDb = 0.0,
            albumGainDb = 0.0,
            trackPeak = 0.8,
            albumPeak = 0.8,
            isSequentialAlbumPlay = false,
            isBitPerfect = false
        )
        assertEquals(1.25f, clamped, epsilon)

        // Without preventClipping, full 2.0 gain is applied
        val unclamped = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.TRACK,
            preampDb = 6.0f,
            preventClipping = false,
            trackGainDb = 0.0,
            albumGainDb = 0.0,
            trackPeak = 0.8,
            albumPeak = 0.8,
            isSequentialAlbumPlay = false,
            isBitPerfect = false
        )
        assertEquals(10.0.pow(6.0 / 20.0).toFloat(), unclamped, epsilon)
    }

    @Test
    fun clippingProtection_ignoresPeak_ifPeakIsZeroOrInvalid() {
        val multiplier = ReplayGainCalculator.calculateEffectiveLinearGain(
            mode = ReplayGainMode.TRACK,
            preampDb = 3.0f,
            preventClipping = true,
            trackGainDb = 0.0,
            albumGainDb = 0.0,
            trackPeak = 0.0,
            albumPeak = null,
            isSequentialAlbumPlay = false,
            isBitPerfect = false
        )
        assertEquals(10.0.pow(3.0 / 20.0).toFloat(), multiplier, epsilon)
    }
}
