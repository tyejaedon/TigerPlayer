package com.example.tigerplayer.data.repository

import android.util.Log
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.LyricsCacheEntity
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.remote.api.LrclibApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val lrclibApi: LrclibApi,
    private val tigerDao: TigerDao
) {
    fun getLyrics(track: AudioTrack): Flow<String?> = flow {
        // 1. CHECK THE LOCAL ARCHIVES FIRST
        val cached = tigerDao.getLyricsCache(track.id)

        if (cached != null && (!cached.syncedLyrics.isNullOrBlank() || !cached.plainLyrics.isNullOrBlank())) {
            Log.d("LyricsRepo", "Cache hit for ${track.title}")
            // Update the timestamp so it isn't deleted during cleanup
            tigerDao.updateLyricsAccessTime(track.id)

            // Prefer synced lyrics, fallback to plain
            emit(cached.syncedLyrics ?: cached.plainLyrics)
            return@flow
        }

        // 2. CACHE MISS: CONSULT LRCLIB
        Log.d("LyricsRepo", "Cache miss. Hunting LRCLIB for ${track.title}...")

        // 🔥 THE FIX: Isolate the try-catch block from the emit function to prevent Flow crashes
        val fetchedLyrics = try {
            val response = lrclibApi.getLyrics(
                trackName = track.title,
                artistName = track.artist,
                albumName = track.album
            )

            if (response.isSuccessful) {
                val data = response.body()
                val synced = data?.syncedLyrics
                val plain = data?.plainLyrics

                // 3. STORE THE LOOT
                if (!synced.isNullOrBlank() || !plain.isNullOrBlank()) {
                    tigerDao.insertLyricsCache(
                        LyricsCacheEntity(
                            trackId = track.id,
                            plainLyrics = plain,
                            syncedLyrics = synced
                        )
                    )

                    // 4. ENFORCE THE 10MB LIMIT (The Janitor)
                    tigerDao.enforceLyricsCacheLimit()

                    synced ?: plain
                } else {
                    null // No lyrics exist on LRCLIB
                }
            } else {
                null
            }
        } catch (e: Exception) {
            // 🔥 THE FIX: Allow coroutine cancellations to pass through silently
            if (e is CancellationException) throw e

            Log.e("LyricsRepo", "Failed to fetch lyrics: ${e.message}")
            null
        }

        // 🔥 THE FIX: Safely emit only after all try/catch blocks are resolved
        emit(fetchedLyrics)

    }.flowOn(Dispatchers.IO)

    suspend fun clearLyricsCache() {
        tigerDao.clearAllLyrics()
    }
}