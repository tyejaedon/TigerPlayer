package com.tigerplayer.data.model

enum class SingerRole { LEAD, BACKGROUND, DUET_SECONDARY }

data class LyricWord(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

/**
 * Represents a single timed lyric line in a synchronized lyrics file.
 *
 * @param timeMs The timestamp in milliseconds when this lyric line should activate.
 * @param durationMs The duration in milliseconds of this line. Default is 0L (unknown).
 * @param text The lyric text content for this line.
 * @param words Optional word-level timing details for filling/highlighting effect.
 * @param translation Optional translated text for this line.
 * @param romanization Optional romanized text for this line.
 * @param singer The singer role for this line.
 */
data class LyricLine(
    val timeMs: Long,
    val durationMs: Long = 0L,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
    val singer: SingerRole = SingerRole.LEAD
)

/**
 * Domain model representing lyrics state for an audio track.
 */
sealed interface Lyrics {
    /**
     * Synchronized lyrics containing timestamped lines and optional playback offset.
     *
     * @param lines The list of [LyricLine]s sorted chronologically by [LyricLine.timeMs].
     * @param offsetMs Global playback offset in milliseconds parsed from `[offset:+/-ms]` tags.
     * @param hasWordLevelTiming True if word-level timings are present and can be used for fill rendering.
     */
    data class Synced(
        val lines: List<LyricLine>,
        val offsetMs: Long = 0L,
        val hasWordLevelTiming: Boolean = false
    ) : Lyrics

    /**
     * Unsynchronized plain text lyrics.
     *
     * @param text The complete raw plain text lyrics.
     */
    data class Plain(
        val text: String
    ) : Lyrics

    /**
     * No lyrics found or available for this track.
     */
    data object None : Lyrics
}
