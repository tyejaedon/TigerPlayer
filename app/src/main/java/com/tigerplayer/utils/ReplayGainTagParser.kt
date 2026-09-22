package com.tigerplayer.utils

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlin.math.abs

data class ReplayGainInfo(
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null
) {
    val hasGainData: Boolean
        get() = trackGainDb != null || albumGainDb != null || trackPeak != null || albumPeak != null
}

/**
 * Parses ReplayGain tags (FLAC Vorbis comments, ID3v2 TXXX, MP4 atoms) per issue #56.
 */
@OptIn(UnstableApi::class)
@UnstableApi
object ReplayGainTagParser {

    private const val TAG_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
    private const val TAG_ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
    private const val TAG_TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
    private const val TAG_ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"

    // Acceptable range for valid gain in dB (-50 dB to +50 dB)
    private const val MIN_GAIN_DB = -50.0
    private const val MAX_GAIN_DB = 50.0

    // Acceptable range for valid peak (positive up to 10.0, where 1.0 is full scale)
    private const val MAX_PEAK = 10.0

    /**
     * Parses a raw gain string into dB.
     * Examples: "-6.50 dB", "+1.20 dB", "-4.20", "3.1 dB", "-6,50 dB"
     */
    fun parseGainDb(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace("dB", "", ignoreCase = true)
            .replace(" ", "")
            .replace(",", ".")
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value.isNaN()) return null
        return value.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    }

    /**
     * Parses a raw peak string into a linear fraction (e.g. 1.0 = 0 dBFS).
     * Examples: "0.985000", "1.0", "1.025", "0,85"
     */
    fun parsePeak(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace(" ", "")
            .replace(",", ".")
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value.isNaN() || value <= 0.0) return null
        return value.coerceAtMost(MAX_PEAK)
    }

    /**
     * Extracts ReplayGain information from a key-value tag map (case-insensitive).
     */
    fun parseFromMap(tags: Map<String, String>): ReplayGainInfo {
        var trackGain: Double? = null
        var albumGain: Double? = null
        var trackPeak: Double? = null
        var albumPeak: Double? = null

        for ((key, value) in tags) {
            val normalizedKey = key.trim().uppercase()
            when (normalizedKey) {
                TAG_TRACK_GAIN, "TRACK_GAIN" -> trackGain = parseGainDb(value)
                TAG_ALBUM_GAIN, "ALBUM_GAIN" -> albumGain = parseGainDb(value)
                TAG_TRACK_PEAK, "TRACK_PEAK" -> trackPeak = parsePeak(value)
                TAG_ALBUM_PEAK, "ALBUM_PEAK" -> albumPeak = parsePeak(value)
            }
        }

        return ReplayGainInfo(
            trackGainDb = trackGain,
            albumGainDb = albumGain,
            trackPeak = trackPeak,
            albumPeak = albumPeak
        )
    }

    /**
     * Extracts ReplayGain tags from Media3/ExoPlayer [Metadata].
     */
    fun parseFromMetadata(metadata: Metadata?): ReplayGainInfo {
        if (metadata == null) return ReplayGainInfo()

        var trackGain: Double? = null
        var albumGain: Double? = null
        var trackPeak: Double? = null
        var albumPeak: Double? = null

        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is TextInformationFrame -> {
                    // ID3v2 TXXX user-defined text
                    val desc = entry.description?.trim()?.uppercase() ?: ""
                    val value = entry.values.firstOrNull()
                    when (desc) {
                        TAG_TRACK_GAIN, "TRACK_GAIN" -> if (trackGain == null) trackGain = parseGainDb(value)
                        TAG_ALBUM_GAIN, "ALBUM_GAIN" -> if (albumGain == null) albumGain = parseGainDb(value)
                        TAG_TRACK_PEAK, "TRACK_PEAK" -> if (trackPeak == null) trackPeak = parsePeak(value)
                        TAG_ALBUM_PEAK, "ALBUM_PEAK" -> if (albumPeak == null) albumPeak = parsePeak(value)
                    }
                }
                is VorbisComment -> {
                    // Vorbis comments in FLAC, OGG, Opus
                    val key = entry.key.trim().uppercase()
                    val value = entry.value
                    when (key) {
                        TAG_TRACK_GAIN, "TRACK_GAIN" -> if (trackGain == null) trackGain = parseGainDb(value)
                        TAG_ALBUM_GAIN, "ALBUM_GAIN" -> if (albumGain == null) albumGain = parseGainDb(value)
                        TAG_TRACK_PEAK, "TRACK_PEAK" -> if (trackPeak == null) trackPeak = parsePeak(value)
                        TAG_ALBUM_PEAK, "ALBUM_PEAK" -> if (albumPeak == null) albumPeak = parsePeak(value)
                    }
                }
                is InternalFrame -> {
                    // MP4 / iTunes custom atom '----'
                    val desc = entry.description.trim().uppercase()
                    val value = entry.text
                    when (desc) {
                        TAG_TRACK_GAIN, "TRACK_GAIN" -> if (trackGain == null) trackGain = parseGainDb(value)
                        TAG_ALBUM_GAIN, "ALBUM_GAIN" -> if (albumGain == null) albumGain = parseGainDb(value)
                        TAG_TRACK_PEAK, "TRACK_PEAK" -> if (trackPeak == null) trackPeak = parsePeak(value)
                        TAG_ALBUM_PEAK, "ALBUM_PEAK" -> if (albumPeak == null) albumPeak = parsePeak(value)
                    }
                }
                is MdtaMetadataEntry -> {
                    // MP4 QuickTime metadata items
                    val key = entry.key.substringAfterLast(':').trim().uppercase()
                    val value = entry.value.toString(Charsets.UTF_8)
                    when (key) {
                        TAG_TRACK_GAIN, "TRACK_GAIN" -> if (trackGain == null) trackGain = parseGainDb(value)
                        TAG_ALBUM_GAIN, "ALBUM_GAIN" -> if (albumGain == null) albumGain = parseGainDb(value)
                        TAG_TRACK_PEAK, "TRACK_PEAK" -> if (trackPeak == null) trackPeak = parsePeak(value)
                        TAG_ALBUM_PEAK, "ALBUM_PEAK" -> if (albumPeak == null) albumPeak = parsePeak(value)
                    }
                }
            }
        }

        return ReplayGainInfo(
            trackGainDb = trackGain,
            albumGainDb = albumGain,
            trackPeak = trackPeak,
            albumPeak = albumPeak
        )
    }
}
