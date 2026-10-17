package com.tigerplayer.ui.coverscreen

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.engine.CoverScreenStatsSnapshot
import com.tigerplayer.ui.dashboard.CurationFeedMeta
import com.tigerplayer.ui.dashboard.DashboardViewModel
import com.tigerplayer.ui.player.PlayerUiState
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.theme.TigerPlayerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverScreenAccessibilityInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleTrack(id: String = "1") = AudioTrack(
        id = id,
        title = "Accessibility Track",
        artist = "Accessible Artist",
        album = "Accessible Album",
        uri = Uri.parse("content://test/$id"),
        artworkUri = Uri.EMPTY,
        durationMs = 195_000L,
        mimeType = "audio/mpeg"
    )

    private fun mockPlayerViewModel(isPlaying: Boolean = true): PlayerViewModel {
        val playerViewModel = mockk<PlayerViewModel>(relaxed = true)
        val uiState = PlayerUiState(
            isPlaying = isPlaying,
            currentTrack = sampleTrack()
        )
        every { playerViewModel.uiState } returns MutableStateFlow(uiState)
        every { playerViewModel.coverScreenStats } returns MutableStateFlow(CoverScreenStatsSnapshot())
        every { playerViewModel.sonicFootprintOpenRequest } returns MutableStateFlow(false)
        return playerViewModel
    }

    private fun mockDashboardViewModel(): DashboardViewModel {
        val dashboardViewModel = mockk<DashboardViewModel>(relaxed = true)
        every { dashboardViewModel.daylistTracks } returns MutableStateFlow(emptyList())
        every { dashboardViewModel.discoveryWeeklyTracks } returns MutableStateFlow(emptyList())
        every { dashboardViewModel.daylistMeta } returns MutableStateFlow(CurationFeedMeta(isLoading = false))
        every { dashboardViewModel.discoveryMeta } returns MutableStateFlow(CurationFeedMeta(isLoading = false))
        return dashboardViewModel
    }

    @Test
    fun accessibleControls_haveMinimum48dpTouchTargets_andRoleButton() {
        var playPauseClicked = false
        var settingsLongClicked = false
        var prevClicked = false
        var nextClicked = false
        var queueClicked = false
        var libraryClicked = false

        composeRule.setContent {
            TigerPlayerTheme {
                Box(modifier = Modifier.size(360.dp, 100.dp)) {
                    CoverAccessibleControls(
                        isPlaying = true,
                        onTogglePlayPause = { playPauseClicked = true },
                        onLongPressPlayPause = { settingsLongClicked = true },
                        onPrevious = { prevClicked = true },
                        onNext = { nextClicked = true },
                        onOpenQueue = { queueClicked = true },
                        onOpenLibrary = { libraryClicked = true }
                    )
                }
            }
        }

        // Verify Play/Pause (Playback settings open) button
        val playPauseNode = composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_SETTINGS_OPEN)
        playPauseNode.assertIsDisplayed()
        playPauseNode.assertWidthIsAtLeast(48.dp)
        playPauseNode.assertHeightIsAtLeast(48.dp)

        val playPauseSemantics = playPauseNode.fetchSemanticsNode()
        val playPauseRole = if (SemanticsProperties.Role in playPauseSemantics.config) {
            playPauseSemantics.config[SemanticsProperties.Role]
        } else null
        assertEquals(Role.Button, playPauseRole)

        // Verify Library button
        val libraryNode = composeRule.onNodeWithTag(CoverScreenTestTags.LIBRARY_OPEN)
        libraryNode.assertIsDisplayed()
        libraryNode.assertWidthIsAtLeast(48.dp)
        libraryNode.assertHeightIsAtLeast(48.dp)

        val librarySemantics = libraryNode.fetchSemanticsNode()
        val libraryRole = if (SemanticsProperties.Role in librarySemantics.config) {
            librarySemantics.config[SemanticsProperties.Role]
        } else null
        assertEquals(Role.Button, libraryRole)

        // Perform interactions
        playPauseNode.performClick()
        composeRule.runOnIdle { assertTrue(playPauseClicked) }

        playPauseNode.performTouchInput { longClick() }
        composeRule.runOnIdle { assertTrue(settingsLongClicked) }

        libraryNode.performClick()
        composeRule.runOnIdle { assertTrue(libraryClicked) }
    }

    @Test
    fun miniHubRoot_declaresAllSixCustomAccessibilityActions_andStateDescription() {
        val playerViewModel = mockPlayerViewModel(isPlaying = true)
        val dashboardViewModel = mockDashboardViewModel()

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenMiniHub(
                    playerViewModel = playerViewModel,
                    dashboardViewModel = dashboardViewModel,
                    windowState = CoverScreenWindowState(
                        widthDp = 372,
                        heightDp = 400,
                        isCoverScreen = true,
                        hasSeparatingHinge = false
                    )
                )
            }
        }

        val rootNode = composeRule.onNodeWithTag(CoverScreenTestTags.MINI_HUB_ROOT)
        rootNode.assertIsDisplayed()

        val semantics = rootNode.fetchSemanticsNode()

        // Verify StateDescription exists and indicates now playing
        val stateDesc = if (SemanticsProperties.StateDescription in semantics.config) {
            semantics.config[SemanticsProperties.StateDescription]
        } else null
        assertNotNull("State description should be present", stateDesc)
        assertTrue("State description should reflect playing state", stateDesc!!.isNotEmpty())

        // Verify ContentDescription exists for gesture hints
        val contentDescList = semantics.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        assertTrue("Content description should contain gesture hints", contentDescList.isNotEmpty())

        // Verify all 6 custom accessibility actions
        val customActions = semantics.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }
        assertEquals(
            "MiniHub root must expose all 6 custom accessibility actions for TalkBack gestures",
            6,
            customActions.size
        )

        // Execute Play/Pause custom action (Action 1)
        val playPauseAction = customActions[0]
        val handledPlayPause = playPauseAction.action()
        assertTrue(handledPlayPause)
        verify(atLeast = 1) { playerViewModel.togglePlayPause() }

        // Execute Next track custom action (Action 2)
        val nextAction = customActions[1]
        val handledNext = nextAction.action()
        assertTrue(handledNext)
        verify(atLeast = 1) { playerViewModel.skipToNext() }

        // Execute Previous track custom action (Action 3)
        val prevAction = customActions[2]
        val handledPrev = prevAction.action()
        assertTrue(handledPrev)
        verify(atLeast = 1) { playerViewModel.skipToPrevious() }

        // Execute Queue custom action (Action 4)
        val queueAction = customActions[3]
        val handledQueue = queueAction.action()
        assertTrue(handledQueue)

        // Execute Dismiss/Close custom action (Action 5)
        val dismissAction = customActions[4]
        val handledDismiss = dismissAction.action()
        assertTrue(handledDismiss)

        // Execute Settings custom action (Action 6)
        val settingsAction = customActions[5]
        val handledSettings = settingsAction.action()
        assertTrue(handledSettings)
    }

    @Test
    fun albumsViewAccessibility_backAndPlayAll_haveRoleButtonAndMinTouchTarget() {
        var backClicked = false
        var playAllClicked = false
        val track = sampleTrack()

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenAlbumsView(
                    album = CoverScreenAlbum(
                        id = "album_1",
                        artistId = "artist_1",
                        name = "Album Accessibility Test",
                        trackCount = 1
                    ),
                    artistName = "Accessible Artist",
                    tracks = listOf(track),
                    onBack = { backClicked = true },
                    onPlayTrack = {},
                    onAddToQueue = {},
                    onRemoveFromQueue = {},
                    onPlayAll = { playAllClicked = true }
                )
            }
        }

        // Verify ALBUM_BACK button
        val backNode = composeRule.onNodeWithTag(CoverScreenTestTags.ALBUM_BACK)
        backNode.assertIsDisplayed()
        backNode.assertWidthIsAtLeast(48.dp)
        backNode.assertHeightIsAtLeast(48.dp)

        val backSemantics = backNode.fetchSemanticsNode()
        val backRole = if (SemanticsProperties.Role in backSemantics.config) {
            backSemantics.config[SemanticsProperties.Role]
        } else null
        assertEquals(Role.Button, backRole)

        backNode.performClick()
        composeRule.runOnIdle { assertTrue(backClicked) }

        // Verify PLAY_ALL button
        val playAllNode = composeRule.onNodeWithTag(CoverScreenTestTags.PLAY_ALL)
        playAllNode.assertIsDisplayed()
        playAllNode.assertHeightIsAtLeast(48.dp)

        val playAllSemantics = playAllNode.fetchSemanticsNode()
        val playAllRole = if (SemanticsProperties.Role in playAllSemantics.config) {
            playAllSemantics.config[SemanticsProperties.Role]
        } else null
        assertEquals(Role.Button, playAllRole)

        playAllNode.performClick()
        composeRule.runOnIdle { assertTrue(playAllClicked) }
    }
}
