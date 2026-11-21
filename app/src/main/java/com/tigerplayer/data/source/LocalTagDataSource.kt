package com.tigerplayer.data.source

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.tigerplayer.BuildConfig
import com.tigerplayer.data.model.ArtworkEditAction
import com.tigerplayer.data.model.AudioTagData
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.BatchTagEditRequest
import com.tigerplayer.data.model.TagWriteResult
import com.tigerplayer.data.model.TrackTagError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes audio metadata tags (MP3, FLAC, M4A, OGG) via jaudiotagger-android,
 * handling Scoped Storage write consent and optional pre-write backups.
 */
@Singleton
class LocalTagDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LocalTagDataSource"
        private const val BACKUP_DIR_NAME = "tag_backups"
        private const val PICTURE_TYPE_FRONT_COVER = 3

        private val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "ogg", "oga")

        private val MIME_TO_EXTENSION = mapOf(
            "audio/mpeg" to "mp3",
            "audio/mp3" to "mp3",
            "audio/flac" to "flac",
            "audio/x-flac" to "flac",
            "audio/mp4" to "m4a",
            "audio/m4a" to "m4a",
            "audio/x-m4a" to "m4a",
            "audio/aac" to "m4a",
            "audio/ogg" to "ogg",
            "application/ogg" to "ogg",
            "audio/vorbis" to "ogg"
        )
    }

    suspend fun readTags(track: AudioTrack): Result<AudioTagData> = withContext(Dispatchers.IO) {
        val ext = resolveSupportedExtension(track)
            ?: return@withContext Result.failure(
                UnsupportedOperationException("Unsupported audio format: ${track.mimeType}")
            )

        val tempFile = createTempAudioFile(ext)
        try {
            copyUriToFile(track.uri, tempFile)
            Result.success(readTagsFromFile(tempFile))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to read tags for trackId=${track.id}", e)
            }
            Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun writeTags(
        tracks: List<AudioTrack>,
        edit: BatchTagEditRequest,
        createBackup: Boolean
    ): TagWriteResult = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) {
            return@withContext TagWriteResult.Failure(
                listOf(TrackTagError("", "", "No tracks selected."))
            )
        }

        val validationErrors = mutableListOf<TrackTagError>()
        val validTracks = mutableListOf<Pair<AudioTrack, String>>()
        for (track in tracks) {
            if (!track.isLocal || track.isRemote) {
                validationErrors += TrackTagError(
                    track.id,
                    track.title,
                    "Only local audio files can be tag-edited."
                )
                continue
            }
            val ext = resolveSupportedExtension(track)
            if (ext == null) {
                validationErrors += TrackTagError(
                    track.id,
                    track.title,
                    "Unsupported format (${track.mimeType}). Supported: MP3, FLAC, M4A, OGG."
                )
                continue
            }
            validTracks += track to ext
        }

        if (validationErrors.isNotEmpty()) {
            return@withContext TagWriteResult.Failure(validationErrors)
        }

        // On Android 11+ (R), pre-check MediaStore URIs and request batch write consent if needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUrisNeedingConsent = validTracks
                .map { it.first.uri }
                .filter { isMediaStoreUri(it) && !hasDirectWriteAccess(it) }

            if (mediaStoreUrisNeedingConsent.isNotEmpty()) {
                val pendingIntent = MediaStore.createWriteRequest(
                    context.contentResolver,
                    mediaStoreUrisNeedingConsent
                )
                return@withContext TagWriteResult.PermissionRequired(pendingIntent.intentSender)
            }
        }

        val updatedTags = mutableMapOf<String, AudioTagData>()
        val errors = mutableListOf<TrackTagError>()

        for ((track, ext) in validTracks) {
            val tempFile = createTempAudioFile(ext)
            try {
                copyUriToFile(track.uri, tempFile)

                if (createBackup) {
                    saveBackup(track, ext, tempFile)
                }

                val newTagData = writeTagsToFile(tempFile, edit)
                copyFileToUri(tempFile, track.uri)
                updatedTags[track.id] = newTagData
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    return@withContext TagWriteResult.PermissionRequired(
                        e.userAction.actionIntent.intentSender
                    )
                }
                errors += TrackTagError(
                    track.id,
                    track.title,
                    e.message ?: "Write permission denied by system."
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to write tags for trackId=${track.id}", e)
                }
                errors += TrackTagError(
                    track.id,
                    track.title,
                    e.message ?: "Failed to write tags."
                )
            } finally {
                tempFile.delete()
            }
        }

        if (errors.isNotEmpty()) {
            TagWriteResult.Failure(errors)
        } else {
            TagWriteResult.Success(updatedTags)
        }
    }

    internal fun readTagsFromFile(file: File): AudioTagData {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tag ?: return AudioTagData()
        val artwork = tag.firstArtwork
        return AudioTagData(
            title = tag.getFirst(FieldKey.TITLE).orEmpty(),
            artist = tag.getFirst(FieldKey.ARTIST).orEmpty(),
            album = tag.getFirst(FieldKey.ALBUM).orEmpty(),
            albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).orEmpty(),
            trackNumber = tag.getFirst(FieldKey.TRACK).orEmpty(),
            discNumber = tag.getFirst(FieldKey.DISC_NO).orEmpty(),
            year = tag.getFirst(FieldKey.YEAR).orEmpty(),
            genre = tag.getFirst(FieldKey.GENRE).orEmpty(),
            artworkBytes = artwork?.binaryData,
            artworkMimeType = artwork?.mimeType
        )
    }

    internal fun writeTagsToFile(file: File, edit: BatchTagEditRequest): AudioTagData {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault

        edit.title?.let { setOrDeleteField(tag, FieldKey.TITLE, it) }
        edit.artist?.let { setOrDeleteField(tag, FieldKey.ARTIST, it) }
        edit.album?.let { setOrDeleteField(tag, FieldKey.ALBUM, it) }
        edit.albumArtist?.let { setOrDeleteField(tag, FieldKey.ALBUM_ARTIST, it) }
        edit.trackNumber?.let { setOrDeleteField(tag, FieldKey.TRACK, it) }
        edit.discNumber?.let { setOrDeleteField(tag, FieldKey.DISC_NO, it) }
        edit.year?.let { setOrDeleteField(tag, FieldKey.YEAR, it) }
        edit.genre?.let { setOrDeleteField(tag, FieldKey.GENRE, it) }

        when (val artAction = edit.artworkAction) {
            ArtworkEditAction.Keep -> Unit
            ArtworkEditAction.Remove -> tag.deleteArtworkField()
            is ArtworkEditAction.Replace -> {
                tag.deleteArtworkField()
                when (tag) {
                    is FlacTag -> {
                        val (w, h) = decodeImageBounds(artAction.bytes)
                        tag.setField(
                            MetadataBlockDataPicture(
                                artAction.bytes,
                                PICTURE_TYPE_FRONT_COVER,
                                artAction.mimeType,
                                "",
                                w,
                                h,
                                24,
                                0
                            )
                        )
                    }
                    is VorbisCommentTag -> {
                        tag.setArtworkField(artAction.bytes, artAction.mimeType)
                    }
                    else -> {
                        val artwork = AndroidArtwork().apply {
                            binaryData = artAction.bytes
                            mimeType = artAction.mimeType
                            pictureType = PICTURE_TYPE_FRONT_COVER
                        }
                        tag.setField(artwork)
                    }
                }
            }
        }

        audioFile.commit()
        return readTagsFromFile(file)
    }

    private fun decodeImageBounds(bytes: ByteArray): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    private fun setOrDeleteField(tag: Tag, key: FieldKey, rawValue: String) {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            tag.deleteField(key)
        } else {
            tag.setField(key, trimmed)
        }
    }

    private fun resolveSupportedExtension(track: AudioTrack): String? {
        val fromPath = track.path?.substringAfterLast('.', "")?.lowercase()
        if (fromPath != null && fromPath in SUPPORTED_EXTENSIONS) {
            return if (fromPath == "oga") "ogg" else fromPath
        }
        return MIME_TO_EXTENSION[track.mimeType.lowercase()]
    }

    private fun createTempAudioFile(ext: String): File =
        File.createTempFile("tiger_tag_", ".$ext", context.cacheDir)

    private fun copyUriToFile(uri: Uri, dest: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open audio file for reading.")
        input.use { inStream ->
            dest.outputStream().use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun copyFileToUri(src: File, uri: Uri) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Unable to open audio file for writing.")
        output.use { outStream ->
            src.inputStream().use { inStream ->
                inStream.copyTo(outStream)
            }
            outStream.flush()
        }
    }

    private fun saveBackup(track: AudioTrack, ext: String, sourceFile: File) {
        val backupDir = File(context.filesDir, BACKUP_DIR_NAME).apply { mkdirs() }
        val sanitizedId = track.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val backupFile = File(backupDir, "${sanitizedId}_${System.currentTimeMillis()}.$ext")
        sourceFile.copyTo(backupFile, overwrite = true)
    }

    private fun isMediaStoreUri(uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == MediaStore.AUTHORITY

    private fun hasDirectWriteAccess(uri: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { true } ?: false
    } catch (_: SecurityException) {
        false
    } catch (_: Exception) {
        false
    }
}
