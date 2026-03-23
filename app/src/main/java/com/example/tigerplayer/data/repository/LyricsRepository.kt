package com.example.tigerplayer.data.repository

import android.content.Context
import android.util.Log
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.remote.api.LrclibApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val lrclibApi: LrclibApi,
    @param:ApplicationContext private val context: Context
) {
    /**
     * Fetches lyrics for a track.
     * 1. Checks local storage for adjacent .lrc or .txt files.
     * 2. Falls back to the free LRCLIB API.
     */
    fun getLyrics(track: AudioTrack): Flow<String?> = flow {
        // 1. Try to find local lyrics file first (Zero network cost)
        val localLyrics = findLocalLyrics(track)
        if (!localLyrics.isNullOrBlank()) {
            emit(localLyrics)
            return@flow
        }

        // 2. Fallback to Remote API (LRCLIB)
        try {
            val response = lrclibApi.getLyrics(
                trackName = track.title,
                artistName = track.artist,
                albumName = track.album
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Priority: Synced -> Plain
                val bestLyrics = body.syncedLyrics?.takeIf { it.isNotBlank() }
                    ?: body.plainLyrics?.takeIf { it.isNotBlank() }

                emit(bestLyrics)
            } else {
                emit(null)
            }
        } catch (e: Exception) {
            Log.e("LyricsRepo", "Ritual failed: Network error fetching lyrics", e)
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Scans the directory of the audio file for .lrc or .txt files
     * with the same filename.
     */
    private fun findLocalLyrics(track: AudioTrack): String? {
        // IMPORTANT: track.path must be the absolute file path (e.g. /storage/emulated/0/Music/song.flac)
        val rawPath = track.path ?: return null

        return try {
            val audioFile = File(rawPath)
            if (!audioFile.exists()) return null

            val parentDir = audioFile.parentFile ?: return null
            val baseName = audioFile.nameWithoutExtension

            // Look for matching sidecar files (song.lrc or song.txt)
            val extensions = listOf(".lrc", ".LRC", ".txt", ".TXT")

            extensions.firstNotNullOfOrNull { ext ->
                val lyricsFile = File(parentDir, "$baseName$ext")
                if (lyricsFile.exists()) {
                    lyricsFile.readText().takeIf { it.isNotBlank() }
                } else null
            }
        } catch (e: Exception) {
            Log.e("LyricsRepo", "Failed to read local scroll", e)
            null
        }
    }
}