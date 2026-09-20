package com.example.tigerplayer.data.source

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.FolderExclusionRules
import com.example.tigerplayer.utils.SafPathResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Walks a user-selected SAF tree (issue #50), picking up audio files MediaStore has not indexed.
 *
 * Honours `.nomedia` markers and the exclude list via [FolderExclusionRules] - the same rule
 * [com.example.tigerplayer.data.repository.AudioRepository] applies to MediaStore-derived tracks,
 * so exclusion behaves identically regardless of which source found a file.
 */
@Singleton
class SafFolderScanner @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SafFolderScanner"
        private const val ID_PREFIX = "saf:"
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "m4a", "wav", "opus", "aac", "wma"
        )
    }

    suspend fun scan(treeUri: Uri, excludedPaths: Set<String>): List<AudioTrack> =
        withContext(Dispatchers.IO) {
            val root = try {
                DocumentFile.fromTreeUri(context, treeUri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open tree $treeUri: ${e.message}")
                null
            } ?: return@withContext emptyList()

            val rootPath = SafPathResolver.resolvePath(treeUri)
            val results = mutableListOf<AudioTrack>()
            walk(root, rootPath, excludedPaths, results)
            results
        }

    private fun walk(
        directory: DocumentFile,
        directoryPath: String?,
        excludedPaths: Set<String>,
        out: MutableList<AudioTrack>
    ) {
        val children = try {
            directory.listFiles()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list ${directory.uri}: ${e.message}")
            return
        }

        val hasNoMedia = children.any { it.isFile && it.name == ".nomedia" }
        if (FolderExclusionRules.isDirectoryExcluded(directoryPath.orEmpty(), excludedPaths, hasNoMedia)) {
            return
        }

        children.forEach { child ->
            when {
                child.isDirectory -> {
                    val childPath = directoryPath?.let { "$it/${child.name}" }
                    walk(child, childPath, excludedPaths, out)
                }
                child.isFile && isAudioFile(child) -> {
                    out.add(child.toAudioTrack(directoryPath))
                }
            }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val mime = file.type
        if (mime != null && mime.startsWith("audio/")) return true
        val extension = file.name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        return extension in AUDIO_EXTENSIONS
    }

    private fun DocumentFile.toAudioTrack(directoryPath: String?): AudioTrack {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        var bitrate = 0
        var year: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
        } catch (e: Exception) {
            // Corrupt/unsupported files must not take the whole scan down with them.
            Log.w(TAG, "Failed to read tags for $uri: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Best-effort cleanup only.
            }
        }

        val fileName = name ?: uri.lastPathSegment ?: "Unknown"
        val resolvedPath = directoryPath?.let { "$it/$fileName" }
        val lastModifiedSec = lastModified() / 1000

        return AudioTrack(
            id = "$ID_PREFIX$uri",
            title = title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
            artist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            album = album?.takeIf { it.isNotBlank() } ?: "Unknown Album",
            uri = uri,
            artworkUri = Uri.EMPTY,
            durationMs = durationMs,
            mimeType = type ?: "audio/*",
            isLocal = true,
            isRemote = false,
            bitrate = bitrate,
            sampleRate = 0,
            path = resolvedPath,
            year = year,
            dateAdded = lastModifiedSec,
            dateModified = lastModifiedSec
        )
    }
}
