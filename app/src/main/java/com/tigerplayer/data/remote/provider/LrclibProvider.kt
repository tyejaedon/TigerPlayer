package com.tigerplayer.data.remote.provider

import android.util.Log
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.remote.api.LrclibApi
import com.tigerplayer.utils.ArtistUtils
import com.tigerplayer.utils.LrcParser
import com.tigerplayer.utils.MusicMetadataSearch.cleanSearchTerm
import com.tigerplayer.utils.MusicMetadataSearch.meaningfulAlbumName
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class LrclibProvider @Inject constructor(
    private val lrclibApi: LrclibApi
) : LyricsProvider {
    override val priority: Int = 20
    override val name: String = "LRCLIB"

    private data class LyricsLookupQuery(
        val trackName: String,
        val artistName: String,
        val albumName: String?
    )

    override suspend fun getLyrics(track: AudioTrack): Lyrics {
        val rawTitle = track.title.trim()
        val rawArtist = track.artist.trim()
        val rawAlbum = meaningfulAlbumName(track.album)
        val cleanedTitle = cleanSearchTerm(rawTitle)
        val cleanedArtist = ArtistUtils.getBaseArtist(rawArtist).trim()
        val cleanedAlbum = meaningfulAlbumName(track.album)

        val queries = listOf(
            LyricsLookupQuery(rawTitle, rawArtist, rawAlbum),
            LyricsLookupQuery(rawTitle, cleanedArtist, rawAlbum),
            LyricsLookupQuery(rawTitle, rawArtist, null),
            LyricsLookupQuery(cleanedTitle, rawArtist, cleanedAlbum),
            LyricsLookupQuery(cleanedTitle, cleanedArtist, cleanedAlbum),
            LyricsLookupQuery(cleanedTitle, cleanedArtist, null)
        ).filter { it.trackName.isNotBlank() && it.artistName.isNotBlank() }.distinct()

        var fallbackPlain: String? = null

        for ((index, query) in queries.withIndex()) {
            val response = try {
                lrclibApi.getLyrics(
                    trackName = query.trackName,
                    artistName = query.artistName,
                    albumName = query.albumName
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }

            if (response != null && response.isSuccessful) {
                val data = response.body()
                val synced = data?.syncedLyrics
                val plain = data?.plainLyrics

                if (!synced.isNullOrBlank()) {
                    val parsed = LrcParser.parse(synced)
                    if (parsed is Lyrics.Synced) {
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
        }

        if (!fallbackPlain.isNullOrBlank()) {
            return Lyrics.Plain(fallbackPlain)
        }

        return Lyrics.None
    }
}
