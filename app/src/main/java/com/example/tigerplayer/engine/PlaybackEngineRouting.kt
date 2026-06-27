package com.example.tigerplayer.engine

import com.example.tigerplayer.data.model.AudioTrack

internal enum class PlaybackTarget {
    LOCAL,
    SPOTIFY
}

internal object PlaybackEngineRouting {

    fun targetForTrackId(trackId: String?): PlaybackTarget {
        return if (!trackId.isNullOrBlank() && trackId.startsWith("spotify:")) {
            PlaybackTarget.SPOTIFY
        } else {
            PlaybackTarget.LOCAL
        }
    }

    fun targetForCurrentTrack(currentTrack: AudioTrack?): PlaybackTarget {
        return targetForTrackId(currentTrack?.id)
    }

    fun targetForQueueTrack(track: AudioTrack): PlaybackTarget {
        return targetForTrackId(track.id)
    }

    fun playlistStartIndex(trackId: String, libraryTrackIds: List<String>): Int {
        return libraryTrackIds.indexOf(trackId).coerceAtLeast(0)
    }

    fun playlistStartIndex(track: AudioTrack, libraryTracks: List<AudioTrack>): Int {
        return playlistStartIndex(track.id, libraryTracks.map { it.id })
    }
}

