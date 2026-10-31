package com.tigerplayer.data.source

import android.content.Context
import android.net.Uri
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.utils.EmbeddedLyricsParser
import com.tigerplayer.utils.LrcParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves lyrics from local file sources:
 * 1. Sidecar `.lrc` companion files located in the track's folder on disk.
 * 2. Embedded audio file metadata (ID3 `USLT` / `SYLT`, Vorbis comments).
 */
@Singleton
class LocalLyricsSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Inspects the directory containing the local audio file for companion `.lrc` sidecar files.
     *
     * Checked file patterns:
     * - `<audio_file_name_without_extension>.lrc` / `.LRC`
     * - `<track.title>.lrc` / `.LRC`
     *
     * Returns parsed [Lyrics] if found and readable, or [Lyrics.None] otherwise.
     */
    fun getSidecarLyrics(track: AudioTrack): Lyrics {
        val path = track.path ?: track.uri.takeIf { it.scheme == "file" }?.path ?: return Lyrics.None
        val audioFile = File(path)
        val parent = audioFile.parentFile ?: return Lyrics.None
        if (!parent.exists() || !parent.isDirectory) return Lyrics.None

        val nameWithoutExt = audioFile.nameWithoutExtension
        val title = track.title.trim()

        val candidateFiles = mutableListOf<File>()
        if (nameWithoutExt.isNotBlank()) {
            candidateFiles.add(File(parent, "$nameWithoutExt.lrc"))
            candidateFiles.add(File(parent, "$nameWithoutExt.LRC"))
        }
        if (title.isNotBlank() && title != nameWithoutExt) {
            candidateFiles.add(File(parent, "$title.lrc"))
            candidateFiles.add(File(parent, "$title.LRC"))
        }

        for (candidate in candidateFiles) {
            if (candidate.exists() && candidate.isFile && candidate.canRead()) {
                try {
                    val content = candidate.readText(Charsets.UTF_8)
                    val parsed = LrcParser.parse(content)
                    if (parsed !is Lyrics.None) {
                        return parsed
                    }
                } catch (_: Exception) {
                    // Try next candidate
                }
            }
        }

        return Lyrics.None
    }

    /**
     * Extracts embedded lyrics (ID3 USLT/SYLT, Vorbis comments) directly from the audio file stream.
     *
     * Returns parsed [Lyrics] if found, or [Lyrics.None] if no embedded lyrics are present.
     */
    fun getEmbeddedLyrics(track: AudioTrack): Lyrics {
        var inputStream: InputStream? = null
        return try {
            if (!track.path.isNullOrBlank()) {
                val file = File(track.path)
                if (file.exists() && file.isFile && file.canRead()) {
                    inputStream = FileInputStream(file)
                }
            }
            if (inputStream == null && track.uri != Uri.EMPTY) {
                inputStream = context.contentResolver.openInputStream(track.uri)
            }
            if (inputStream != null) {
                EmbeddedLyricsParser.parse(inputStream)
            } else {
                Lyrics.None
            }
        } catch (_: Exception) {
            Lyrics.None
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {}
        }
    }
}
