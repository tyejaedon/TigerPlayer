package com.tigerplayer.ui.coverscreen

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.model.ArtistSource
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoverScreenSearchResultsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val track = AudioTrack(
        id = "track-1",
        title = "Blue Train",
        artist = "John Coltrane",
        album = "Blue Train",
        uri = Uri.parse("content://tracks/1"),
        artworkUri = Uri.EMPTY,
        durationMs = 60000,
        mimeType = "audio/mpeg"
    )

    @Test
    fun selectingTrackFilterShowsMatchesAndSupportsPlayAndQueue() {
        var played: AudioTrack? = null
        var queued: AudioTrack? = null

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSearchResults(
                    query = "blue",
                    artists = emptyList(),
                    albums = emptyList(),
                    tracks = listOf(track),
                    onArtistSelected = {},
                    onAlbumSelected = {},
                    onPlayTrack = { played = it },
                    onQueueTrack = { queued = it }
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.searchResultType("TRACKS")).performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.searchResultTrack(track.id))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.searchResultQueue(track.id)).performClick()

        composeRule.runOnIdle {
            assertEquals(track, played)
            assertEquals(track, queued)
        }
    }

    @Test
    fun artistAndAlbumMatchesAreDisplayedAndSelectable() {
        val artist = Artist(
            id = "artist-1",
            name = "John Coltrane",
            source = ArtistSource.LOCAL,
            albumCount = 1
        )
        val album = CoverScreenAlbum(
            id = "album-1",
            artistId = artist.id,
            name = "Blue Train",
            trackCount = 5
        )
        var selectedArtist: Artist? = null
        var selectedAlbum: CoverScreenAlbum? = null

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSearchResults(
                    query = "blue",
                    artists = listOf(artist),
                    albums = listOf(album),
                    tracks = emptyList(),
                    onArtistSelected = { selectedArtist = it },
                    onAlbumSelected = { selectedAlbum = it },
                    onPlayTrack = {},
                    onQueueTrack = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.searchResultArtist(artist.id))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.searchResultAlbum(album.id))
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(artist, selectedArtist)
            assertEquals(album, selectedAlbum)
        }
    }

    @Test
    fun noMatchingResultsShowAnAccessibleEmptyState() {
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSearchResults(
                    query = "missing",
                    artists = emptyList(),
                    albums = emptyList(),
                    tracks = listOf(track),
                    onArtistSelected = {},
                    onAlbumSelected = {},
                    onPlayTrack = {},
                    onQueueTrack = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.SEARCH_NO_RESULTS)
            .assertIsDisplayed()
    }
}
