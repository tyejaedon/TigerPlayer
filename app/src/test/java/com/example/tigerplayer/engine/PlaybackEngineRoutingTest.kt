package com.example.tigerplayer.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackEngineRoutingTest {

    @Test
    fun targetForTrackId_routesSpotifyTrackToSpotify() {
        val target = PlaybackEngineRouting.targetForTrackId("spotify:abc")

        assertEquals(PlaybackTarget.SPOTIFY, target)
    }

    @Test
    fun targetForTrackId_routesNullBlankAndLocalToLocal() {
        val nullTarget = PlaybackEngineRouting.targetForTrackId(trackId = null)
        val blankTarget = PlaybackEngineRouting.targetForTrackId(trackId = "")
        val localTarget = PlaybackEngineRouting.targetForTrackId(trackId = "local_1")

        assertEquals(PlaybackTarget.LOCAL, nullTarget)
        assertEquals(PlaybackTarget.LOCAL, blankTarget)
        assertEquals(PlaybackTarget.LOCAL, localTarget)
    }

    @Test
    fun targetForTrackId_routesByIdPrefix() {
        val spotifyTarget = PlaybackEngineRouting.targetForTrackId(trackId = "spotify:queued")
        val localTarget = PlaybackEngineRouting.targetForTrackId(trackId = "queued_local")

        assertEquals(PlaybackTarget.SPOTIFY, spotifyTarget)
        assertEquals(PlaybackTarget.LOCAL, localTarget)
    }

    @Test
    fun playlistStartIndex_returnsTrackIndexWhenPresent() {
        val libraryIds = listOf("one", "two", "three")

        val startIndex = PlaybackEngineRouting.playlistStartIndex(trackId = "two", libraryTrackIds = libraryIds)

        assertEquals(1, startIndex)
    }

    @Test
    fun playlistStartIndex_fallsBackToZeroWhenTrackMissing() {
        val libraryIds = listOf("one", "two")

        val startIndex = PlaybackEngineRouting.playlistStartIndex(trackId = "missing", libraryTrackIds = libraryIds)

        assertEquals(0, startIndex)
    }
}

