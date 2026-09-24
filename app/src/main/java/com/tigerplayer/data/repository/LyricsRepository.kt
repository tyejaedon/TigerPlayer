package com.tigerplayer.data.repository

import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.LyricsCacheEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.remote.api.LrclibApi
import com.tigerplayer.utils.ArtistUtils
import com.tigerplayer.utils.MusicMetadataSearch.cleanSearchTerm
import com.tigerplayer.utils.MusicMetadataSearch.meaningfulAlbumName
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class LyricsRepository @Inject constructor(
    private val lrclibApi: LrclibApi,
    private val tigerDao: TigerDao
) {
    private data class LyricsLookupQuery(
        val trackName: String,
        val artistName: String,
        val albumName: String?
    )

    fun getLyrics(track: AudioTrack): Flow<String?> = flow {
        // 1. CHECK THE LOCAL ARCHIVES FIRST
        val cached = tigerDao.getLyricsCache(track.id)

        if (cached != null && (!cached.syncedLyrics.isNullOrBlank() || !cached.plainLyrics.isNullOrBlank())) {
            // Update the timestamp so it isn't deleted during cleanup
            tigerDao.updateLyricsAccessTime(track.id)

            // Prefer synced lyrics, fallback to plain
            emit(cached.syncedLyrics ?: cached.plainLyrics)
            return@flow
        }

        // 2. CACHE MISS: CONSULT LRCLIB
        val lookupQueries = buildLookupQueries(track)

        // 🔥 THE FIX: Isolate the try-catch block from the emit function to prevent Flow crashes
        val fetchedLyrics = try {
            fetchLyricsFromRemote(track.id, lookupQueries)
        } catch (e: Exception) {
            // 🔥 THE FIX: Allow coroutine cancellations to pass through silently
            if (e is CancellationException) throw e

            Log.e(TAG, "Failed to fetch lyrics from LRCLIB", e)
            null
        }

        // 🔥 THE FIX: Safely emit only after all try/catch blocks are resolved
        emit(fetchedLyrics)

    }.flowOn(Dispatchers.IO)

    private suspend fun fetchLyricsFromRemote(
        trackId: String,
        lookupQueries: List<LyricsLookupQuery>
    ): String? {
        for ((index, query) in lookupQueries.withIndex()) {
            val response = lrclibApi.getLyrics(
                trackName = query.trackName,
                artistName = query.artistName,
                albumName = query.albumName
            )

            if (!response.isSuccessful) {
                Log.w(
                    TAG,
                    "LRCLIB lookup variant ${index + 1}/${lookupQueries.size} failed with HTTP ${response.code()}."
                )
                continue
            }

            val data = response.body()
            val synced = data?.syncedLyrics
            val plain = data?.plainLyrics

            if (synced.isNullOrBlank() && plain.isNullOrBlank()) {
                continue
            }

            tigerDao.insertLyricsCache(
                LyricsCacheEntity(
                    trackId = trackId,
                    plainLyrics = plain,
                    syncedLyrics = synced
                )
            )
            tigerDao.enforceLyricsCacheLimit()
            return synced ?: plain
        }

        return null
    }

    private fun buildLookupQueries(track: AudioTrack): List<LyricsLookupQuery> {
        val rawTitle = track.title.trim()
        val rawArtist = track.artist.trim()
        val rawAlbum = meaningfulAlbumName(track.album)
        val cleanedTitle = cleanSearchTerm(rawTitle)
        val cleanedArtist = ArtistUtils.getBaseArtist(rawArtist).trim()
        val cleanedAlbum = meaningfulAlbumName(track.album)

        return listOf(
            LyricsLookupQuery(rawTitle, rawArtist, rawAlbum),
            LyricsLookupQuery(rawTitle, cleanedArtist, rawAlbum),
            LyricsLookupQuery(rawTitle, rawArtist, null),
            LyricsLookupQuery(cleanedTitle, rawArtist, cleanedAlbum),
            LyricsLookupQuery(cleanedTitle, cleanedArtist, cleanedAlbum),
            LyricsLookupQuery(cleanedTitle, cleanedArtist, null)
        ).filter { it.trackName.isNotBlank() && it.artistName.isNotBlank() }
            .distinct()
    }

    suspend fun clearLyricsCache() {
        tigerDao.clearAllLyrics()
    }

    private companion object {
        const val TAG = "LyricsRepo"
    }
}