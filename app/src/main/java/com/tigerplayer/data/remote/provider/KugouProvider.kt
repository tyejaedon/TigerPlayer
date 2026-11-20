package com.tigerplayer.data.remote.provider

import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.remote.api.KugouApi
import com.tigerplayer.utils.ArtistUtils
import com.tigerplayer.utils.LrcParser
import com.tigerplayer.utils.MusicMetadataSearch.cleanSearchTerm
import kotlinx.coroutines.CancellationException
import java.util.Base64
import javax.inject.Inject

class KugouProvider @Inject constructor(
    private val kugouApi: KugouApi
) : LyricsProvider {
    override val priority: Int = 40
    override val name: String = "Kugou"

    override suspend fun getLyrics(track: AudioTrack): Lyrics {
        try {
            val query = "${ArtistUtils.getBaseArtist(track.artist).trim()} ${cleanSearchTerm(track.title)}".trim()
            if (query.isBlank()) return Lyrics.None

            val searchResponse = kugouApi.searchLyrics(
                keyword = query,
                durationMs = if (track.durationMs > 0) track.durationMs else null
            )

            if (searchResponse.isSuccessful) {
                val candidates = searchResponse.body()?.candidates
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
                                    return parsed
                                } else if (parsed is Lyrics.Plain) {
                                    return parsed
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        return Lyrics.None
    }

    private fun decodeBase64(base64Content: String): String? {
        return runCatching {
            val sanitized = base64Content.trim().replace("\r", "").replace("\n", "")
            val bytes = Base64.getDecoder().decode(sanitized)
            String(bytes, Charsets.UTF_8)
        }.getOrNull()
    }
}
