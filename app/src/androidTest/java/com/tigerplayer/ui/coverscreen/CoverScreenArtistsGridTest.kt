package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.model.ArtistSource
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoverScreenArtistsGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingAnArtistShowsItsAlbumsAndBackReturnsToArtists() {
        val artist = Artist(
            id = "artist-1",
            name = "Sample Artist",
            source = ArtistSource.LOCAL,
            albumCount = 1
        )

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenArtistsGrid(
                    artists = listOf(artist),
                    albums = listOf(
                        CoverScreenAlbum(
                            id = "album-1",
                            artistId = artist.id,
                            name = "Sample Album",
                            trackCount = 8
                        )
                    )
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.artist(artist.id)).performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.album("album-1")).assertIsDisplayed()
        composeRule.onNodeWithTag(CoverScreenTestTags.ARTIST_BACK).performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.artist(artist.id)).assertIsDisplayed()
    }

    @Test
    fun selectingAnAlbumReportsItToTheCaller() {
        var selectedAlbum: CoverScreenAlbum? = null
        val artist = Artist("artist-1", "Sample Artist", ArtistSource.SPOTIFY, albumCount = 1)
        val album = CoverScreenAlbum("album-1", artist.id, "Sample Album", trackCount = 8)

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenArtistsGrid(
                    artists = listOf(artist),
                    albums = listOf(album),
                    onAlbumSelected = { selectedAlbum = it }
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.artist(artist.id)).performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.album(album.id)).performClick()

        composeRule.runOnIdle { assertEquals(album, selectedAlbum) }
    }
}
