package com.tigerplayer.data.remote.provider

import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics

/**
 * Pluggable provider for fetching lyrics from different sources (local, embedded, remote APIs).
 */
interface LyricsProvider {
    /**
     * The priority of this provider. Lower value = higher priority.
     */
    val priority: Int

    /**
     * The name of the provider (e.g., "LRCLIB", "Netease", "Local").
     */
    val name: String

    /**
     * Attempt to fetch lyrics for the given [track].
     * Should return [Lyrics.None] if no lyrics are found, or if an error occurs and it should be ignored.
     */
    suspend fun getLyrics(track: AudioTrack): Lyrics
}
