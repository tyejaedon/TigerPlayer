package com.example.tigerplayer.data.source

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresExtension
import com.example.tigerplayer.data.model.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import androidx.core.net.toUri

class LocalAudioDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * THE INDEXING RITUAL
     * Streams scan progress back to the UI so the user isn't left in the dark.
     */
    fun getLocalAudioFiles(): Flow<ScanStatus> = flow {
        val tracks = mutableListOf<AudioTrack>()

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR
        ).apply {
            add(MediaStore.Audio.Media.BITRATE)
            add(MediaStore.Audio.Media.SAMPLERATE)
        }.toTypedArray()

        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE '%flac%') " +
                "AND ${MediaStore.Audio.Media.IS_RINGTONE} == 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} == 0"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val total = cursor.count
            emit(ScanStatus.Started(total))

            // SPEED HACK: Fetch indices OUTSIDE the loop
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val rawTrack = cursor.getInt(trackCol)
                val year = cursor.getInt(yearCol)

                // Track number normalization (e.g. 1004 -> 4)
                val cleanTrackNum = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack

                tracks.add(AudioTrack(
                    id = id.toString(),
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol) ?: "Unknown Artist",
                    album = cursor.getString(albumCol) ?: "Unknown Album",
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    artworkUri = "content://media/external/audio/albumart/$albumId".toUri(),
                    durationMs = cursor.getLong(durCol),
                    mimeType = cursor.getString(mimeCol) ?: "audio/mpeg",
                    isLocal = true,
                    isRemote = false,
                    trackNumber = cleanTrackNum,
                    path = cursor.getString(dataCol),
                    year = if (year != 0) year.toString() else null,
                    bitrate = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE).coerceAtLeast(0)),
                    sampleRate = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.SAMPLERATE).coerceAtLeast(0)),
                    serverPath = null
                ))

                // Update UI every 10 tracks to keep the main thread fluid
                if (tracks.size % 10 == 0 || tracks.size == total) {
                    emit(ScanStatus.InProgress(tracks.size, total))
                }
            }
        }
        // Ensure we always emit Complete, even if the cursor was null or empty
        emit(ScanStatus.Complete(tracks))
    }.flowOn(Dispatchers.IO)

    sealed class ScanStatus {
        data class Started(val total: Int) : ScanStatus()
        data class InProgress(val current: Int, val total: Int) : ScanStatus()
        data class Complete(val tracks: List<AudioTrack>) : ScanStatus()
    }
}