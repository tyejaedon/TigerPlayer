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
    @ApplicationContext private val context: Context
) {
    /**
     * Fetches lyrics for a track.
     * 1. Checks local storage for adjacent .lrc or .txt files.
     * 2. Falls back to the free LRCLIB API.
     */
    fun getLyrics(track: AudioTrack): Flow<String?> = flow {
        // 1. Try to find local lyrics file first (Zero network cost)
        val localLyrics = findLocalLyrics(track)
        if (localLyrics != null) {
            Log.d("LyricsRepo", "Found local lyrics for ${track.title}")
            emit(localLyrics)
            return@flow
        }

        // 2. Fallback to Remote API
        try {
            Log.d("LyricsRepo", "Fetching remote lyrics for ${track.title}...")
            val response = lrclibApi.getLyrics(
                trackName = track.title,
                artistName = track.artist,
                albumName = track.album
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Prefer synced lyrics (for karaoke style), fallback to plain
                val bestLyrics = body.syncedLyrics ?: body.plainLyrics
                emit(bestLyrics)
            } else {
                Log.w("LyricsRepo", "No lyrics found on LRCLIB: ${response.code()}")
                emit(null)
            }
        } catch (e: Exception) {
            Log.e("LyricsRepo", "Network error fetching lyrics", e)
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    private fun findLocalLyrics(track: AudioTrack): String? {
        // THE FIX: Use the actual raw absolute file path, NOT the MediaStore URI path!
        // Assuming your AudioTrack has a 'data' or 'filePath' string property.
        // Change 'track.data' to whatever you named the absolute path string in your model.
        val rawPath = track.id ?: return null

        val audioFile = File(rawPath)
        if (!audioFile.exists()) return null

        val parentDir = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        // Look for matching files in the exact same folder
        val extensions = listOf(".lrc", ".txt")

        for (ext in extensions) {
            val lyricsFile = File(parentDir, "$baseName$ext")
            if (lyricsFile.exists()) {
                return try {
                    lyricsFile.readText()
                } catch (e: Exception) {
                    Log.e("LyricsRepo", "Failed to read local lyrics file", e)
                    null
                }
            }
        }
        return null
    }
}