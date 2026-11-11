package com.tigerplayer.data.repository

import android.util.Log
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.LyricsCacheEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.remote.provider.KugouProvider
import com.tigerplayer.data.remote.provider.LocalLyricsProvider
import com.tigerplayer.data.remote.provider.LrclibProvider
import com.tigerplayer.data.remote.provider.LyricsProvider
import com.tigerplayer.data.remote.provider.NeteaseProvider
import com.tigerplayer.utils.LrcParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val tigerDao: TigerDao,
    private val localLyricsProvider: LocalLyricsProvider,
    lrclibProvider: LrclibProvider,
    neteaseProvider: NeteaseProvider,
    kugouProvider: KugouProvider
) {
    private val remoteProviders: List<LyricsProvider> = listOf(
        lrclibProvider,
        neteaseProvider,
        kugouProvider
    ).sortedBy { it.priority }

    fun getLyrics(track: AudioTrack): Flow<Lyrics> = flow {
        // 1. LOCAL PROVIDERS (Sidecar / Embedded)
        // We bypass the generic provider loop for LocalLyricsProvider to ensure it has absolute
        // precedence over the Room cache without polluting the cache logic.
        val localLyrics = localLyricsProvider.getLyrics(track)
        if (localLyrics !is Lyrics.None) {
            emit(localLyrics)
            return@flow
        }

        // 2. CHECK THE LOCAL ROOM ARCHIVES
        val cached = tigerDao.getLyricsCache(track.id)
        if (cached != null) {
            val hasLyrics = !cached.syncedLyrics.isNullOrBlank() || !cached.plainLyrics.isNullOrBlank()
            if (hasLyrics) {
                tigerDao.updateLyricsAccessTime(track.id)
                val parsed = if (!cached.syncedLyrics.isNullOrBlank()) {
                    val parsedSynced = LrcParser.parse(cached.syncedLyrics)
                    if (parsedSynced is Lyrics.Synced) {
                        parsedSynced
                    } else if (!cached.plainLyrics.isNullOrBlank()) {
                        Lyrics.Plain(cached.plainLyrics)
                    } else {
                        parsedSynced
                    }
                } else {
                    Lyrics.Plain(cached.plainLyrics!!)
                }
                emit(parsed)
                return@flow
            }

            if (System.currentTimeMillis() - cached.lastAccessed < NEGATIVE_CACHE_TTL_MS) {
                emit(Lyrics.None)
                return@flow
            }
        }

        // 3. CACHE MISS: CONSULT REMOTE PROVIDERS IN PRIORITY ORDER
        var fallbackPlain: String? = null

        for (provider in remoteProviders) {
            try {
                val lyrics = provider.getLyrics(track)
                if (lyrics is Lyrics.Synced) {
                    val rawLrc = generateRawLrc(lyrics) // Simple serialization for caching
                    cacheLyrics(track.id, plainLyrics = fallbackPlain, syncedLyrics = rawLrc)
                    emit(lyrics)
                    return@flow
                } else if (lyrics is Lyrics.Plain && fallbackPlain == null) {
                    fallbackPlain = lyrics.text
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Provider ${provider.name} failed: ${e.message}")
            }
        }

        // 3. Fall back to plain lyrics if found from any provider
        if (!fallbackPlain.isNullOrBlank()) {
            cacheLyrics(track.id, plainLyrics = fallbackPlain, syncedLyrics = null)
            emit(Lyrics.Plain(fallbackPlain))
            return@flow
        }

        // 4. Complete miss across all sources -> cache negative miss
        cacheNegativeLyrics(track.id)
        emit(Lyrics.None)

    }.flowOn(Dispatchers.IO)

    private fun generateRawLrc(synced: Lyrics.Synced): String {
        return buildString {
            if (synced.offsetMs != 0L) {
                appendLine("[offset:${synced.offsetMs}]")
            }
            for (line in synced.lines) {
                val min = line.timeMs / 60000
                val sec = (line.timeMs % 60000) / 1000
                val ms = line.timeMs % 1000
                val timeStr = String.format(java.util.Locale.US, "[%02d:%02d.%03d]", min, sec, ms)
                append(timeStr)

                if (synced.hasWordLevelTiming && line.words.isNotEmpty()) {
                    for (word in line.words) {
                        val wMin = word.startTimeMs / 60000
                        val wSec = (word.startTimeMs % 60000) / 1000
                        val wMs = word.startTimeMs % 1000
                        val wTimeStr = String.format(java.util.Locale.US, "<%02d:%02d.%03d>", wMin, wSec, wMs)
                        append(wTimeStr).append(word.text)
                    }
                    appendLine()
                } else {
                    appendLine(line.text)
                }

                if (line.translation != null) {
                    appendLine("$timeStr${line.translation}")
                }
            }
        }
    }

    private suspend fun cacheLyrics(trackId: String, plainLyrics: String?, syncedLyrics: String?) {
        tigerDao.insertLyricsCache(
            LyricsCacheEntity(
                trackId = trackId,
                plainLyrics = plainLyrics,
                syncedLyrics = syncedLyrics
            )
        )
        tigerDao.enforceLyricsCacheLimit()
    }

    private suspend fun cacheNegativeLyrics(trackId: String) {
        tigerDao.insertLyricsCache(
            LyricsCacheEntity(
                trackId = trackId,
                plainLyrics = null,
                syncedLyrics = null
            )
        )
        tigerDao.enforceLyricsCacheLimit()
    }

    suspend fun clearLyricsCache() {
        tigerDao.clearAllLyrics()
    }

    private companion object {
        const val TAG = "LyricsRepo"
        const val NEGATIVE_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
