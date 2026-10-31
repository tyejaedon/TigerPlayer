package com.tigerplayer.utils

import com.tigerplayer.data.model.LrcLine
import com.tigerplayer.data.model.Lyrics

/**
 * Robust parser for LRC format and plain lyrics content.
 *
 * Supports:
 * - Standard timestamped lines: `[mm:ss.xx]` or `[mm:ss]`
 * - Centiseconds (`.xx`), milliseconds (`.xxx`), and single tenths (`.x`)
 * - Comma decimal separators: `[mm:ss,xx]`
 * - Multiple timestamps on a single line: `[00:01.00][00:05.00]Chorus`
 * - LRC global offset tag: `[offset:+/-ms]`
 * - Graceful fallback to [Lyrics.Plain] for unsynchronized lyrics text
 */
object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.,](\d{1,3}))?]""")
    private val OFFSET_REGEX = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*]""")

    /**
     * Parses the given raw lyrics string into a typed [Lyrics] instance.
     */
    fun parse(rawText: String?): Lyrics {
        if (rawText.isNullOrBlank()) return Lyrics.None

        val lines = rawText.lines()
        val parsedLines = mutableListOf<LrcLine>()
        var offsetMs = 0L

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val offsetMatch = OFFSET_REGEX.find(trimmed)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: offsetMs
                continue
            }

            val matches = TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lastMatch = matches.last()
                val text = trimmed.substring(lastMatch.range.last + 1).trim()
                for (match in matches) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val msStr = match.groupValues[3]
                    val ms = when (msStr.length) {
                        0 -> 0L
                        1 -> (msStr.toLongOrNull() ?: 0L) * 100
                        2 -> (msStr.toLongOrNull() ?: 0L) * 10
                        else -> msStr.toLongOrNull() ?: 0L
                    }
                    val timeMs = min * 60_000L + sec * 1_000L + ms
                    parsedLines.add(LrcLine(timeMs = timeMs, text = text))
                }
            }
        }

        return if (parsedLines.isNotEmpty()) {
            Lyrics.Synced(lines = parsedLines.sortedBy { it.timeMs }, offsetMs = offsetMs)
        } else {
            val nonTagLines = lines.filterNot { it.trim().matches(METADATA_TAG_REGEX) }
            val plainContent = nonTagLines.joinToString("\n").trim()
            if (plainContent.isNotBlank()) {
                Lyrics.Plain(text = rawText.trim())
            } else {
                Lyrics.None
            }
        }
    }
}
