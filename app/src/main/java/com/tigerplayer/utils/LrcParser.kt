package com.tigerplayer.utils

import com.tigerplayer.data.model.LyricLine
import com.tigerplayer.data.model.LyricWord
import com.tigerplayer.data.model.Lyrics
import kotlin.math.abs

/**
 * Robust parser for LRC format and plain lyrics content.
 *
 * Supports:
 * - Standard timestamped lines: `[mm:ss.xx]` or `[mm:ss]`
 * - Centiseconds (`.xx`), milliseconds (`.xxx`), and single tenths (`.x`)
 * - Comma decimal separators: `[mm:ss,xx]`
 * - Multiple timestamps on a single line: `[00:01.00][00:05.00]Chorus`
 * - LRC global offset tag: `[offset:+/-ms]`
 * - Enhanced LRC (ELRC) word-level timestamps: `<mm:ss.xx>word`
 * - Dual-line translations (consecutive lines with identical timestamps)
 * - Graceful fallback to [Lyrics.Plain] for unsynchronized lyrics text
 */
object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.,](\d{1,3}))?]""")
    private val OFFSET_REGEX = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*]""")
    private val WORD_REGEX = Regex("""<(\d{1,2}):(\d{2})(?:[.,](\d{1,3}))?>([^<]*)""")

    /**
     * Parses the given raw lyrics string into a typed [Lyrics] instance.
     */
    fun parse(rawText: String?): Lyrics {
        if (rawText.isNullOrBlank()) return Lyrics.None

        val lines = rawText.lines()
        val parsedLines = mutableListOf<LyricLine>()
        var offsetMs = 0L
        var hasWordLevelTiming = false

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
                val textContent = trimmed.substring(lastMatch.range.last + 1).trim()

                // Parse word-level timings if ELRC tags are present
                val words = mutableListOf<LyricWord>()
                val wordMatches = WORD_REGEX.findAll(textContent).toList()
                if (wordMatches.isNotEmpty()) {
                    hasWordLevelTiming = true
                    for (i in wordMatches.indices) {
                        val wMatch = wordMatches[i]
                        val startTime = parseTimeMs(wMatch.groupValues[1], wMatch.groupValues[2], wMatch.groupValues[3])
                        val wordText = wMatch.groupValues[4]

                        // End time is start of next word, or start + 500ms for last word
                        val endTime = if (i < wordMatches.size - 1) {
                            val next = wordMatches[i + 1]
                            parseTimeMs(next.groupValues[1], next.groupValues[2], next.groupValues[3])
                        } else {
                            startTime + 500L
                        }

                        // Only add words that have actual text
                        if (wordText.isNotEmpty()) {
                            words.add(LyricWord(startTimeMs = startTime, endTimeMs = endTime, text = wordText))
                        }
                    }
                }

                // Remove ELRC tags to get clean display text if words were found
                val cleanText = if (words.isNotEmpty()) {
                    textContent.replace(Regex("""<\d{1,2}:\d{2}(?:[.,]\d{1,3})?>"""), "")
                } else {
                    textContent
                }

                for (match in matches) {
                    val timeMs = parseTimeMs(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    parsedLines.add(LyricLine(timeMs = timeMs, text = cleanText, words = words.toList()))
                }
            }
        }

        return if (parsedLines.isNotEmpty()) {
            // Sort by time
            parsedLines.sortBy { it.timeMs }

            // Merge dual lines for translations (consecutive lines with exactly the same timestamp)
            val mergedLines = mutableListOf<LyricLine>()
            for (line in parsedLines) {
                if (mergedLines.isNotEmpty() && abs(mergedLines.last().timeMs - line.timeMs) <= 10L) { // 10ms tolerance
                    val prev = mergedLines.removeAt(mergedLines.lastIndex)
                    // Assuming the second line with same time is the translation
                    mergedLines.add(prev.copy(translation = line.text))
                } else {
                    mergedLines.add(line)
                }
            }

            // Calculate line durations
            for (i in mergedLines.indices) {
                val current = mergedLines[i]
                val duration = if (i < mergedLines.size - 1) {
                    mergedLines[i + 1].timeMs - current.timeMs
                } else {
                    5000L // 5 seconds default for last line
                }
                mergedLines[i] = current.copy(durationMs = duration)
            }

            Lyrics.Synced(lines = mergedLines, offsetMs = offsetMs, hasWordLevelTiming = hasWordLevelTiming)
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

    private fun parseTimeMs(minStr: String, secStr: String, msStr: String): Long {
        val min = minStr.toLongOrNull() ?: 0L
        val sec = secStr.toLongOrNull() ?: 0L
        val ms = when (msStr.length) {
            0 -> 0L
            1 -> (msStr.toLongOrNull() ?: 0L) * 100
            2 -> (msStr.toLongOrNull() ?: 0L) * 10
            else -> msStr.toLongOrNull() ?: 0L
        }
        return min * 60_000L + sec * 1_000L + ms
    }
}
