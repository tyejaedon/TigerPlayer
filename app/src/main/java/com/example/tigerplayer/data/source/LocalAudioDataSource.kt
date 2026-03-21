package com.example.tigerplayer.data.source

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import androidx.core.net.toUri
import com.example.tigerplayer.data.model.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalAudioDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
    suspend fun getLocalAudioFiles(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.SAMPLERATE,
            MediaStore.Audio.Media.TRACK // <-- THE FIX: Pull the track number directly
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val bitrateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
            val sampleRateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SAMPLERATE)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)

                // Track numbers in MediaStore are sometimes stored as e.g., 1004 (Disc 1, Track 4)
                // We parse it down to just the track number safely.
                val rawTrack = cursor.getInt(trackColumn)
                val cleanTrackNum = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack

                tracks.add(
                    AudioTrack(
                        id = id.toString(),
                        title = cursor.getString(titleColumn) ?: "Unknown Title",
                        artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                        album = cursor.getString(albumColumn) ?: "Unknown Album",
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        artworkUri = "content://media/external/audio/albumart/$albumId".toUri(),
                        durationMs = cursor.getLong(durationColumn),
                        mimeType = cursor.getString(mimeTypeColumn) ?: "audio/mpeg",
                        isLocal = true,
                        bitrate = cursor.getInt(bitrateColumn),
                        sampleRate = cursor.getInt(sampleRateColumn),
                        trackNumber = cleanTrackNum // Extracted instantly!
                    )
                )
            }
        }

        return@withContext tracks
    }
}