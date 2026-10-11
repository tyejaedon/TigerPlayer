package com.tigerplayer.ui.coverscreen

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoverScreenAlbumsViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingATrackRequestsPlayback() {
        val track = sampleTrack()
        var played: AudioTrack? = null

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenAlbumsView(
                    album = sampleAlbum(),
                    artistName = "Sample Artist",
                    tracks = listOf(track),
                    onBack = {},
                    onPlayTrack = { played = it },
                    onAddToQueue = {},
                    onRemoveFromQueue = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.track(track.id)).performClick()

        composeRule.runOnIdle { assertEquals(track, played) }
    }

    @Test
    fun swipeLeftRevealsRemoveFromQueueAction() {
        val track = sampleTrack()
        var removed: AudioTrack? = null

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenAlbumsView(
                    album = sampleAlbum(),
                    artistName = "Sample Artist",
                    tracks = listOf(track),
                    onBack = {},
                    onPlayTrack = {},
                    onAddToQueue = {},
                    onRemoveFromQueue = { removed = it }
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.track(track.id))
            .performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(CoverScreenTestTags.removeTrack(track.id))
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { assertEquals(track, removed) }
    }

    private fun sampleAlbum() = CoverScreenAlbum(
        id = "album-1",
        artistId = "artist-1",
        name = "Sample Album",
        trackCount = 1
    )

    private fun sampleTrack() = AudioTrack(
        id = "track-1",
        title = "Sample Track",
        artist = "Sample Artist",
        album = "Sample Album",
        uri = Uri.parse("content://audio/track-1"),
        artworkUri = Uri.EMPTY,
        durationMs = 185_000L,
        mimeType = "audio/mpeg"
    )
}
