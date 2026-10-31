package com.tigerplayer.data.model

/**
 * Represents a single timed lyric line in a synchronized lyrics file.
 *
 * @param timeMs The timestamp in milliseconds when this lyric line should activate.
 * @param text The lyric text content for this line.
 */
data class LrcLine(
    val timeMs: Long,
    val text: String
)

/**
 * Domain model representing lyrics state for an audio track.
 */
sealed interface Lyrics {
    /**
     * Synchronized lyrics containing timestamped lines and optional playback offset.
     *
     * @param lines The list of [LrcLine]s sorted chronologically by [LrcLine.timeMs].
     * @param offsetMs Global playback offset in milliseconds parsed from `[offset:+/-ms]` tags.
     */
    data class Synced(
        val lines: List<LrcLine>,
        val offsetMs: Long = 0L
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
