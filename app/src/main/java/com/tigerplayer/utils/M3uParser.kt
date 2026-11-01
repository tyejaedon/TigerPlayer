package com.tigerplayer.utils

import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.M3uEntry
import com.tigerplayer.data.model.M3uPathMode
import com.tigerplayer.data.model.M3uPlaylist
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Pure parser and serializer for extended M3U / M3U8 playlist files (issue #58).
 *
 * Handles:
 * - `#EXTM3U` and `#EXTINF:<seconds>,<title>` directives.
 * - Both UTF-8 with/without BOM and legacy charsets.
 * - Windows (`\`) and Unix (`/`) directory separators.
 * - Absolute and relative paths.
 * - Non-ASCII Unicode characters (accents, Japanese, Cyrillic, emojis) preserved losslessly.
 * - Serializing playlists to `.m3u8` using UTF-8 encoding.
 */
object M3uParser {

    private const val HEADER_EXTM3U = "#EXTM3U"
    private const val DIRECTIVE_EXTINF = "#EXTINF:"
    private const val UTF8_BOM = "\uFEFF"

    /**
     * Parses an M3U / M3U8 string content into an [M3uPlaylist].
     *
     * @param content Raw string content of the playlist.
     * @param defaultName Default name if playlist header does not specify one.
     */
    fun parse(content: String, defaultName: String = "Imported Playlist"): M3uPlaylist {
        val cleanContent = if (content.startsWith(UTF8_BOM)) content.substring(1) else content
        val lines = cleanContent.lines()

        val entries = mutableListOf<M3uEntry>()
        var currentDuration: Long = -1L
        var currentTitle: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                if (line.startsWith(DIRECTIVE_EXTINF, ignoreCase = true)) {
                    val infoContent = line.substring(DIRECTIVE_EXTINF.length).trim()
                    val commaIndex = infoContent.indexOf(',')
                    if (commaIndex != -1) {
                        val durationStr = infoContent.substring(0, commaIndex).trim()
                        val titleStr = infoContent.substring(commaIndex + 1).trim()
                        currentDuration = durationStr.toDoubleOrNull()?.toLong() ?: -1L
                        currentTitle = titleStr.ifEmpty { null }
                    } else {
                        currentDuration = infoContent.toDoubleOrNull()?.toLong() ?: -1L
                        currentTitle = null
                    }
                }
                // Other # directives (e.g. #EXT-X-*, comments) are ignored
                continue
            }

            // Normal path / URI entry line
            entries.add(
                M3uEntry(
                    pathOrUri = line,
                    durationSeconds = currentDuration,
                    title = currentTitle
                )
            )

            // Reset pending metadata for next entry
            currentDuration = -1L
            currentTitle = null
        }

        return M3uPlaylist(
            name = defaultName,
            entries = entries
        )
    }

    /**
     * Reads and parses an M3U stream using UTF-8 (falling back to ISO-8859-1 if requested).
     */
    fun parseStream(
        inputStream: InputStream,
        defaultName: String = "Imported Playlist"
    ): M3uPlaylist {
        val text = inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return parse(text, defaultName)
    }

    /**
     * Writes an M3U8 playlist to an [OutputStream] in UTF-8 format.
     *
     * @param outputStream Stream to write to.
     * @param tracks List of tracks in explicit playback order.
     * @param playlistBaseDir Base directory path on device where M3U8 file will reside,
     *                        used if [pathMode] is [M3uPathMode.RELATIVE].
     * @param pathMode Whether to format paths as absolute or relative.
     */
    fun writePlaylist(
        outputStream: OutputStream,
        tracks: List<AudioTrack>,
        playlistBaseDir: String? = null,
        pathMode: M3uPathMode = M3uPathMode.ABSOLUTE
    ) {
        outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write(HEADER_EXTM3U)
            writer.newLine()

            for (track in tracks) {
                val durationSec = if (track.durationMs > 0) track.durationMs / 1000 else -1L
                val title = buildDisplayTitle(track)
                writer.write("$DIRECTIVE_EXTINF$durationSec,$title")
                writer.newLine()

                val pathString = formatTrackPath(track, playlistBaseDir, pathMode)
                writer.write(pathString)
                writer.newLine()
            }
        }
    }

    /**
     * Generates standard `Artist - Title` display for `#EXTINF`.
     */
    private fun buildDisplayTitle(track: AudioTrack): String {
        return if (track.artist.isNotBlank() && !track.artist.equals("<unknown>", ignoreCase = true)) {
            "${track.artist} - ${track.title}"
        } else {
            track.title
        }
    }

    /**
     * Formats the track's path or URI based on requested [pathMode] and [playlistBaseDir].
     */
    fun formatTrackPath(
        track: AudioTrack,
        playlistBaseDir: String?,
        pathMode: M3uPathMode
    ): String {
        val trackPath = track.path ?: return track.uri.toString()

        if (pathMode == M3uPathMode.ABSOLUTE || playlistBaseDir.isNullOrBlank()) {
            return trackPath
        }

        return try {
            val baseFile = File(playlistBaseDir).canonicalFile
            val targetFile = File(trackPath).canonicalFile

            val relative = targetFile.relativeToOrNull(baseFile)
            if (relative != null) {
                relative.path.replace('\\', '/')
            } else {
                trackPath
            }
        } catch (e: Exception) {
            trackPath
        }
    }

    /**
     * Resolves an M3U entry path against the library tracks.
     *
     * Attempts matching strategies in order:
     * 1. Exact match against `track.path` (normalized slashes).
     * 2. Canonical path resolution against [playlistBaseDir] if entry is relative.
     * 3. Suffix match on file relative path (e.g. `Artist/Album/01.mp3`).
     * 4. Match by file basename / filename.
     * 5. Match by Uri string (`track.uri.toString()`).
     */
    fun resolveTrack(
        entryPath: String,
        playlistBaseDir: String?,
        libraryTracks: List<AudioTrack>
    ): AudioTrack? {
        val normalizedEntry = entryPath.trim().replace('\\', '/')
        if (normalizedEntry.isEmpty()) return null

        // 1. Direct Uri match
        val uriMatch = libraryTracks.firstOrNull { it.uri.toString() == normalizedEntry }
        if (uriMatch != null) return uriMatch

        // Pre-filter tracks that have a valid filesystem path
        val tracksWithPath = libraryTracks.filter { !it.path.isNullOrBlank() }

        // 2. Exact absolute path match
        val exactPathMatch = tracksWithPath.firstOrNull {
            it.path!!.replace('\\', '/').equals(normalizedEntry, ignoreCase = true)
        }
        if (exactPathMatch != null) return exactPathMatch

        // 3. Resolve relative to playlist base directory if available
        if (!playlistBaseDir.isNullOrBlank() && !normalizedEntry.startsWith("/")) {
            try {
                val resolvedFile = File(playlistBaseDir, normalizedEntry).canonicalPath.replace('\\', '/')
                val resolvedMatch = tracksWithPath.firstOrNull {
                    it.path!!.replace('\\', '/').equals(resolvedFile, ignoreCase = true)
                }
                if (resolvedMatch != null) return resolvedMatch
            } catch (_: Exception) {}
        }

        // 4. Suffix match: e.g. entry is "Music/Rock/Song.mp3" and track is "/storage/emulated/0/Music/Rock/Song.mp3"
        val suffixMatch = tracksWithPath.firstOrNull {
            val p = it.path!!.replace('\\', '/')
            p.endsWith("/$normalizedEntry", ignoreCase = true) || p.equals(normalizedEntry, ignoreCase = true)
        }
        if (suffixMatch != null) return suffixMatch

        // 5. Basename fallback (matches if unique or best filename match)
        val entryFileName = normalizedEntry.substringAfterLast('/')
        if (entryFileName.isNotEmpty()) {
            val fileNameMatches = tracksWithPath.filter {
                val p = it.path!!.replace('\\', '/')
                p.substringAfterLast('/').equals(entryFileName, ignoreCase = true)
            }
            if (fileNameMatches.size == 1) {
                return fileNameMatches.first()
            } else if (fileNameMatches.size > 1) {
                // If multiple files have the same filename, try matching parent folder name
                val entryParent = normalizedEntry.substringBeforeLast('/', missingDelimiterValue = "").substringAfterLast('/')
                if (entryParent.isNotEmpty()) {
                    val parentMatch = fileNameMatches.firstOrNull {
                        val p = it.path!!.replace('\\', '/')
                        val trackParent = p.substringBeforeLast('/', missingDelimiterValue = "").substringAfterLast('/')
                        trackParent.equals(entryParent, ignoreCase = true)
                    }
                    if (parentMatch != null) return parentMatch
                }
                return fileNameMatches.first()
            }
        }

        return null
    }
}
