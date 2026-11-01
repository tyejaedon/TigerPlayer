package com.tigerplayer.data.repository

import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.LyricsCacheEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.remote.api.KugouApi
import com.tigerplayer.data.remote.api.LrclibApi
import com.tigerplayer.data.source.LocalLyricsSource
import com.tigerplayer.utils.ArtistUtils
import com.tigerplayer.utils.LrcParser
import com.tigerplayer.utils.MusicMetadataSearch.cleanSearchTerm
import com.tigerplayer.utils.MusicMetadataSearch.meaningfulAlbumName
import android.util.Log
import java.util.Base64
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
    private val kugouApi: KugouApi,
    private val tigerDao: TigerDao,
    private val localLyricsSource: LocalLyricsSource
) {
    private data class LyricsLookupQuery(
        val trackName: String,
        val artistName: String,
        val albumName: String?
    )

    fun getLyrics(track: AudioTrack): Flow<Lyrics> = flow {
        // 1. CHECK LOCAL .LRC SIDECAR FILES FIRST (highest precedence)
        val sidecarLyrics = localLyricsSource.getSidecarLyrics(track)
        if (sidecarLyrics !is Lyrics.None) {
            emit(sidecarLyrics)
            return@flow
        }

        // 2. CHECK EMBEDDED AUDIO METADATA (SYLT / USLT / Vorbis)
        val embeddedLyrics = localLyricsSource.getEmbeddedLyrics(track)
        if (embeddedLyrics !is Lyrics.None) {
            emit(embeddedLyrics)
            return@flow
        }

        // 3. CHECK THE LOCAL ROOM ARCHIVES
        val cached = tigerDao.getLyricsCache(track.id)

        if (cached != null) {
            val hasLyrics = !cached.syncedLyrics.isNullOrBlank() || !cached.plainLyrics.isNullOrBlank()
            if (hasLyrics) {
                // Update the timestamp so it isn't deleted during cleanup
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

        // 4. CACHE MISS: CONSULT REMOTE PROVIDERS (LRCLIB + KUGOU)
        val lookupQueries = buildLookupQueries(track)

        val fetchedLyrics = try {
            fetchLyricsFromRemote(track, lookupQueries)
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Log.e(TAG, "Failed to fetch lyrics from remote providers", e)
            cacheNegativeLyrics(track.id)
            Lyrics.None
        }

        emit(fetchedLyrics)

    }.flowOn(Dispatchers.IO)

    private suspend fun fetchLyricsFromRemote(
        track: AudioTrack,
        lookupQueries: List<LyricsLookupQuery>
    ): Lyrics {
        var fallbackPlain: String? = null

        // Pass 1: Try LRCLIB exact getLyrics queries
        for ((index, query) in lookupQueries.withIndex()) {
            val response = try {
                lrclibApi.getLyrics(
                    trackName = query.trackName,
                    artistName = query.artistName,
                    albumName = query.albumName
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "LRCLIB lookup variant ${index + 1} exception: ${e.message}")
                null
            }

            if (response != null && response.isSuccessful) {
                val data = response.body()
                val synced = data?.syncedLyrics
                val plain = data?.plainLyrics

                if (!synced.isNullOrBlank()) {
                    val parsed = LrcParser.parse(synced)
                    if (parsed is Lyrics.Synced) {
                        cacheLyrics(track.id, plainLyrics = plain, syncedLyrics = synced)
                        return parsed
                    }
                }
                if (!plain.isNullOrBlank() && fallbackPlain == null) {
                    fallbackPlain = plain
                }
            } else if (response != null && response.code() >= 500) {
                break
            }
        }

        // Pass 2: If no synced lyrics yet, try LRCLIB search
        try {
            val searchResponse = lrclibApi.searchLyrics(
                trackName = cleanSearchTerm(track.title),
                artistName = ArtistUtils.getBaseArtist(track.artist).trim()
            )
            if (searchResponse.isSuccessful) {
                val results = searchResponse.body()
                val syncedMatch = results?.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
                if (syncedMatch?.syncedLyrics != null) {
                    val parsed = LrcParser.parse(syncedMatch.syncedLyrics)
                    if (parsed is Lyrics.Synced) {
                        cacheLyrics(
                            track.id,
                            plainLyrics = syncedMatch.plainLyrics ?: fallbackPlain,
                            syncedLyrics = syncedMatch.syncedLyrics
                        )
                        return parsed
                    }
                }
                if (fallbackPlain == null) {
                    val plainMatch = results?.firstOrNull { !it.plainLyrics.isNullOrBlank() }
                    fallbackPlain = plainMatch?.plainLyrics
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "LRCLIB search fallback failed: ${e.message}")
        }

        // Pass 3: If still no synced lyrics, consult Kugou secondary provider
        try {
            val kugouQuery = "${ArtistUtils.getBaseArtist(track.artist).trim()} ${cleanSearchTerm(track.title)}".trim()
            if (kugouQuery.isNotBlank()) {
                val kugouSearch = kugouApi.searchLyrics(
                    keyword = kugouQuery,
                    durationMs = if (track.durationMs > 0) track.durationMs else null
                )
                if (kugouSearch.isSuccessful) {
                    val candidates = kugouSearch.body()?.candidates
                    if (!candidates.isNullOrEmpty()) {
                        val candidate = if (track.durationMs > 0) {
                            candidates.minByOrNull { Math.abs((it.duration ?: 0L) - track.durationMs) }
                                ?: candidates.first()
                        } else {
                            candidates.first()
                        }

                        val downloadRes = kugouApi.downloadLyrics(id = candidate.id, accessKey = candidate.accesskey)
                        if (downloadRes.isSuccessful) {
                            val base64Content = downloadRes.body()?.content
                            if (!base64Content.isNullOrBlank()) {
                                val decodedLrc = decodeBase64(base64Content)
                                if (!decodedLrc.isNullOrBlank()) {
                                    val parsed = LrcParser.parse(decodedLrc)
                                    if (parsed is Lyrics.Synced) {
                                        cacheLyrics(
                                            track.id,
                                            plainLyrics = fallbackPlain,
                                            syncedLyrics = decodedLrc
                                        )
                                        return parsed
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Kugou secondary lyrics lookup failed: ${e.message}")
        }

        // Pass 4: Fall back to plain lyrics if found from any provider
        if (!fallbackPlain.isNullOrBlank()) {
            cacheLyrics(track.id, plainLyrics = fallbackPlain, syncedLyrics = null)
            return Lyrics.Plain(fallbackPlain)
        }

        // Pass 5: Complete miss across all sources -> cache negative miss
        cacheNegativeLyrics(track.id)
        return Lyrics.None
    }

    private fun decodeBase64(base64Content: String): String? {
        return runCatching {
            val sanitized = base64Content.trim().replace("\r", "").replace("\n", "")
            val bytes = Base64.getDecoder().decode(sanitized)
            String(bytes, Charsets.UTF_8)
        }.getOrNull()
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
        const val NEGATIVE_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
