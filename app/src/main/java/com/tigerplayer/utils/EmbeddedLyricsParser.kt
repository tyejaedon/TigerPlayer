package com.tigerplayer.utils

import com.tigerplayer.data.model.LrcLine
import com.tigerplayer.data.model.Lyrics
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Parser for embedded audio metadata lyrics:
 * - ID3v2.2, ID3v2.3, ID3v2.4 `USLT` / `ULT` (unsynchronized lyrics)
 * - ID3v2.2, ID3v2.3, ID3v2.4 `SYLT` / `SLT` (synchronized lyrics)
 * - FLAC Vorbis comment blocks (`LYRICS`, `UNSYNCEDLYRICS`, `SYNCEDLYRICS`)
 */
object EmbeddedLyricsParser {

    private const val MAX_TAG_READ_BYTES = 2 * 1024 * 1024 // 2 MB safety cap

    /**
     * Extracts embedded lyrics from an audio [InputStream].
     *
     * Returns parsed [Lyrics] if found, or [Lyrics.None] if no embedded lyrics exist.
     */
    fun parse(inputStream: InputStream): Lyrics {
        return try {
            val header = ByteArray(10)
            val bytesRead = inputStream.read(header)
            if (bytesRead < 10) return Lyrics.None

            if (header[0] == 'I'.code.toByte() &&
                header[1] == 'D'.code.toByte() &&
                header[2] == '3'.code.toByte()
            ) {
                parseId3(header, inputStream)
            } else if (header[0] == 'f'.code.toByte() &&
                header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() &&
                header[3] == 'C'.code.toByte()
            ) {
                parseFlac(header, inputStream)
            } else {
                Lyrics.None
            }
        } catch (_: Exception) {
            Lyrics.None
        }
    }

    private fun parseId3(header: ByteArray, inputStream: InputStream): Lyrics {
        val version = header[3].toInt() and 0xFF // 2, 3, or 4
        val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)

        if (tagSize <= 0) return Lyrics.None

        val readSize = tagSize.coerceAtMost(MAX_TAG_READ_BYTES)
        val tagData = ByteArray(readSize)
        var offset = 0
        while (offset < readSize) {
            val r = inputStream.read(tagData, offset, readSize - offset)
            if (r == -1) break
            offset += r
        }

        var pos = 0
        var usltFallback: Lyrics? = null

        if (version == 3 || version == 4) {
            while (pos + 10 <= offset) {
                if (tagData[pos] == 0.toByte() && tagData[pos + 1] == 0.toByte()) break

                val frameId = String(tagData, pos, 4, Charsets.US_ASCII)
                val frameSize = if (version == 4) {
                    ((tagData[pos + 4].toInt() and 0x7F) shl 21) or
                            ((tagData[pos + 5].toInt() and 0x7F) shl 14) or
                            ((tagData[pos + 6].toInt() and 0x7F) shl 7) or
                            (tagData[pos + 7].toInt() and 0x7F)
                } else {
                    ((tagData[pos + 4].toInt() and 0xFF) shl 24) or
                            ((tagData[pos + 5].toInt() and 0xFF) shl 16) or
                            ((tagData[pos + 6].toInt() and 0xFF) shl 8) or
                            (tagData[pos + 7].toInt() and 0xFF)
                }

                pos += 10
                if (frameSize <= 0 || pos + frameSize > offset) break

                if (frameId == "SYLT") {
                    val syltLyrics = parseSyltFrame(tagData, pos, frameSize)
                    if (syltLyrics is Lyrics.Synced) return syltLyrics
                } else if (frameId == "USLT" && usltFallback == null) {
                    usltFallback = parseUsltFrame(tagData, pos, frameSize)
                }

                pos += frameSize
            }
        } else if (version == 2) {
            while (pos + 6 <= offset) {
                if (tagData[pos] == 0.toByte() && tagData[pos + 1] == 0.toByte()) break

                val frameId = String(tagData, pos, 3, Charsets.US_ASCII)
                val frameSize = ((tagData[pos + 3].toInt() and 0xFF) shl 16) or
                        ((tagData[pos + 4].toInt() and 0xFF) shl 8) or
                        (tagData[pos + 5].toInt() and 0xFF)

                pos += 6
                if (frameSize <= 0 || pos + frameSize > offset) break

                if (frameId == "SLT") {
                    val syltLyrics = parseSyltFrame(tagData, pos, frameSize)
                    if (syltLyrics is Lyrics.Synced) return syltLyrics
                } else if (frameId == "ULT" && usltFallback == null) {
                    usltFallback = parseUsltFrame(tagData, pos, frameSize)
                }

                pos += frameSize
            }
        }

        return usltFallback ?: Lyrics.None
    }

    private fun parseUsltFrame(data: ByteArray, start: Int, length: Int): Lyrics {
        if (length < 5) return Lyrics.None
        val encodingByte = data[start].toInt() and 0xFF
        val charset = getCharset(encodingByte)
        val isTwoByte = encodingByte == 1 || encodingByte == 2

        var p = start + 4 // Skip encoding (1 byte) + language (3 bytes)
        val end = start + length

        // Find delimiter separating content descriptor from lyrics body
        if (isTwoByte) {
            while (p + 1 < end) {
                if (data[p] == 0.toByte() && data[p + 1] == 0.toByte()) {
                    p += 2
                    break
                }
                p += 2
            }
        } else {
            while (p < end) {
                if (data[p] == 0.toByte()) {
                    p += 1
                    break
                }
                p++
            }
        }

        if (p >= end) return Lyrics.None
        val lyricsText = String(data, p, end - p, charset).trim()
        return LrcParser.parse(lyricsText)
    }

    private fun parseSyltFrame(data: ByteArray, start: Int, length: Int): Lyrics {
        if (length < 6) return Lyrics.None
        val encodingByte = data[start].toInt() and 0xFF
        val charset = getCharset(encodingByte)
        val isTwoByte = encodingByte == 1 || encodingByte == 2

        var p = start + 6 // Skip encoding (1 byte) + lang (3) + timeFormat (1) + contentType (1)
        val end = start + length

        // Find delimiter separating content descriptor from synced entries
        if (isTwoByte) {
            while (p + 1 < end) {
                if (data[p] == 0.toByte() && data[p + 1] == 0.toByte()) {
                    p += 2
                    break
                }
                p += 2
            }
        } else {
            while (p < end) {
                if (data[p] == 0.toByte()) {
                    p += 1
                    break
                }
                p++
            }
        }

        val lines = mutableListOf<LrcLine>()
        while (p < end) {
            val textStart = p
            if (isTwoByte) {
                while (p + 1 < end && !(data[p] == 0.toByte() && data[p + 1] == 0.toByte())) {
                    p += 2
                }
                val text = if (p > textStart) String(data, textStart, p - textStart, charset).trim() else ""
                p += 2 // skip null terminator
                if (p + 4 <= end) {
                    val timeVal = ((data[p].toLong() and 0xFF) shl 24) or
                            ((data[p + 1].toLong() and 0xFF) shl 16) or
                            ((data[p + 2].toLong() and 0xFF) shl 8) or
                            (data[p + 3].toLong() and 0xFF)
                    p += 4
                    lines.add(LrcLine(timeMs = timeVal, text = text))
                } else break
            } else {
                while (p < end && data[p] != 0.toByte()) {
                    p++
                }
                val text = if (p > textStart) String(data, textStart, p - textStart, charset).trim() else ""
                p++ // skip null terminator
                if (p + 4 <= end) {
                    val timeVal = ((data[p].toLong() and 0xFF) shl 24) or
                            ((data[p + 1].toLong() and 0xFF) shl 16) or
                            ((data[p + 2].toLong() and 0xFF) shl 8) or
                            (data[p + 3].toLong() and 0xFF)
                    p += 4
                    lines.add(LrcLine(timeMs = timeVal, text = text))
                } else break
            }
        }

        return if (lines.isNotEmpty()) {
            Lyrics.Synced(lines = lines.sortedBy { it.timeMs }, offsetMs = 0L)
        } else {
            Lyrics.None
        }
    }

    private fun parseFlac(header: ByteArray, inputStream: InputStream): Lyrics {
        var isLast = false
        var plainFallback: Lyrics? = null
        var leftover: ByteArray? = header.copyOfRange(4, 10)

        fun readFully(b: ByteArray, off: Int, len: Int): Boolean {
            var n = 0
            while (n < len) {
                if (leftover != null && leftover!!.isNotEmpty()) {
                    val toCopy = minOf(leftover!!.size, len - n)
                    System.arraycopy(leftover!!, 0, b, off + n, toCopy)
                    leftover = if (toCopy == leftover!!.size) null else leftover!!.copyOfRange(toCopy, leftover!!.size)
                    n += toCopy
                } else {
                    val r = inputStream.read(b, off + n, len - n)
                    if (r == -1) return false
                    n += r
                }
            }
            return true
        }

        fun skipBytes(count: Int) {
            var remaining = count
            val scratch = ByteArray(4096)
            while (remaining > 0) {
                val toRead = minOf(remaining, scratch.size)
                if (!readFully(scratch, 0, toRead)) break
                remaining -= toRead
            }
        }

        val blockHeader = ByteArray(4)
        while (!isLast) {
            if (!readFully(blockHeader, 0, 4)) break
            val flagAndType = blockHeader[0].toInt() and 0xFF
            isLast = (flagAndType and 0x80) != 0
            val blockType = flagAndType and 0x7F
            val blockLength = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                    ((blockHeader[2].toInt() and 0xFF) shl 8) or
                    (blockHeader[3].toInt() and 0xFF)

            if (blockType == 4) { // VORBIS_COMMENT
                if (blockLength > MAX_TAG_READ_BYTES) break
                val blockData = ByteArray(blockLength)
                if (!readFully(blockData, 0, blockLength)) break

                val lyrics = parseVorbisCommentBlock(blockData)
                if (lyrics is Lyrics.Synced) return lyrics
                if (lyrics is Lyrics.Plain && plainFallback == null) {
                    plainFallback = lyrics
                }
            } else {
                skipBytes(blockLength)
            }
        }

        return plainFallback ?: Lyrics.None
    }

    private fun parseVorbisCommentBlock(data: ByteArray): Lyrics {
        if (data.size < 8) return Lyrics.None
        var p = 0
        val vendorLen = (data[p].toInt() and 0xFF) or
                ((data[p + 1].toInt() and 0xFF) shl 8) or
                ((data[p + 2].toInt() and 0xFF) shl 16) or
                ((data[p + 3].toInt() and 0xFF) shl 24)
        p += 4 + vendorLen
        if (p + 4 > data.size) return Lyrics.None

        val commentCount = (data[p].toInt() and 0xFF) or
                ((data[p + 1].toInt() and 0xFF) shl 8) or
                ((data[p + 2].toInt() and 0xFF) shl 16) or
                ((data[p + 3].toInt() and 0xFF) shl 24)
        p += 4

        var plainLyrics: Lyrics.Plain? = null

        for (i in 0 until commentCount) {
            if (p + 4 > data.size) break
            val strLen = (data[p].toInt() and 0xFF) or
                    ((data[p + 1].toInt() and 0xFF) shl 8) or
                    ((data[p + 2].toInt() and 0xFF) shl 16) or
                    ((data[p + 3].toInt() and 0xFF) shl 24)
            p += 4
            if (p + strLen > data.size) break

            val comment = String(data, p, strLen, Charsets.UTF_8)
            p += strLen

            val eq = comment.indexOf('=')
            if (eq > 0) {
                val key = comment.substring(0, eq).trim().uppercase()
                if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYNCEDLYRICS") {
                    val value = comment.substring(eq + 1).trim()
                    val parsed = LrcParser.parse(value)
                    if (parsed is Lyrics.Synced) return parsed
                    if (parsed is Lyrics.Plain && plainLyrics == null) {
                        plainLyrics = parsed
                    }
                }
            }
        }

        return plainLyrics ?: Lyrics.None
    }

    private fun getCharset(encodingByte: Int): Charset = when (encodingByte) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }
}
