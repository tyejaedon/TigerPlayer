package com.tigerplayer.data.remote.provider

import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.remote.api.NeteaseApi
import com.tigerplayer.utils.ArtistUtils
import com.tigerplayer.utils.LrcParser
import com.tigerplayer.utils.MusicMetadataSearch.cleanSearchTerm
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlin.math.abs

class NeteaseProvider @Inject constructor(
    private val neteaseApi: NeteaseApi
) : LyricsProvider {
    override val priority: Int = 30
    override val name: String = "Netease"

    override suspend fun getLyrics(track: AudioTrack): Lyrics {
        try {
            val query = "${ArtistUtils.getBaseArtist(track.artist).trim()} ${cleanSearchTerm(track.title)}".trim()
            if (query.isBlank()) return Lyrics.None

            val searchResponse = neteaseApi.search(query = query)
            if (!searchResponse.isSuccessful) return Lyrics.None

            val songs = searchResponse.body()?.result?.songs
            if (songs.isNullOrEmpty()) return Lyrics.None

            // Find best match by duration if possible
            val bestMatch = if (track.durationMs > 0) {
                songs.minByOrNull { abs((it.duration ?: 0L) - track.durationMs) } ?: songs.first()
            } else {
                songs.first()
            }

            val lyricResponse = neteaseApi.getLyrics(songId = bestMatch.id)
            if (lyricResponse.isSuccessful) {
                val data = lyricResponse.body()

                val lrcText = data?.lrc?.lyric
                val tlyricText = data?.tlyric?.lyric // Translations

                if (!lrcText.isNullOrBlank()) {
                    // We can combine original + translation lines for the parser to merge
                    val combined = buildString {
                        append(lrcText)
                        if (!tlyricText.isNullOrBlank()) {
                            append("\n")
                            append(tlyricText)
                        }
                    }
                    val parsed = LrcParser.parse(combined)
                    if (parsed is Lyrics.Synced) {
                        return parsed
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        return Lyrics.None
    }
}
