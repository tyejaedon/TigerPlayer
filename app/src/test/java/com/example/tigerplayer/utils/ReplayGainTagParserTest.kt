package com.tigerplayer.utils

import androidx.media3.common.Metadata
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainTagParserTest {

    private val epsilon = 1e-4

    @Test
    fun parseGainDb_handlesVariousFormats() {
        assertEquals(-6.5, ReplayGainTagParser.parseGainDb("-6.50 dB")!!, epsilon)
        assertEquals(1.2, ReplayGainTagParser.parseGainDb("+1.20 dB")!!, epsilon)
        assertEquals(-4.2, ReplayGainTagParser.parseGainDb("-4.20")!!, epsilon)
        assertEquals(3.1, ReplayGainTagParser.parseGainDb("3.1 dB")!!, epsilon)
        assertEquals(-6.5, ReplayGainTagParser.parseGainDb("-6,50 dB")!!, epsilon)
        assertEquals(50.0, ReplayGainTagParser.parseGainDb("+65.0 dB")!!, epsilon) // clamped
        assertEquals(-50.0, ReplayGainTagParser.parseGainDb("-80.0 dB")!!, epsilon) // clamped
        assertNull(ReplayGainTagParser.parseGainDb(null))
        assertNull(ReplayGainTagParser.parseGainDb(""))
        assertNull(ReplayGainTagParser.parseGainDb("invalid"))
    }

    @Test
    fun parsePeak_handlesVariousFormats() {
        assertEquals(0.985, ReplayGainTagParser.parsePeak("0.985000")!!, epsilon)
        assertEquals(1.0, ReplayGainTagParser.parsePeak("1.0")!!, epsilon)
        assertEquals(1.025, ReplayGainTagParser.parsePeak("1.025")!!, epsilon)
        assertEquals(0.85, ReplayGainTagParser.parsePeak("0,85")!!, epsilon)
        assertEquals(10.0, ReplayGainTagParser.parsePeak("15.5")!!, epsilon) // clamped
        assertNull(ReplayGainTagParser.parsePeak(null))
        assertNull(ReplayGainTagParser.parsePeak(""))
        assertNull(ReplayGainTagParser.parsePeak("-0.5"))
        assertNull(ReplayGainTagParser.parsePeak("0.0"))
        assertNull(ReplayGainTagParser.parsePeak("invalid"))
    }

    @Test
    fun parseFromMap_extractsCaseInsensitiveTags() {
        val tags = mapOf(
            "replaygain_track_gain" to "-7.20 dB",
            "REPLAYGAIN_ALBUM_GAIN" to "-5.40 dB",
            "ReplayGain_Track_Peak" to "0.950000",
            "REPLAYGAIN_ALBUM_PEAK" to "0.990000"
        )
        val info = ReplayGainTagParser.parseFromMap(tags)
        assertTrue(info.hasGainData)
        assertEquals(-7.20, info.trackGainDb!!, epsilon)
        assertEquals(-5.40, info.albumGainDb!!, epsilon)
        assertEquals(0.95, info.trackPeak!!, epsilon)
        assertEquals(0.99, info.albumPeak!!, epsilon)
    }

    @Test
    fun parseFromMetadata_parsesId3v2TxxxFrames() {
        val txxxTrackGain = TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", listOf("-8.1 dB"))
        val txxxAlbumGain = TextInformationFrame("TXXX", "REPLAYGAIN_ALBUM_GAIN", listOf("-6.0 dB"))
        val txxxTrackPeak = TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_PEAK", listOf("0.89"))
        val txxxAlbumPeak = TextInformationFrame("TXXX", "REPLAYGAIN_ALBUM_PEAK", listOf("0.94"))

        val metadata = Metadata(txxxTrackGain, txxxAlbumGain, txxxTrackPeak, txxxAlbumPeak)
        val info = ReplayGainTagParser.parseFromMetadata(metadata)

        assertTrue(info.hasGainData)
        assertEquals(-8.1, info.trackGainDb!!, epsilon)
        assertEquals(-6.0, info.albumGainDb!!, epsilon)
        assertEquals(0.89, info.trackPeak!!, epsilon)
        assertEquals(0.94, info.albumPeak!!, epsilon)
    }

    @Test
    fun parseFromMetadata_parsesVorbisComments() {
        val vorbisTrack = VorbisComment("REPLAYGAIN_TRACK_GAIN", "-3.50 dB")
        val vorbisAlbum = VorbisComment("REPLAYGAIN_ALBUM_GAIN", "-2.10 dB")
        val vorbisTrackPeak = VorbisComment("REPLAYGAIN_TRACK_PEAK", "0.78")
        val vorbisAlbumPeak = VorbisComment("REPLAYGAIN_ALBUM_PEAK", "0.85")

        val metadata = Metadata(vorbisTrack, vorbisAlbum, vorbisTrackPeak, vorbisAlbumPeak)
        val info = ReplayGainTagParser.parseFromMetadata(metadata)

        assertTrue(info.hasGainData)
        assertEquals(-3.50, info.trackGainDb!!, epsilon)
        assertEquals(-2.10, info.albumGainDb!!, epsilon)
        assertEquals(0.78, info.trackPeak!!, epsilon)
        assertEquals(0.85, info.albumPeak!!, epsilon)
    }

    @Test
    fun parseFromMetadata_parsesMp4InternalFrames() {
        val frameTrack = InternalFrame("com.apple.iTunes", "REPLAYGAIN_TRACK_GAIN", "-5.5 dB")
        val frameAlbum = InternalFrame("com.apple.iTunes", "REPLAYGAIN_ALBUM_GAIN", "-4.0 dB")
        val frameTrackPeak = InternalFrame("com.apple.iTunes", "REPLAYGAIN_TRACK_PEAK", "0.92")

        val metadata = Metadata(frameTrack, frameAlbum, frameTrackPeak)
        val info = ReplayGainTagParser.parseFromMetadata(metadata)

        assertTrue(info.hasGainData)
        assertEquals(-5.5, info.trackGainDb!!, epsilon)
        assertEquals(-4.0, info.albumGainDb!!, epsilon)
        assertEquals(0.92, info.trackPeak!!, epsilon)
        assertNull(info.albumPeak)
    }

    @Test
    fun parseFromMetadata_parsesMp4MdtaMetadata() {
        val mdtaTrack = MdtaMetadataEntry(
            "com.apple.iTunes:replaygain_track_gain",
            "-4.5 dB".toByteArray(Charsets.UTF_8),
            0,
            0
        )
        val mdtaTrackPeak = MdtaMetadataEntry(
            "com.apple.iTunes:replaygain_track_peak",
            "0.88".toByteArray(Charsets.UTF_8),
            0,
            0
        )

        val metadata = Metadata(mdtaTrack, mdtaTrackPeak)
        val info = ReplayGainTagParser.parseFromMetadata(metadata)

        assertTrue(info.hasGainData)
        assertEquals(-4.5, info.trackGainDb!!, epsilon)
        assertEquals(0.88, info.trackPeak!!, epsilon)
        assertNull(info.albumGainDb)
        assertNull(info.albumPeak)
    }
}
