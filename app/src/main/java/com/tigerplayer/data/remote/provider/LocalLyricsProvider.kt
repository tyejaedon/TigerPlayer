package com.tigerplayer.data.remote.provider

import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.source.LocalLyricsSource
import javax.inject.Inject

class LocalLyricsProvider @Inject constructor(
    private val localLyricsSource: LocalLyricsSource
) : LyricsProvider {
    override val priority: Int = 10
    override val name: String = "Local"

    override suspend fun getLyrics(track: AudioTrack): Lyrics {
        val sidecarLyrics = localLyricsSource.getSidecarLyrics(track)
        if (sidecarLyrics !is Lyrics.None) {
            return sidecarLyrics
        }
        val embeddedLyrics = localLyricsSource.getEmbeddedLyrics(track)
        if (embeddedLyrics !is Lyrics.None) {
            return embeddedLyrics
        }
        return Lyrics.None
    }
}
